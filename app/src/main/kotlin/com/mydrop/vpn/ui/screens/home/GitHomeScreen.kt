package com.mydrop.vpn.ui.screens.home

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mydrop.vpn.core.format.daysUntil
import com.mydrop.vpn.core.model.LatencyResult
import com.mydrop.vpn.core.model.Protocol
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.ServerSort
import com.mydrop.vpn.core.model.Subscription
import com.mydrop.vpn.core.model.VpnState
import com.mydrop.vpn.core.model.WorldMap
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.ui.MainUiState
import com.mydrop.vpn.ui.components.GlobePoint
import com.mydrop.vpn.ui.components.QrShareDialog
import com.mydrop.vpn.ui.format.formatBytes
import com.mydrop.vpn.ui.format.pluralServers
import com.mydrop.vpn.ui.theme.AvacaPalette
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The main screen of the Git design: a planet of dots on black that is also the connect button,
 * and the subscriptions under it. The state and the actions are [HomeScreen]'s, menus included —
 * only the look differs. Drawn after the egui prototype in take-design (`rust-ui/src/home.rs`).
 */
@Composable
fun GitHomeScreen(
    state: MainUiState,
    onToggleConnection: () -> Unit,
    onSelect: (String) -> Unit,
    onPing: (String) -> Unit,
    onPingGroup: (List<String>) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onRemoveSubscription: (String) -> Unit,
    onRemoveNode: (String) -> Unit,
    onSetTlsInsecure: (String, Boolean) -> Unit,
    onToggleGroup: (String) -> Unit,
    onSort: (ServerSort) -> Unit,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vpn = state.vpnState
    val connected = vpn is VpnState.Connected
    // The server the tunnel is on — or, while it is down, the one a tap would connect to.
    val node = remember(vpn, state.nodes, state.selectedNode) {
        vpn.activeNodeId?.let { id -> state.nodes.firstOrNull { it.id == id } } ?: state.selectedNode
    }
    val place = remember(node?.name) { node?.name?.let { GlobePoint.ofServerName(it) } }
    val locale = LocalConfiguration.current.locales[0]
    val caption = remember(node?.name, locale) { node?.let { placeName(it.name, locale) } }
    val land = rememberLand()
    val motion = rememberGitMotion(connected, place)
    val touch = remember { MutableInteractionSource() }
    val pressed by touch.collectIsPressedAsState()
    val hint = when {
        state.nodes.isEmpty() -> stringResource(R.string.git_hint_add)
        vpn is VpnState.Failed -> vpn.message
        connected -> stringResource(R.string.git_hint_connected)
        else -> stringResource(R.string.git_hint_idle)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp
        val radius = min(maxWidth * 0.25f, maxHeight * 0.145f).coerceIn(72.dp, 100.dp)
        val centerY = top + 64.dp + radius
        val statusY = centerY + radius + 22.dp
        val barY = statusY + 42.dp

        GitGlobe(
            land = land,
            motion = motion,
            radius = radius,
            centerY = centerY,
            place = place,
            caption = caption,
            pressed = pressed,
            modifier = Modifier.fillMaxWidth().height(centerY + radius + 20.dp),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = centerY - radius)
                .size(radius * 2)
                .clip(CircleShape)
                .clickable(
                    interactionSource = touch,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(
                        if (vpn.isActive) R.string.connect_button_disconnect else R.string.connect_button_connect,
                    ),
                    onClick = if (state.nodes.isEmpty()) onAdd else onToggleConnection,
                ),
        )

        GitHeader(top = top, onOpenSettings = onOpenSettings, onAdd = onAdd)

        CenteredLine(centerY = statusY, height = 26.dp) {
            Text(stateLabel(vpn), fontSize = 17.sp, lineHeight = 22.sp, color = gray(235), maxLines = 1)
        }
        CenteredLine(centerY = statusY + 21.dp, height = 18.dp) {
            Text(
                hint,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = gray(115),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SubscriptionsBar(
            top = barY - 8.dp,
            sort = state.settings.serverSort,
            pinging = state.nodes.any { it.id in state.pingingNodeIds },
            canPing = state.nodes.isNotEmpty(),
            motion = motion,
            onSort = onSort,
            onPingAll = { onPingGroup(state.nodes.map { it.id }) },
            onAdd = onAdd,
        )

        GitServerList(
            state = state,
            onSelect = onSelect,
            onPing = onPing,
            onRefreshAll = onRefreshAll,
            onRefreshSubscription = onRefreshSubscription,
            onRemoveSubscription = onRemoveSubscription,
            onRemoveNode = onRemoveNode,
            onSetTlsInsecure = onSetTlsInsecure,
            onToggleGroup = onToggleGroup,
            modifier = Modifier.fillMaxSize().padding(top = barY + 25.dp),
        )
    }
}

@Composable
private fun CenteredLine(centerY: Dp, height: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = centerY - height / 2, start = 24.dp, end = 24.dp)
            .height(height),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------------------------- the header

@Composable
private fun GitHeader(top: Dp, onOpenSettings: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = top, start = 20.dp, end = 20.dp).height(48.dp)) {
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.settings_title), role = Role.Button, onClick = onOpenSettings),
        ) {
            val half = 8.dp.toPx()
            for (dy in floatArrayOf(-6f, 0f, 6f)) {
                val y = center.y + dy.dp.toPx()
                drawLine(gray(205), Offset(center.x - half, y), Offset(center.x + half, y), 1.5.dp.toPx())
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 4.dp).height(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("AVACA", fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 1.sp, color = gray(245), maxLines = 1)
        }
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 33.dp).height(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.git_tagline),
                fontSize = 8.sp,
                lineHeight = 10.sp,
                letterSpacing = 1.4.sp,
                color = gray(110),
                maxLines = 1,
            )
        }
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.action_add), role = Role.Button, onClick = onAdd),
        ) {
            drawPlus(8.dp.toPx(), Color.White)
        }
    }
}

