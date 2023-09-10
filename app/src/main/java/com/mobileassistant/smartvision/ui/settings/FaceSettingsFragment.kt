package com.mobileassistant.smartvision.ui.settings

import android.app.Activity
import android.app.Dialog
import android.content.Context
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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentFaceSettingsBinding
import com.mobileassistant.smartvision.db.FaceInfo
import com.mobileassistant.smartvision.db.FaceInfoDatabase
import com.mobileassistant.smartvision.db.FaceInfoRepository
import java.io.ByteArrayOutputStream
import java.io.IOException

class FaceSettingsFragment : Fragment() {

    private var _binding: FragmentFaceSettingsBinding? = null
    private lateinit var addNewFaceButton: Button
    private var sharedPreferences: SharedPreferences? = null
    private var allFacesRecyclerView: RecyclerView? = null
    private var errorFaceImageView: ImageView? = null
    private var errorFaceTextView: TextView? = null
    private lateinit var faceSettingViewModel: FaceSettingsViewModel


    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFaceSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        activity?.let {
            val faceInfoDAO = FaceInfoDatabase.getInstance(it.application)?.faceInfoDAO
            val repository = FaceInfoRepository(faceInfoDAO!!)
            val factory = FaceSettingsViewModelFactory(repository)
            faceSettingViewModel =
                ViewModelProvider(this, factory)[FaceSettingsViewModel::class.java]
        }
        allFacesRecyclerView = binding.allFacesRecyclerView
        addNewFaceButton = binding.addNewFaceButton
        sharedPreferences = activity?.getSharedPreferences(
            SMART_VISION_PREFERENCES, Context.MODE_PRIVATE
        )
        errorFaceImageView = binding.errorFaceImageView
        errorFaceTextView = binding.errorFaceTextview

        initOnClickListener()
        initObserver()

        return root
    }

    private fun setupUi(faceList: List<FaceInfo>) {
        val facesAdapter = context?.let {
            FacesAdapter(
                it, faceList, ::onDeleteButtonClickListener, ::onEditButtonClickListener
            )
        }
        allFacesRecyclerView?.adapter = facesAdapter

        if (faceList.isEmpty()) {
            errorFaceImageView?.visibility = VISIBLE
            errorFaceTextView?.visibility = VISIBLE
        } else {
            allFacesRecyclerView?.let {
                it.layoutManager = GridLayoutManager(context, 2)
                it.visibility = VISIBLE
                errorFaceImageView?.visibility = GONE
                errorFaceTextView?.visibility = GONE
            }
        }
    }

    private fun onDeleteButtonClickListener(faceInfo: FaceInfo) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setMessage(
            String.format(getString(R.string.dialog_title_msg), faceInfo.faceName)
        ).setPositiveButton(R.string.ok_text) { _, _ ->
            faceSettingViewModel.deleteFace(faceInfo)
        }.setNegativeButton(R.string.cancel_text) { _, _ ->
            // do nothing
        }.show()
    }

    private fun onEditButtonClickListener(faceInfo: FaceInfo) {
        val base64 = Base64.decode(faceInfo.faceImage, Base64.DEFAULT)
        val bitmapImage = BitmapFactory.decodeByteArray(base64, 0, base64.size)
        showCroppedFaceInCustomDialog(
            faceInfo.id, bitmapImage, faceInfo.faceName, isEditMode = true
        )
    }

    private fun initOnClickListener() {
        addNewFaceButton.setOnClickListener { launchCameraForTakingPicture() }
    }

    private fun initObserver() {
        faceSettingViewModel.faces.observe(viewLifecycleOwner) { faceList ->
            setupUi(faceList)
        }
    }

    private fun launchCameraForTakingPicture() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            val pictureClicked = data?.extras?.get("data") as Bitmap
            detectFacesOnImage(pictureClicked)
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
                        showNoFaceToastMessage()
                    } else {
                        getInfoFromFaceDetected(bitmap, faces)
                    }
                }.addOnFailureListener { e ->
                    showNoFaceToastMessage()
                }

            } catch (e: IOException) {
                showNoFaceToastMessage()
            }
        }
    }

    private fun getInfoFromFaceDetected(image: Bitmap, faces: List<Face>?) {
        faces?.let {
            var croppedFaceBitmap: Bitmap? = null
            for (face in faces) {
                croppedFaceBitmap = cropToBBox(image, face.boundingBox, 0)
            }
            if (croppedFaceBitmap != null) {
                showCroppedFaceInCustomDialog(croppedFaceBitmap = croppedFaceBitmap)
            } else {
                Toast.makeText(context, R.string.unable_to_crop_image_msg, Toast.LENGTH_LONG).show()
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

    private fun showNoFaceToastMessage() =
        Toast.makeText(context, R.string.no_face_detected_text, Toast.LENGTH_LONG).show()

    private fun showCroppedFaceInCustomDialog(
        faceId: Int? = null,
        croppedFaceBitmap: Bitmap?,
        faceName: String? = null,
        isEditMode: Boolean = false
    ) {
        val dialog = context?.let { Dialog(it) }
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog?.setContentView(R.layout.custom_face_dialog)
        dialog?.setCancelable(false)
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog?.window!!.attributes)
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        val faceImageView = dialog.findViewById<ImageView>(R.id.face_image)
        val faceEditText = dialog.findViewById<EditText>(R.id.face_name_edit_text)
        val okButton = dialog.findViewById<Button>(R.id.ok_button)
        val cancelButton = dialog.findViewById<Button>(R.id.cancel_button)
        val noteTextView = dialog.findViewById<TextView>(R.id.note_textview)

        if (isEditMode) {
            noteTextView.visibility = VISIBLE
        }

        faceName?.let {
            faceEditText.setText(faceName)
        }

        okButton.setOnClickListener {
            saveOrUpdateFamiliarFaceInDatabaseBasedOnFlag(
                faceId, faceEditText.text.toString(), croppedFaceBitmap, isEditMode
            )
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        faceImageView.setImageBitmap(croppedFaceBitmap)
        dialog.show()
        dialog.window!!.attributes = layoutParams
    }

    private fun saveOrUpdateFamiliarFaceInDatabaseBasedOnFlag(
        faceId: Int?, faceName: String, faceImage: Bitmap?, isEditMode: Boolean
    ) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        faceImage?.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)

        val compressImage = byteArrayOutputStream.toByteArray()
        val sEncodedImage: String = Base64.encodeToString(compressImage, Base64.DEFAULT)

        if (isEditMode) {
            faceSettingViewModel.updateFace(FaceInfo(faceId, faceName, sEncodedImage))
        } else {
            faceSettingViewModel.insertFace(FaceInfo(null, faceName, sEncodedImage))
        }
    }
}