package com.mobileassistant.smartvision.ui.detect_faces

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentFaceDetectionBinding
import com.mobileassistant.smartvision.db.FaceInfo
import com.mobileassistant.smartvision.db.FaceInfoDatabase
import com.mobileassistant.smartvision.db.FaceInfoRepository
import com.mobileassistant.smartvision.mlkit.objectdetector.BoxWithText
import com.mobileassistant.smartvision.mlkit.textdetector.ACTIVATED_STATUS_TEXT
import com.mobileassistant.smartvision.mlkit.textdetector.DEACTIVATED_STATUS_TEXT
import com.mobileassistant.smartvision.model.Person
import com.mobileassistant.smartvision.ui.detect_objects.PROCESSING_DELAY_IN_MILLI_SECONDS
import com.mobileassistant.smartvision.ui.detect_objects.TEXT_TO_BE_TRIMMED
import com.mobileassistant.smartvision.ui.detect_objects.TIMEOUT_VALUE_IN_MILLISECONDS
import com.mobileassistant.smartvision.ui.settings.ANNOUNCEMENT_STATUS_KEY
import com.mobileassistant.smartvision.ui.settings.CAM_SERVER_URL_KEY
import com.mobileassistant.smartvision.ui.settings.FaceSettingsViewModel
import com.mobileassistant.smartvision.ui.settings.FaceSettingsViewModelFactory
import com.mobileassistant.smartvision.ui.settings.SMART_VISION_PREFERENCES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.InetAddress
import java.net.URL
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Locale
import kotlin.math.sqrt

private const val NO_FACE_DETECTED_TEXT = "No Face is Detected"
private const val ONE = 1
private const val FACENET_INPUT_IMAGE_SIZE = 112
const val EMPTY = ""

class FaceDetectionFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentFaceDetectionBinding? = null
    private lateinit var camImageView: ImageView
    private lateinit var cardView: CardView
    private lateinit var camTextView: TextView
    private lateinit var errorLayout: LinearLayout
    private lateinit var detailFacesLayout: LinearLayout
    private var sharedPreferences: SharedPreferences? = null
    private var textToSpeech: TextToSpeech? = null
    private var isAnnouncementEnabled: Boolean = false
    private lateinit var camServerUrl: String

    private var faceNetImageProcessor: ImageProcessor? = null
    private var faceNetModelInterpreter: Interpreter? = null
    private var recognisedFaceList: List<Person?> = ArrayList<Person>()

    private lateinit var faceSettingViewModel: FaceSettingsViewModel

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFaceDetectionBinding.inflate(inflater, container, false)
        val root: View = binding.root
        camImageView = binding.camImageView
        cardView = binding.cardView
        camTextView = binding.camDetectedLabel
        errorLayout = binding.errorLayout
        detailFacesLayout = binding.detectedFaceLayout
        sharedPreferences = activity?.getSharedPreferences(
            SMART_VISION_PREFERENCES, Context.MODE_PRIVATE
        )

        activity?.let {
            val faceInfoDAO = FaceInfoDatabase.getInstance(it.application)?.faceInfoDAO
            val repository = FaceInfoRepository(faceInfoDAO!!)
            val factory = FaceSettingsViewModelFactory(repository)
            faceSettingViewModel =
                ViewModelProvider(this, factory)[FaceSettingsViewModel::class.java]
        }

        retrieveSavedFacesFromDatabase()

        try {
            context?.let {
                faceNetModelInterpreter = Interpreter(
                    FileUtil.loadMappedFile(it, "custom_models/mobile_face_net.tflite"),
                    Interpreter.Options()
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        faceNetImageProcessor = ImageProcessor.Builder().add(
            ResizeOp(
                FACENET_INPUT_IMAGE_SIZE, FACENET_INPUT_IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR
            )
        ).add(NormalizeOp(0f, 255f)).build()

        setupUi()
        var postConnectionToastShown = false
        textToSpeech = TextToSpeech(context, this)

        lifecycleScope.launch(Dispatchers.IO) {
            //inetAddress operation is to be made in a background Thread.
            val isPingSuccessful: Boolean = try {
                val inetAddress =
                    InetAddress.getByName(camServerUrl.trim { letter -> letter in TEXT_TO_BE_TRIMMED })
                inetAddress.isReachable(TIMEOUT_VALUE_IN_MILLISECONDS)
            } catch (exception: Exception) {
                false
            }
            if (isPingSuccessful) {
                lifecycleScope.launch(Dispatchers.IO) {
                    while (true) {
                        val downloadedImage = downloadImageFromUrl(camServerUrl)
                        withContext(Dispatchers.Main) {
                            if (!postConnectionToastShown) {
                                Toast.makeText(
                                    context,
                                    getString(R.string.connection_successful_msg),
                                    Toast.LENGTH_SHORT
                                ).show()
                                postConnectionToastShown = true
                            }
                            detectFacesOnImage(downloadedImage)
                        }
                        delay(PROCESSING_DELAY_IN_MILLI_SECONDS)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    showConnectionErrorScreen()
                }
            }
        }
        binding.announcementToggleButton.setOnCheckedChangeListener { _, isChecked ->
            setAnnouncementStatus(isChecked)
        }

        return root
    }

    private fun setupUi() {
        isAnnouncementEnabled =
            sharedPreferences?.getBoolean(ANNOUNCEMENT_STATUS_KEY, false) == true
        binding.announcementToggleButton.isChecked = isAnnouncementEnabled

        camServerUrl =
            sharedPreferences?.getString(CAM_SERVER_URL_KEY, getString(R.string.image_url))
                .toString()
    }

    private fun retrieveSavedFacesFromDatabase() {
        faceSettingViewModel.faces.observe(viewLifecycleOwner) { faceList ->
            recognisedFaceList = createRecognizedFaceList(faceList)
        }
    }

    private fun downloadImageFromUrl(imageServerUrl: String): Bitmap? {
        return try {
            val connection = URL(imageServerUrl).openConnection()
            connection.connect()
            val inputStream = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun detectFacesOnImage(imageBitmap: Bitmap?) {
        val image: InputImage
        imageBitmap?.let { bitmap ->
            try {
                image = InputImage.fromBitmap(bitmap, 0)

                val faceOptionBuilder = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build()

                val faceDetector = FaceDetection.getClient(faceOptionBuilder)

                faceDetector.process(image).addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        camImageView.setImageBitmap(bitmap)
                        cardView.isVisible = true
                        camTextView.text = NO_FACE_DETECTED_TEXT
                    } else {
                        getInfoFromFaceDetected(bitmap, faces)
                    }
                }.addOnFailureListener { e ->
                    camImageView.setImageBitmap(bitmap)
                    cardView.isVisible = true
                }

            } catch (e: IOException) {
                camImageView.setImageBitmap(bitmap)
                cardView.isVisible = true
            }
        }
    }

    private fun getInfoFromFaceDetected(image: Bitmap, faces: List<Face>?) {
        faces?.let {
            val boxes: MutableList<BoxWithText> = mutableListOf()
            var faceRecognized: Pair<Boolean, String> = Pair(false, EMPTY)
            val allAnalyzedFaces: MutableList<Pair<Boolean, String>> = mutableListOf()
            for (face in faces) {
                val faceNumber = (faces.indexOf(face) + ONE).toString()
                // now we have a face, so we can use that to analyse
                val croppedFaceBitmap: Bitmap? = cropToBBox(image, face.boundingBox, 0)

                croppedFaceBitmap?.let {
                    faceRecognized = analyzeCroppedFace(croppedFaceBitmap)
                    allAnalyzedFaces.add(faceRecognized)
                }
                val boxWithText = BoxWithText(faceNumber, face.boundingBox, faceRecognized)
                boxes.add(boxWithText)
            }
            val bitmapDrawn = drawDetectionResult(image, boxes)
            camImageView.setImageBitmap(bitmapDrawn)
            cardView.isVisible = true

            context?.let {
                val faceDetectedText = if (faces.size == ONE) {
                    if (allAnalyzedFaces.isNotEmpty()) {
                        if (allAnalyzedFaces[0].first == true) {
                            getString(R.string.one_face_detected_text) + " Face Found : " + allAnalyzedFaces[0].second
                        } else {
                            getString(R.string.one_face_detected_text)
                        }
                    } else {
                        getString(R.string.one_face_detected_text)
                    }
                } else {
                    val allFoundFaces =
                        allAnalyzedFaces.filter { recognizedFace -> recognizedFace.first == true }

                    var faceFoundString = ""
                    allFoundFaces.forEach { foundFace ->
                        faceFoundString += "\n Face Found : " + foundFace.second
                    }
                    String.format(
                        getString(R.string.more_than_one_face_detected_text), faces.size
                    ) + faceFoundString
                }

                camTextView.text = faceDetectedText
                announceTextToUserIfEnabled(faceDetectedText)
            }
        }
    }

    private fun drawDetectionResult(
        bitmap: Bitmap, detectionResults: List<BoxWithText>
    ): Bitmap? {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT
        for (box in detectionResults) {
            // draw bounding box
            pen.color = if (box.additionalInfo.first) {
                // Use GREEN color if familiar face is recognized
                Color.GREEN
            } else {
                // Use RED color if face is detected.
                Color.RED
            }
            pen.strokeWidth = 8f
            pen.style = Paint.Style.STROKE
            canvas.drawRect(box.rect, pen)
            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            pen.style = Paint.Style.FILL_AND_STROKE
            pen.color = Color.YELLOW
            pen.strokeWidth = 2f
            pen.textSize = 96f
            pen.getTextBounds(box.text, 0, box.text.length, tagSize)
            val fontSize: Float = pen.textSize * box.rect.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) {
                pen.textSize = fontSize
            }
            var margin: Float = (box.rect.width() - tagSize.width()) / 2.0f
            if (margin < 0f) margin = 0f
            canvas.drawText(
                box.text, box.rect.left + margin, (box.rect.top + tagSize.height()).toFloat(), pen
            )
        }
        return outputBitmap
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        textToSpeech?.let {
            it.stop()
            it.shutdown()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech!!.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language not supported!")
            }
        }
    }

    private fun announceTextToUserIfEnabled(textContent: String) {
        if (isAnnouncementEnabled) {
            textToSpeech?.speak(textContent, TextToSpeech.QUEUE_ADD, null, "")
        }
    }

    private fun setAnnouncementStatus(isEnabled: Boolean) {
        val statusText = if (isEnabled) {
            ACTIVATED_STATUS_TEXT
        } else {
            DEACTIVATED_STATUS_TEXT
        }
        textToSpeech?.speak(statusText, TextToSpeech.QUEUE_FLUSH, null, "")
        isAnnouncementEnabled = isEnabled
    }

    private fun showConnectionErrorScreen() {
        errorLayout.isVisible = true
        camImageView.isVisible = false
        cardView.isVisible = false
        detailFacesLayout.isVisible = false
    }

    private fun cropToBBox(image: Bitmap, boundingBox: Rect, rotation: Int): Bitmap? {
        val shift = 0
//        if (rotation != 0) {
//            val matrix = Matrix()
//            matrix.postRotate(rotation.toFloat())
//            croppedImage = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
//        }
        return if (boundingBox.top >= 0 && boundingBox.bottom <= image.width && boundingBox.top + boundingBox.height() <= image.height && boundingBox.left >= 0 && boundingBox.left + boundingBox.width() <= image.width) {
            Bitmap.createBitmap(
                image,
                boundingBox.left,
                boundingBox.top + shift,
                boundingBox.width(),
                boundingBox.height()
            )
        } else null
    }

    // looks for the nearest vector in the dataset (using L2 norm)
    // and returns the pair <name, distance>
    private fun findNearestFace(vector: FloatArray): Pair<String, Float>? {
        var ret: Pair<String, Float>? = null
        for (person in recognisedFaceList) {
            val name: String? = person?.name
            val knownVector: FloatArray? = person?.faceVector
            var distance = 0f
            knownVector?.let {
                for (i in vector.indices) {
                    val diff = vector[i] - knownVector[i]
                    distance += diff * diff
                }
            }
            distance = sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                ret = Pair(name, distance)
            }
        }
        return ret
    }

    private fun analyzeCroppedFace(faceBitmap: Bitmap): Pair<Boolean, String> {
        val tensorImage = TensorImage.fromBitmap(faceBitmap)
        val faceNetByteBuffer: ByteBuffer? = faceNetImageProcessor?.process(tensorImage)?.buffer
        val faceOutputArray = Array(1) {
            FloatArray(
                192
            )
        }

        faceNetModelInterpreter?.run(faceNetByteBuffer, faceOutputArray)
        Log.d("Face recognized", "output array: " + Arrays.deepToString(faceOutputArray))

        if (recognisedFaceList.isNotEmpty()) {
            val result: Pair<String, Float>? = findNearestFace(faceOutputArray[0])
            // if distance is within confidence
            result?.let {
                if (result.second < 1.0f) {
                    val confidence =
                        "${BigDecimal(result.second * 100.0).setScale(2, RoundingMode.FLOOR)}%"
                    return Pair(true, "Person-${result.first} \n Confidence-${confidence}")
                }
            }
        }
        return Pair(false, EMPTY)
    }

    private fun createRecognizedFaceList(retrievedFaceList: List<FaceInfo>): List<Person?> {
        val createdRecognizedFaceList = mutableListOf<Person>()
        retrievedFaceList.forEach { faceInfo ->
            val encodedFaceImage = faceInfo.faceImage
            if (encodedFaceImage.isNotEmpty()) {
                val b = Base64.decode(encodedFaceImage, Base64.DEFAULT)
                val savedFaceBitmapImage = BitmapFactory.decodeByteArray(b, 0, b.size)
                val tensorImage = TensorImage.fromBitmap(savedFaceBitmapImage)
                val faceNetByteBuffer = faceNetImageProcessor!!.process(tensorImage).buffer
                val faceOutputArray = Array(1) {
                    FloatArray(
                        192
                    )
                }
                faceNetModelInterpreter!!.run(faceNetByteBuffer, faceOutputArray)
                createdRecognizedFaceList.add(Person(faceInfo.faceName, faceOutputArray[0]))
            }
        }

        return createdRecognizedFaceList
    }
}