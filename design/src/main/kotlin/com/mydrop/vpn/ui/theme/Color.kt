package com.mydrop.vpn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/*
 * One accent, spent in one place.
 *
 * Glacier blue on a near-black ground: it reads as "protected" without the green-means-on
 * cliché, and it survives a phone screen in sunlight. Connection state is carried by the shape
 * of the flow figure — empty outline, warming, dense — so colour only ever confirms what the
 * form already said. That keeps the tunnel screen legible to a colour-blind user.
 *
 * Neutrals are biased cold rather than left pure grey, so they agree with the accent instead of
 * merely sitting next to it. Sand is the second data colour (upload against download) and coral
 * is reserved for failure; neither is allowed to compete with the ice.
 */

private val Ember = Color(0xFFFF8A3D)
private val EmberInk = Color(0xFF1A0F07)
private val EmberContainer = Color(0xFF3A1F0D)
private val EmberOnContainer = Color(0xFFFFD0AA)
private val EmberDark = Color(0xFFD96F2B)
private val EmberLightContainer = Color(0xFFFFE6CF)

private val Amber = Color(0xFFFFB36B)
private val AmberInk = Color(0xFF2C1606)
private val AmberContainer = Color(0xFF553010)
private val AmberOnContainer = Color(0xFFFFE3C0)
private val AmberDark = Color(0xFFC9782E)
private val AmberLightContainer = Color(0xFFFFEAC8)

private val Ash = Color(0xFFB85A27)
private val AshInk = Color(0xFFFFF2E8)
private val AshContainer = Color(0xFF652C13)
private val AshOnContainer = Color(0xFFFFE3D1)

private val EmberError = Color(0xFFFF7A59)
private val EmberErrorInk = Color(0xFF2B0D06)
private val EmberErrorContainer = Color(0xFF5B1D13)
private val EmberErrorOnContainer = Color(0xFFFFD4CC)

val MyDropDarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = EmberInk,
    primaryContainer = EmberContainer,
    onPrimaryContainer = EmberOnContainer,
    inversePrimary = EmberDark,

    secondary = Amber,
    onSecondary = AmberInk,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,

    tertiary = Ash,
    onTertiary = AshInk,
    tertiaryContainer = AshContainer,
    onTertiaryContainer = AshOnContainer,

    error = EmberError,
    onError = EmberErrorInk,
    errorContainer = EmberErrorContainer,
    onErrorContainer = EmberErrorOnContainer,

    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF4F0EC),
    surface = Color(0xFF0B0B0B),
    onSurface = Color(0xFFF4F0EC),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFB7A79A),
    surfaceTint = Ember,

    surfaceBright = Color(0xFF1A1A1A),
    surfaceDim = Color(0xFF090909),
    surfaceContainerLowest = Color(0xFF070707),
    surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = Color(0xFF151515),
    surfaceContainerHigh = Color(0xFF1D1D1D),
    surfaceContainerHighest = Color(0xFF262626),

    inverseSurface = Color(0xFFF4F0EC),
    inverseOnSurface = Color(0xFF151515),

    outline = Color(0xFF534031),
    outlineVariant = Color(0xFF2C241F),
    scrim = Color.Black,
)

/*
 * The light scheme is a translation, not an inversion: glacier blue at full lightness has no
 * contrast on paper, so the accent drops to its darker sibling while the containers keep the
 * original hue. Everything that was a tone above the ground becomes a tone below it.
 */
val MyDropLightColors = lightColorScheme(
    primary = EmberDark,
    onPrimary = Color.White,
    primaryContainer = EmberLightContainer,
    onPrimaryContainer = Color(0xFF2B1206),
    inversePrimary = Ember,

    secondary = AmberDark,
    onSecondary = Color.White,
    secondaryContainer = AmberLightContainer,
    onSecondaryContainer = Color(0xFF2A1508),

    tertiary = Color(0xFF9A4B1A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBBF),
    onTertiaryContainer = Color(0xFF2D1408),

    error = Color(0xFFB34B2A),
    onError = Color.White,
    errorContainer = Color(0xFFFFD5C8),
    onErrorContainer = Color(0xFF3A1107),

    background = Color(0xFFF8F2ED),
    onBackground = Color(0xFF171210),
    surface = Color(0xFFF8F2ED),
    onSurface = Color(0xFF171210),
    surfaceVariant = Color(0xFFEEDDCB),
    onSurfaceVariant = Color(0xFF5A4337),
    surfaceTint = EmberDark,

    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5D4C7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6EAE0),
    surfaceContainer = Color(0xFFF1E2D7),
    surfaceContainerHigh = Color(0xFFEAD7C7),
    surfaceContainerHighest = Color(0xFFE3CDBB),

    inverseSurface = Color(0xFF1E1A17),
    inverseOnSurface = Color(0xFFF6EEE7),

    outline = Color(0xFFB28B68),
    outlineVariant = Color(0xFFD9B799),
    scrim = Color.Black,
)

