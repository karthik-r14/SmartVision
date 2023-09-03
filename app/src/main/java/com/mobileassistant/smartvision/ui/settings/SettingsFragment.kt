package com.mobileassistant.smartvision.ui.settings

import android.app.Activity.RESULT_OK
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentSettingsBinding
import com.mobileassistant.smartvision.ui.detect_faces.EMPTY
import java.io.ByteArrayOutputStream
import java.io.IOException


const val SMART_VISION_PREFERENCES = "smart_vision_pref"
const val ANNOUNCEMENT_STATUS_KEY = "announcement_status_key"
const val CAM_SERVER_URL_KEY = "cam_server_url_key"
const val OBJECT_DETECTION_MODE_KEY = "object_detection_mode_key"
const val MIN_CONFIDENCE_THRESHOLD_KEY = "min_confidence_threshold_key"
val minConfidenceThresholdArray = arrayOf("50", "60", "70", "80", "90")
const val DEFAULT_CONFIDENCE_POSITION = 2
const val CAMERA_REQUEST = 100
const val FACE_IMAGE_KEY = "face_image_key"
const val FACE_NAME_KEY = "face_name_key"


class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private var announcementSwitch: SwitchMaterial? = null
    private var camServerUrlEditText: EditText? = null
    private var objectDetectionModeRadioGroup: RadioGroup? = null
    private var detectObjectsRadioBtn: RadioButton? = null
    private var trackObjectsRadioBtn: RadioButton? = null
    private var confidenceSelectionSpinner: Spinner? = null
    private var restoreDefaultButton: Button? = null
    private var addFaceButton: Button? = null
    private var familiarFaceImageView: ImageView? = null
    private var faceNameEditText: EditText? = null
    private var sharedPreferences: SharedPreferences? = null
    private var prefEditor: SharedPreferences.Editor? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        announcementSwitch = binding.announcementStatusSwitch
        objectDetectionModeRadioGroup = binding.objectDetectionModeRadioGroup
        detectObjectsRadioBtn = binding.detectObjectsRadioBtn
        trackObjectsRadioBtn = binding.trackObjectsRadioBtn
        camServerUrlEditText = binding.camServerUrlEditText
        confidenceSelectionSpinner = binding.confidenceSelectionSpinner
        restoreDefaultButton = binding.restoreDefaultBtn
        addFaceButton = binding.addFaceButton
        familiarFaceImageView = binding.familiarFaceImageview
        faceNameEditText = binding.faceNameEditText
        sharedPreferences = activity?.getSharedPreferences(SMART_VISION_PREFERENCES, MODE_PRIVATE)

        setupUi()
        setOnClickListener()

        return root
    }

    private fun setupUi() {
        val announcementStatus = sharedPreferences?.getBoolean(ANNOUNCEMENT_STATUS_KEY, false)
        announcementSwitch?.isChecked = announcementStatus == true

        val camServerUrl =
            sharedPreferences?.getString(CAM_SERVER_URL_KEY, getString(R.string.image_url))
        camServerUrlEditText?.setText(camServerUrl)

        val objectDetectionMode = sharedPreferences?.getBoolean(OBJECT_DETECTION_MODE_KEY, true)
        detectObjectsRadioBtn?.isChecked = objectDetectionMode == true
        trackObjectsRadioBtn?.isChecked = objectDetectionMode == false

        val minThresholdConfidencePosition = sharedPreferences?.getInt(
            MIN_CONFIDENCE_THRESHOLD_KEY, DEFAULT_CONFIDENCE_POSITION
        ) // position 2 corresponds to 70 percent Confidence in the array given above


        val confidenceAdapter = activity?.let {
            ArrayAdapter(
                it, android.R.layout.simple_spinner_dropdown_item, minConfidenceThresholdArray
            )
        }
        confidenceSelectionSpinner?.adapter = confidenceAdapter
        minThresholdConfidencePosition?.let { position ->
            confidenceSelectionSpinner?.setSelection(
                position
            )
        }

        val faceName = sharedPreferences?.getString(FACE_NAME_KEY, EMPTY)
        faceNameEditText?.setText(faceName)

        confidenceSelectionSpinner?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, position: Int, id: Long
                ) {
                    val prefEditor = sharedPreferences?.edit()
                    prefEditor?.putInt(MIN_CONFIDENCE_THRESHOLD_KEY, position)
                    prefEditor?.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // write code to perform some action
                }
            }

        retrieveFaceImageFromSharedPreferencesAndSetImageview()
    }

    private fun setOnClickListener() {
        prefEditor = sharedPreferences?.edit()
        announcementSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefEditor?.putBoolean(ANNOUNCEMENT_STATUS_KEY, isChecked)
            prefEditor?.apply()
        }

        camServerUrlEditText?.doAfterTextChanged {
            prefEditor?.putString(CAM_SERVER_URL_KEY, camServerUrlEditText?.text.toString())
            prefEditor?.apply()
        }

        faceNameEditText?.doAfterTextChanged {
            prefEditor?.putString(FACE_NAME_KEY, faceNameEditText?.text.toString())
            prefEditor?.apply()
        }

        objectDetectionModeRadioGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.detectObjectsRadioBtn -> prefEditor?.putBoolean(
                    OBJECT_DETECTION_MODE_KEY, true
                )

                R.id.trackObjectsRadioBtn -> prefEditor?.putBoolean(
                    OBJECT_DETECTION_MODE_KEY, false
                )
            }
            prefEditor?.apply()
        }

        addFaceButton?.setOnClickListener {
            launchCameraForTakingPicture()
        }

        restoreDefaultButton?.setOnClickListener {
            // reset announcement status
            announcementSwitch?.isChecked = false
            prefEditor?.putBoolean(ANNOUNCEMENT_STATUS_KEY, false)

            // reset cam server url
            camServerUrlEditText?.setText(getString(R.string.image_url))
            prefEditor?.putString(CAM_SERVER_URL_KEY, getString(R.string.image_url))

            // reset object detection settings
            detectObjectsRadioBtn?.isChecked = true
            prefEditor?.putBoolean(
                OBJECT_DETECTION_MODE_KEY, true
            )

            // reset min confidence threshold
            confidenceSelectionSpinner?.setSelection(DEFAULT_CONFIDENCE_POSITION)
            prefEditor?.putInt(MIN_CONFIDENCE_THRESHOLD_KEY, DEFAULT_CONFIDENCE_POSITION)

            prefEditor?.putString(FACE_IMAGE_KEY, EMPTY)
            prefEditor?.putString(FACE_NAME_KEY, EMPTY)
            familiarFaceImageView?.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources, R.drawable.face_recognition, null
                )
            )
            faceNameEditText?.setText(EMPTY)

            prefEditor?.apply()
        }
    }

    private fun launchCameraForTakingPicture() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            val pictureClicked = data?.extras?.get("data") as Bitmap
            detectFacesOnImage(pictureClicked)
        }
    }

    private fun saveImageInSharedPreferences(image: Bitmap) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)

        val compressImage = byteArrayOutputStream.toByteArray()
        val sEncodedImage: String = Base64.encodeToString(compressImage, Base64.DEFAULT)

        prefEditor?.putString(FACE_IMAGE_KEY, sEncodedImage)
        prefEditor?.apply()
    }

    private fun retrieveFaceImageFromSharedPreferencesAndSetImageview() {
        val encodedImage: String? = sharedPreferences?.getString(FACE_IMAGE_KEY, EMPTY)
        if (encodedImage?.isNotEmpty() == true) {
            val b = Base64.decode(encodedImage, Base64.DEFAULT)
            val bitmapImage = BitmapFactory.decodeByteArray(b, 0, b.size)
            familiarFaceImageView?.setImageBitmap(bitmapImage)
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
                        familiarFaceImageView?.setImageBitmap(bitmap)
                    } else {
                        getInfoFromFaceDetected(bitmap, faces)
                    }
                }.addOnFailureListener { e ->
                    familiarFaceImageView?.setImageBitmap(bitmap)
                }

            } catch (e: IOException) {
                familiarFaceImageView?.setImageBitmap(bitmap)
            }
        }
    }

    private fun getInfoFromFaceDetected(image: Bitmap, faces: List<Face>?) {
        faces?.let {
            var croppedFaceBitmap: Bitmap? = null
            for (face in faces) {
                croppedFaceBitmap = cropToBBox(image, face.boundingBox, 0)
            }
            familiarFaceImageView?.setImageBitmap(croppedFaceBitmap)
            if (croppedFaceBitmap != null) {
                saveImageInSharedPreferences(croppedFaceBitmap)
            }
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}