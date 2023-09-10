package com.mobileassistant.smartvision.db

class FaceInfoRepository(private val dao: FaceInfoDAO) {

    val faces = dao.getAllFaces()

    suspend fun insertFace(faceInfo: FaceInfo) {
        dao.insertFaceInfo(faceInfo)
    }

    suspend fun updateFace(faceInfo: FaceInfo) {
        dao.updateFaceInfo(faceInfo)
    }

    suspend fun deleteFace(faceInfo: FaceInfo) {
        dao.deleteFaceInfo(faceInfo)
    }

    suspend fun deleteAllFaces() {
        dao.deleteAllFaces()
    }
}