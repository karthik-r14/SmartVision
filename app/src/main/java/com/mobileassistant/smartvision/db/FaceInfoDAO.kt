package com.mobileassistant.smartvision.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface FaceInfoDAO {

    @Insert
    suspend fun insertFaceInfo(faceInfo: FaceInfo): Long

    @Update
    suspend fun updateFaceInfo(faceInfo: FaceInfo)

    @Delete
    suspend fun deleteFaceInfo(faceInfo: FaceInfo)

    @Query("DELETE FROM face_info_table")
    suspend fun deleteAllFaces()

    @Query("SELECT * FROM face_info_table")
    fun getAllFaces(): LiveData<List<FaceInfo>>
}