private fun DrawScope.drawPlus(half: Float, color: Color) {
    val w = 1.5.dp.toPx()
    drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), w)
    drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), w)
}

// ------------------------------------------------------------------------------------ the planet

/** Frame-driven values, read only while drawing: a running clock, the glow and the planet's turn. */
@Stable
private class GitMotion {
    var time by mutableFloatStateOf(0f)

    /** 0 while the tunnel is down, 1 while it is up, eased in between. */
    var power by mutableFloatStateOf(0f)
    var rotation by mutableFloatStateOf(Float.NaN)
}

@Composable
private fun rememberGitMotion(connected: Boolean, place: GlobePoint?): GitMotion {
    val motion = remember { GitMotion() }
    val target by rememberUpdatedState(if (connected) 1f else 0f)
    val longitude by rememberUpdatedState(place?.longitude)
    LaunchedEffect(motion) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                val t = motion.time + dt
                motion.time = t
                motion.power += (target - motion.power) * (1f - exp(-dt * 7f))
                // With a chosen server the planet sways round it instead of turning it to the far
                // side; with none it turns slowly. A new choice is eased into, not jumped to.
                val lon = longitude
                val aim = if (lon != null) {
                    -Math.toRadians(lon).toFloat() + sin(t * 0.70f) * 0.78f
                } else {
                    t * 0.23f
                }
                val was = motion.rotation
                motion.rotation = if (was.isNaN()) {
                    wrapAngle(aim)
                } else {
                    wrapAngle(was + wrapAngle(aim - was) * (1f - exp(-dt * 5f)))
                }
            }
        }
    }
    return motion
}

private fun wrapAngle(angle: Float): Float {
    var a = angle % TAU
    if (a > PI_F) a -= TAU else if (a < -PI_F) a += TAU
    return a
}

/** Cells of the two-degree land mask, as the sines and cosines of their latitude and longitude. */
private class LandPoints(
    val sinLat: FloatArray,
    val cosLat: FloatArray,
    val sinLon: FloatArray,
    val cosLon: FloatArray,
) {
    val size: Int get() = sinLat.size
}

/** Read once per process: the mask never changes, and a second read would blink the planet. */
private var landCache: LandPoints? = null

@Composable
private fun rememberLand(): LandPoints? {
    val context = LocalContext.current.applicationContext
    val land by produceState(landCache) {
        if (value == null) value = withContext(Dispatchers.IO) { loadLand(context) }?.also { landCache = it }
    }
    return land
}

