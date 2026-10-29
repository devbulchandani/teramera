package com.example.teramera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.teramera.R

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: FontWeight) = Font(
    resId = R.font.bricolage_grotesque,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun instrument(weight: FontWeight) = Font(
    resId = R.font.instrument_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DisplayFamily = FontFamily(
    bricolage(FontWeight.Normal),
    bricolage(FontWeight.SemiBold),
    bricolage(FontWeight.Bold),
    bricolage(FontWeight.ExtraBold),
)

val UiFamily = FontFamily(
    instrument(FontWeight.Normal),
    instrument(FontWeight.Medium),
    instrument(FontWeight.SemiBold),
    instrument(FontWeight.Bold),
)

// Scale mirrors tokens/colors_and_type.css: 12/13/15/17/30/44.
// Every money style is tabular so balances don't jitter as they update.
val Typography = Typography(
    displayLarge = TextStyle( // hero money figures, 44
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle( // 30
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum",
    ),
    titleLarge = TextStyle( // screen titles, 30 Bricolage 700
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.8).sp,
    ),
    titleMedium = TextStyle( // 17 section heads / group names
        fontFamily = UiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontFeatureSettings = "tnum",
    ),
    bodyLarge = TextStyle( // primary body, 15
        fontFamily = UiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle( // 13 secondary
        fontFamily = UiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle( // 12 captions / row metadata
        fontFamily = UiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    ),
    labelLarge = TextStyle( // buttons
        fontFamily = UiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle( // chips, 13 semibold
        fontFamily = UiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle( // captions, 12 semibold
        fontFamily = UiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

private fun TextStyle(
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
    fontFeatureSettings: String? = null,
) = androidx.compose.ui.text.TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = fontFeatureSettings,
)
