package com.mydrop.vpn.ui.screens.home

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.mydrop.vpn.core.format.daysUntil
import com.mydrop.vpn.core.format.formatDuration
import com.mydrop.vpn.core.model.LatencyResult
import com.mydrop.vpn.core.model.Protocol
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.Subscription
import com.mydrop.vpn.core.model.TrafficStats
import com.mydrop.vpn.core.model.VpnState
import com.mydrop.vpn.core.model.WorldMap
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.ui.MainUiState
import com.mydrop.vpn.ui.components.GlobeMotion
import com.mydrop.vpn.ui.components.GlobePoint
import com.mydrop.vpn.ui.components.QrShareDialog
import com.mydrop.vpn.ui.components.WireGlobe
import com.mydrop.vpn.ui.components.neumorphic
import com.mydrop.vpn.ui.format.formatBytes
import com.mydrop.vpn.ui.format.formatRate
import com.mydrop.vpn.ui.format.pluralServers
import com.mydrop.vpn.ui.theme.AvacaPalette
import com.mydrop.vpn.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay

/**
 * The whole app on one screen.
 *
 * The top third is the tunnel: one button, what it is connected to, and the planet behind it,
 * whose lines take the tunnel's colour — white while off, yellow while connecting, green while
 * connected, red when it failed. The rest is the servers, one card per subscription. Everything
 * else — settings, the journal, split tunnelling — is behind the gear, so this screen only ever
 * asks one question: connect, and through which server.
 */
@Composable
fun HomeScreen(
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
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(AvacaPalette.Graphite)) {
        val heroHeight = max(maxHeight / 3, HeroMinHeight)
        // The hero is taller than its third: the planet runs on under the list and fades out there,
        // so the two read as one surface instead of two panels with a seam between them.
        ConnectHero(
            state = state,
            heroHeight = heroHeight,
            // With nothing to connect to, the big button is the way to add something.
            onToggle = if (state.nodes.isEmpty()) onAdd else onToggleConnection,
            onAdd = onAdd,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(heroHeight + HeroBleed),
        )
        ServerList(
                state = state,
                onSelect = onSelect,
                onPing = onPing,
                onPingGroup = onPingGroup,
                onRefreshAll = onRefreshAll,
                onRefreshSubscription = onRefreshSubscription,
                onRemoveSubscription = onRemoveSubscription,
                onRemoveNode = onRemoveNode,
                onSetTlsInsecure = onSetTlsInsecure,
                onToggleGroup = onToggleGroup,
                onAdd = onAdd,
                // A gap under the hero, so rows scroll away into the fade, not against the hero's edge.
                modifier = Modifier.fillMaxSize().padding(top = heroHeight + 10.dp),
            )
    }
}

