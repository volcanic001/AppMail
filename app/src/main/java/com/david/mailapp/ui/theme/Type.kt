package com.david.mailapp.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.david.mailapp.R

/**
 * Material Expressive typography scale.
 *
 * Compared to standard Material3:
 * - Display & headline are bolder (more expressive for hero text)
 * - Body uses slightly more line-height for email readability
 * - Labels are tighter for nav bar icons & badges
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val GoogleSansFamily = FontFamily(
    Font(R.font.google_sans_flex, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.google_sans_flex, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.google_sans_flex, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.google_sans_flex, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.google_sans_flex, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.google_sans_flex, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900)))
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val GoogleSansRoundedFlexFamily = FontFamily(
    Font(R.font.google_sans_flex, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.Setting("ROND", 100f))),
    Font(R.font.google_sans_flex, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500), FontVariation.Setting("ROND", 100f))),
    Font(R.font.google_sans_flex, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.Setting("ROND", 100f))),
    Font(R.font.google_sans_flex, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("ROND", 100f))),
    Font(R.font.google_sans_flex, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.Setting("ROND", 100f))),
    Font(R.font.google_sans_flex, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.Setting("ROND", 100f)))
)

val GoogleSansRoundedStaticFamily = FontFamily(
    Font(R.font.google_sans_rounded_bold, weight = FontWeight.Normal),
    Font(R.font.google_sans_rounded_bold, weight = FontWeight.Medium),
    Font(R.font.google_sans_rounded_bold, weight = FontWeight.SemiBold),
    Font(R.font.google_sans_rounded_bold, weight = FontWeight.Bold),
    Font(R.font.google_sans_rounded_bold, weight = FontWeight.ExtraBold),
    Font(R.font.google_sans_rounded_bold, weight = FontWeight.Black)
)

// Legacy alias for compatibility
val GoogleSansRoundedFamily = GoogleSansRoundedFlexFamily

fun getMailAppTypography(useCustomFont: Boolean): Typography {
    val isPixelDevice = Build.MANUFACTURER.equals("Google", ignoreCase = true)
    val roundedFamily = if (isPixelDevice) GoogleSansRoundedFlexFamily else GoogleSansRoundedStaticFamily
    val family = if (useCustomFont) roundedFamily else FontFamily.Default

    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp, // extra line-height for email readability
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
}
