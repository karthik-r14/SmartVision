package com.mobileassistant.smartvision.mlkit.objectdetector

import android.graphics.Rect
import android.util.Pair
import com.mobileassistant.smartvision.ui.detect_faces.EMPTY

data class BoxWithText(
    val text: String, val rect: Rect, val additionalInfo: Pair<Boolean, String> = Pair(false, EMPTY)
)