private fun loadLand(context: Context): LandPoints? {
    val mask = runCatching {
        context.resources.openRawResource(R.raw.world_land).use { it.readBytes() }
    }.getOrNull() ?: return null
    if (mask.size < WorldMap.MASK_BYTES) return null
    val columns = WorldMap.MASK_COLUMNS
    val rows = WorldMap.MASK_ROWS
    val cells = ArrayList<Int>()
    // The two rows at each pole are left out: there, the dots would crowd into a smudge.
    for (row in 2 until rows - 2) {
        for (column in 0 until columns) {
            val index = row * columns + column
            if ((mask[index / 8].toInt() and (0x80 ushr (index % 8))) != 0) cells += index
        }
    }
    val n = cells.size
    val sinLat = FloatArray(n)
    val cosLat = FloatArray(n)
    val sinLon = FloatArray(n)
    val cosLon = FloatArray(n)
    cells.forEachIndexed { i, index ->
        val lat = Math.toRadians(89.0 - 2 * (index / columns))
        val lon = Math.toRadians(-179.0 + 2 * (index % columns))
        sinLat[i] = sin(lat).toFloat()
        cosLat[i] = cos(lat).toFloat()
        sinLon[i] = sin(lon).toFloat()
        cosLon[i] = cos(lon).toFloat()
    }
    return LandPoints(sinLat, cosLat, sinLon, cosLon)
}

/** Visible dots sorted by shade, so a frame costs one native call per shade, not one per dot. */
private class DotBuffer(capacity: Int) {
    val points = Array(DOT_SHADES) { FloatArray(capacity * 2) }
    val counts = IntArray(DOT_SHADES)

    fun reset() = counts.fill(0)

    fun add(shade: Int, x: Float, y: Float) {
        val n = counts[shade]
        val into = points[shade]
        into[n * 2] = x
        into[n * 2 + 1] = y
        counts[shade] = n + 1
    }
}

