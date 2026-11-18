package com.mydrop.vpn.ui.components

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mydrop.vpn.core.model.WorldMap
import com.mydrop.vpn.shared.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Paint as NativePaint

/** A point on the Earth: where the tunnel comes out. */
@Immutable
data class GlobePoint(val latitude: Double, val longitude: Double) {
    companion object {
        /**
         * Where the server named [name] is, read from the flag in its name — the same way the
         * subscriptions already say it ("🇳🇱 Нидерланды"). Null when the name carries no flag or
         * the flag is not a country the table knows: then there is nowhere honest to point at.
         */
        fun ofServerName(name: String): GlobePoint? {
            val code = WorldMap.countryCodeOf(name) ?: return null
            val at = WorldMap.centroidOf(code) ?: return null
            return GlobePoint(at[0], at[1])
        }
    }
}

/** What the globe is doing. */
enum class GlobeMotion {
    /** Turning slowly on its own: the tunnel is down. */
    Drift,

    /** Spinning fast: the tunnel is coming up. */
    Spin,

    /** Turning the target to face the viewer, flying in to its part of the world, and marking it. */
    Pin,
}

/**
 * The planet behind the connect button: the Earth drawn in thin lines — coastlines and a
 * graticule — on an orthographic projection, softly blurred so it stays a backdrop.
 *
 * Once connected it works like a camera. The globe turns until the server's country faces the
 * viewer while the camera flies in — from the whole planet at [center] to its part of the world,
 * with the country at [focus] — and only then does the country come into focus: the blur lifts
 * around it, and a sand marker settles onto it from above, as if lowered to the surface, with one
 * ring. On disconnect the camera pulls back out and the planet drifts again.
 *
 * The coastlines are traced from the two-degree land mask the app ships (`R.raw.world_land`) with
 * marching squares — no extra data, no network.
 *
 * @param center where the globe's centre sits at rest, in pixels.
 * @param radius the globe's radius at rest, in pixels.
 * @param focus where the server's country ends up once the camera has flown in, in pixels.
 * @param zoomedRadius the globe's radius once flown in: big enough that one part of the world
 * fills the view.
 */
