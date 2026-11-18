package com.mydrop.vpn.ui

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.core.model.AddKind
import com.mydrop.vpn.core.model.AppSettings
import com.mydrop.vpn.core.model.DnsProfile
import com.mydrop.vpn.core.model.LatencyResult
import com.mydrop.vpn.core.model.LogEntry
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.RoutingMode
import com.mydrop.vpn.core.model.Subscription
import com.mydrop.vpn.core.model.SubscriptionUpdate
import com.mydrop.vpn.core.model.TrafficStats
import com.mydrop.vpn.core.model.VpnState
import com.mydrop.vpn.core.model.withProvider
import com.mydrop.vpn.core.parse.DeepLinkParser
import com.mydrop.vpn.core.parse.DeepLinkPayload
import com.mydrop.vpn.core.parse.DnsUriParser
import com.mydrop.vpn.core.parse.ProxyUriParser
import com.mydrop.vpn.data.AppContainer
import com.mydrop.vpn.data.GeoAssetStore
import com.mydrop.vpn.data.Ipv6Guard
import com.mydrop.vpn.data.ConnectOutcome
import com.mydrop.vpn.data.describe
import com.mydrop.vpn.vpn.OpenfluxCore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val nodes: List<ProxyNode> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val selectedNode: ProxyNode? = null,
    val latencies: Map<String, LatencyResult> = emptyMap(),
    val vpnState: VpnState = VpnState.Disconnected,
    val traffic: TrafficStats = TrafficStats.Zero,
    val settings: AppSettings = AppSettings(),
    val dnsProfiles: List<DnsProfile> = emptyList(),
    /** Null means the resolver typed into settings is the one in use. */
    val selectedDnsId: String? = null,
    val pingingNodeIds: Set<String> = emptySet(),
    val refreshingSubscriptionIds: Set<String> = emptySet(),
    val tunnelIsSimulated: Boolean = true,
) {
    val isBusy: Boolean get() = pingingNodeIds.isNotEmpty() || refreshingSubscriptionIds.isNotEmpty()

    /** Servers not attached to any subscription, shown as a synthetic "added manually" group. */
    fun manualNodes(): List<ProxyNode> = nodes.filter { node ->
        node.subscriptionId == null || subscriptions.none { it.id == node.subscriptionId }
    }
}

/** What an external link wants to add, and where it says it came from. */
data class PendingImport(
    val payload: DeepLinkPayload,
    val source: String,
)

