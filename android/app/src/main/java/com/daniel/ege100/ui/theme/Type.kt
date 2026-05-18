package com.daniel.ege100.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.daniel.ege100.R

/**
 * Шрифт Inter через Google Fonts downloadable. Provider —
 * `com.google.android.gms.fonts` (Google Play Services). На устройствах без
 * GMS (например китайские прошивки) Compose автоматически откатится к
 * системному шрифту.
 *
 * iOS Typography (STAGE_3_POLISH §Б2): крупные заголовки 34/28sp Bold с
 * отрицательным letterSpacing, body 17sp с line-height 24. Близко к
 * SF Pro Display / SF Pro Text.
 */
private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val InterFontName = GoogleFont("Inter")

val InterFamily: FontFamily = FontFamily(
    Font(googleFont = InterFontName, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = InterFontName, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = InterFontName, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFontName, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
)

@Composable
fun appleTypography(): Typography {
    val ff = InterFamily
    return remember(ff) {
        Typography(
            displayLarge = TextStyle(
                fontFamily = ff,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                lineHeight = 41.sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = ff,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                lineHeight = 34.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = ff,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                lineHeight = 28.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = ff,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            ),
            titleSmall = TextStyle(
                fontFamily = ff,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = ff,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 24.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = ff,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 21.sp,
            ),
            bodySmall = TextStyle(
                fontFamily = ff,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = ff,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            labelMedium = TextStyle(
                fontFamily = ff,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            labelSmall = TextStyle(
                fontFamily = ff,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