@Composable
fun WireGlobe(
    center: Offset,
    radius: Float,
    focus: Offset,
    zoomedRadius: Float,
    motion: GlobeMotion,
    target: GlobePoint?,
    lineColor: Color,
    markerColor: Color,
    markerCore: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val geometry by produceState(GlobeGeometry.GridOnly) {
        val app = context.applicationContext
        value = withContext(Dispatchers.Default) { GlobeGeometry.load(app) }
    }
    val reduceMotion = remember { animationsDisabled(context) }

    val state = remember { GlobeState() }
    val currentMotion = rememberUpdatedState(motion)
    val currentTarget = rememberUpdatedState(target)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, reduceMotion) {
        // Frames only while the screen is actually showing: a globe turning behind a locked phone
        // is battery spent on nobody.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state.run(
                motion = { currentMotion.value },
                target = { currentTarget.value },
                reduceMotion = reduceMotion,
            )
        }
    }

    // The camera: 0 — the whole planet at rest, 1 — flown in to the target's part of the world.
    val zoom = remember { Animatable(0f) }
    val flyIn = motion == GlobeMotion.Pin && target != null
    LaunchedEffect(flyIn, reduceMotion) {
        when {
            reduceMotion -> zoom.snapTo(if (flyIn) 1f else 0f)
            flyIn -> zoom.animateTo(1f, tween(ZOOM_IN_MILLIS, easing = FastOutSlowInEasing))
            else -> zoom.animateTo(0f, tween(ZOOM_OUT_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    // Focus and marker: once the globe has stopped and the camera has arrived, the blur lifts
    // around the country, then the marker settles onto it and rings once.
    val sharp = remember { Animatable(0f) }
    val land = remember { Animatable(0f) }
    val ring = remember { Animatable(1f) }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(state.settledOn) {
        if (state.settledOn == null) {
            launch { shown.animateTo(0f, tween(200)) }
            sharp.animateTo(0f, tween(260))
            return@LaunchedEffect
        }
        if (reduceMotion) {
            sharp.snapTo(1f)
            land.snapTo(1f)
            shown.snapTo(1f)
            return@LaunchedEffect
        }
        snapshotFlow { zoom.value }.first { it >= 0.999f }
        sharp.animateTo(1f, tween(SHARPEN_MILLIS))
        land.snapTo(0f)
        ring.snapTo(1f)
        shown.snapTo(1f)
        land.animateTo(1f, tween(LAND_MILLIS, easing = LinearOutSlowInEasing))
        ring.snapTo(0f)
        ring.animateTo(1f, tween(RING_MILLIS, easing = LinearOutSlowInEasing))
    }
    // Only composed while the marker is down: an infinite transition asks for every frame for as
    // long as it exists, and with the tunnel down nothing here would use them.
    val breath = if (state.settledOn != null && !reduceMotion) {
        rememberInfiniteTransition(label = "marker-breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
            label = "marker-breath-value",
        )
    } else {
        null
    }

    val soft = remember { GlobeRenderer() }
    val crisp = remember { GlobeRenderer() }
    val lineArgb = lineColor.toArgb()

    Box(modifier) {
        // The whole planet, soft — a touch softer once flown in, so the country stands out more.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blur = (BLUR_AT_REST + (BLUR_FLOWN_IN - BLUR_AT_REST) * zoom.value).toPx()
                        renderEffect = BlurEffect(blur, blur, TileMode.Decal)
                    }
                },
        ) {
            val e = zoom.value
            drawGlobe(
                renderer = soft,
                geometry = geometry,
                yaw = state.yaw,
                pitch = state.pitch,
                center = lerp(center, focus, e),
                radius = radius + (zoomedRadius - radius) * e,
                argb = lineArgb,
                lineColor = lineColor,
                alphaScale = 1f - 0.2f * e,
            )
        }

        // The country itself, sharp: the same lines without blur, kept only in a soft circle
        // round the target.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val s = sharp.value
            val point = state.settledOn
            if (s <= 0.01f || point == null) return@Canvas
            val e = zoom.value
            val c = lerp(center, focus, e)
            val r = radius + (zoomedRadius - radius) * e
            val at = project(point, state.yaw, state.pitch, c, r) ?: return@Canvas
            drawGlobe(
                renderer = crisp,
                geometry = geometry,
                yaw = state.yaw,
                pitch = state.pitch,
                center = c,
                radius = r,
                argb = lineArgb,
                lineColor = lineColor,
                alphaScale = 1.15f,
            )
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Black.copy(alpha = s),
                    0.55f to Color.Black.copy(alpha = s * 0.8f),
                    1f to Color.Transparent,
                    center = at,
                    radius = SHARP_RADIUS.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

        // The marker, sharp, on top of everything.
        Canvas(Modifier.fillMaxSize()) {
            val point = state.settledOn ?: return@Canvas
            val alpha = shown.value
            if (alpha <= 0.01f) return@Canvas
            val e = zoom.value
            val at = project(
                point,
                state.yaw,
                state.pitch,
                lerp(center, focus, e),
                radius + (zoomedRadius - radius) * e,
            ) ?: return@Canvas

            // Lowered onto the surface: large and faint while still "high", full-size once down.
            val landed = land.value
            val scale = 1f + (1f - landed) * (LAND_SCALE - 1f)
            val opacity = min(1f, landed * 1.6f) * alpha

            val ringProgress = ring.value
            if (ringProgress < 1f) {
                drawCircle(
                    color = markerColor.copy(alpha = (1f - ringProgress) * 0.75f * alpha),
                    radius = 5.dp.toPx() + ringProgress * 22.dp.toPx(),
                    center = at,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            if (landed >= 1f) {
                val b = breath?.value ?: 0.5f
                drawCircle(
                    color = markerColor.copy(alpha = (0.18f + b * 0.16f) * alpha),
                    radius = 11.dp.toPx() + b * 3.dp.toPx(),
                    center = at,
                )
            }
            drawCircle(color = markerColor.copy(alpha = opacity), radius = 5.5.dp.toPx() * scale, center = at)
            drawCircle(color = markerCore.copy(alpha = opacity), radius = 2.2.dp.toPx() * scale, center = at)
        }
    }
}

/** Grid, coastlines and limb, with the given projection. */
private fun DrawScope.drawGlobe(
    renderer: GlobeRenderer,
    geometry: GlobeGeometry,
    yaw: Float,
    pitch: Float,
    center: Offset,
    radius: Float,
    argb: Int,
    lineColor: Color,
    alphaScale: Float,
) {
    val stroke = 1.dp.toPx()
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        renderer.draw(native, geometry.grid, yaw, pitch, center, radius, argb, GRID_ALPHA * alphaScale, stroke)
        renderer.draw(native, geometry.coast, yaw, pitch, center, radius, argb, COAST_ALPHA * alphaScale, stroke * 1.35f)
    }
    drawCircle(
        color = lineColor.copy(alpha = (LIMB_ALPHA * alphaScale).coerceAtMost(1f)),
        radius = radius,
        center = center,
        style = Stroke(width = stroke * 1.2f),
    )
}

/** Rotation of the globe, in degrees, and the motion controller that drives it. */
@Stable
private class GlobeState {
    var yaw by mutableFloatStateOf(INITIAL_YAW)
    var pitch by mutableFloatStateOf(DRIFT_PITCH)

    /** The point the globe has come to rest on, or null while it is moving. */
    var settledOn by mutableStateOf<GlobePoint?>(null)

    /** Degrees per second; the globe only ever turns one way. */
    private var velocity = 0f

    /** Degrees still to turn before the pinned target faces the viewer. */
    private var remaining = 0f

    /** The target [remaining] was measured for; null while not pinning. */
    private var plannedFor: GlobePoint? = null

    suspend fun run(
        motion: () -> GlobeMotion,
        target: () -> GlobePoint?,
        reduceMotion: Boolean,
    ) = kotlinx.coroutines.coroutineScope {
        var last = 0L
        while (isActive) {
            val wanted = motion()
            val point = target()

            if (reduceMotion) {
                // No spinning at all; just face the right way.
                if (wanted == GlobeMotion.Pin && point != null) {
                    yaw = pinYaw(point)
                    pitch = pinPitch(point)
                    settledOn = point
                } else {
                    pitch = DRIFT_PITCH
                    settledOn = null
                }
                snapshotFlow { motion() to target() }.first { it != (wanted to point) }
                continue
            }

            if (settledOn != null && wanted == GlobeMotion.Pin && point == settledOn) {
                // At rest on the target: nothing moves, so nothing needs a frame until the tunnel
                // changes. Waiting here is what keeps a connected screen from redrawing forever.
                snapshotFlow { motion() to target() }.first { it != (wanted to point) }
                last = 0L
                continue
            }

            // A slow drift needs far fewer frames than a spin to look smooth.
            if (wanted == GlobeMotion.Drift && abs(velocity - DRIFT_SPEED) < 0.5f) {
                delay(DRIFT_FRAME_MILLIS)
            }

            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                if (wanted == GlobeMotion.Pin && point != null) {
                    if (plannedFor != point) {
                        // A new country (or the first): the old marker goes at once rather than
                        // riding the globe round, and the way there is measured now, once.
                        settledOn = null
                        plan(pinYaw(point))
                        plannedFor = point
                    }
                    val arrived = stepPinned(pinYaw(point), dt)
                    pitch = ease(pitch, pinPitch(point), dt)
                    if (arrived && abs(pitch - pinPitch(point)) < 0.2f) {
                        pitch = pinPitch(point)
                        settledOn = point
                    }
                } else {
                    settledOn = null
                    plannedFor = null
                    val speed = if (wanted == GlobeMotion.Spin) SPIN_SPEED else DRIFT_SPEED
                    velocity = approach(velocity, speed, ACCELERATION * dt)
                    yaw = wrap(yaw + velocity * dt)
                    pitch = ease(pitch, DRIFT_PITCH, dt)
                }
            }
        }
    }

    /**
     * Measures, once, how far the globe has to turn — forwards only — to put [goal] in front. If it
     * is already too fast to stop that soon, the way gets one more lap rather than a hard brake: a
     * spin that overshoots and backs up looks like a glitch.
     *
     * Once, and not every frame. Asked every frame, the question "can it still stop in time?" was
     * answered with the speed of the frame before, so the braking distance always came out a hair
     * longer than the way left, a lap was added every time, and the globe never stopped — which is
     * why the marker never fell.
     */
    private fun plan(goal: Float) {
        var distance = floorMod(goal - yaw, 360f)
        val stopping = velocity * velocity / (2f * DECELERATION)
        while (distance + 0.5f < stopping) distance += 360f
        remaining = distance
    }

    /**
     * One frame along the planned way, braking at a constant rate so the globe stops exactly on
     * [goal]. True once it is there.
     */
    private fun stepPinned(goal: Float, dt: Float): Boolean {
        if (remaining > 0.01f) {
            val ceiling = sqrt(2f * DECELERATION * remaining)
            velocity = approach(velocity, min(SPIN_SPEED, ceiling), ACCELERATION * dt).coerceAtMost(ceiling)
            // At least a nudge per frame, so the last fraction of a degree does not take forever.
            val moved = min(remaining, maxOf(velocity * dt, 0.03f))
            remaining -= moved
            yaw = wrap(yaw + moved)
        }
        if (remaining > 0.01f) return false
        remaining = 0f
        yaw = wrap(goal)
        velocity = 0f
        return true
    }
}