/**
 * Colours the M3 roles cannot express: tunnel health and latency quality.
 *
 * [connected] deliberately is not green. The flow figure already says "running" by being full
 * of moving traffic, so the accent stays ice and the palette keeps a single loud colour. The
 * latency ramp is the one place a green/amber/red scale earns its keep — there the colour *is*
 * the measurement, and a list of two dozen servers has to be scannable at a glance.
 */
data class MyDropSemanticColors(
    val connected: Color,
    val onConnected: Color,
    val connectedContainer: Color,
    val onConnectedContainer: Color,
    val connecting: Color,
    val latencyFast: Color,
    val latencyMedium: Color,
    val latencySlow: Color,
    val latencyDead: Color,
    val download: Color,
    val upload: Color,
    /** The cookie turning over the pixel planet; see [markerAccent]. */
    val marker: Color,
)

/**
 * Derives the semantic colours from whichever scheme is active.
 *
 * This is what makes the dynamic-colour switch mean something. The tunnel state, the traffic
 * waves and the control's outline all read from here, so hardcoding them left the screen stubbornly
 * ice-blue in exactly those places while everything else followed the wallpaper — the accent has to
 * come from the scheme, not from a constant beside it. With the built-in palette `primary` *is* the
 * ice blue, so nothing changes there.
 *
 * The latency ramp is the deliberate exception. Green/amber/red is a measurement scale rather than
 * decoration: the colour *is* the reading, and rewriting it from the user's wallpaper would make a
 * fast server indistinguishable from a dead one.
 */
fun ColorScheme.toSemanticColors(dark: Boolean): MyDropSemanticColors = MyDropSemanticColors(
    connected = primary,
    onConnected = onPrimary,
    connectedContainer = primaryContainer,
    onConnectedContainer = onPrimaryContainer,
    connecting = secondary,
    latencyFast = if (dark) Color(0xFFFFB36B) else Color(0xFFB96A1F),
    latencyMedium = if (dark) Amber else AmberDark,
    latencySlow = if (dark) EmberError else Color(0xFFB34B2A),
    latencyDead = if (dark) Color(0xFF7A6F66) else Color(0xFF7A6258),
    // Two channels have to stay apart at a glance; primary and tertiary are the furthest apart
    // any Material scheme guarantees.
    download = primary,
    upload = tertiary,
    marker = markerAccent(),
)

/**
 * The marker on the pixel planet: the one thing in the figure that is not the map.
 *
 * The accent's own hue taken to the top of its lightness range — near white, still unmistakably
 * the colour the rest of the app is painted in. Not a role from the scheme, because no role
 * promises what this needs: the map is drawn in `primary` and `primaryContainer`, the two ends of
 * the accent ramp, and anything the scheme offers in between is a tone that will match one of them
 * in some theme.
 *
 * Two wrong answers came before this one, and both are worth remembering. Picking whichever accent
 * stood furthest away on the colour wheel put a cold blue cookie on warm sand — loud, and reading
 * as something from another application that had landed on the planet by mistake. Then
 * `inversePrimary`, which is the accent at the *opposite* lightness to the land: correct against
 * the land by construction, and on a dark theme that means a dark shape, which vanished the moment
 * it crossed the sea.
 *
 * Brighter than both is the answer that holds. In a dark scheme the land is light and the sea is
 * deep, and this is lighter than the land. In a light scheme the land is dark, so it is lighter
 * still, and only the pale sea comes close — which is what the shadow under it is for.
 */
private fun ColorScheme.markerAccent(): Color {
    val red = primary.red
    val green = primary.green
    val blue = primary.blue
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    val span = high - low
    // A grey accent has no hue to keep, and asking for one would invent a colour the theme does
    // not contain. Monochrome wallpaper schemes are the real case.
    if (span < 0.0001f) return Color(MARKER_LIGHTNESS, MARKER_LIGHTNESS, MARKER_LIGHTNESS)
    val lightness = (high + low) / 2f
    val hue = when (high) {
        red -> (green - blue) / span + if (green < blue) 6f else 0f
        green -> (blue - red) / span + 2f
        else -> (red - green) / span + 4f
    } * 60f
    val saturation = span / (1f - abs(2f * lightness - 1f))
    // Capped, because a fully saturated tint at this lightness is a highlighter rather than a
    // marker, and the shape is small enough that the colour is all of it.
    return Color.hsl(hue, saturation.coerceIn(0f, 0.85f), MARKER_LIGHTNESS)
}

/** As light as a colour can be and still carry its hue. */
private const val MARKER_LIGHTNESS = 0.93f