private data class TransientState(
    val pingingNodeIds: Set<String> = emptySet(),
    val refreshingSubscriptionIds: Set<String> = emptySet(),
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _permissionRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val permissionRequests = _permissionRequests.asSharedFlow()

    private var pendingConnectNodeId: String? = null

    /**
     * An import from outside the app, waiting to be looked at. Null whenever there is nothing
     * pending; see [importFromExternalLink].
     */
    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    /* ── OpenFlux document-transport sign-in ─────────────────────────────────────────────── */

    /**
     * The sign-in page a document transport (Mail.ru / Yandex) is waiting on, or null. Published
     * straight from the engine, which owns the watcher; [com.mydrop.vpn.ui.components.OpenfluxLoginScreen]
     * shows it and returns the cookies the user's own sign-in produced. The engine clears the
     * prompt once the cookies are accepted, which takes the overlay back down on its own.
     */
    val openfluxPrompt: StateFlow<OpenfluxCore.Prompt?> = OpenfluxCore.pendingPrompt

    /** Hands the engine the cookies the sign-in WebView collected (off the main thread). "" = ok. */
    suspend fun submitOpenfluxCookies(cookieHeader: String): String =
        withContext(Dispatchers.IO) { OpenfluxCore.submitCookies(cookieHeader) }

    /** The user closed the sign-in without finishing. */
    fun cancelOpenfluxPrompt() = OpenfluxCore.cancelPrompt()

    /** Snackbars are user-facing text, so they follow the chosen language like the screens do. */
    private val strings = container.strings

    val logs: StateFlow<List<LogEntry>> = container.logs.entries

    val uiState: StateFlow<MainUiState> = combine(
        container.profiles.state,
        container.settings.settings,
        container.tunnel.state,
        container.tunnel.traffic,
        transient,
    ) { profiles, settings, vpnState, traffic, transient ->
        MainUiState(
            nodes = profiles.visibleNodes,
            subscriptions = profiles.subscriptions,
            selectedNode = profiles.visibleNodes.firstOrNull { it.id == profiles.selectedNodeId },
            latencies = profiles.latencies,
            vpnState = vpnState,
            traffic = traffic,
            settings = settings,
            dnsProfiles = profiles.dnsProfiles,
            selectedDnsId = profiles.selectedDnsId,
            pingingNodeIds = transient.pingingNodeIds,
            refreshingSubscriptionIds = transient.refreshingSubscriptionIds,
            tunnelIsSimulated = !container.tunnel.isReal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState(settings = container.settings.value))

    // ------------------------------------------------------------- Tunnel

    fun toggleConnection() {
        // A tap on a control that is busy stopping is not a request to start again. `isActive` is
        // false while disconnecting, so the tap used to fall through to connect: an impatient
        // second press on "Отключение…" put the tunnel straight back up behind the stop.
        if (uiState.value.vpnState is VpnState.Disconnecting) return
        if (uiState.value.vpnState.isActive) {
            container.tunnelLauncher.disconnect()
            return
        }
        viewModelScope.launch {
            when (val outcome = container.tunnelLauncher.connect()) {
                is ConnectOutcome.Started -> Unit

                // VPN consent has to be granted through an Activity, so the intent is surfaced to
                // the UI and the connection is retried once the system dialog comes back.
                is ConnectOutcome.NeedsConsent -> {
                    pendingConnectNodeId = container.profiles.selectedNode()?.id
                    _permissionRequests.tryEmit(outcome.intent)
                }

                is ConnectOutcome.Rejected -> emit(outcome.reason)
            }
        }
    }

    /** Called by the Activity once the system VPN consent dialog has been answered. */
    fun onVpnPermissionResult(granted: Boolean) {
        val nodeId = pendingConnectNodeId
        pendingConnectNodeId = null
        if (!granted) {
            emit(R.string.message_no_vpn_permission)
            return
        }
        // Said rather than swallowed. The node can be gone — nothing was selected when consent
        // was asked for, or the process was killed behind the system dialog and took the pending id
        // with it — and the old code simply returned: the user granted permission and watched
        // nothing happen, with no message and no line in the journal.
        val node = container.profiles.nodes.firstOrNull { it.id == nodeId }
        if (node == null) {
            emit(R.string.message_no_server_after_consent)
            return
        }
        container.tunnelLauncher.connectTo(node, "vpn consent granted")
    }

    /** What the settings screen shows about the routing databases; see [GeoAssetStore]. */
    val geoAssets: StateFlow<GeoAssetStore.State> = container.geoAssets.state

    /** Whether the running tunnel hands out IPv6, and why not; see [Ipv6Guard]. */
    val ipv6: StateFlow<Ipv6Guard.Status> = container.ipv6Guard.status

    /**
     * Fetches the routing databases again, whether or not they are already there.
     *
     * Asked for by a person rather than by the app, so it re-downloads rather than skipping what is
     * on disk: the reason to press it is a list that has grown stale, and a store that answers
     * "already have them" would make the button do nothing visible and nothing useful.
     */
    fun refreshGeoAssets() {
        viewModelScope.launch { container.geoAssets.refresh(force = true) }
    }

    fun selectNode(nodeId: String) {
        container.profiles.selectNode(nodeId)
        // Switching servers while up should land the user on the new one, not silently keep
        // routing through the old tunnel. Re-dialling the server that is already carrying the
        // traffic is the one case worth skipping: it would drop every open connection to arrive
        // exactly where it started.
        val state = uiState.value.vpnState
        if (!state.isActive || state.activeNodeId == nodeId) return
        container.profiles.nodes
            .firstOrNull { it.id == nodeId }
            ?.let { container.tunnelLauncher.connectTo(it, "server tapped") }
    }

    /**
     * Routing mode is baked into the generated configuration, so on a live tunnel it means nothing
     * until the core is handed a new one. The chips sit on the statistics panel of a running
     * tunnel, where a control that quietly does nothing until the next reconnect reads as broken —
     * and now that switching servers reloads the core in place, applying it costs the same.
     */
    fun setRoutingMode(mode: RoutingMode) {
        if (uiState.value.settings.routingMode == mode) return
        container.settings.update { it.copy(routingMode = mode) }

        if (!uiState.value.vpnState.isActive) return
        val node = container.profiles.selectedNode() ?: return
        container.tunnelLauncher.connectTo(node, "routing mode changed")
        emit(R.string.message_routing_applied, strings.get(mode.labelRes))
    }

    /**
     * Flips certificate checking for one server, and rebuilds the tunnel when it is the one
     * carrying traffic — the core was handed the old flag at startup and will not reread it.
     */
    fun setTlsInsecure(nodeId: String, insecure: Boolean) {
        val node = container.profiles.nodes.firstOrNull { it.id == nodeId } ?: return
        val tls = node.tls ?: return
        if (tls.insecure == insecure) return

        container.profiles.setTlsInsecure(nodeId, insecure)
        emit(
            if (insecure) {
                R.string.message_certificate_skipped
            } else {
                R.string.message_certificate_verified
            },
            node.name,
        )

        if (!uiState.value.vpnState.isActive) return
        if (container.profiles.selectedNode()?.id != nodeId) return
        container.tunnelLauncher.connectTo(node.copy(tls = tls.copy(insecure = insecure)), "certificate flag changed")
    }

    // ------------------------------------------------------------ Latency

    fun pingNode(nodeId: String) {
        val node = container.profiles.nodes.firstOrNull { it.id == nodeId } ?: return
        viewModelScope.launch {
            transient.update { it.copy(pingingNodeIds = it.pingingNodeIds + nodeId) }
            val result = container.latencyTester.measure(node, container.settings.value.pingMode)
            container.profiles.recordLatency(result)
            transient.update { it.copy(pingingNodeIds = it.pingingNodeIds - nodeId) }
        }
    }

    private var pingAllJob: Job? = null

    /**
     * Measures every server, one sweep at a time.
     *
     * A second press while a sweep is running is ignored rather than starting another one beside
     * it: the button is a FAB that stays under the thumb, and two sweeps meant twice the probes
     * against every server, results landing out of order, and the first sweep's end clearing the
     * spinners of the second while it was still measuring.
     *
     * Only this sweep's ids are cleared at the end, so a single server being measured from its own
     * menu keeps its spinner until its own answer arrives.
     */
    /** Developer mode: walks [node] step by step; see ServerDiagnostics. */
    suspend fun diagnose(
        node: com.mydrop.vpn.core.model.ProxyNode,
        russian: Boolean,
        onStep: (com.mydrop.vpn.data.ServerDiagnostics.Step) -> Unit,
    ) {
        val vpn = uiState.value.vpnState
        container.serverDiagnostics.run(
            node = node,
            settings = container.settings.value,
            probe = container.tunnelConfigs.probe.value,
            connected = vpn is VpnState.Connected,
            activeNodeId = vpn.activeNodeId,
            russian = russian,
            onStep = onStep,
        )
    }

    /**
     * Developer mode: the document the core would be handed for [node] alone, with its provider's
     * settings applied — built here, purely, so looking at it never touches the running tunnel.
     */
    fun xrayConfigFor(node: com.mydrop.vpn.core.model.ProxyNode): String? = runCatching {
        val provider = container.profiles.state.value.subscriptions
            .firstOrNull { it.id == node.subscriptionId }?.provider
        com.mydrop.vpn.core.xray.XrayConfigFactory.build(
            nodes = listOf(node),
            selected = node,
            settings = container.settings.value.withProvider(provider),
            geoAvailable = container.geoAssets.available(),
            // So the page shows what the tunnel would get, operator and user geo rules included.
            geoCatalog = container.geoAssets.catalog(),
        ).json
    }.getOrNull()

    /** Where each connection went, newest first; see ConnectionLog. */
    val connections = container.connectionLog.entries

    fun clearConnections() = container.connectionLog.clear()

    /** [host] into one of the user's own rule lists, from the connection log. */
    fun addCustomRule(action: com.mydrop.vpn.core.model.CustomRules.Action, host: String) {
        val entry = com.mydrop.vpn.core.model.CustomRules.normalize(host) ?: return
        updateSettings { it.copy(customRules = it.customRules.adding(action, entry)) }
    }

    fun pingAll() = pingNodes(container.profiles.nodes.map { it.id })

    /**
     * The same sweep over some of the servers — one subscription's, from its header on the home
     * screen. Shares [pingAllJob] with [pingAll], so two sweeps never run side by side.
     */
    fun pingNodes(nodeIds: List<String>) {
        if (pingAllJob?.isActive == true) return
        val wanted = nodeIds.toSet()
        val nodes = container.profiles.nodes.filter { it.id in wanted }
        if (nodes.isEmpty()) return
        val ids = nodes.map { it.id }.toSet()
        pingAllJob = viewModelScope.launch {
            transient.update { it.copy(pingingNodeIds = it.pingingNodeIds + ids) }
            try {
                val mode = container.settings.value.pingMode
                container.latencyTester.measureAll(nodes, mode) { result ->
                    container.profiles.recordLatency(result)
                    transient.update { it.copy(pingingNodeIds = it.pingingNodeIds - result.nodeId) }
                }
            } finally {
                transient.update { it.copy(pingingNodeIds = it.pingingNodeIds - ids) }
            }
            emit(R.string.message_latency_done)
        }
    }

    fun selectFastest() {
        // Through the launcher rather than a second implementation here. This one used to sort on
        // whatever measurements existed, however old, while the launcher discarded anything past
        // half an hour — so the same button picked different servers depending on which code path
        // reached it.
        val fastest = container.tunnelLauncher.fastestNode()
        if (fastest == null) {
            emit(R.string.message_measure_first)
            return
        }
        selectNode(fastest.id)
        emit(R.string.message_selected, fastest.name)
    }

    // ------------------------------------------------------- Subscriptions

    fun refreshSubscription(subscriptionId: String) {
        val subscription = uiState.value.subscriptions.firstOrNull { it.id == subscriptionId }
            ?: return
        viewModelScope.launch {
            transient.update {
                it.copy(refreshingSubscriptionIds = it.refreshingSubscriptionIds + subscriptionId)
            }
            emit(container.subscriptionRefresher.refresh(subscription))

            transient.update {
                it.copy(refreshingSubscriptionIds = it.refreshingSubscriptionIds - subscriptionId)
            }
        }
    }

    fun refreshAllSubscriptions() {
        uiState.value.subscriptions.filter { it.enabled }.forEach { refreshSubscription(it.id) }
    }

    fun addSubscription(url: String, name: String?) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        if (uiState.value.subscriptions.any { it.url == trimmed }) {
            emit(R.string.message_subscription_exists)
            return
        }
        val subscription = Subscription(
            id = UUID.randomUUID().toString(),
            name = name?.takeIf(String::isNotBlank) ?: guessName(trimmed),
            url = trimmed,
        )
        container.profiles.addSubscription(subscription)
        refreshSubscription(subscription.id)
    }

    fun removeSubscription(subscriptionId: String) {
        container.profiles.removeSubscription(subscriptionId)
        emit(R.string.message_subscription_removed)
    }

    fun setSubscriptionEnabled(subscriptionId: String, enabled: Boolean) =
        container.profiles.setSubscriptionEnabled(subscriptionId, enabled)

    // ------------------------------------------------------------ Import

    /**
     * The add sheet's entry point, where the user may have said what they are pasting.
     *
     * [AddKind.Auto] falls through to the same detection QR scans and shared text use; the other
     * kinds skip it. Naming a kind and getting silence back would be worse than the guessing it
     * replaced, so each branch says plainly when the text is not what it was called.
     */
    fun addFromText(raw: String, name: String?, kind: AddKind) {
        val text = raw.trim()
        if (text.isEmpty()) return

        when (kind) {
            AddKind.Auto -> importText(text)

            AddKind.Subscription -> {
                if (!text.startsWith("http", ignoreCase = true)) {
                    emit(R.string.message_not_a_subscription)
                    return
                }
                addSubscription(text, name)
            }

            AddKind.Server -> {
                val nodes = ProxyUriParser.parseAll(text)
                if (nodes.isEmpty()) {
                    emit(R.string.message_not_a_server)
                    return
                }
                container.profiles.addNodes(nodes)
                container.logs.info(R.string.log_servers_imported, nodes.size)
                emit(R.string.message_servers_added, nodes.size)
                warnAboutInsecure(nodes)
            }

            AddKind.Dns -> {
                // Named explicitly, so the path convention that keeps a subscription from being
                // read as a resolver is not applied here — the user already answered that.
                val profiles = DnsUriParser.parseAll(text).ifEmpty {
                    listOfNotNull(DnsUriParser.parse(text) ?: dnsFromPlainUrl(text, name))
                }
                if (profiles.isEmpty()) {
                    emit(R.string.message_not_a_dns)
                    return
                }
                container.profiles.addDnsProfiles(profiles)
                container.logs.info(R.string.log_dns_imported, profiles.size)
                emit(R.string.message_dns_added, profiles.first().name)
            }
        }
    }

    /** An https resolver whose path is not the well-known one, taken at the user's word. */
    private fun dnsFromPlainUrl(text: String, name: String?): DnsProfile? {
        if (!text.startsWith("https://", ignoreCase = true) &&
            !text.startsWith("h3://", ignoreCase = true)
        ) {
            return null
        }
        return DnsProfile(
            id = DnsProfile.stableId(text),
            name = name ?: text.substringAfter("://").substringBefore('/'),
            url = text,
        )
    }

    /**
     * Text the user put in themselves: pasted into the add sheet, or read off a QR code they
     * pointed the camera at. They asked for it, so it applies straight away.
     */
    fun importText(raw: String) {
        applyImport(DeepLinkParser.parse(raw))
    }

    /**
     * Text that arrived from outside — a `happ://` link tapped in a browser, a share from another
     * app — which is a different thing entirely and gets a confirmation first.
     *
     * The schemes are declared `BROWSABLE` in the manifest, so any web page can navigate to
     * `happ://add/<base64 of a subscription url>` and, until this existed, the app would add that
     * subscription and immediately fetch it. From there `addNodes` fills a blank `selectedNodeId`,
     * and with "pick the fastest" or failover on, somebody else's server can end up carrying the
     * traffic. Nothing about that requires the user to have agreed to anything.
     *
     * An unreadable link is reported rather than confirmed: there is nothing to agree to.
     */
    fun importFromExternalLink(raw: String) {
        val payload = DeepLinkParser.parse(raw)
        if (payload is DeepLinkPayload.Unsupported) {
            emit(strings.describe(payload))
            return
        }
        _pendingImport.value = PendingImport(payload = payload, source = originOf(raw))
    }

    fun confirmPendingImport() {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        applyImport(pending.payload)
    }

    fun dismissPendingImport() {
        _pendingImport.value = null
    }

    /** Where a link claims to come from, for the confirmation to show. */
    private fun originOf(raw: String): String {
        val body = raw.trim().substringAfter("://", missingDelimiterValue = "")
        val host = body.substringBefore('/').substringAfter('@').substringBefore('?')
        return host.ifEmpty { raw.trim().take(40) }
    }

    private fun applyImport(payload: DeepLinkPayload) {
        when (payload) {
            is DeepLinkPayload.AddSubscription -> addSubscription(payload.url, payload.name)

            is DeepLinkPayload.AddNodes -> {
                container.profiles.addNodes(payload.nodes)
                container.logs.info(R.string.log_servers_imported, payload.nodes.size)
                emit(R.string.message_servers_added, payload.nodes.size)
                warnAboutInsecure(payload.nodes)
            }

            is DeepLinkPayload.AddDns -> {
                container.profiles.addDnsProfiles(payload.profiles)
                container.logs.info(R.string.log_dns_imported, payload.profiles.size)
                if (payload.profiles.size == 1) {
                    emit(R.string.message_dns_added, payload.profiles.single().name)
                } else {
                    emit(R.string.message_dns_added_many, payload.profiles.size)
                }
            }

            is DeepLinkPayload.Unsupported -> emit(strings.describe(payload))
        }
    }

    /**
     * Names the servers that arrived asking for certificate checking to be skipped.
     *
     * `allowInsecure=1` rides in a link's query string, where nobody reads it, and it decides
     * whether the connection can be read by whoever carries it. Setting it by hand already
     * produces a snackbar; arriving with it set used to produce nothing but a small badge in the
     * list, which is not where somebody looks right after pasting forty servers.
     */
    private fun warnAboutInsecure(nodes: List<ProxyNode>) {
        val count = nodes.count { node ->
            node.tls?.let { it.enabled && it.insecure && it.reality == null } == true
        }
        if (count > 0) {
            container.logs.warn(R.string.log_imported_insecure, count)
            emit(R.string.message_imported_insecure, count)
        }
    }

    fun removeNode(nodeId: String) = container.profiles.removeNode(nodeId)

    // ---------------------------------------------------------- Settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) =
        container.settings.update(transform)

    /** Folds one subscription group on the servers tab shut, or opens it again. */
    fun toggleServerGroup(groupId: String) = updateSettings { settings ->
        settings.copy(
            collapsedGroupIds = if (groupId in settings.collapsedGroupIds) {
                settings.collapsedGroupIds - groupId
            } else {
                settings.collapsedGroupIds + groupId
            },
        )
    }

    /**
     * Null puts the resolver from settings back in charge.
     *
     * Applied to a running tunnel, not just stored. The resolver is baked into the configuration
     * the core is holding, so picking another one and leaving the core alone would show a chosen
     * resolver that nothing resolves through — the same quiet lie the routing chips used to tell
     * before they reloaded. Reloading in place costs about a second and no consent dialog.
     */
    fun selectDns(id: String?) {
        if (uiState.value.selectedDnsId == id) return
        container.profiles.selectDnsProfile(id)
        applyDnsToRunningTunnel(
            container.profiles.selectedDnsProfile()?.name
                ?: strings.get(R.string.message_dns_from_settings),
        )
    }

    fun removeDns(id: String) {
        // Removing the resolver in use falls back to the settings field, which is a change to the
        // running configuration exactly like selecting another one.
        val wasSelected = uiState.value.selectedDnsId == id
        container.profiles.removeDnsProfile(id)
        if (wasSelected) {
            applyDnsToRunningTunnel(strings.get(R.string.message_dns_from_settings))
        }
    }

    private fun applyDnsToRunningTunnel(name: String) {
        if (!uiState.value.vpnState.isActive) return
        val node = container.profiles.selectedNode() ?: return
        container.tunnelLauncher.connectTo(node, "resolver changed")
        emit(R.string.message_dns_applied, name)
    }

    fun clearLogs() = container.logs.clear()

    // ----------------------------------------------------------- Helpers

    private fun guessName(url: String): String =
        runCatching { java.net.URL(url).host }.getOrNull()?.removePrefix("www.")
            ?: strings.get(R.string.message_default_subscription_name)

    private fun emit(message: String) {
        _messages.tryEmit(message)
    }

    private fun emit(@StringRes id: Int, vararg args: Any) = emit(strings.get(id, *args))

    private companion object {
        /** One phase's worth of 150 ms samples, which is exactly the window the trace shows. */
        const val TRACE_SAMPLES = 60
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(container) as T
    }
}
