package com.mobileassistant.smartvision.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobileassistant.smartvision.db.FaceInfoRepository

class FaceSettingsViewModelFactory(private val repository: FaceInfoRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceSettingsViewModel::class.java)) {
            return FaceSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }
}