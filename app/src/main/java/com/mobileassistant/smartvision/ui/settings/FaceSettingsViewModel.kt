package com.mobileassistant.smartvision.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileassistant.smartvision.db.FaceInfo
import com.mobileassistant.smartvision.db.FaceInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FaceSettingsViewModel(private val repository: FaceInfoRepository) : ViewModel() {

    val faces = repository.faces

    fun insertFace(faceInfo: FaceInfo) =
        viewModelScope.launch(Dispatchers.IO) { repository.insertFace(faceInfo) }

    fun updateFace(faceInfo: FaceInfo) =
        viewModelScope.launch(Dispatchers.IO) { repository.updateFace(faceInfo) }

    fun deleteFace(faceInfo: FaceInfo) =
        viewModelScope.launch(Dispatchers.IO) { repository.deleteFace(faceInfo) }

    fun deleteAllFaces() = viewModelScope.launch(Dispatchers.IO) { repository.deleteAllFaces() }

}