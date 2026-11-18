package com.mydrop.vpn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * One full set of the app's colours. There are two, one per design:
 *
 * - [Avaca] — dark graphite, soft relief, and one quiet accent, a muted sand. Surfaces are the
 *   colour of the ground and stand out only by their shadows, a lighter edge up and to the left and
 *   a darker one down and to the right. There is no gradient and no pink or purple anywhere.
 * - [Git] — black and greys; rows stand apart by their fill rather than by relief, and the one
 *   accent is the blue of the chosen server's marker on the dotted planet.
 *
 * The names follow the Avaca design — [sand] is the accent in both. The accent goes only where
 * something is active: a switch that is on, the selected server's tick, a quota bar.
 */
data class PaletteScheme(
    /** Ground and every raised surface. */
    val graphite: Color,
    /** The lit edge of a raised surface. */
    val shadowLight: Color,
    /** The shaded edge of a raised surface. */
    val shadowDark: Color,
    /** Thin lines between rows. */
    val divider: Color,
    /** A chosen row. */
    val selected: Color,
    val text: Color,
    val textMuted: Color,
    /** The accent. */
    val sand: Color,
    /** The accent lifted, for small bright details. */
    val sandLight: Color,
    /** The accent sunk into the ground, for containers that belong to it. */
    val sandDeep: Color,
    /** The card behind a group of settings rows. */
    val groupFill: Color,
    /** A recess inside a group: a switch's track while off, a segmented control's track. */
    val well: Color,
    /** Borders and small handles. */
    val outline: Color,
    /** Below the ground: code and log blocks. */
    val sunken: Color,
    val surfaceVariant: Color,
    val surfaceDim: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val inversePrimary: Color,
) {
    companion object {
        val Avaca = PaletteScheme(
            graphite = Color(0xFF292929),
            shadowLight = Color(0xFF383838),
            shadowDark = Color(0xFF181818),
            divider = Color(0xFF353535),
            selected = Color(0xFF3A3A3A),
            text = Color(0xFFF2F2F2),
            textMuted = Color(0xFF9C9C9C),
            sand = Color(0xFFB8A487),
            sandLight = Color(0xFFE2D8C9),
            sandDeep = Color(0xFF443E36),
            groupFill = Color(0xFF313131),
            well = Color(0xFF3D3D3D),
            outline = Color(0xFF4A4A4A),
            sunken = Color(0xFF222222),
            surfaceVariant = Color(0xFF333333),
            surfaceDim = Color(0xFF232323),
            containerLow = Color(0xFF2C2C2C),
            container = Color(0xFF303030),
            containerHigh = Color(0xFF353535),
            inversePrimary = Color(0xFF857660),
        )

        val Git = PaletteScheme(
            graphite = Color(0xFF000000),
            shadowLight = Color(0xFF161616),
            shadowDark = Color(0xFF000000),
            divider = Color(0xFF2A2A2A),
            selected = Color(0xFF3A3A3A),
            text = Color(0xFFF0F0F0),
            textMuted = Color(0xFF878787),
            sand = Color(0xFF4897FF),
            sandLight = Color(0xFFA8CBFF),
            sandDeep = Color(0xFF10233F),
            groupFill = Color(0xFF1B1B1B),
            well = Color(0xFF2E2E2E),
            outline = Color(0xFF3C3C3C),
            sunken = Color(0xFF101010),
            surfaceVariant = Color(0xFF1F1F1F),
            surfaceDim = Color(0xFF0A0A0A),
            containerLow = Color(0xFF111111),
            container = Color(0xFF171717),
            containerHigh = Color(0xFF1F1F1F),
            inversePrimary = Color(0xFF2F6BC0),
        )
    }
}

/**
 * The colours in use. Each one reads [scheme], which is snapshot state: switching the design
 * repaints every screen that drew with them, compositions and draw passes alike.
 *
 * The tunnel's four states keep their colours in both designs — white while off, yellow while
 * connecting, green while connected, red when it failed — softened so that a state colour reads as
 * information, not as an alarm.
 */
object AvacaPalette {
    var scheme by mutableStateOf(PaletteScheme.Avaca)
        private set

