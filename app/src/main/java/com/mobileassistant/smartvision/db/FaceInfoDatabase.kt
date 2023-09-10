package com.mobileassistant.smartvision.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FaceInfo::class], version = 1)
abstract class FaceInfoDatabase : RoomDatabase() {

    abstract val faceInfoDAO: FaceInfoDAO

    companion object {
        @Volatile
        private var INSTANCE: FaceInfoDatabase? = null
        fun getInstance(context: Context): FaceInfoDatabase? {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        FaceInfoDatabase::class.java,
                        "face_info_database"
                    ).build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }

}