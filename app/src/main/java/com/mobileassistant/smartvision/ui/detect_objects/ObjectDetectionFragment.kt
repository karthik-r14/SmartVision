package com.mobileassistant.smartvision.ui.detect_objects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentObjectDetectionBinding
import com.mobileassistant.smartvision.mlkit.textdetector.ACTIVATED_STATUS_TEXT
import com.mobileassistant.smartvision.mlkit.textdetector.DEACTIVATED_STATUS_TEXT
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.util.Locale

private const val MIN_CONFIDENCE_THRESHOLD = 0.7f

private const val NO_OBJECT_DETECTED_TEXT = "No Object is Detected"

class ObjectDetectionFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentObjectDetectionBinding? = null
    private lateinit var camImageView: ImageView
    private lateinit var camTextView: TextView
    private var textToSpeech: TextToSpeech? = null
    private var isAnnouncementEnabled: Boolean = false

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
//        val smartCapViewModel = ViewModelProvider(this).get(SmartCapViewModel::class.java)

        _binding = FragmentObjectDetectionBinding.inflate(inflater, container, false)
        val root: View = binding.root
        camImageView = binding.camImageView
        camTextView = binding.camDetectedLabel
        textToSpeech = TextToSpeech(context, this)
//        val textView: TextView = binding.textGallery
//        galleryViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }

        lifecycleScope.launch(IO) {
            while (true) {
                val downloadedImage = downloadImageFromUrl(getString(R.string.image_url))
                withContext(Main) {
                    camImageView.setImageBitmap(downloadedImage)
                    detectObstaclesOnImage(downloadedImage)
                }
            }
        }

        binding.announcementToggleButton.setOnCheckedChangeListener { _, isChecked ->
            setAnnouncementStatus(isChecked)
        }

        return root
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

    private fun detectObstaclesOnImage(videoBitmap: Bitmap?) {
        val image: InputImage
        videoBitmap?.let { bitmap ->
            try {
                image = InputImage.fromBitmap(bitmap, 0)
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
                            detectedLabelText += "Object is : $text ---- Confidence : $confidence \n"
                            announceTextToUserIfEnabled(textContent = text)
                        }
                        camTextView.text = detectedLabelText
                    }
                }.addOnFailureListener { e ->
                    Log.i(
                        "com.example.mobileassistantsample", "Failure Exception = $e"
                    )
                }

            } catch (e: IOException) {
                Log.i(
                    "com.example.mobileassistantsample", "IO Exception = $e"
                )
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
}