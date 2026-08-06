package com.david.mailapp.feature.emaildetail

// ── PdfActionLabels — etiquetas resueltas para callbacks no composables ──

internal data class PdfActionLabels(
    val cacheExpired: String,
    val saved: String,
    val saveFailed: String,
    val noFilePicker: String,
    val pickerOpenFailed: String,
    val noViewer: String,
    val openFailed: String
)
