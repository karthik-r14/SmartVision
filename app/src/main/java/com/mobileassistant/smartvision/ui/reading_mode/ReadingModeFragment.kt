package com.mobileassistant.smartvision.ui.reading_mode

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.mobileassistant.smartvision.databinding.FragmentReadingModeBinding
import com.mobileassistant.smartvision.mlkit.textdetector.TextRecognitionProcessor
import com.mobileassistant.smartvision.mlkit.utils.CameraSource
import com.mobileassistant.smartvision.mlkit.utils.CameraSourcePreview
import com.mobileassistant.smartvision.mlkit.utils.GraphicOverlay
import com.mobileassistant.smartvision.ui.settings.ANNOUNCEMENT_STATUS_KEY
import com.mobileassistant.smartvision.ui.settings.SMART_VISION_PREFERENCES
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class ReadingModeFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentReadingModeBinding? = null
    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var textToSpeech: TextToSpeech? = null
    private var sharedPreferences: SharedPreferences? = null
    private var announcementEnabledStatus: Boolean? = false

    companion object {
        private const val TAG = "ReadingModeScreen"
        private const val PERMISSION_REQUESTS = 1
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingModeBinding.inflate(inflater, container, false)
        preview = binding.previewView
        graphicOverlay = binding.graphicOverlay
        textToSpeech = TextToSpeech(context, this)
        sharedPreferences = activity?.getSharedPreferences(
            SMART_VISION_PREFERENCES, Context.MODE_PRIVATE
        )
        setupUi()

        if (allPermissionsGranted()) {
            createCameraSource()
            startCameraSource()
        } else {
            runtimePermissions
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            createCameraSource()
            startCameraSource()
        }
    }

    private fun setupUi() {
        announcementEnabledStatus = sharedPreferences?.getBoolean(ANNOUNCEMENT_STATUS_KEY, false)
        binding.announcementToggleButton.isChecked = announcementEnabledStatus == true
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(activity, graphicOverlay)
        }
        try {
            context?.let {
                lifecycleScope.launch(IO) {
                    val textRecognitionProcessor = TextRecognitionProcessor(
                        it, DevanagariTextRecognizerOptions.Builder().build(), textToSpeech, announcementEnabledStatus
                    )
                    cameraSource!!.setMachineLearningFrameProcessor(textRecognitionProcessor)

                    withContext(Main) {
                        binding.announcementToggleButton.setOnCheckedChangeListener { _, isChecked ->
                            textRecognitionProcessor.setAnnouncementStatus(isChecked)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can not create image processor:", e)
            Toast.makeText(
                context, "Can not create image processor: " + e.message, Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null")
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null")
                }
                preview!!.start(cameraSource, graphicOverlay)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                cameraSource!!.release()
                cameraSource = null
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(context, permission)) {
                return false
            }
        }
        return true
    }

    private val runtimePermissions: Unit
        get() {
            val allNeededPermissions: MutableList<String?> = ArrayList()
            for (permission in requiredPermissions) {
                if (!isPermissionGranted(context, permission)) {
                    allNeededPermissions.add(permission)
                }
            }
            if (allNeededPermissions.isNotEmpty()) {
                activity?.let {
                    ActivityCompat.requestPermissions(
                        it, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS
                    )
                }
            }
        }

    private val requiredPermissions: Array<String?>
        get() = try {
            val info = activity?.packageName?.let {
                activity?.packageManager?.getPackageInfo(it, PackageManager.GET_PERMISSIONS)
            }
            val ps = info?.requestedPermissions
            if (!ps.isNullOrEmpty()) {
                ps
            } else {
                arrayOfNulls(0)
            }
        } catch (e: Exception) {
            arrayOfNulls(0)
        }

    private fun isPermissionGranted(
        context: Context?, permission: String?
    ): Boolean {
        if (context?.let {
                ContextCompat.checkSelfPermission(
                    it, permission!!
                )
            } == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        Log.i(TAG, "Permission granted!")
        if (allPermissionsGranted()) {
            createCameraSource()
            startCameraSource()
        } else {
            findNavController().navigateUp()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
}