/** Keeps the content whole down to [solidUntil], then fades it out to nothing at the bottom. */
private fun Modifier.fadeBelow(solidUntil: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val stop = (solidUntil.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Black, stop to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** Fades the first [length] of the content in from nothing, so rows leave under a soft edge. */
internal fun Modifier.fadeTop(length: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val stop = (length.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, stop to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

/** The tunnel's colour: the planet's lines, the caption, the glow. */
private fun stateColor(vpn: VpnState): Color = when (vpn) {
    VpnState.Disconnected, VpnState.Disconnecting -> AvacaPalette.Idle
    is VpnState.Connecting -> AvacaPalette.Connecting
    is VpnState.Connected -> AvacaPalette.Connected
    is VpnState.Failed -> AvacaPalette.Failed
}

// ------------------------------------------------------------------------------------- the hero

@Composable
private fun ConnectHero(
    state: MainUiState,
    /** The hero's own height; the box is taller by the bleed the planet fades out in. */
    heroHeight: Dp,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vpn = state.vpnState
    // The server the tunnel is on — or, while it is down, the one a tap would connect to.
    val node = remember(vpn, state.nodes, state.selectedNode) {
        vpn.activeNodeId?.let { id -> state.nodes.firstOrNull { it.id == id } } ?: state.selectedNode
    }
    val connected = vpn is VpnState.Connected
    val target = remember(node?.name) { node?.name?.let { GlobePoint.ofServerName(it) } }
    val motion = when {
        vpn is VpnState.Connecting -> GlobeMotion.Spin
        connected && target != null -> GlobeMotion.Pin
        else -> GlobeMotion.Drift
    }
    val tone by animateColorAsState(stateColor(vpn), tween(600), label = "tunnel-tone")

    BoxWithConstraints(modifier.background(AvacaPalette.Graphite)) {
        val density = LocalDensity.current
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val clusterTop = topInset + TopBarHeight
        // The ring's centre: under the top bar, after the status capsule above it.
        val ringCenterY = clusterTop + RingClusterTop + CapsuleHeight + RingGap + RingFrame / 2
        val width = maxWidth
        val globeCenter = with(density) { Offset((width * 0.78f).toPx(), ringCenterY.toPx()) }
        val globeRadius = with(density) { minOf(heroHeight.toPx() * 0.40f, width.toPx() * 0.34f) }
        // Where the country lands once the camera has flown in: where the ring was, a little to
        // the right — the ring has gone by then, and the bar at the bottom names the place.
        val focus = with(density) { Offset((width * 0.58f).toPx(), (ringCenterY - 8.dp).toPx()) }

        // The wash and the planet together, fading out below the hero's own third.
        Box(Modifier.fillMaxSize().fadeBelow(heroHeight - 36.dp)) {
        // A faint wash of the tunnel's colour behind the planet: the state, felt before it is read.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(tone.copy(alpha = 0.07f), Color.Transparent),
                            center = globeCenter,
                            radius = globeRadius * 1.45f,
                        ),
                        radius = globeRadius * 1.45f,
                        center = globeCenter,
                    )
                },
        )

        WireGlobe(
            center = globeCenter,
            radius = globeRadius,
            focus = focus,
            // Three times the resting size: one part of the world — Europe, South America, East
            // Asia — fills the hero.
            zoomedRadius = globeRadius * 3f,
            motion = motion,
            target = target,
            lineColor = tone,
            markerColor = AvacaPalette.Sand,
            markerCore = AvacaPalette.SandLight,
            modifier = Modifier.fillMaxSize(),
        )
        }

        TopBar(
            onAdd = onAdd,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxWidth().padding(top = topInset).height(TopBarHeight),
        )

        // Off, connecting or failed: the ring, what is going on above it, the server below it.
        // Connected: the ring gives the stage to the map, and a bar at the bottom says where and
        // how fast, with the switch that ends it.
        val shift by animateFloatAsState(
            targetValue = if (connected) 1f else 0f,
            animationSpec = tween(520, easing = FastOutSlowInEasing),
            label = "hero-shift",
        )

        if (shift < 0.999f) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = clusterTop + RingClusterTop)
                    .graphicsLayer {
                        alpha = 1f - shift
                        val s = 1f - 0.06f * shift
                        scaleX = s
                        scaleY = s
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusCapsule(vpn = vpn)
                Spacer(Modifier.height(RingGap))
                RingButton(vpn = vpn, onClick = onToggle, modifier = Modifier.size(RingFrame))
                Spacer(Modifier.height(6.dp))
                ServerCapsule(
                    node = node,
                    latency = node?.let { state.latencies[it.id] },
                    hasServers = state.nodes.isNotEmpty(),
                    failure = (vpn as? VpnState.Failed)?.message?.takeIf { it.isNotBlank() },
                )
            }
        }

        if (shift > 0.001f && node != null) {
            ConnectedBar(
                node = node,
                vpn = vpn,
                traffic = state.traffic,
                onToggle = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = heroHeight - ConnectedBarHeight - 8.dp)
                    .height(ConnectedBarHeight)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        alpha = ((shift - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        translationY = (1f - shift) * 16.dp.toPx()
                    },
            )
        }
    }
}

@Composable
private fun TopBar(onAdd: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 22.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp),
            color = AvacaPalette.Text,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add), tint = AvacaPalette.Text)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title), tint = AvacaPalette.Text)
        }
    }
}

/**
 * The connect button, in the reference's relief: a soft square lifted off the ground while off,
 * filled with sand while connected — the one large patch of the accent. While it is
 * busy a yellow glow breathes behind it; when a connect fails the glyph turns red.
 */
/**
 * The connect control while the tunnel is down: a thin ring round the power glyph, grey at rest,
 * yellow with a running arc while connecting, red when a connect failed. No fill, no relief —
 * outline only, so the screen stays quiet until something happens.
 */
