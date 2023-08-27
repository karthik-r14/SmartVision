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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentFaceDetectionBinding
import com.mobileassistant.smartvision.mlkit.objectdetector.BoxWithText
import com.mobileassistant.smartvision.ui.detect_objects.PROCESSING_DELAY_IN_MILLI_SECONDS
import com.mobileassistant.smartvision.ui.detect_objects.TEXT_TO_BE_TRIMMED
import com.mobileassistant.smartvision.ui.detect_objects.TIMEOUT_VALUE_IN_MILLISECONDS
import com.mobileassistant.smartvision.ui.settings.ANNOUNCEMENT_STATUS_KEY
import com.mobileassistant.smartvision.ui.settings.CAM_SERVER_URL_KEY
import com.mobileassistant.smartvision.ui.settings.SMART_VISION_PREFERENCES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.URL

private const val NO_FACE_DETECTED_TEXT = "No Face is Detected"
private const val ONE = 1

class FaceDetectionFragment : Fragment() {

    private var _binding: FragmentFaceDetectionBinding? = null
    private lateinit var camImageView: ImageView
    private lateinit var camTextView: TextView
    private lateinit var errorLayout: LinearLayout
    private lateinit var detailFacesLayout: LinearLayout
    private var sharedPreferences: SharedPreferences? = null
    private var isAnnouncementEnabled: Boolean = false
    private lateinit var camServerUrl: String

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFaceDetectionBinding.inflate(inflater, container, false)
        val root: View = binding.root
        camImageView = binding.camImageView
        camTextView = binding.camDetectedLabel
        errorLayout = binding.errorLayout
        detailFacesLayout = binding.detectedFaceLayout
        sharedPreferences = activity?.getSharedPreferences(
            SMART_VISION_PREFERENCES, Context.MODE_PRIVATE
        )

        setupUi()
        var postConnectionToastShown = false

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
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()

                val faceDetector = FaceDetection.getClient(faceOptionBuilder)

                faceDetector.process(image).addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        camImageView.setImageBitmap(bitmap)
                        camTextView.text = NO_FACE_DETECTED_TEXT
                    } else {
                        getInfoFromFaceDetected(bitmap, faces)
                    }
                }.addOnFailureListener { e ->
                    camImageView.setImageBitmap(bitmap)
                }

            } catch (e: IOException) {
                camImageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun getInfoFromFaceDetected(image: Bitmap, faces: List<Face>?) {
        faces?.let {
            val boxes: MutableList<BoxWithText> = mutableListOf()
            for (face in faces) {
                val faceNumber = (faces.indexOf(face) + ONE).toString()
                val boxWithText = BoxWithText(faceNumber, face.boundingBox)
                boxes.add(boxWithText)
            }
            val bitmapDrawn = drawDetectionResult(image, boxes)
            camImageView.setImageBitmap(bitmapDrawn)
            camTextView.text = if (faces.size == ONE) {
                getString(R.string.one_face_detected_text)
            } else {
                String.format(getString(R.string.more_than_one_face_detected_text), faces.size)
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
            pen.color = Color.RED
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
    }

    private fun showConnectionErrorScreen() {
        errorLayout.isVisible = true
        camImageView.isVisible = false
        detailFacesLayout.isVisible = false
    }
}