/**
 * Coastline and graticule segments on the unit sphere, six floats each: x, y, z of one end, then
 * of the other. x points right, y up (to the north pole), z towards the viewer at yaw 0.
 */
private class GlobeGeometry(val coast: FloatArray, val grid: FloatArray) {
    companion object {
        val GridOnly = GlobeGeometry(FloatArray(0), graticule())

        fun load(context: Context): GlobeGeometry {
            val mask = runCatching {
                context.resources.openRawResource(R.raw.world_land).use { it.readBytes() }
            }.getOrNull()
            val coast = if (mask != null && mask.size >= WorldMap.MASK_BYTES) coastline(mask) else FloatArray(0)
            return GlobeGeometry(coast, GridOnly.grid)
        }
    }
}

/**
 * Traces the land mask's outline with marching squares over the cell centres. Each 2×2 block of
 * cells gives at most two segments between the midpoints of its edges; the longitude wraps, so the
 * antimeridian is not a seam.
 */
private fun coastline(mask: ByteArray): FloatArray {
    val columns = WorldMap.MASK_COLUMNS
    val rows = WorldMap.MASK_ROWS
    fun land(row: Int, column: Int): Int {
        val c = ((column % columns) + columns) % columns
        val index = row * columns + c
        return if ((mask[index / 8].toInt() and (0x80 ushr (index % 8))) != 0) 1 else 0
    }
    val out = SegmentBuffer()
    for (row in 0 until rows - 1) {
        val top = 89.0 - 2 * row
        val middle = top - 1.0
        val bottom = top - 2.0
        for (column in 0 until columns) {
            val left = -179.0 + 2 * column
            val centre = left + 1.0
            val right = left + 2.0
            val case = (land(row, column) shl 3) or
                (land(row, column + 1) shl 2) or
                (land(row + 1, column + 1) shl 1) or
                land(row + 1, column)
            // Edge midpoints: T(op), R(ight), B(ottom), L(eft).
            fun t() = doubleArrayOf(top, centre)
            fun r() = doubleArrayOf(middle, right)
            fun b() = doubleArrayOf(bottom, centre)
            fun l() = doubleArrayOf(middle, left)
            when (case) {
                1, 14 -> out.add(l(), b())
                2, 13 -> out.add(b(), r())
                3, 12 -> out.add(l(), r())
                4, 11 -> out.add(t(), r())
                5 -> { out.add(l(), t()); out.add(b(), r()) }
                6, 9 -> out.add(t(), b())
                7, 8 -> out.add(l(), t())
                10 -> { out.add(t(), r()); out.add(l(), b()) }
                else -> Unit
            }
        }
    }
    return out.toArray()
}