@Composable
private fun RingButton(vpn: VpnState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val busy = vpn is VpnState.Connecting || vpn is VpnState.Disconnecting
    val tint by animateColorAsState(
        targetValue = when (vpn) {
            is VpnState.Connecting -> AvacaPalette.Connecting
            is VpnState.Failed -> AvacaPalette.Failed
            else -> RingIdle
        },
        animationSpec = tween(320),
        label = "ring-tint",
    )
    val glyph by animateColorAsState(
        targetValue = when (vpn) {
            is VpnState.Connecting -> AvacaPalette.Connecting
            is VpnState.Failed -> AvacaPalette.Failed
            else -> GlyphIdle
        },
        animationSpec = tween(320),
        label = "ring-glyph",
    )
    val angle = if (busy) {
        rememberInfiniteTransition(label = "ring-busy").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "ring-busy-angle",
        )
    } else {
        null
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "ring-press")

    val haptics = LocalHapticFeedback.current
    val action = stringResource(
        if (vpn.isActive) R.string.connect_button_disconnect else R.string.connect_button_connect,
    )
    val stateText = stateLabel(vpn)

    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = RingSize.toPx() / 2f
            // A moat of the ground round the button: opaque up to the ring, then fading out, so the
            // planet's lines stop short of it instead of running through it.
            val moat = r + 26.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0f to AvacaPalette.Graphite,
                    ((r + 4.dp.toPx()) / moat) to AvacaPalette.Graphite,
                    1f to AvacaPalette.Graphite.copy(alpha = 0f),
                    center = center,
                    radius = moat,
                ),
                radius = moat,
            )
            // The face a step lighter than the ground: a surface you press, not a hole.
            drawCircle(RingFace, radius = r)
            // A faint second ring round the first: the button's only ornament.
            drawCircle(tint.copy(alpha = 0.08f), radius = r + 6.dp.toPx(), style = Stroke(width = 8.dp.toPx()))
            if (angle == null) {
                drawCircle(tint, radius = r, style = Stroke(width = 1.75.dp.toPx()))
            } else {
                drawCircle(tint.copy(alpha = 0.25f), radius = r, style = Stroke(width = 1.75.dp.toPx()))
                drawArc(
                    color = tint,
                    startAngle = angle.value,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(RingSize)
                .clip(CircleShape)
                .semantics {
                    contentDescription = action
                    stateDescription = stateText
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    // A second tap on "Отключение…" is not a request to start again.
                    enabled = vpn !is VpnState.Disconnecting,
                    role = Role.Button,
                    onClickLabel = action,
                ) {
                    haptics.performHapticFeedback(
                        if (vpn.isActive) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = glyph,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

@Composable
internal fun stateLabel(vpn: VpnState): String = when (vpn) {
    VpnState.Disconnected -> stringResource(R.string.home_state_not_connected)
    is VpnState.Connecting -> stringResource(R.string.home_state_connecting)
    is VpnState.Connected -> stringResource(R.string.connect_state_connected)
    VpnState.Disconnecting -> stringResource(R.string.home_state_disconnecting)
    is VpnState.Failed -> stringResource(R.string.home_state_failed)
}

/** A thin-outlined capsule — the one shape every small piece of status sits in. */
@Composable
private fun Capsule(
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, CapsuleLine, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A small dot in the tunnel's colour, with a soft ring round it. */
@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)))
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
    }
}

/** Above the ring: what the tunnel is doing, in a word and a coloured dot. */
@Composable
private fun StatusCapsule(vpn: VpnState, modifier: Modifier = Modifier) {
    val dot = when (vpn) {
        VpnState.Disconnected, VpnState.Disconnecting -> RingIdle
        else -> stateColor(vpn)
    }
    Capsule(modifier.height(CapsuleHeight)) {
        StatusDot(dot)
        Spacer(Modifier.width(7.dp))
        Text(
            text = stateLabel(vpn),
            style = MaterialTheme.typography.labelLarge,
            color = AvacaPalette.Text,
            maxLines = 1,
        )
    }
}

