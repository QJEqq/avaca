package com.mydrop.vpn.ui.screens.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mydrop.vpn.core.model.AppDesign
import com.mydrop.vpn.core.model.LatencyResult
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.ProxySettings
import com.mydrop.vpn.core.model.Subscription
import com.mydrop.vpn.core.model.VpnState
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.ui.MainUiState
import com.mydrop.vpn.ui.palette
import com.mydrop.vpn.ui.screens.home.GitHomeScreen
import com.mydrop.vpn.ui.screens.home.HomeScreen
import com.mydrop.vpn.ui.theme.AvacaPalette
import java.util.Locale

/**
 * Shown once, on the first launch: the two designs a swipe apart, each drawn live by its own main
 * screen over a sample subscription. The screen takes the palette of the design in front, so the
 * choice is seen whole. Settings → Appearance changes it later.
 */
@Composable
fun DesignPickerScreen(onChoose: (AppDesign) -> Unit, modifier: Modifier = Modifier) {
    val designs = AppDesign.entries
    val pager = rememberPagerState { designs.size }
    val current = designs[pager.currentPage]
    LaunchedEffect(current) { AvacaPalette.use(current.palette()) }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val sample = remember(locale) { sampleState(locale) }
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    Column(
        modifier
            .fillMaxSize()
            .background(AvacaPalette.Graphite)
            // Over the app, not beside it: taps stop here instead of reaching the screen below.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.design_picker_title),
            style = MaterialTheme.typography.headlineSmall,
            color = AvacaPalette.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.design_picker_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = AvacaPalette.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val scale = minOf(maxHeight / screenHeight, maxWidth * 0.8f / screenWidth)
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DesignPreview(designs[page], sample, screenWidth, screenHeight, scale)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            designs.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (index == pager.currentPage) AvacaPalette.Sand else AvacaPalette.Well),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(current.labelRes), style = MaterialTheme.typography.titleMedium, color = AvacaPalette.Text)
        Text(
            stringResource(if (current == AppDesign.Git) R.string.design_git_caption else R.string.design_avaca_caption),
            style = MaterialTheme.typography.bodySmall,
            color = AvacaPalette.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onChoose(current) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AvacaPalette.Sand,
                contentColor = AvacaPalette.Graphite,
            ),
        ) {
            Text(
                stringResource(R.string.design_picker_choose, stringResource(current.labelRes)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The design's own main screen at full size, scaled down into a phone-shaped frame. */
@Composable
private fun DesignPreview(design: AppDesign, sample: MainUiState, width: Dp, height: Dp, scale: Float) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .size(width * scale, height * scale)
            .clip(shape)
            .border(1.dp, AvacaPalette.Outline, shape),
    ) {
        Box(
            Modifier
                .requiredSize(width, height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            when (design) {
                AppDesign.Avaca -> HomeScreen(
                    state = sample,
                    onToggleConnection = {},
                    onSelect = {},
                    onPing = {},
                    onPingGroup = {},
                    onRefreshAll = {},
                    onRefreshSubscription = {},
                    onRemoveSubscription = {},
                    onRemoveNode = {},
                    onSetTlsInsecure = { _, _ -> },
                    onToggleGroup = {},
                    onAdd = {},
                    onOpenSettings = {},
                )
                AppDesign.Git -> GitHomeScreen(
                    state = sample,
                    onToggleConnection = {},
                    onSelect = {},
                    onPing = {},
                    onPingGroup = {},
                    onRefreshAll = {},
                    onRefreshSubscription = {},
                    onRemoveSubscription = {},
                    onRemoveNode = {},
                    onSetTlsInsecure = { _, _ -> },
                    onToggleGroup = {},
                    onSort = {},
                    onAdd = {},
                    onOpenSettings = {},
                )
            }
        }
        // A picture of the screen, not the screen: taps end here, swipes go on to the pager.
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
    }
}

/** A connected sample subscription of four servers, named in the interface's language. */
private fun sampleState(locale: Locale): MainUiState {
    fun country(code: String) = runCatching {
        Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
    }.getOrDefault(code)
    fun flag(code: String) = code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    val latencies = listOf("NL" to 42, "DE" to 57, "FI" to 64, "PL" to 49)
    val nodes = latencies.map { (code, _) ->
        ProxyNode(
            id = "sample-$code",
            name = "${flag(code)} ${country(code)}",
            server = "example.com",
            port = 443,
            settings = ProxySettings.Vless(uuid = "00000000-0000-0000-0000-000000000000"),
            subscriptionId = SAMPLE_ID,
        )
    }
    return MainUiState(
        nodes = nodes,
        subscriptions = listOf(
            Subscription(id = SAMPLE_ID, name = "Avaca", url = "https://example.com/sub", nodeIds = nodes.map { it.id }),
        ),
        selectedNode = nodes.first(),
        latencies = latencies.associate { (code, ms) -> "sample-$code" to LatencyResult("sample-$code", ms, 0L) },
        vpnState = VpnState.Connected(nodes.first().id, System.currentTimeMillis()),
        tunnelIsSimulated = false,
    )
}

private const val SAMPLE_ID = "sample"
