package com.mydrop.vpn.ui.screens.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Translate
import com.mydrop.vpn.ui.components.Segmented
import com.mydrop.vpn.core.model.AppDesign
import com.mydrop.vpn.data.GeoAssetStore
import com.mydrop.vpn.data.Ipv6Guard
import com.mydrop.vpn.ui.format.formatBytes
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mydrop.vpn.ui.components.SettingsGroup
import com.mydrop.vpn.ui.components.PillSwitch
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.core.model.AppLanguage
import com.mydrop.vpn.core.model.AppSettings
import com.mydrop.vpn.core.model.LogLevel
import com.mydrop.vpn.core.model.DnsProfile
import com.mydrop.vpn.core.model.PingMode
import com.mydrop.vpn.core.model.SplitTunnelMode
import com.mydrop.vpn.core.model.RoutingMode
import com.mydrop.vpn.ui.components.ScreenHeader
import com.mydrop.vpn.ui.theme.AvacaPalette
import com.mydrop.vpn.ui.components.neumorphic
import androidx.compose.material.icons.rounded.Security
import com.mydrop.vpn.ui.components.TonalIconButton
import com.mydrop.vpn.ui.components.ShapeSpinner
import com.mydrop.vpn.vpn.TunnelTileService

/**
 * A text setting that only reaches storage once it is valid.
 *
 * Writing every keystroke straight into persisted settings is how both DNS fields ended up empty:
 * clearing one to retype it saved a blank address, the generated config carried `"server": ""`,
 * and sing-box refused to start — which looked like the tunnel dropping the instant it connected.
 * The text being edited lives here instead, and only a value that passes [isValid] is committed,
 * so the last good setting survives an unfinished edit.
 */
@Composable
private fun ValidatedField(
    initial: String,
    label: String,
    hint: String,
    error: String,
    isValid: (String) -> Boolean,
    onCommit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val valid = isValid(text)

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (isValid(it)) onCommit(it)
        },
        label = { Text(label) },
        supportingText = { Text(if (valid) hint else error) },
        isError = !valid,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

/** Connections per mux connection offered in settings. */
private val MUX_CONCURRENCY = listOf(4, 8, 16)

/** A number of bytes or milliseconds, or a range of them: "100" or "100-200". */
private val FRAGMENT_RANGE = Regex("""\d{1,5}(-\d{1,5})?""")