/** Below the ring: the server a tap would connect to, and its ping — or why it cannot. */
@Composable
private fun ServerCapsule(
    node: ProxyNode?,
    latency: LatencyResult?,
    hasServers: Boolean,
    failure: String?,
    modifier: Modifier = Modifier,
) {
    val text = when {
        !hasServers -> stringResource(R.string.home_hint_add_server)
        failure != null -> failure
        node != null -> titleOf(node)
        else -> stringResource(R.string.home_hint_pick_server)
    }
    Capsule(modifier.widthIn(max = 300.dp).height(CapsuleHeight)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (failure != null) AvacaPalette.Failed else AvacaPalette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (failure == null && node != null && latency != null && !latency.failed) {
            Text(
                text = " · " + stringResource(R.string.servers_latency_value, latency.millis),
                style = MaterialTheme.typography.labelLarge,
                color = AvacaPalette.TextMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * The bottom of the hero once connected: where the tunnel comes out and for how long, in a capsule
 * over the map, the speed under it, and the switch that ends it.
 */
@Composable
private fun ConnectedBar(
    node: ProxyNode,
    vpn: VpnState,
    traffic: TrafficStats,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val since = (vpn as? VpnState.Connected)?.connectedAtEpochMillis
    val now by produceState(System.currentTimeMillis(), since) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val down = formatRate(traffic.downloadBytesPerSecond).toString()
    val up = formatRate(traffic.uploadBytesPerSecond).toString()
    val speeds = stringResource(R.string.connect_rate_download) + " " + down + ", " +
        stringResource(R.string.connect_rate_upload) + " " + up
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Capsule(
                modifier = Modifier.height(30.dp),
                background = AvacaPalette.Graphite.copy(alpha = 0.72f),
            ) {
                StatusDot(AvacaPalette.Connected)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = titleOf(node),
                    style = MaterialTheme.typography.labelLarge,
                    color = AvacaPalette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (since != null) {
                    Text(
                        text = " · " + formatDuration(now - since),
                        style = MaterialTheme.typography.labelLarge,
                        color = AvacaPalette.TextMuted,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "↓ $down    ↑ $up",
                style = MaterialTheme.typography.labelMedium,
                color = AvacaPalette.TextMuted,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .semantics { contentDescription = speeds },
            )
        }
        Spacer(Modifier.width(12.dp))
        BigToggle(on = vpn is VpnState.Connected, onToggle = onToggle)
    }
}

/** The switch that ends the tunnel: a sand outline with a sand knob while on, grey once off. */
@Composable
private fun BigToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "big-toggle",
    )
    val line = androidx.compose.ui.graphics.lerp(RingIdle, AvacaPalette.Sand, progress)
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.connect_button_disconnect)
    Box(
        modifier
            .size(width = 58.dp, height = 34.dp)
            .clip(CircleShape)
            .border(1.5.dp, line, CircleShape)
            .toggleable(value = on, role = Role.Switch) {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                onToggle()
            }
            .semantics { contentDescription = label }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = 24.dp * progress)
                .size(26.dp)
                .clip(CircleShape)
                .background(line),
        )
    }
}

// ------------------------------------------------------------------------------------ the list

internal data class HomeGroup(
    val id: String,
    val title: String,
    /** Null for the servers added by hand. */
    val subscription: Subscription?,
    val nodes: List<ProxyNode>,
)

internal fun buildGroups(state: MainUiState, manualTitle: String): List<HomeGroup> {
    val fromSubscriptions = state.subscriptions.map { subscription ->
        HomeGroup(
            id = subscription.id,
            title = subscription.remoteTitle ?: subscription.name,
            subscription = subscription,
            nodes = state.nodes.filter { it.subscriptionId == subscription.id },
        )
    }
    val manual = state.manualNodes()
    val manualGroup = if (manual.isEmpty()) null else HomeGroup(MANUAL_GROUP_ID, manualTitle, null, manual)
    // An empty subscription stays visible: it is where its refresh and delete live, and a
    // subscription that silently vanished after a failed update would be a mystery.
    return fromSubscriptions + listOfNotNull(manualGroup)
}

