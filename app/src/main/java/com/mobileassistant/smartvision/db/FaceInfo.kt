package com.mobileassistant.smartvision.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_info_table")
data class FaceInfo(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "face_id") val id: Int?,
    @ColumnInfo(name = "face_name") val faceName: String,
    @ColumnInfo(name = "face_image") val faceImage: String
)