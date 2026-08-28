package com.david.mailapp.feature.emaildetail.components

internal data class PreparedDocument(val key: String, val html: String)

internal fun buildLoadKey(
    body: String,
    showImages: Boolean,
    isDark: Boolean,
    surfaceArgb: Int,
    onSurfaceArgb: Int,
    primaryArgb: Int
): String =
    "${body.hashCode()}_${showImages}_${isDark}_${surfaceArgb}_${onSurfaceArgb}_${primaryArgb}"

internal fun toCssRgb(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgb($r,$g,$b)"
}
