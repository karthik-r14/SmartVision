package com.mobileassistant.smartvision.ui.scan_code

import android.content.Context
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
import androidx.navigation.fragment.findNavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentScanCodeBinding
import com.mobileassistant.smartvision.mlkit.barcodescanner.BarcodeScannerProcessor
import com.mobileassistant.smartvision.mlkit.utils.CameraSource
import com.mobileassistant.smartvision.mlkit.utils.CameraSourcePreview
import com.mobileassistant.smartvision.mlkit.utils.GraphicOverlay
import com.mobileassistant.smartvision.ui.reading_mode.ReadingModeFragment
import java.io.IOException
import java.util.Locale

private const val COLLECTION_PATH = "products"

class ScanCodeFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentScanCodeBinding? = null
    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var textToSpeech: TextToSpeech? = null
    private lateinit var database: FirebaseFirestore

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanCodeBinding.inflate(inflater, container, false)
        preview = binding.previewView
        graphicOverlay = binding.graphicOverlay
        textToSpeech = TextToSpeech(context, this)
        database = Firebase.firestore

        if (allPermissionsGranted()) {
            createCameraSource()
            startCameraSource()
        } else {
            runtimePermissions
        }

        return binding.root
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(activity, graphicOverlay)
        }

        binding.productInfoButton.setOnClickListener {
            onProductInfoButtonClicked()
        }

        try {
            context?.let {
                val barcodeScannerProcessor = BarcodeScannerProcessor(it)
                barcodeScannerProcessor.isCodeScannedLiveData.observe(
                    viewLifecycleOwner
                ) { barcode ->
                    barcode?.let {
                        with(binding) {
                            productInfoButton.isEnabled = true
                            barcodeValue.text = barcode.displayValue
                        }
                    } ?: run {
                        binding.productInfoButton.isEnabled = false
                    }
                }
                cameraSource!!.setMachineLearningFrameProcessor(barcodeScannerProcessor)
            }
        } catch (e: Exception) {
            Log.e("ScanCodeFragment", "Can not create image processor:", e)
            Toast.makeText(
                context, "Can not create image processor: " + e.message, Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun onProductInfoButtonClicked() {
        val barcodeValue = binding.barcodeValue.text
        val docRef = database.collection(COLLECTION_PATH).document(barcodeValue.toString())
        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val product = documentSnapshot.toObject(ProductInfo::class.java)
                binding.productInfo.text = getString(
                    R.string.product_info,
                    product?.productName,
                    product?.productType,
                    product?.productCost
                )
                textToSpeech?.speak(binding.productInfo.text, TextToSpeech.QUEUE_ADD, null, "")
            } else {
                binding.productInfo.text = getString(R.string.product_not_found_msg)
                textToSpeech?.speak(binding.productInfo.text, TextToSpeech.QUEUE_ADD, null, "")
            }
        }.addOnFailureListener { exception ->
            binding.productInfo.text = getString(R.string.product_not_found_msg)
            textToSpeech?.speak(binding.productInfo.text, TextToSpeech.QUEUE_ADD, null, "")
        }
    }

    private fun startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d("ScanCodeFragment", "resume: Preview is null")
                }
                if (graphicOverlay == null) {
                    Log.d("ScanCodeFragment", "resume: graphOverlay is null")
                }
                preview!!.start(cameraSource, graphicOverlay)
            } catch (e: IOException) {
                Log.e("ScanCodeFragment", "Unable to start camera source.", e)
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
                        it,
                        allNeededPermissions.toTypedArray(),
                        ReadingModeFragment.PERMISSION_REQUESTS
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
            Log.i("ScanCodeFragment", "Permission granted: $permission")
            return true
        }
        Log.i("ScanCodeFragment", "Permission NOT granted: $permission")
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        Log.i("ScanCodeFragment", "Permission granted!")
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