    fun use(next: PaletteScheme) {
        if (next != scheme) scheme = next
    }

    val Graphite: Color get() = scheme.graphite
    val ShadowLight: Color get() = scheme.shadowLight
    val ShadowDark: Color get() = scheme.shadowDark
    val Divider: Color get() = scheme.divider
    val Selected: Color get() = scheme.selected
    val Text: Color get() = scheme.text
    val TextMuted: Color get() = scheme.textMuted
    val Sand: Color get() = scheme.sand
    val SandLight: Color get() = scheme.sandLight
    val SandDeep: Color get() = scheme.sandDeep
    val GroupFill: Color get() = scheme.groupFill
    val Well: Color get() = scheme.well
    val Outline: Color get() = scheme.outline
    val Sunken: Color get() = scheme.sunken

    // The tunnel's four states.
    val Idle = Color(0xFFEDEDED)
    val Connecting = Color(0xFFF4C95D)
    val Connected = Color(0xFF5BD48E)
    val Failed = Color(0xFFF0616D)
}

private fun colorSchemeOf(s: PaletteScheme): ColorScheme = darkColorScheme(
    primary = s.sand,
    onPrimary = s.graphite,
    primaryContainer = s.sandDeep,
    onPrimaryContainer = s.sandLight,
    inversePrimary = s.inversePrimary,

    secondary = s.sand,
    onSecondary = s.graphite,
    // Containers stay grey: a selected chip or a filled tonal button should read as "chosen",
    // not as another splash of the accent.
    secondaryContainer = s.selected,
    onSecondaryContainer = s.text,

    tertiary = s.sandLight,
    onTertiary = s.graphite,
    tertiaryContainer = s.sandDeep,
    onTertiaryContainer = s.sandLight,

    error = AvacaPalette.Failed,
    onError = Color(0xFF2B0D10),
    errorContainer = Color(0xFF4A2226),
    onErrorContainer = Color(0xFFFFD9DC),

    background = s.graphite,
    onBackground = s.text,
    surface = s.graphite,
    onSurface = s.text,
    surfaceVariant = s.surfaceVariant,
    onSurfaceVariant = s.textMuted,
    // The ground, not the accent: tonal elevation would otherwise tint every menu and sheet.
    surfaceTint = s.graphite,

    surfaceBright = s.selected,
    surfaceDim = s.surfaceDim,
    surfaceContainerLowest = s.surfaceDim,
    surfaceContainerLow = s.containerLow,
    surfaceContainer = s.container,
    surfaceContainerHigh = s.containerHigh,
    surfaceContainerHighest = s.selected,

    inverseSurface = s.text,
    inverseOnSurface = s.graphite,

    outline = s.outline,
    outlineVariant = s.divider,
    scrim = Color.Black,
)

/**
 * The latency scale speaks the tunnel's own language — green, yellow, red — so a good server and
 * a live tunnel look alike, and a dead one looks like a failed connection.
 */
private fun semanticColorsOf(s: PaletteScheme) = MyDropSemanticColors(
    connected = AvacaPalette.Connected,
    onConnected = s.graphite,
    connectedContainer = Color(0xFF22392C),
    onConnectedContainer = Color(0xFFCFF5DD),
    connecting = AvacaPalette.Connecting,
    latencyFast = AvacaPalette.Connected,
    latencyMedium = AvacaPalette.Connecting,
    latencySlow = AvacaPalette.Failed,
    latencyDead = Color(0xFF6B6B6B),
    download = s.sand,
    upload = s.sandLight,
    marker = s.sand,
)

/**
 * Avaca's theme: always dark, in the palette of the chosen design.
 *
 * The planets and the relief are drawn for a dark ground, and a light variant would be a third
 * design rather than a recolouring — so there is no light mode, no wallpaper colours and no AMOLED
 * switch to keep in step with them.
 */
@Composable
fun AvacaTheme(content: @Composable () -> Unit) {
    val scheme = AvacaPalette.scheme
    val colors = remember(scheme) { colorSchemeOf(scheme) }
    val semantic = remember(scheme) { semanticColorsOf(scheme) }
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = MotionScheme.expressive(),
            typography = MyDropTypography,
            content = content,
        )
    }
}
