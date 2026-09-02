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

internal fun buildHtml(
    body: String,
    showImages: Boolean,
    isDark: Boolean,
    surfaceArgb: Int,
    onSurfaceArgb: Int,
    primaryArgb: Int
): String {
    val bodyRgb = toCssRgb(surfaceArgb)
    val textRgb = if (isDark) "rgb(224, 224, 224)" else "rgb(33, 33, 33)"
    val linkRgb = toCssRgb(primaryArgb)
    val colorScheme = if (isDark) "dark" else "light"
    val hideRemoteImages = if (!showImages) "img:not([src^=\"data:\"]){display:none!important}" else ""
    val cssOverrides = """
  * {
    -webkit-tap-highlight-color: transparent;
    color: var(--text) !important;
    opacity: 1 !important;
    text-shadow: none !important;
  }
  a, a * {
    color: var(--link) !important;
  }
    """.trimIndent()

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
<style>
  :root {
    color-scheme: $colorScheme;
    --bg: $bodyRgb;
    --text: $textRgb;
    --link: $linkRgb;
  }
  $cssOverrides
  body {
    background-color: var(--bg);
    color: var(--text);
    font-family: -apple-system, Roboto, sans-serif;
    font-size: 15px;
    line-height: 1.5;
    margin: 0;
    padding: 8px 0;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }
  img, table { max-width: 100%; height: auto; }
  blockquote {
    border-left: 3px solid var(--link);
    margin-left: 0;
    padding-left: 12px;
    color: var(--text);
  }
  pre, code {
    white-space: pre-wrap;
    word-break: break-all;
  }
  $hideRemoteImages
</style>
</head>
<body>
$body
</body>
</html>
""".trimIndent()
}
