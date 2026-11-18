package com.mydrop.vpn.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mydrop.vpn.ui.theme.AvacaPalette

/**
 * Soft relief: a surface the colour of the ground, lifted by two blurred shadows — a lit one up
 * and to the left, a dark one down and to the right — the way the reference draws its cards.
 *
 * Only the shadows: put the surface's own background after this modifier, in the same shape.
 * Nothing may clip the element, or the shadows are cut off at its edges.
 *
 * Drawn with a blur mask filter on the platform canvas. Below Android 9 the hardware canvas
 * ignores the filter and the shadows come out as hard edges, so there they are skipped.
 */
fun Modifier.neumorphic(
    cornerRadius: Dp,
    distance: Dp = 5.dp,
    blur: Dp = 12.dp,
    light: Color = AvacaPalette.ShadowLight,
    dark: Color = AvacaPalette.ShadowDark,
): Modifier = drawWithCache {
    val radius = cornerRadius.toPx()
    val offset = distance.toPx()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    val lightArgb = light.toArgb()
    val darkArgb = dark.toArgb()
    val supported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
    onDrawBehind {
        if (!supported) return@onDrawBehind
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            paint.color = darkArgb
            native.drawRoundRect(offset, offset, size.width + offset, size.height + offset, radius, radius, paint)
            paint.color = lightArgb
            native.drawRoundRect(-offset, -offset, size.width - offset, size.height - offset, radius, radius, paint)
        }
    }
}
