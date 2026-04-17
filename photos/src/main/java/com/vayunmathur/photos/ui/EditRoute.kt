package com.vayunmathur.photos.ui

import com.vayunmathur.library.util.NavKey
import com.vayunmathur.photos.data.DrawingTool
import kotlinx.serialization.Serializable

@Serializable
sealed interface EditRoute : NavKey {
    @Serializable
    data class EditPhoto(val id: Long, val uri: String? = null) : EditRoute

    @Serializable
    data class DrawingSettings(
        val tool: DrawingTool,
        val currentColor: Int,
        val currentThickness: Float,
        val currentOpacity: Float
    ) : EditRoute
}
