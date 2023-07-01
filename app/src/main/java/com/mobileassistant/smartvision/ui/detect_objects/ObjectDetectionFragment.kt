package com.mobileassistant.smartvision.ui.detect_objects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val MIN_CONFIDENCE_THRESHOLD = 0.7f

class ObjectDetectionFragment : Fragment() {

    private var _binding: FragmentObjectDetectionBinding? = null
    private var camImageView: ImageView? = null
    private var camTextView: TextView? = null

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
//        val textView: TextView = binding.textGallery
//        galleryViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }

        lifecycleScope.launch(IO) {
            while (true) {
                val downloadedImage = downloadImageFromUrl(getString(R.string.image_url))
                withContext(Main) {
                    camImageView?.setImageBitmap(downloadedImage)
                    detectObstaclesOnImage(downloadedImage)
                }
            }
        }

        return root
    }

    private fun downloadImageFromUrl(imageServerUrl: String): Bitmap? {
        try {
            val url = URL(imageServerUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()
            val inputStream = connection.inputStream
            return BitmapFactory.decodeStream(inputStream)
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
                    var detectedLabelText = ""
                    for (label in labels) {
                        val text = label.text
                        val confidence = label.confidence
                        detectedLabelText += "Object is : $text ---- Confidence : $confidence \n"
                    }
                    camTextView?.text = detectedLabelText
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}