/** Meridians every 30° and the parallels at 0, ±30 and ±60, in 4° steps. */
private fun graticule(): FloatArray {
    val out = SegmentBuffer()
    for (longitude in -180 until 180 step 30) {
        for (latitude in -80 until 80 step 4) {
            out.add(
                doubleArrayOf(latitude.toDouble(), longitude.toDouble()),
                doubleArrayOf(latitude + 4.0, longitude.toDouble()),
            )
        }
    }
    for (latitude in intArrayOf(-60, -30, 0, 30, 60)) {
        for (longitude in -180 until 180 step 4) {
            out.add(
                doubleArrayOf(latitude.toDouble(), longitude.toDouble()),
                doubleArrayOf(latitude.toDouble(), longitude + 4.0),
            )
        }
    }
    return out.toArray()
}

private class SegmentBuffer {
    private var data = FloatArray(6 * 1024)
    private var size = 0

    fun add(a: DoubleArray, b: DoubleArray) {
        if (size + 6 > data.size) data = data.copyOf(data.size * 2)
        put(a[0], a[1])
        put(b[0], b[1])
    }

    private fun put(latitude: Double, longitude: Double) {
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        val c = cos(phi)
        data[size++] = (c * sin(lambda)).toFloat()
        data[size++] = sin(phi).toFloat()
        data[size++] = (c * cos(lambda)).toFloat()
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

/**
 * Projects the segments and draws the visible ones in three depth bands, fainter towards the limb,
 * so the wireframe reads as a sphere rather than a flat disc. One `drawLines` call per band.
 */
private class GlobeRenderer {
    private val bands = Array(3) { FloatArray(4 * 1024) }
    private val counts = IntArray(3)
    private val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        style = NativePaint.Style.STROKE
        strokeCap = NativePaint.Cap.ROUND
    }

    fun draw(
        canvas: android.graphics.Canvas,
        segments: FloatArray,
        yawDegrees: Float,
        pitchDegrees: Float,
        center: Offset,
        radius: Float,
        argb: Int,
        alpha: Float,
        strokeWidth: Float,
    ) {
        if (segments.isEmpty()) return
        counts.fill(0)
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        var i = 0
        while (i < segments.size) {
            val ax = segments[i]; val ay = segments[i + 1]; val az = segments[i + 2]
            val bx = segments[i + 3]; val by = segments[i + 4]; val bz = segments[i + 5]
            i += 6
            val ax1 = ax * cy + az * sy
            val az1 = az * cy - ax * sy
            val az2 = ay * sp + az1 * cp
            if (az2 <= 0f) continue
            val bx1 = bx * cy + bz * sy
            val bz1 = bz * cy - bx * sy
            val bz2 = by * sp + bz1 * cp
            if (bz2 <= 0f) continue
            val ay2 = ay * cp - az1 * sp
            val by2 = by * cp - bz1 * sp
            val depth = (az2 + bz2) * 0.5f
            val band = if (depth < 0.35f) 0 else if (depth < 0.7f) 1 else 2
            var buffer = bands[band]
            if (counts[band] + 4 > buffer.size) {
                buffer = buffer.copyOf(buffer.size * 2)
                bands[band] = buffer
            }
            var n = counts[band]
            buffer[n++] = center.x + radius * ax1
            buffer[n++] = center.y - radius * ay2
            buffer[n++] = center.x + radius * bx1
            buffer[n++] = center.y - radius * by2
            counts[band] = n
        }
        paint.strokeWidth = strokeWidth
        for (band in 0..2) {
            if (counts[band] == 0) continue
            paint.color = argb
            paint.alpha = (alpha * BAND_ALPHA[band] * 255f).toInt().coerceIn(0, 255)
            canvas.drawLines(bands[band], 0, counts[band], paint)
        }
    }
}

/** Where [point] lands on screen, or null when it is on the far side of the globe. */
private fun project(point: GlobePoint, yawDegrees: Float, pitchDegrees: Float, center: Offset, radius: Float): Offset? {
    val phi = Math.toRadians(point.latitude)
    val lambda = Math.toRadians(point.longitude)
    val x = cos(phi) * sin(lambda)
    val y = sin(phi)
    val z = cos(phi) * cos(lambda)
    val yaw = Math.toRadians(yawDegrees.toDouble())
    val pitch = Math.toRadians(pitchDegrees.toDouble())
    val x1 = x * cos(yaw) + z * sin(yaw)
    val z1 = z * cos(yaw) - x * sin(yaw)
    val y2 = y * cos(pitch) - z1 * sin(pitch)
    val z2 = y * sin(pitch) + z1 * cos(pitch)
    if (z2 <= 0.0) return null
    return Offset(center.x + radius * x1.toFloat(), center.y - radius * y2.toFloat())
}

/** The yaw that brings [point] round to the globe's centre line, facing the viewer. */
private fun pinYaw(point: GlobePoint): Float = wrap((-point.longitude).toFloat())

/**
 * The tilt that brings [point] to the globe's centre, so that flying in lands on it. Capped near
 * the poles, where looking straight down would make the map spin round its own axis.
 */
private fun pinPitch(point: GlobePoint): Float =
    point.latitude.toFloat().coerceIn(MIN_PITCH, MAX_PITCH)

private fun animationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

private fun approach(value: Float, goal: Float, maxStep: Float): Float = when {
    value < goal -> min(goal, value + maxStep)
    value > goal -> maxOf(goal, value - maxStep)
    else -> value
}

private fun ease(value: Float, goal: Float, dt: Float): Float =
    value + (goal - value) * (dt * 2.6f).coerceAtMost(1f)

private fun wrap(degrees: Float): Float = floorMod(degrees, 360f)

private fun floorMod(value: Float, modulus: Float): Float {
    val r = value % modulus
    return if (r < 0f) r + modulus else r
}

private const val INITIAL_YAW = 200f
private const val DRIFT_PITCH = 18f
private const val MIN_PITCH = -70f
private const val MAX_PITCH = 72f

/** 72 seconds a turn: alive, but not something the eye follows. */
private const val DRIFT_SPEED = 5f
private const val SPIN_SPEED = 160f
private const val ACCELERATION = 240f
private const val DECELERATION = 110f
private const val DRIFT_FRAME_MILLIS = 33L

private const val GRID_ALPHA = 0.16f
private const val COAST_ALPHA = 0.50f
private const val LIMB_ALPHA = 0.34f
private val BAND_ALPHA = floatArrayOf(0.35f, 0.7f, 1f)

private val BLUR_AT_REST = 1.6.dp
private val BLUR_FLOWN_IN = 2.6.dp
private val SHARP_RADIUS = 72.dp

private const val ZOOM_IN_MILLIS = 1800
private const val ZOOM_OUT_MILLIS = 1100
private const val SHARPEN_MILLIS = 450
private const val LAND_MILLIS = 520
private const val RING_MILLIS = 1000

/** How large the marker starts while it is still "high above" the country. */
private const val LAND_SCALE = 2.6f
