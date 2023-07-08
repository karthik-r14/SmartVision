package com.mobileassistant.smartvision.ui.gallery

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentSettingsBinding

const val SMART_VISION_PREFERENCES = "smart_vision_pref"
const val ANNOUNCEMENT_STATUS_KEY = "announcement_status_key"
const val SMART_CAP_CAM_KEY = "smart_cap_cam_key"
const val CAM_SERVER_URL_KEY = "cam_server_url_key"
const val OBJECT_DETECTION_MODE_KEY = "object_detection_mode_key"

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private var announcementSwitch: SwitchMaterial? = null
    private var smartCapCamRadioGroup: RadioGroup? = null
    private var accessPointModeRadioBtn: RadioButton? = null
    private var commonNetworkModeRadioBtn: RadioButton? = null
    private var camServerUrlEditText: EditText? = null
    private var objectDetectionModeRadioGroup: RadioGroup? = null
    private var detectObjectsRadioBtn: RadioButton? = null
    private var trackObjectsRadioBtn: RadioButton? = null
    private var sharedPreferences: SharedPreferences? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        announcementSwitch = binding.announcementStatusSwitch
        smartCapCamRadioGroup = binding.smartCapCamRadioGroup
        objectDetectionModeRadioGroup = binding.objectDetectionModeRadioGroup
        detectObjectsRadioBtn = binding.detectObjectsRadioBtn
        trackObjectsRadioBtn = binding.trackObjectsRadioBtn
        accessPointModeRadioBtn = binding.accessPointModeRadioBtn
        commonNetworkModeRadioBtn = binding.commonNetworkModeRadioBtn
        camServerUrlEditText = binding.camServerUrlEditText
        sharedPreferences = activity?.getSharedPreferences(SMART_VISION_PREFERENCES, MODE_PRIVATE)

        setupUi()
        setOnClickListener()

        return root
    }

    private fun setupUi() {
        val announcementStatus = sharedPreferences?.getBoolean(ANNOUNCEMENT_STATUS_KEY, false)
        announcementSwitch?.isChecked = announcementStatus == true

        val smartCapCamSetting = sharedPreferences?.getBoolean(SMART_CAP_CAM_KEY, true)
        accessPointModeRadioBtn?.isChecked = smartCapCamSetting == true
        commonNetworkModeRadioBtn?.isChecked = smartCapCamSetting == false
        val camServerUrl =
            sharedPreferences?.getString(CAM_SERVER_URL_KEY, getString(R.string.image_url))
        camServerUrlEditText?.setText(camServerUrl)

        val objectDetectionMode = sharedPreferences?.getBoolean(OBJECT_DETECTION_MODE_KEY, true)
        detectObjectsRadioBtn?.isChecked = objectDetectionMode == true
        trackObjectsRadioBtn?.isChecked = objectDetectionMode == false
    }

    private fun setOnClickListener() {
        val prefEditor = sharedPreferences?.edit()
        announcementSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefEditor?.putBoolean(ANNOUNCEMENT_STATUS_KEY, isChecked)
            prefEditor?.apply()
        }

        smartCapCamRadioGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.accessPointModeRadioBtn -> prefEditor?.putBoolean(
                    SMART_CAP_CAM_KEY, true
                )

                R.id.commonNetworkModeRadioBtn -> prefEditor?.putBoolean(
                    SMART_CAP_CAM_KEY, false
                )
            }
            prefEditor?.apply()
        }

        camServerUrlEditText?.doAfterTextChanged {
            prefEditor?.putString(CAM_SERVER_URL_KEY, camServerUrlEditText?.text.toString())
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}