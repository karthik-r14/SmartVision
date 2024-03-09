package com.mobileassistant.smartvision.ui.settings

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentSettingsBinding
import com.mobileassistant.smartvision.ui.detect_objects.MODE_DETECT_OBJECTS_POS
import com.mobileassistant.smartvision.ui.detect_objects.MODE_GEMINI_AI_TRACK_POS
import com.mobileassistant.smartvision.ui.detect_objects.MODE_TRACK_OBJECTS_POS


const val SMART_VISION_PREFERENCES = "smart_vision_pref"
const val ANNOUNCEMENT_STATUS_KEY = "announcement_status_key"
const val CAM_SERVER_URL_KEY = "cam_server_url_key"
const val OBJECT_DETECTION_MODE_KEY = "object_detection_mode_key"
const val PROMPT_TEXT_KEY = "prompt_text_key"
const val MIN_CONFIDENCE_THRESHOLD_KEY = "min_confidence_threshold_key"
val minConfidenceThresholdArray = arrayOf("50", "60", "70", "80", "90")
const val DEFAULT_CONFIDENCE_POSITION = 2
const val CAMERA_REQUEST = 100


class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private var announcementSwitch: SwitchMaterial? = null
    private var camServerUrlEditText: EditText? = null
    private var promptEditText: EditText? = null
    private var objectDetectionModeRadioGroup: RadioGroup? = null
    private var detectObjectsRadioBtn: RadioButton? = null
    private var trackObjectsRadioBtn: RadioButton? = null
    private var detectObjectsUsingGeminiRadioBtn: RadioButton? = null
    private var confidenceSelectionSpinner: Spinner? = null
    private var restoreDefaultButton: Button? = null
    private var gotoFaceSettingsButton: Button? = null
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
        detectObjectsUsingGeminiRadioBtn = binding.detectObjectsUsingGeminiRadioBtn
        camServerUrlEditText = binding.camServerUrlEditText
        promptEditText = binding.promptEditText
        confidenceSelectionSpinner = binding.confidenceSelectionSpinner
        restoreDefaultButton = binding.restoreDefaultBtn
        gotoFaceSettingsButton = binding.goToFaceSettingsButton
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

        val objectDetectionMode =
            sharedPreferences?.getInt(OBJECT_DETECTION_MODE_KEY, MODE_DETECT_OBJECTS_POS)
        detectObjectsRadioBtn?.isChecked = objectDetectionMode == MODE_DETECT_OBJECTS_POS
        trackObjectsRadioBtn?.isChecked = objectDetectionMode == MODE_TRACK_OBJECTS_POS
        detectObjectsUsingGeminiRadioBtn?.isChecked =
            objectDetectionMode == MODE_GEMINI_AI_TRACK_POS

        val promptText =
            sharedPreferences?.getString(PROMPT_TEXT_KEY, getString(R.string.default_prompt_text))
        promptEditText?.setText(promptText)

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

        promptEditText?.doAfterTextChanged {
            prefEditor?.putString(PROMPT_TEXT_KEY, promptEditText?.text.toString())
            prefEditor?.apply()
        }

        objectDetectionModeRadioGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.detectObjectsRadioBtn -> prefEditor?.putInt(
                    OBJECT_DETECTION_MODE_KEY, MODE_DETECT_OBJECTS_POS
                )

                R.id.trackObjectsRadioBtn -> prefEditor?.putInt(
                    OBJECT_DETECTION_MODE_KEY, MODE_TRACK_OBJECTS_POS
                )

                R.id.detectObjectsUsingGeminiRadioBtn -> prefEditor?.putInt(
                    OBJECT_DETECTION_MODE_KEY, MODE_GEMINI_AI_TRACK_POS
                )
            }
            prefEditor?.apply()
        }

        gotoFaceSettingsButton?.setOnClickListener {
            findNavController().navigate(R.id.nav_face_settings)
            //            launchCameraForTakingPicture()
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
            prefEditor?.putInt(
                OBJECT_DETECTION_MODE_KEY, MODE_DETECT_OBJECTS_POS
            )

            //reset prompt text
            promptEditText?.setText(getString(R.string.default_prompt_text))
            prefEditor?.putString(PROMPT_TEXT_KEY, getString(R.string.default_prompt_text))

            // reset min confidence threshold
            confidenceSelectionSpinner?.setSelection(DEFAULT_CONFIDENCE_POSITION)
            prefEditor?.putInt(MIN_CONFIDENCE_THRESHOLD_KEY, DEFAULT_CONFIDENCE_POSITION)

            prefEditor?.apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}