@Composable
private fun ServerList(
    state: MainUiState,
    onSelect: (String) -> Unit,
    onPing: (String) -> Unit,
    onPingGroup: (List<String>) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onRemoveSubscription: (String) -> Unit,
    onRemoveNode: (String) -> Unit,
    onSetTlsInsecure: (String, Boolean) -> Unit,
    onToggleGroup: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manualTitle = stringResource(R.string.home_group_manual)
    val groups = remember(state.nodes, state.subscriptions, manualTitle) { buildGroups(state, manualTitle) }
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
            modifier = Modifier.fillMaxSize().fadeTop(24.dp),
            // Room on every side for the cards' shadows, which fall outside the cards.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = bottomInset + 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (groups.isEmpty()) {
                item(key = "empty") {
                    EmptyState(onAdd = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                }
            }
            // One card per subscription, as one item: the card's relief is drawn round the whole
            // of it, which a card cut into rows could not do without seams.
            items(groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    collapsed = group.id in state.settings.collapsedGroupIds || group.nodes.isEmpty(),
                    state = state,
                    onToggle = { if (group.nodes.isNotEmpty()) onToggleGroup(group.id) },
                    onPing = { onPingGroup(group.nodes.map { it.id }) },
                    onRefresh = group.subscription?.let { s -> { onRefreshSubscription(s.id) } },
                    onShareSubscription = group.subscription?.let { s -> { sharing = s.displayTitle() to s.url } },
                    onDelete = group.subscription?.let { s -> { deleting = s } },
                    onSupport = group.subscription?.supportUrl?.let { url -> { open(url) } },
                    onOpenPage = group.subscription?.webPageUrl?.let { url -> { open(url) } },
                    onSelect = onSelect,
                    onPingNode = onPing,
                    onRemoveNode = onRemoveNode,
                    onSetTlsInsecure = onSetTlsInsecure,
                    onShareNode = { node -> node.sourceUri?.let { sharing = node.name to it } },
                )
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

@Composable
private fun GroupCard(
    group: HomeGroup,
    collapsed: Boolean,
    state: MainUiState,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    onRefresh: (() -> Unit)?,
    onShareSubscription: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onSupport: (() -> Unit)?,
    onOpenPage: (() -> Unit)?,
    onSelect: (String) -> Unit,
    onPingNode: (String) -> Unit,
    onRemoveNode: (String) -> Unit,
    onSetTlsInsecure: (String, Boolean) -> Unit,
    onShareNode: (ProxyNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .neumorphic(CardRadius)
            .clip(shape)
            .background(AvacaPalette.Graphite),
    ) {
        GroupHeader(
            group = group,
            collapsed = collapsed,
            pinging = group.nodes.any { it.id in state.pingingNodeIds },
            onToggle = onToggle,
            onPing = onPing,
            onRefresh = onRefresh,
            onShare = onShareSubscription,
            onDelete = onDelete,
            onSupport = onSupport,
            onOpenPage = onOpenPage,
        )
        if (!collapsed) {
            group.nodes.forEachIndexed { index, node ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 18.dp),
                        thickness = 1.dp,
                        color = AvacaPalette.Divider,
                    )
                }
                ServerRow(
                    node = node,
                    selected = node.id == state.selectedNode?.id,
                    latency = state.latencies[node.id],
                    measuring = node.id in state.pingingNodeIds,
                    onSelect = { onSelect(node.id) },
                    onPing = { onPingNode(node.id) },
                    onRemove = { onRemoveNode(node.id) },
                    onSetTlsInsecure = tlsToggleOf(node, onSetTlsInsecure),
                    onShare = node.sourceUri?.let { { onShareNode(node) } },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupHeader(
    group: HomeGroup,
    collapsed: Boolean,
    pinging: Boolean,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    onRefresh: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onSupport: (() -> Unit)?,
    onOpenPage: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val quota = group.subscription?.userInfo?.usedFraction
    val note = group.subscription?.announce
    Column(
        modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(if (collapsed) R.string.action_expand else R.string.action_collapse),
                onClick = onToggle,
            )
            .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = if (collapsed) 12.dp else 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = AvacaPalette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (group.nodes.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            tint = AvacaPalette.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = groupSubtitle(group),
                    style = MaterialTheme.typography.bodySmall,
                    color = AvacaPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (group.nodes.isNotEmpty()) {
                IconButton(onClick = onPing) {
                    if (pinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AvacaPalette.Connecting,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = stringResource(R.string.home_ping_group),
                            tint = AvacaPalette.TextMuted,
                        )
                    }
                }
            }
            if (onRefresh != null || onShare != null || onDelete != null || onSupport != null || onOpenPage != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.servers_actions),
                            tint = AvacaPalette.TextMuted,
                        )
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
        // The provider's note — what Happ shows under the subscription's name. Full width, below
        // the row, so a long one is not squeezed between the title and the buttons.
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = AvacaPalette.Text.copy(alpha = 0.82f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 14.dp, top = 6.dp),
            )
        }
        if (quota != null) {
            QuotaBar(
                fraction = quota,
                modifier = Modifier.fillMaxWidth().padding(end = 14.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun groupSubtitle(group: HomeGroup): String {
    val info = group.subscription?.userInfo
    val parts = buildList {
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
        if (isEmpty()) add(pluralServers(group.nodes.size))
    }
    return parts.joinToString(" · ")
}

/** How much of the quota is used: sand, turning red once nine tenths are gone. */
@Composable
private fun QuotaBar(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AvacaPalette.Divider),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(3.dp)
                .background(if (f > 0.9f) AvacaPalette.Failed else AvacaPalette.Sand),
        )
    }
}

@Composable
private fun ServerRow(
    node: ProxyNode,
    selected: Boolean,
    latency: LatencyResult?,
    measuring: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onRemove: () -> Unit,
    /** Null where there is no certificate check to switch off (plaintext, REALITY). */
    onSetTlsInsecure: (() -> Unit)?,
    /** Null when the server was not parsed from a link and has nothing to hand over. */
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val highlight by animateColorAsState(
        targetValue = if (selected) AvacaPalette.Selected else Color.Transparent,
        animationSpec = tween(220),
        label = "row-highlight",
    )
    val isOpenflux = node.protocol == Protocol.OPENFLUX

    Box(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(highlight)
                .semantics { this.selected = selected }
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = stringResource(R.string.servers_actions),
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerMark(node)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = nameWithoutFlag(node.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AvacaPalette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detailOf(node),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOpenflux) AvacaPalette.Sand else AvacaPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Ping(node = node, latency = latency, measuring = measuring)
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = AvacaPalette.Sand,
                    modifier = Modifier.size(18.dp),
                )
            }
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

/** The flag, when the name has one; otherwise a mark for the kind of server. */
@Composable
private fun ServerMark(node: ProxyNode) {
    val flag = remember(node.name) { flagOf(node.name) }
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AvacaPalette.Selected),
        contentAlignment = Alignment.Center,
    ) {
        when {
            flag != null -> Text(flag, fontSize = 19.sp)
            node.protocol == Protocol.OPENFLUX ->
                Icon(Icons.AutoMirrored.Rounded.AltRoute, contentDescription = null, tint = AvacaPalette.Sand, modifier = Modifier.size(18.dp))
            else ->
                Icon(Icons.Rounded.Public, contentDescription = null, tint = AvacaPalette.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Ping(node: ProxyNode, latency: LatencyResult?, measuring: Boolean) {
    val semantic = LocalSemanticColors.current
    val style = MaterialTheme.typography.labelLarge
    when {
        measuring -> CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 1.5.dp,
            color = AvacaPalette.Connecting,
        )
        // The bypass engine dials its own carriers; there is no single endpoint to time from here.
        node.protocol == Protocol.OPENFLUX -> Unit
        latency == null -> Text("—", style = style, color = semantic.latencyDead)
        latency.failed && node.protocol.isQuicBased -> Text("—", style = style, color = semantic.latencyDead)
        latency.failed -> Text(stringResource(R.string.home_ping_failed), style = style, color = semantic.latencySlow)
        else -> Text(
            text = stringResource(R.string.servers_latency_value, latency.millis),
            style = style,
            color = when {
                latency.millis < 150 -> semantic.latencyFast
                latency.millis < 400 -> semantic.latencyMedium
                else -> semantic.latencySlow
            },
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.Public,
            contentDescription = null,
            tint = AvacaPalette.TextMuted,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = AvacaPalette.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AvacaPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_add))
        }
    }
}

