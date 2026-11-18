package com.mydrop.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mydrop.vpn.shared.R

/*
 * Three voices, two superfamilies.
 *
 * The poster voice is Roboto Flex instanced at wght 880 / wdth 90 / opsz 144: heavy, slightly
 * narrowed, optically sized for large settings. System Roboto cannot reach it — there is no
 * width axis and nothing above Black — which is why the faces are bundled rather than requested
 * from the platform.
 *
 * Static instances rather than the variable file: pinning the axes at build time drops every
 * delta table, so all five faces together come to ~170 KB instead of ~1.8 MB, and none of the
 * runtime variable-font API is involved.
 */

private val Ui = FontFamily(
    Font(R.font.roboto_flex_regular, FontWeight.Normal),
    Font(R.font.roboto_flex_medium, FontWeight.Medium),
    Font(R.font.roboto_flex_semibold, FontWeight.SemiBold),
)

/** Digits that line up in columns: latency, throughput, the session timer. */
val MonoFamily = FontFamily(Font(R.font.roboto_mono_medium, FontWeight.Medium))

/**
 * Trimmed leading. Poster type set at 0.86 line height only looks right once the font's own
 * ascent/descent padding is out of the way, and [LineHeightStyle.Trim] needs
 * `includeFontPadding = false` to have anything to trim.
 */
@Suppress("DEPRECATION")
private val TightPlatform = PlatformTextStyle(includeFontPadding = false)

private fun ui(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = 1.42f,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = Ui,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    platformStyle = TightPlatform,
)

val MyDropTypography = Typography(
    // Avaca sets everything in the UI face, headlines included, at Medium at most. The poster face
    // (Roboto Flex at wght 880) read as shouting on a calm graphite screen — most of all in
    // settings, where every section title was one.
    displayLarge = ui(40, FontWeight.Medium, lineHeight = 1.15f, tracking = -0.01f),
    displayMedium = ui(34, FontWeight.Medium, lineHeight = 1.15f, tracking = -0.01f),
    displaySmall = ui(28, FontWeight.Medium, lineHeight = 1.2f, tracking = -0.005f),

    headlineLarge = ui(26, FontWeight.Medium, lineHeight = 1.2f),
    headlineMedium = ui(22, FontWeight.Medium, lineHeight = 1.25f),
    headlineSmall = ui(20, FontWeight.Medium, lineHeight = 1.25f),

    titleLarge = ui(19, FontWeight.Medium, lineHeight = 1.3f),
    titleMedium = ui(16, FontWeight.Medium, lineHeight = 1.35f),
    titleSmall = ui(14, FontWeight.Medium, lineHeight = 1.35f),

    bodyLarge = ui(16),
    bodyMedium = ui(14),
    bodySmall = ui(12, lineHeight = 1.35f),

    labelLarge = ui(14, FontWeight.Medium, lineHeight = 1.2f),
    labelMedium = ui(12, FontWeight.Medium, lineHeight = 1.2f, tracking = 0.02f),
    // Uppercase micro-labels ("ПРИЁМ", "МС") need the extra tracking to stay readable.
    labelSmall = ui(10, FontWeight.Medium, lineHeight = 1.2f, tracking = 0.08f),
)

/** Tabular figures for anything that has to line up or tick without jitter. */
val MonoStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.02f).em,
    platformStyle = TightPlatform,
)
