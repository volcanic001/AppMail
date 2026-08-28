package com.david.mailapp.feature.emaildetail.components

import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

internal fun WebSettings.applyHardening(showImages: Boolean, isDark: Boolean) {
    javaScriptEnabled = false
    domStorageEnabled = false
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    mediaPlaybackRequiresUserGesture = true
    cacheMode = WebSettings.LOAD_NO_CACHE
    blockNetworkImage = !showImages
    blockNetworkLoads = !showImages

    // Configuración de zoom y viewport para que los correos (especialmente newsletters con tablas)
    // se adapten al ancho de la pantalla móvil en lugar de verse gigantes o hacer zoom por defecto.
    useWideViewPort = true
    loadWithOverviewMode = true
    textZoom = 100
    builtInZoomControls = true
    displayZoomControls = false
    setSupportZoom(true)

    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, isDark)
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        isAlgorithmicDarkeningAllowed = isDark
    }
}