// ------------------------------------------------------------------- shared by both designs

/** A subscription's actions, the same in both designs. A null action is left out. */
@Composable
internal fun SubscriptionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRefresh: (() -> Unit)?,
    onSupport: (() -> Unit)?,
    onOpenPage: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        onRefresh?.let { refresh ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_refresh)) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = { onDismiss(); refresh() },
            )
        }
        onSupport?.let { support ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_support)) },
                leadingIcon = { Icon(Icons.Rounded.SupportAgent, null) },
                onClick = { onDismiss(); support() },
            )
        }
        onOpenPage?.let { page ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_open_page)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                onClick = { onDismiss(); page() },
            )
        }
        onShare?.let { share ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.subscriptions_show_qr)) },
                leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                onClick = { onDismiss(); share() },
            )
        }
        onDelete?.let { delete ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.subscriptions_delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { onDismiss(); delete() },
            )
        }
    }
}

/** A server's actions, the same in both designs. */
@Composable
internal fun ServerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    node: ProxyNode,
    onPing: () -> Unit,
    /** Null where there is no certificate check to switch off (plaintext, REALITY). */
    onSetTlsInsecure: (() -> Unit)?,
    /** Null when the server was not parsed from a link and has nothing to hand over. */
    onShare: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (node.protocol != Protocol.OPENFLUX) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.servers_measure_latency)) },
                leadingIcon = { Icon(Icons.Rounded.NetworkPing, null) },
                onClick = { onDismiss(); onPing() },
            )
        }
        onSetTlsInsecure?.let { toggle ->
            val skipping = node.tls?.insecure == true
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (skipping) R.string.servers_verify_certificate else R.string.servers_skip_certificate,
                        ),
                    )
                },
                leadingIcon = { Icon(if (skipping) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe, null) },
                onClick = { onDismiss(); toggle() },
            )
        }
        onShare?.let { share ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.servers_show_qr)) },
                leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                onClick = { onDismiss(); share() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.servers_delete)) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
            onClick = { onDismiss(); onRemove() },
        )
    }
}