/** Offered cadences. Anything under half an hour re-reads a list that changes daily. */
private val UPDATE_INTERVALS = listOf(
    30 to R.string.settings_interval_30m,
    60 to R.string.settings_interval_1h,
    180 to R.string.settings_interval_3h,
    360 to R.string.settings_interval_6h,
    720 to R.string.settings_interval_12h,
    1440 to R.string.settings_interval_1d,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    splitTunnelAppCount: Int,
    dnsProfiles: List<DnsProfile>,
    selectedDnsId: String?,
    onSelectDns: (String?) -> Unit,
    onRemoveDns: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onRoutingModeChange: (RoutingMode) -> Unit,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSplitTunnel: () -> Unit,
    onOpenFailover: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenXrayConfig: () -> Unit,
    onOpenCustomRules: () -> Unit = {},
    onOpenConnections: () -> Unit = {},
    geoAssets: GeoAssetStore.State,
    onRefreshGeo: () -> Unit,
    ipv6: Ipv6Guard.Status,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item("header") {
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                modifier = Modifier.padding(bottom = 8.dp),
                actions = {
                    TonalIconButton(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        stringResource(R.string.action_back),
                        onBack,
                    )
                    // Back sits where every Android app puts it, at the start, not at the end.
                    Spacer(Modifier.weight(1f))
                },
            )
        }

        item("appearance") {
            SettingsSection(
                title = stringResource(R.string.settings_appearance),
                icon = Icons.Rounded.Palette,
            ) {
                Text(
                    text = stringResource(R.string.settings_design),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Segmented(
                    options = AppDesign.entries.map { stringResource(it.labelRes) },
                    selected = settings.appDesign.ordinal,
                    onSelect = { index -> onUpdate { it.copy(appDesign = AppDesign.entries[index], designChosen = true) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                )
                AppIconPicker()
                Text(
                    text = stringResource(R.string.settings_app_icon_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        item("routing") {
            SettingsSection(
                title = stringResource(R.string.settings_routing),
                icon = Icons.Rounded.Router,
            ) {
                // Used to live as chips on the tunnel screen; it is a setting, and the one screen
                // that is left has no room for it.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutingMode.entries.forEach { mode ->
                        ChoiceButton(
                            label = stringResource(mode.labelRes),
                            checked = settings.routingMode == mode,
                            onClick = { onRoutingModeChange(mode) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(settings.routingMode.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_operator_routing),
                    subtitle = stringResource(R.string.settings_operator_routing_desc),
                    checked = settings.operatorRouting,
                    onCheckedChange = { onUpdate { s -> s.copy(operatorRouting = it) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_bypass_lan),
                    subtitle = stringResource(R.string.settings_bypass_lan_subtitle),
                    checked = settings.bypassLan,
                    onCheckedChange = { onUpdate { s -> s.copy(bypassLan = it) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_block_ads),
                    subtitle = stringResource(R.string.settings_block_ads_subtitle),
                    checked = settings.blockAds,
                    onCheckedChange = { onUpdate { s -> s.copy(blockAds = it) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_block_quic),
                    subtitle = stringResource(R.string.settings_block_quic_subtitle),
                    checked = settings.blockQuic,
                    onCheckedChange = { onUpdate { s -> s.copy(blockQuic = it) } },
                )

                NavigationRow(
                    title = stringResource(R.string.settings_custom_rules),
                    subtitle = if (settings.customRules.isEmpty) {
                        stringResource(R.string.settings_custom_rules_empty)
                    } else {
                        stringResource(R.string.settings_custom_rules_count, settings.customRules.size)
                    },
                    icon = Icons.Rounded.SwapHoriz,
                    onClick = onOpenCustomRules,
                )
                NavigationRow(
                    title = stringResource(R.string.settings_split_tunnel),
                    subtitle = when (settings.splitTunnelMode) {
                        SplitTunnelMode.Off -> stringResource(R.string.settings_split_tunnel_off)
                        SplitTunnelMode.AllowList ->
                            stringResource(R.string.settings_split_tunnel_allow, splitTunnelAppCount)
                        SplitTunnelMode.BlockList ->
                            stringResource(R.string.settings_split_tunnel_block, splitTunnelAppCount)
                    },
                    icon = Icons.Rounded.Apps,
                    onClick = onOpenSplitTunnel,
                )
            }
        }

        item("censorship") {
            SettingsSection(
                title = stringResource(R.string.settings_censorship),
                icon = Icons.Rounded.Security,
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_mux),
                    subtitle = stringResource(R.string.settings_mux_subtitle),
                    checked = settings.muxEnabled,
                    onCheckedChange = { onUpdate { s -> s.copy(muxEnabled = it) } },
                )
                AnimatedVisibility(visible = settings.muxEnabled) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_mux_concurrency),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MUX_CONCURRENCY.forEach { n ->
                                ChoiceButton(
                                    label = "$n",
                                    checked = settings.muxConcurrency == n,
                                    onClick = { onUpdate { it.copy(muxConcurrency = n) } },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.settings_resolve),
                    subtitle = stringResource(R.string.settings_resolve_subtitle),
                    checked = settings.resolveServerAddress,
                    onCheckedChange = { onUpdate { s -> s.copy(resolveServerAddress = it) } },
                )
                AnimatedVisibility(visible = settings.resolveServerAddress) {
                    Column {
                        ValidatedField(
                            initial = settings.resolveDohUrl,
                            label = stringResource(R.string.settings_resolve_url),
                            hint = stringResource(R.string.settings_resolve_url_hint),
                            error = stringResource(R.string.settings_resolve_url_error),
                            isValid = { it.trim().startsWith("https://") && it.trim().length > 10 },
                            onCommit = { value -> onUpdate { it.copy(resolveDohUrl = value.trim()) } },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.settings_fragment),
                    subtitle = stringResource(R.string.settings_fragment_subtitle),
                    checked = settings.fragmentEnabled,
                    onCheckedChange = { onUpdate { s -> s.copy(fragmentEnabled = it) } },
                )
                AnimatedVisibility(visible = settings.fragmentEnabled) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_fragment_packets),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceButton(
                                label = stringResource(R.string.settings_fragment_tlshello),
                                checked = settings.fragmentPackets == "tlshello",
                                onClick = { onUpdate { it.copy(fragmentPackets = "tlshello") } },
                            )
                            ChoiceButton(
                                label = stringResource(R.string.settings_fragment_first),
                                checked = settings.fragmentPackets == "1-3",
                                onClick = { onUpdate { it.copy(fragmentPackets = "1-3") } },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        ValidatedField(
                            initial = settings.fragmentLength,
                            label = stringResource(R.string.settings_fragment_length),
                            hint = stringResource(R.string.settings_range_hint, "100-200"),
                            error = stringResource(R.string.settings_range_error),
                            isValid = { FRAGMENT_RANGE.matches(it.trim()) },
                            onCommit = { value -> onUpdate { it.copy(fragmentLength = value.trim()) } },
                        )
                        Spacer(Modifier.height(4.dp))
                        ValidatedField(
                            initial = settings.fragmentInterval,
                            label = stringResource(R.string.settings_fragment_interval),
                            hint = stringResource(R.string.settings_range_hint, "10-20"),
                            error = stringResource(R.string.settings_range_error),
                            isValid = { FRAGMENT_RANGE.matches(it.trim()) },
                            onCommit = { value -> onUpdate { it.copy(fragmentInterval = value.trim()) } },
                        )
                    }
                }
            }
        }

        item("behaviour") {
            SettingsSection(
                title = stringResource(R.string.settings_behaviour),
                icon = Icons.Rounded.Bolt,
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_boot),
                    subtitle = stringResource(R.string.settings_boot_subtitle),
                    checked = settings.autoConnectOnBoot,
                    onCheckedChange = { onUpdate { s -> s.copy(autoConnectOnBoot = it) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_fastest),
                    subtitle = stringResource(R.string.settings_fastest_subtitle),
                    checked = settings.autoSelectFastest,
                    onCheckedChange = { onUpdate { s -> s.copy(autoSelectFastest = it) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_auto_update),
                    subtitle = stringResource(R.string.settings_auto_update_subtitle),
                    checked = settings.subscriptionAutoUpdate,
                    onCheckedChange = { onUpdate { s -> s.copy(subscriptionAutoUpdate = it) } },
                )

                AnimatedVisibility(visible = settings.subscriptionAutoUpdate) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_interval),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            UPDATE_INTERVALS.forEach { (minutes, labelRes) ->
                                ToggleButton(
                                    checked = settings.subscriptionUpdateMinutes == minutes,
                                    onCheckedChange = {
                                        onUpdate { s -> s.copy(subscriptionUpdateMinutes = minutes) }
                                    },
                                ) {
                                    Text(
                                        stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_interval_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }


        /*
         * Своя секция, а не три строки в общем списке.
         *
         * Раньше «Уходить с упавшего сервера», «Возвращаться на мой сервер» и «Серверы для подмены»
         * лежали в «Поведении» вперемешку с автозапуском и обновлением подписок — одинаковыми
         * строками, ничем не показывая, что две из них подчинены третьей и что все три вообще про
         * одно и то же. Владелец так и сказал: непонятно, относятся они к замене сервера или нет.
         *
         * Поэтому: отдельная карточка со своим заголовком, а подчинённые настройки — во вложенной
         * поверхности на тон выше. Вложенность здесь несёт смысл, а не украшает: она ровно и
         * означает «это работает, только пока включён переключатель сверху», и исчезает вместе с
         * ним.
         */

        item("failover") {
            SettingsSection(
                title = stringResource(R.string.settings_failover_section),
                icon = Icons.Rounded.SwapHoriz,
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_failover),
                    subtitle = stringResource(R.string.settings_failover_subtitle),
                    checked = settings.autoFailover,
                    onCheckedChange = { onUpdate { s -> s.copy(autoFailover = it) } },
                )
                AnimatedVisibility(visible = settings.autoFailover) {
                    DependentGroup {
                        NavigationRow(
                            title = stringResource(R.string.settings_failover_servers),
                            subtitle = if (settings.failoverNodeIds.isEmpty()) {
                                stringResource(R.string.settings_failover_auto)
                            } else {
                                stringResource(
                                    R.string.settings_failover_manual,
                                    settings.failoverNodeIds.size,
                                )
                            },
                            icon = Icons.Rounded.Dns,
                            onClick = onOpenFailover,
                        )
                        GroupDivider()
                        GroupDivider()
                        SwitchRow(
                            title = stringResource(R.string.settings_return_home),
                            subtitle = stringResource(R.string.settings_return_home_subtitle),
                            checked = settings.returnHome,
                            onCheckedChange = { onUpdate { s -> s.copy(returnHome = it) } },
                        )
                    }
                }
            }
        }

        item("tunnel") {
            SettingsSection(
                title = stringResource(R.string.settings_tunnel),
                icon = Icons.Rounded.Shield,
            ) {
                SwitchRow(
                    title = "IPv6",
                    subtitle = ipv6Subtitle(settings.enableIpv6, ipv6),
                    checked = settings.enableIpv6,
                    onCheckedChange = { onUpdate { s -> s.copy(enableIpv6 = it) } },
                    // The one state here that is a warning rather than a description: the user
                    // asked for IPv6 and a server is refusing it.
                    subtitleColor = if (settings.enableIpv6 && ipv6 is Ipv6Guard.Status.Refused) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_hijack_dns),
                    subtitle = stringResource(R.string.settings_hijack_dns_subtitle),
                    checked = settings.hijackDns,
                    onCheckedChange = { onUpdate { s -> s.copy(hijackDns = it) } },
                )

                Spacer(Modifier.height(8.dp))

                ValidatedField(
                    initial = settings.mtu.toString(),
                    label = "MTU",
                    hint = stringResource(R.string.settings_mtu_hint),
                    // Anything below the IPv6 minimum silently breaks large packets.
                    error = stringResource(R.string.settings_mtu_error),
                    isValid = { it.toIntOrNull() in 1280..9000 },
                    onCommit = { raw -> onUpdate { s -> s.copy(mtu = raw.toInt()) } },
                )
            }
        }

        item("dns") {
            SettingsSection(
                title = stringResource(R.string.settings_dns),
                icon = Icons.Rounded.Dns,
            ) {
                ValidatedField(
                    initial = settings.remoteDns,
                    label = stringResource(R.string.settings_remote_dns),
                    hint = stringResource(R.string.settings_remote_dns_hint),
                    error = stringResource(R.string.settings_dns_empty_error),
                    isValid = { it.isNotBlank() },
                    onCommit = { value -> onUpdate { s -> s.copy(remoteDns = value) } },
                )
                Spacer(Modifier.height(8.dp))
                ValidatedField(
                    initial = settings.directDns,
                    label = stringResource(R.string.settings_direct_dns),
                    hint = stringResource(R.string.settings_direct_dns_hint),
                    error = stringResource(R.string.settings_dns_empty_error),
                    isValid = { it.isNotBlank() },
                    onCommit = { value -> onUpdate { s -> s.copy(directDns = value) } },
                )

                Spacer(Modifier.height(8.dp))

                SwitchRow(
                    title = stringResource(R.string.settings_dns_fallback),
                    subtitle = stringResource(R.string.settings_dns_fallback_subtitle),
                    checked = settings.dnsFallback,
                    onCheckedChange = { onUpdate { s -> s.copy(dnsFallback = it) } },
                )

                if (dnsProfiles.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_dns_resolvers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    // "From settings" is an entry of its own rather than a switch: with a resolver
                    // selected, the fields above are not what the tunnel uses, and a list you
                    // cannot step out of would leave no way to say that.
                    DnsRow(
                        title = stringResource(R.string.settings_dns_from_settings),
                        subtitle = settings.remoteDns,
                        badge = null,
                        selected = selectedDnsId == null,
                        onSelect = { onSelectDns(null) },
                        onRemove = null,
                    )
                    dnsProfiles.forEach { profile ->
                        DnsRow(
                            title = profile.name,
                            subtitle = profile.url,
                            badge = profile.kind,
                            selected = profile.id == selectedDnsId,
                            onSelect = { onSelectDns(profile.id) },
                            onRemove = { onRemoveDns(profile.id) },
                        )
                    }
                }
            }
        }

        item("ping") {
            SettingsSection(
                title = stringResource(R.string.settings_latency),
                icon = Icons.Rounded.NetworkPing,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PingMode.entries.forEach { mode ->
                        ChoiceButton(
                            label = stringResource(mode.labelRes),
                            checked = settings.pingMode == mode,
                            onClick = { onUpdate { it.copy(pingMode = mode) } },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(settings.pingMode.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_quic_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item("alerts") {
            SettingsSection(
                title = stringResource(R.string.settings_alerts_section),
                icon = Icons.Rounded.NotificationsActive,
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_alert_server),
                    subtitle = stringResource(R.string.settings_alert_server_subtitle),
                    checked = settings.alertServer,
                    onCheckedChange = { onUpdate { s -> s.copy(alertServer = it) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_alert_dns),
                    subtitle = stringResource(R.string.settings_alert_dns_subtitle),
                    checked = settings.alertDns,
                    onCheckedChange = { onUpdate { s -> s.copy(alertDns = it) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_alert_lists),
                    subtitle = stringResource(R.string.settings_alert_lists_subtitle),
                    checked = settings.alertLists,
                    onCheckedChange = { onUpdate { s -> s.copy(alertLists = it) } },
                )
            }
        }

        item("geo") {
            SettingsSection(
                title = stringResource(R.string.settings_geo),
                icon = Icons.Rounded.Router,
            ) {
                GeoRow(state = geoAssets, onRefresh = onRefreshGeo)
            }
        }

        item("diagnostics") {
            SettingsSection(
                title = stringResource(R.string.settings_diagnostics),
                icon = Icons.Rounded.Article,
            ) {
                NavigationRow(
                    title = stringResource(R.string.settings_logs),
                    subtitle = stringResource(R.string.settings_logs_subtitle),
                    icon = Icons.Rounded.Article,
                    onClick = onOpenLogs,
                )
                QuickTileRow()
                SwitchRow(
                    title = stringResource(R.string.settings_developer),
                    subtitle = stringResource(R.string.settings_developer_subtitle),
                    checked = settings.developerMode,
                    onCheckedChange = { onUpdate { s -> s.copy(developerMode = it) } },
                )
                AnimatedVisibility(visible = settings.developerMode) {
                    Column {
                        NavigationRow(
                            title = stringResource(R.string.settings_dev_diagnostics),
                            subtitle = stringResource(R.string.settings_dev_diagnostics_subtitle),
                            icon = Icons.Rounded.NetworkPing,
                            onClick = onOpenDiagnostics,
                        )
                        NavigationRow(
                            title = stringResource(R.string.settings_dev_config),
                            subtitle = stringResource(R.string.settings_dev_config_subtitle),
                            icon = Icons.Rounded.Article,
                            onClick = onOpenXrayConfig,
                        )
                        NavigationRow(
                            title = stringResource(R.string.settings_dev_connections),
                            subtitle = stringResource(R.string.settings_dev_connections_subtitle),
                            icon = Icons.Rounded.Router,
                            onClick = onOpenConnections,
                        )
                        Text(
                            text = stringResource(R.string.settings_dev_log_level),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        // The core's own log level: "debug" shows every connection and why it was
                        // routed where it went — what an operator reads when "connected" carries nothing.
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LogLevel.entries.forEach { level ->
                                ChoiceButton(
                                    label = level.name.lowercase(),
                                    checked = settings.logLevel == level,
                                    onClick = { onUpdate { it.copy(logLevel = level) } },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        /*
         * Four switches rather than one, because the events differ in how often they arrive and in
         * what they cost to miss. A replaced server can happen several times on a bad evening; a
         * hand-picked list emptying itself happens once and quietly disarms something the user set
         * up. Somebody who has silenced the first still wants the second.
         */
        /*
         * Visible because its absence is not. The databases arrive in the background and the rules
         * that name them are left out of the configuration when they are missing — so "route by
         * rules" and "block ads" can both be switched on and quietly doing nothing, with the tunnel
         * working perfectly and no way to tell from the outside.
         */

        item("language") {
            // Only the language is left here: Avaca has one theme (see AvacaTheme), so the theme,
            // palette, wallpaper-colour, AMOLED and visualiser switches had nothing left to change.
            SettingsSection(
                title = stringResource(R.string.settings_language),
                icon = Icons.Rounded.Translate,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        // The endonyms stay in their own language in every locale: somebody
                        // looking for their language recognises "Русский", not "Russian".
                        ChoiceButton(
                            label = stringResource(language.labelRes),
                            checked = settings.language == language,
                            onClick = { onUpdate { it.copy(language = language) } },
                        )
                    }
                }
            }
        }

        item("tail") { Spacer(Modifier.height(88.dp)) }
    }
}

/**
 * The state of the routing databases, and a way to fetch them again.
 *
 * The size is shown rather than a bare "ready", because that is the number that says whether these
 * are the real databases or a truncated download: the pair comes to a couple of dozen megabytes.
 */
@Composable
private fun GeoRow(state: GeoAssetStore.State, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (state.ready) R.string.settings_geo_ready else R.string.settings_geo_missing,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (state.ready) {
                    val size = formatBytes(state.bytes)
                    stringResource(R.string.settings_geo_size, "${size.value} ${size.unit}")
                } else {
                    stringResource(R.string.settings_geo_missing_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (state.refreshing) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            FilledTonalButton(onClick = onRefresh) {
                Text(
                    stringResource(
                        if (state.ready) R.string.settings_geo_update else R.string.settings_geo_download,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    @Suppress("UNUSED_PARAMETER") icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    // iOS / One UI grouping: a quiet caption over a rounded card — no icon, no relief, so a long
    // settings page reads as a calm list of groups rather than a stack of posters.
    SettingsGroup(
        title = title,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        content()
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Настройки, подчинённые переключателю над ними.
 *
 * Тон выше, чем у карточки-секции, и заметный скруглённый край: на плоском списке невозможно
 * увидеть, что три строки — это одна мысль, а не три соседние. Отступ сверху отделяет группу от
 * своего переключателя настолько, чтобы читалось «внутри», а не «следом».
 */
@Composable
private fun DependentGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        content = content,
    )
}

/** Волосяная линия внутри группы: строки разные, но принадлежат одному. */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

/**
 * What the IPv6 switch says about itself.
 *
 * Its old line, "raise an IPv6 address on the interface", described a detail that happens either
 * way and said nothing about the part that bites: through a server that cannot reach IPv6, turning
 * this on used to break YouTube and every Google app while everything else went on working.
 */
@Composable
private fun ipv6Subtitle(enabled: Boolean, status: Ipv6Guard.Status): String = when {
    !enabled -> stringResource(R.string.settings_ipv6_subtitle)
    status is Ipv6Guard.Status.Refused -> status.servers.let { servers ->
        if (servers.size == 1) {
            stringResource(R.string.settings_ipv6_refused, servers.first())
        } else {
            stringResource(R.string.settings_ipv6_refused_many, servers.first(), servers.size - 1)
        }
    }
    status == Ipv6Guard.Status.Checking -> stringResource(R.string.settings_ipv6_checking)
    status == Ipv6Guard.Status.Active -> stringResource(R.string.settings_ipv6_active)
    status == Ipv6Guard.Status.Unchecked -> stringResource(R.string.settings_ipv6_unchecked)
    else -> stringResource(R.string.settings_ipv6_on)
}

/**
 * One option of an equal-width choice row: theme, language, connection picture, ping mode.
 *
 * The stock 16 dp side padding left a third of the card on a 360 dp phone 61 dp for its label, and
 * "Системная" needs 63: it broke into "Системна / я", and "Системный", held to one line, was cut to
 * "Системны". At 8 dp the label gets 77 dp there; the ellipsis is for screens narrower still.
 */
@Composable
private fun RowScope.ChoiceButton(label: String, checked: Boolean, onClick: () -> Unit) {
    // A pill per option: sand when chosen, a dark well when not — the segmented look of the
    // iOS and One UI pickers, without Material's outlined toggle buttons.
    val container by animateColorAsState(
        if (checked) AvacaPalette.Sand else AvacaPalette.Well,
        tween(200),
        label = "choice",
    )
    val content by animateColorAsState(
        if (checked) AvacaPalette.Graphite else AvacaPalette.Text,
        tween(200),
        label = "choice-text",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .selectable(selected = checked, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    // The whole row is the toggle, as in iOS and One UI; the pill only shows the state.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        PillSwitch(checked = checked)
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailingIcon: ImageVector = Icons.Rounded.ChevronRight,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Asks the system to put the tunnel tile in the shade.
 *
 * The tile is declared in the manifest and Pixel offers it in the shade's own editor, but some
 * shells — HyperOS among them — do not list third-party tiles there dependably, and a tile the
 * user cannot find is a tile that does not exist. Android 13 added a request that raises the
 * system's own "add this tile?" dialog, which does not depend on what the shell's editor chooses
 * to show. Below that version the row explains where to look instead of pretending to act.
 */
/**
 * One resolver in the list: tap to use it, cross to forget it.
 *
 * A radio rather than a switch, because exactly one resolver is in use at a time and the entry
 * that says "the one from settings" has to be reachable the same way as the others.
 */
@Composable
private fun DnsRow(
    title: String,
    subtitle: String,
    badge: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)

        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        badge?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        onRemove?.let {
            IconButton(onClick = it) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickTileRow() {
    val context = LocalContext.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    NavigationRow(
        title = stringResource(R.string.settings_tile),
        subtitle = if (supported) {
            stringResource(R.string.settings_tile_subtitle)
        } else {
            stringResource(R.string.settings_tile_manual)
        },
        icon = Icons.Rounded.Widgets,
        trailingIcon = if (supported) Icons.Rounded.AddCircleOutline else Icons.Rounded.ChevronRight,
        onClick = { if (supported) requestQuickTile(context) },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestQuickTile(context: Context) {
    runCatching {
        context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
            ComponentName(context, TunnelTileService::class.java),
            context.getString(R.string.app_name),
            // Fully qualified: `Icon` in this file is the Compose composable.
            android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_notification),
            context.mainExecutor,
            {},
        )
    }
}

/** The project's channel: builds and announcements. */