@Composable
private fun GitGlobe(
    land: LandPoints?,
    motion: GitMotion,
    radius: Dp,
    centerY: Dp,
    place: GlobePoint?,
    caption: String?,
    pressed: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val label = remember(caption, density) {
        caption?.takeIf { it.isNotBlank() }?.let {
            measurer.measure(
                text = it,
                style = TextStyle(fontSize = 10.sp, color = MarkerText),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = with(density) { 124.dp.roundToPx() }),
            )
        }
    }
    val buffer = remember(land) { land?.let { DotBuffer(it.size) } }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    Canvas(modifier) {
        val r = radius.toPx()
        val c = Offset(size.width / 2f, centerY.toPx())
        val t = motion.time
        val power = motion.power.coerceIn(0f, 1f)
        val rotation = motion.rotation.let { if (it.isNaN()) 0f else it }

        // A faint halo of rings, then the dark disc.
        for (ring in 11 downTo 0) {
            drawCircle(gray(16 - ring), radius = r + ring * 1.3.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
        }
        drawCircle(gray(7), radius = r, center = c)

        if (power > 0.001f) {
            // A restrained glow at the core: powered, not neon. Under the land, which sits on top.
            for (layer in 7 downTo 1) {
                val fade = layer / 9f
                drawCircle(
                    Glow.copy(alpha = ((2f + (1f - fade) * 6f) * power / 255f).coerceIn(0f, 1f)),
                    radius = r * (0.04f + (1f - fade) * 0.10f),
                    center = c,
                )
            }
            drawCircle(
                NucleusRing.copy(alpha = (12f * power / 255f).coerceIn(0f, 1f)),
                radius = r * (0.055f + power * 0.012f),
                center = c,
                style = Stroke(0.8.dp.toPx()),
            )
            drawCircle(gray((12 + 32 * power).toInt()), radius = r * (0.012f + power * 0.008f), center = c)
        }

        if (land != null && buffer != null) drawLand(land, buffer, paint, c, r, rotation)

        if (power > 0.001f) {
            val breath = sin(t * 1.7f) * 0.5f + 0.5f
            for (layer in 5 downTo 0) {
                drawCircle(
                    Halo.copy(alpha = ((7 + (5 - layer) * 7) * power / 255f).coerceIn(0f, 1f)),
                    radius = r + 2.dp.toPx() + layer * 2.dp.toPx(),
                    center = c,
                    style = Stroke((1f + breath * 0.3f).dp.toPx()),
                )
            }
            drawCircle(gray((80 + 160 * power).toInt()), radius = r + 1.dp.toPx(), center = c, style = Stroke(1.25.dp.toPx()))
        }
        if (pressed) {
            drawCircle(gray(125), radius = r + 3.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
        }

        val marker = place?.let { markerPosition(it, rotation, c, r) }
        if (land != null && power > 0.01f) drawTraffic(land, t, power, rotation, c, r, marker)
        if (marker != null && power > 0.01f) drawMarker(marker, t, power, c, r, label)
    }
}

private fun DrawScope.drawLand(
    land: LandPoints,
    buffer: DotBuffer,
    paint: Paint,
    c: Offset,
    r: Float,
    rotation: Float,
) {
    val turnSin = sin(rotation)
    val turnCos = cos(rotation)
    buffer.reset()
    for (i in 0 until land.size) {
        val s = land.sinLon[i] * turnCos + land.cosLon[i] * turnSin
        val co = land.cosLon[i] * turnCos - land.sinLon[i] * turnSin
        val cosLat = land.cosLat[i]
        val z = cosLat * co
        if (z <= 0f) continue
        val shade = (sqrt(z) * (DOT_SHADES - 1)).roundToInt()
        buffer.add(shade, c.x + cosLat * s * r, c.y - land.sinLat[i] * r)
    }
    paint.strokeWidth = max(r / 130f, 0.7.dp.toPx()) * 2f
    drawIntoCanvas { canvas ->
        for (shade in 0 until DOT_SHADES) {
            val n = buffer.counts[shade]
            if (n == 0) continue
            val v = (45 + shade / (DOT_SHADES - 1f) * 145).toInt()
            paint.color = android.graphics.Color.rgb(v, v, v)
            canvas.nativeCanvas.drawPoints(buffer.points[shade], 0, n * 2, paint)
        }
    }
}

private fun markerPosition(place: GlobePoint, rotation: Float, c: Offset, r: Float): Offset? {
    val lat = Math.toRadians(place.latitude).toFloat()
    val lon = Math.toRadians(place.longitude).toFloat() + rotation
    if (cos(lat) * cos(lon) <= 0f) return null
    return Offset(c.x + cos(lat) * sin(lon) * r, c.y - sin(lat) * r)
}

/**
 * Points that light up and fade on land while the tunnel is up: motion, not data. They stand for
 * nothing the tunnel carries, so they carry no addresses either.
 */
private fun DrawScope.drawTraffic(
    land: LandPoints,
    t: Float,
    power: Float,
    rotation: Float,
    c: Offset,
    r: Float,
    marker: Offset?,
) {
    if (land.size == 0) return
    val turnSin = sin(rotation)
    val turnCos = cos(rotation)
    for (lane in 0 until 3) {
        val cycle = t * 1.9f + lane * 0.34f
        val slot = floor(cycle).toLong()
        val phase = cycle - floor(cycle)
        val fade = minOf(phase * 7f, 1f, (1f - phase) * 7f).coerceAtLeast(0f) * power
        if (fade < 0.24f) continue
        val at = trafficPoint(land, slot, lane.toLong(), turnSin, turnCos, c, r) ?: continue
        if (marker != null && (marker - at).getDistance() < 24.dp.toPx()) continue
        val opacity = sqrt(fade)
        drawCircle(TrafficGlow.copy(alpha = 32f / 255f * opacity), radius = 4.8.dp.toPx(), center = at)
        drawCircle(Halo.copy(alpha = 245f / 255f * opacity), radius = 1.8.dp.toPx(), center = at)
    }
}

private fun trafficPoint(
    land: LandPoints,
    slot: Long,
    lane: Long,
    turnSin: Float,
    turnCos: Float,
    c: Offset,
    r: Float,
): Offset? {
    var seed = scramble(slot * 17 + lane)
    repeat(24) {
        seed = scramble(seed)
        val i = ((seed ushr 1) % land.size).toInt()
        val s = land.sinLon[i] * turnCos + land.cosLon[i] * turnSin
        val co = land.cosLon[i] * turnCos - land.sinLon[i] * turnSin
        if (land.cosLat[i] * co >= 0.27f) return Offset(c.x + land.cosLat[i] * s * r, c.y - land.sinLat[i] * r)
    }
    return null
}

/** SplitMix64's finaliser: a cheap, well-spread hash of the slot, so each flash lands elsewhere. */
private fun scramble(value: Long): Long {
    var x = value + MIX_GOLDEN
    x = (x xor (x ushr 30)) * MIX_1
    x = (x xor (x ushr 27)) * MIX_2
    return x xor (x ushr 31)
}

private fun DrawScope.drawMarker(
    marker: Offset,
    t: Float,
    power: Float,
    c: Offset,
    r: Float,
    label: TextLayoutResult?,
) {
    val pulse = (sin(t * 2.4f) + 1f) * 0.5f
    drawCircle(Blue.copy(alpha = 175f / 255f * power), radius = (7.5f + pulse * 5.5f).dp.toPx(), center = marker, style = Stroke(1.dp.toPx()))
    drawCircle(Blue.copy(alpha = power), radius = (2.8f + 1.7f * power).dp.toPx(), center = marker)
    drawCircle(Color.White.copy(alpha = power), radius = (1f + 0.7f * power).dp.toPx(), center = marker)
    if (label == null) return

    val w = (label.size.width + 18.dp.toPx()).coerceIn(62.dp.toPx(), 142.dp.toPx())
    val h = 21.dp.toPx()
    val left = if (marker.x + w + 13.dp.toPx() < size.width - 7.dp.toPx()) {
        marker.x + 12.dp.toPx()
    } else {
        marker.x - w - 12.dp.toPx()
    }
    val top = (marker.y - 15.dp.toPx()).coerceIn(c.y - r + 3.dp.toPx(), c.y + r - 22.dp.toPx())
    val edge = if (left > marker.x) left else left + w
    drawLine(Blue.copy(alpha = 140f / 255f * power), marker, Offset(edge, top + h / 2), 1.dp.toPx())
    val corner = CornerRadius(7.dp.toPx())
    drawRoundRect(CalloutFill.copy(alpha = 230f / 255f * power), topLeft = Offset(left, top), size = Size(w, h), cornerRadius = corner)
    val inset = 0.4.dp.toPx()
    drawRoundRect(
        Blue.copy(alpha = 165f / 255f * power),
        topLeft = Offset(left + inset, top + inset),
        size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = corner,
        style = Stroke(0.8.dp.toPx()),
    )
    drawText(
        label,
        topLeft = Offset(left + (w - label.size.width) / 2f, top + (h - label.size.height) / 2f),
        alpha = power,
    )
}

/** The marker's caption: the country in the interface's language, or else the server's own name. */
private fun placeName(name: String, locale: Locale): String {
    val code = WorldMap.countryCodeOf(name)
    val country = code?.let {
        runCatching { Locale.Builder().setRegion(it).build().getDisplayCountry(locale) }.getOrNull()
    }
    return country?.takeIf { it.isNotBlank() && it != code } ?: nameWithoutFlag(name)
}

// ----------------------------------------------------------------------------- subscriptions bar

@Composable
private fun SubscriptionsBar(
    top: Dp,
    sort: ServerSort,
    pinging: Boolean,
    canPing: Boolean,
    motion: GitMotion,
    onSort: (ServerSort) -> Unit,
    onPingAll: () -> Unit,
    onAdd: () -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().padding(top = top, start = 20.dp, end = 20.dp).height(30.dp)) {
        Text(
            stringResource(R.string.git_subscriptions),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.8.sp,
            color = gray(150),
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 70.dp)
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.git_sort), role = Role.Button) { sortOpen = !sortOpen },
        ) {
            // Three lines, shorter downwards; brighter once the order is not the subscription's own.
            val shade = gray(if (sort == ServerSort.AsListed) 175 else 245)
            val origin = Offset(center.x - 7.5.dp.toPx(), center.y - 5.dp.toPx())
            for ((row, length) in listOf(0f to 15f, 5f to 11f, 10f to 7f)) {
                val y = origin.y + row.dp.toPx()
                drawLine(shade, Offset(origin.x, y), Offset(origin.x + length.dp.toPx(), y), 1.5.dp.toPx())
            }
        }
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 34.dp)
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = canPing && !pinging,
                    onClickLabel = stringResource(R.string.git_ping_all),
                    role = Role.Button,
                    onClick = onPingAll,
                ),
        ) {
            val c = center + Offset(0f, 2.dp.toPx())
            if (pinging) {
                val phase = (motion.time * 4f) % TAU
                for (i in 0 until 8) {
                    val a = phase + i * TAU / 8f
                    drawCircle(
                        gray(75 + i * 21),
                        radius = 1.1.dp.toPx(),
                        center = c + Offset(cos(a), sin(a)) * 9.dp.toPx(),
                    )
                }
            } else {
                // A gauge: half a dial and its needle.
                val shade = gray(if (canPing) 190 else 80)
                val d = 10.dp.toPx()
                val w = 1.6.dp.toPx()
                drawArc(
                    shade,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = c - Offset(d, d),
                    size = Size(d * 2, d * 2),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
                drawLine(shade, c, c + Offset(5.dp.toPx(), -6.dp.toPx()), w, cap = StrokeCap.Round)
            }
        }
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.action_add), role = Role.Button, onClick = onAdd),
        ) {
            drawPlus(6.dp.toPx(), Color.White)
        }
        if (sortOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(density) { IntOffset(-16.dp.roundToPx(), 33.dp.roundToPx()) },
                onDismissRequest = { sortOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                SortMenu(current = sort) { picked ->
                    sortOpen = false
                    if (picked != sort) onSort(picked)
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: ServerSort, onPick: (ServerSort) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .background(gray(23))
            .border(1.dp, gray(60), shape)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ServerSort.entries.forEach { mode ->
            val chosen = mode == current
            Box(
                Modifier
                    .size(170.dp, 37.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (chosen) Color.White else gray(23))
                    .semantics { selected = chosen }
                    .clickable { onPick(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(mode.labelRes), fontSize = 12.sp, color = if (chosen) Color.Black else Color.White)
            }
        }
    }
}

// -------------------------------------------------------------------------------------- the list

@Composable
private fun GitServerList(
    state: MainUiState,
    onSelect: (String) -> Unit,
    onPing: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onRemoveSubscription: (String) -> Unit,
    onRemoveNode: (String) -> Unit,
    onSetTlsInsecure: (String, Boolean) -> Unit,
    onToggleGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manualTitle = stringResource(R.string.home_group_manual)
    val sort = state.settings.serverSort
    val groups = remember(state.nodes, state.subscriptions, manualTitle, sort, state.latencies) {
        // Own servers first, as the design has them; then the subscriptions in their own order.
        buildGroups(state, manualTitle)
            .sortedBy { if (it.subscription == null) 0 else 1 }
            .map { it.copy(nodes = sortedNodes(it.nodes, sort, state.latencies)) }
    }
    var sharing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleting by remember { mutableStateOf<Subscription?>(null) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val uri = LocalUriHandler.current
    // A link the phone has nothing to open with is not worth a crash.
    fun open(url: String) { runCatching { uri.openUri(url) } }

    PullToRefreshBox(
        isRefreshing = state.refreshingSubscriptionIds.isNotEmpty(),
        onRefresh = onRefreshAll,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadeTop(10.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = bottomInset + 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.git_empty),
                        fontSize = 12.sp,
                        color = gray(140),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            groups.forEach { group ->
                val collapsed = group.id in state.settings.collapsedGroupIds || group.nodes.isEmpty()
                val subscription = group.subscription
                item(key = "g:${group.id}") {
                    GitGroupHeader(
                        group = group,
                        collapsed = collapsed,
                        onToggle = { if (group.nodes.isNotEmpty()) onToggleGroup(group.id) },
                        onRefresh = subscription?.let { s -> { onRefreshSubscription(s.id) } },
                        onSupport = subscription?.supportUrl?.let { url -> { open(url) } },
                        onOpenPage = subscription?.webPageUrl?.let { url -> { open(url) } },
                        onShare = subscription?.let { s -> { sharing = s.displayTitle() to s.url } },
                        onDelete = subscription?.let { s -> { deleting = s } },
                    )
                }
                if (!collapsed) {
                    items(group.nodes, key = { "n:${group.id}:${it.id}" }) { node ->
                        GitServerRow(
                            node = node,
                            selected = node.id == state.selectedNode?.id,
                            latency = state.latencies[node.id],
                            measuring = node.id in state.pingingNodeIds,
                            onSelect = { onSelect(node.id) },
                            onPing = { onPing(node.id) },
                            onRemove = { onRemoveNode(node.id) },
                            onSetTlsInsecure = tlsToggleOf(node, onSetTlsInsecure),
                            onShare = node.sourceUri?.let { link -> { sharing = node.name to link } },
                        )
                    }
                }
                item(key = "s:${group.id}") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    sharing?.let { (title, link) ->
        QrShareDialog(title = title, link = link, onDismiss = { sharing = null })
    }
    deleting?.let { subscription ->
        DeleteSubscriptionDialog(
            subscription = subscription,
            onConfirm = {
                onRemoveSubscription(subscription.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

private fun sortedNodes(nodes: List<ProxyNode>, sort: ServerSort, latencies: Map<String, LatencyResult>): List<ProxyNode> =
    when (sort) {
        ServerSort.AsListed -> nodes
        // Measured first, fastest first; then the unmeasured; the failed last.
        ServerSort.Latency -> nodes.sortedWith(
            compareBy<ProxyNode> { node ->
                val l = latencies[node.id]
                when {
                    l == null -> 1
                    l.failed -> 2
                    else -> 0
                }
            }.thenBy { node -> latencies[node.id]?.takeIf { !it.failed }?.millis ?: 0 },
        )
        ServerSort.Name -> nodes.sortedBy { nameWithoutFlag(it.name).lowercase() }
        ServerSort.Protocol -> nodes.sortedBy { it.protocol.label.lowercase() }
    }

@Composable
private fun GitGroupHeader(
    group: HomeGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onRefresh: (() -> Unit)?,
    onSupport: (() -> Unit)?,
    onOpenPage: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onRefresh != null || onSupport != null || onOpenPage != null || onShare != null || onDelete != null
    val note = group.subscription?.announce
    val quota = group.subscription?.userInfo?.usedFraction
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 55.dp)
            .clip(shape)
            .background(gray(27))
            .combinedClickable(
                onClickLabel = stringResource(if (collapsed) R.string.action_expand else R.string.action_collapse),
                onLongClickLabel = stringResource(R.string.servers_actions),
                onLongClick = if (hasMenu) ({ menuOpen = true }) else null,
                onClick = onToggle,
            ),
    ) {
        if (group.nodes.isNotEmpty()) {
            Canvas(Modifier.size(30.dp, 55.dp)) {
                val a = Offset(15.dp.toPx(), size.height / 2)
                val w = 1.3.dp.toPx()
                val color = gray(170)
                fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
                    drawLine(color, a + Offset(x1.dp.toPx(), y1.dp.toPx()), a + Offset(x2.dp.toPx(), y2.dp.toPx()), w, cap = StrokeCap.Round)
                if (collapsed) {
                    seg(-2f, -5f, 3f, 0f)
                    seg(3f, 0f, -2f, 5f)
                } else {
                    seg(-5f, -2f, 0f, 3f)
                    seg(0f, 3f, 5f, -2f)
                }
            }
        }
        Column(Modifier.padding(start = 32.dp, end = if (hasMenu) 44.dp else 14.dp, top = 9.dp, bottom = 9.dp)) {
            Text(
                group.title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = gray(240),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                gitGroupSubtitle(group),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = gray(135),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The provider's note, as on the Avaca screen.
            if (note != null) {
                Spacer(Modifier.height(6.dp))
                Text(note, fontSize = 10.sp, lineHeight = 14.sp, color = gray(185), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (quota != null) {
                val f = quota.coerceIn(0f, 1f)
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(gray(45))) {
                    Box(
                        Modifier
                            .fillMaxWidth(f)
                            .fillMaxHeight()
                            .background(if (f > 0.9f) AvacaPalette.Failed else gray(205)),
                    )
                }
            }
        }
        if (hasMenu) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(44.dp, 55.dp)
                    .clickable(onClickLabel = stringResource(R.string.servers_actions), role = Role.Button) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(18.dp)) {
                    for (dx in floatArrayOf(-5f, 0f, 5f)) {
                        drawCircle(gray(150), radius = 1.3.dp.toPx(), center = center + Offset(dx.dp.toPx(), 0f))
                    }
                }
                SubscriptionMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onRefresh = onRefresh,
                    onSupport = onSupport,
                    onOpenPage = onOpenPage,
                    onShare = onShare,
                    onDelete = onDelete,
                )
            }
        }
    }
}

/** The number of servers, then whatever the provider says about the quota and the term. */
@Composable
private fun gitGroupSubtitle(group: HomeGroup): String {
    val info = group.subscription?.userInfo
    val parts = buildList {
        add(pluralServers(group.nodes.size))
        if (info != null) {
            val used = info.usedBytes
            val total = info.totalBytes?.takeIf { it > 0 }
            when {
                used != null && total != null ->
                    add(stringResource(R.string.subscriptions_used_of, formatBytes(used).toString(), formatBytes(total).toString()))
                used != null -> add(formatBytes(used).toString())
            }
            daysUntil(info.expiresAtEpochSeconds)?.let { days ->
                add(
                    if (days > 0) {
                        stringResource(R.string.subscriptions_days_left, days)
                    } else {
                        stringResource(R.string.subscriptions_expired)
                    },
                )
            }
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun GitServerRow(
    node: ProxyNode,
    selected: Boolean,
    latency: LatencyResult?,
    measuring: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onRemove: () -> Unit,
    onSetTlsInsecure: (() -> Unit)?,
    onShare: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val flag = remember(node.name) { flagOf(node.name) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(gray(if (selected) 58 else 13))
            .drawBehind {
                val y = size.height - 0.25.dp.toPx()
                drawLine(gray(48), Offset(42.dp.toPx(), y), Offset(size.width - 1.dp.toPx(), y), 0.5.dp.toPx())
                if (selected) {
                    drawRect(Color.White, size = Size(4.dp.toPx(), size.height))
                    val inset = 1.5.dp.toPx()
                    drawRect(
                        gray(105),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            .semantics { this.selected = selected }
            .combinedClickable(
                onLongClickLabel = stringResource(R.string.servers_actions),
                onLongClick = { menuOpen = true },
                onClick = onSelect,
            ),
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (flag != null) {
                Text(flag, fontSize = 17.sp, lineHeight = 20.sp)
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(gray(95), radius = 11.dp.toPx(), style = Stroke(1.dp.toPx()))
                    drawCircle(gray(150), radius = 1.5.dp.toPx())
                }
            }
        }
        Row(
            Modifier.fillMaxSize().padding(start = 42.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    nameWithoutFlag(node.name),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = gray(236),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    detailOf(node),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = gray(135),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            RowEnd(node = node, selected = selected, latency = latency, measuring = measuring)
        }
        ServerMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            node = node,
            onPing = onPing,
            onSetTlsInsecure = onSetTlsInsecure,
            onShare = onShare,
            onRemove = onRemove,
        )
    }
}

/** The row's right end: the check in progress, the latency, a timeout, or the chosen dot / a chevron. */
@Composable
private fun RowEnd(node: ProxyNode, selected: Boolean, latency: LatencyResult?, measuring: Boolean) {
    // The bypass engine dials its own carriers; there is no single endpoint to time.
    val timed = node.protocol != Protocol.OPENFLUX
    when {
        measuring -> Text(stringResource(R.string.git_checking), fontSize = 10.sp, color = gray(180))
        timed && latency != null && !latency.failed -> {
            val ms = latency.millis
            Text(
                stringResource(R.string.servers_latency_value, ms),
                fontSize = 11.sp,
                color = gray(
                    when {
                        ms < 180 -> 235
                        ms < 500 -> 185
                        else -> 135
                    },
                ),
            )
        }
        // A QUIC server's UDP probe fails on networks that drop UDP; that is no verdict on the server.
        timed && latency != null && !node.protocol.isQuicBased ->
            Text(stringResource(R.string.git_timeout), fontSize = 10.sp, color = gray(125))
        selected -> Canvas(Modifier.size(10.dp)) { drawCircle(Color.White, radius = 3.dp.toPx()) }
        else -> Canvas(Modifier.size(10.dp)) {
            val w = 1.2.dp.toPx()
            val color = gray(135)
            drawLine(color, center + Offset(-2.dp.toPx(), -4.dp.toPx()), center + Offset(2.dp.toPx(), 0f), w, cap = StrokeCap.Round)
            drawLine(color, center + Offset(2.dp.toPx(), 0f), center + Offset(-2.dp.toPx(), 4.dp.toPx()), w, cap = StrokeCap.Round)
        }
    }
}

// ----------------------------------------------------------------------------------- constants

private fun gray(value: Int): Color {
    val v = value.coerceIn(0, 255)
    return Color(v, v, v)
}

private val Blue = Color(72, 151, 255)
private val Glow = Color(235, 242, 255)
private val NucleusRing = Color(210, 220, 235)
private val Halo = Color(245, 248, 255)
private val TrafficGlow = Color(230, 238, 250)
private val CalloutFill = Color(7, 14, 24)
private val MarkerText = Color(235, 242, 255)

private const val DOT_SHADES = 12
private val PI_F = PI.toFloat()
private val TAU = (2 * PI).toFloat()
private val MIX_GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
private val MIX_1 = 0xBF58476D1CE4E5B9uL.toLong()
private val MIX_2 = 0x94D049BB133111EBuL.toLong()