@Composable
internal fun DeleteSubscriptionDialog(subscription: Subscription, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_delete_subscription_title)) },
        text = { Text(stringResource(R.string.home_delete_subscription_body, subscription.displayTitle())) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The switch for a server's certificate check, or null where there is none to switch. */
internal fun tlsToggleOf(node: ProxyNode, onSetTlsInsecure: (String, Boolean) -> Unit): (() -> Unit)? =
    node.tls
        ?.takeIf { it.enabled && it.reality == null }
        ?.let { tls -> { onSetTlsInsecure(node.id, !tls.insecure) } }

// ---------------------------------------------------------------------------------- naming

internal fun Subscription.displayTitle(): String = remoteTitle ?: name

/** "🇳🇱 Нидерланды" — the flag first, then the name without it, the same for every provider. */
private fun titleOf(node: ProxyNode): String {
    val flag = flagOf(node.name) ?: return node.name
    return "$flag ${nameWithoutFlag(node.name)}"
}

/** The flag emoji for the country a server's name carries, rebuilt from its ISO code. */
internal fun flagOf(name: String): String? = WorldMap.countryCodeOf(name)?.let { code ->
    code.map { letter -> String(Character.toChars(REGIONAL_A + (letter - 'A'))) }.joinToString("")
}

/** The name with its flag taken out — the flag gets its own place in the row. */
internal fun nameWithoutFlag(name: String): String {
    val flag = flagOf(name) ?: return name
    val stripped = name.replaceFirst(flag, "").trim().trimStart('|', '-', '·', ':', ',').trim()
    return stripped.ifEmpty { name }
}

/** One quiet line under the name: the protocol and what actually changes how it is dialled. */
internal fun detailOf(node: ProxyNode): String = buildList {
    node.group?.let { add("Auto ×${it.members.size}") }
    add(node.protocol.label)
    node.transport?.label?.let(::add)
    node.tls?.badge?.let(::add)
}.distinct().joinToString(" · ")

internal const val MANUAL_GROUP_ID = "manual"
private const val REGIONAL_A = 0x1F1E6

private val HeroMinHeight = 264.dp
/** How far below its third the planet runs on, fading, under the list. */
private val HeroBleed = 150.dp
private val TopBarHeight = 56.dp
private val RingSize = 96.dp
/** The ring with room for its faint outer ring. */
private val RingFrame = 112.dp
private val RingGap = 10.dp
private val RingClusterTop = 8.dp
private val CapsuleHeight = 28.dp
private val ConnectedBarHeight = 60.dp
private val CapsuleLine = Color(0xFF444444)
private val RingIdle = Color(0xFF8E8E8E)
private val RingFace = Color(0xFF2E2E2E)
private val CardRadius = 22.dp
private val GlyphIdle = Color(0xFFCFCFCF)
