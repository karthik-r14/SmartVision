package com.mobileassistant.smartvision.ui.detect_objects

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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentObjectDetectionBinding
import com.mobileassistant.smartvision.mlkit.objectdetector.BoxWithText
import com.mobileassistant.smartvision.mlkit.textdetector.ACTIVATED_STATUS_TEXT
import com.mobileassistant.smartvision.mlkit.textdetector.DEACTIVATED_STATUS_TEXT
import com.mobileassistant.smartvision.ui.gallery.ANNOUNCEMENT_STATUS_KEY
import com.mobileassistant.smartvision.ui.gallery.CAM_SERVER_URL_KEY
import com.mobileassistant.smartvision.ui.gallery.OBJECT_DETECTION_MODE_KEY
import com.mobileassistant.smartvision.ui.gallery.SMART_VISION_PREFERENCES
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.util.Locale


private const val MIN_CONFIDENCE_THRESHOLD = 0.7f
private const val NO_OBJECT_DETECTED_TEXT = "No Object is Detected"
private val modeListArray = arrayOf("Mode-1 Detect Objects", "Mode-2 Track Objects")
private const val MODE_CHANGED_TEXT = "Mode Changed"


private const val MODE_DETECT_OBJECTS_POS = 0
private const val MODE_TRACK_OBJECTS_POS = 1

class ObjectDetectionFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentObjectDetectionBinding? = null
    private lateinit var camImageView: ImageView
    private lateinit var camTextView: TextView
    private lateinit var modeSelectionSpinner: Spinner
    private var sharedPreferences: SharedPreferences? = null
    private var textToSpeech: TextToSpeech? = null
    private var isAnnouncementEnabled: Boolean = false
    private var modeSelected = -1
    private lateinit var camServerUrl: String

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectDetectionBinding.inflate(inflater, container, false)
        val root: View = binding.root
        camImageView = binding.camImageView
        camTextView = binding.camDetectedLabel
        modeSelectionSpinner = binding.modeSelectionSpinner
        sharedPreferences = activity?.getSharedPreferences(
            SMART_VISION_PREFERENCES, Context.MODE_PRIVATE
        )
        setupUi()

        context?.let {
            val modeAdapter =
                ArrayAdapter(it, android.R.layout.simple_spinner_dropdown_item, modeListArray)
            modeSelectionSpinner.adapter = modeAdapter
            modeSelectionSpinner.setSelection(modeSelected)
            modeSelectionSpinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View, position: Int, id: Long
                ) {
                    modeSelected = position
                    textToSpeech?.speak(MODE_CHANGED_TEXT, TextToSpeech.QUEUE_FLUSH, null, "")
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // write code to perform some action
                }
            }
        }
        textToSpeech = TextToSpeech(context, this)

        lifecycleScope.launch(IO) {
            while (true) {
                val downloadedImage = downloadImageFromUrl(camServerUrl)
                withContext(Main) {
                    if (modeSelected == MODE_DETECT_OBJECTS_POS) {
                        detectAndLabelObjectsOnImage(downloadedImage)
                    } else {
                        detectAndTrackObjectsOnImage(downloadedImage)
                    }
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

        modeSelected = if (sharedPreferences?.getBoolean(
                OBJECT_DETECTION_MODE_KEY, true
            ) == true
        ) MODE_DETECT_OBJECTS_POS else MODE_TRACK_OBJECTS_POS

        camServerUrl =
            sharedPreferences?.getString(CAM_SERVER_URL_KEY, getString(R.string.image_url))
                .toString()
    }

    private fun downloadImageFromUrl(imageServerUrl: String): Bitmap? {
        try {
            val url = URL(imageServerUrl)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(url.openStream(), null, options)
            val inSampleSizeVal =
                calculateInSampleSize(options, camImageView.width, camImageView.height)

            val finalOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = inSampleSizeVal
            }

            return BitmapFactory.decodeStream(url.openStream(), null, finalOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun detectAndLabelObjectsOnImage(imageBitmap: Bitmap?) {
        val image: InputImage
        imageBitmap?.let { bitmap ->
            try {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 800, 600, false);
                image = InputImage.fromBitmap(scaledBitmap, 0)

                val optionsBuilder =
                    ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE_THRESHOLD)
                        .build()
                val labeler = ImageLabeling.getClient(optionsBuilder)

                labeler.process(image).addOnSuccessListener { labels ->
                    if (labels.isEmpty()) {
                        val nothingDetectText = NO_OBJECT_DETECTED_TEXT
                        camTextView.text = nothingDetectText
                    } else {
                        var detectedLabelText = ""
                        for (label in labels) {
                            val text = label.text
                            val confidence = label.confidence
                            detectedLabelText += "Object Detected is : $text ---- Confidence : $confidence \n"
                            announceTextToUserIfEnabled(textContent = text)
                        }
                        camTextView.text = detectedLabelText
                    }
                    camImageView.setImageBitmap(scaledBitmap)
                }.addOnFailureListener { e ->
                    camImageView.setImageBitmap(scaledBitmap)
                }

            } catch (e: IOException) {
                camImageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun detectAndTrackObjectsOnImage(imageBitmap: Bitmap?) {
        imageBitmap?.let { bitmap ->
            try {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 800, 600, false);
                val image = InputImage.fromBitmap(scaledBitmap, 0)

                val localModel =
                    LocalModel.Builder().setAssetFilePath("custom_models/object_labeler.tflite")
                        .build()

                val customObjectDetectorOptions = CustomObjectDetectorOptions.Builder(localModel)
                    .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects().enableClassification()
                    .setClassificationConfidenceThreshold(MIN_CONFIDENCE_THRESHOLD)
                    .setMaxPerObjectLabelCount(3).build()

                val objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)
                objectDetector.process(image).addOnSuccessListener { detectedObjects ->
                    val list = mutableListOf<BoxWithText>()
                    val sb = StringBuilder()
                    for (detectedObject in detectedObjects) {
                        for (label in detectedObject.labels) {
                            Log.d(
                                "Tracking",
                                sb.append("Object Tracked : ").append(label.text).append(" : ")
                                    .append(label.confidence).append("\n").toString()
                            )
                        }
                        if (detectedObject.labels.isNotEmpty()) {
                            list.add(
                                BoxWithText(
                                    detectedObject.labels[0].text, detectedObject.boundingBox
                                )
                            )
                            announceTextToUserIfEnabled(textContent = detectedObject.labels[0].text)
                        }
                    }
                    camImageView.setImageBitmap(drawDetectionResult(scaledBitmap, list))

                    if (detectedObjects.isEmpty()) {
                        camImageView.setImageBitmap(scaledBitmap)
                    } else {
                        camTextView.text = sb.toString()
                    }
                }.addOnFailureListener { e ->
                    camImageView.setImageBitmap(scaledBitmap)
                }
            } catch (e: IOException) {
                camImageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int
    ): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
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
}