package com.mydrop.vpn.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.core.model.NodeIdMigration
import com.mydrop.vpn.core.model.VpnState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual dependency graph. The object count here is small enough that a DI framework would add
 * build time and indirection without buying anything.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.substringBefore('-') ?: "0"

    // Order matters here: the journal writes localized lines, so it needs the resolver, and the
    // resolver needs somewhere to read the chosen language from.
    /**
     * Indirection because the journal is built after the first store that reports into it. Writes
     * only ever happen after construction, so by the time this is called it points somewhere.
     */
    private var reportWriteFailure: (Throwable) -> Unit = {}
    private val writeFailure: (Throwable) -> Unit = { reportWriteFailure(it) }

    val settings = SettingsRepository(context.filesDir, applicationScope, writeFailure)
    val strings = Strings(context) { settings.value.language }
    /**
     * Whether this build is debuggable — the flag Android itself sets, so no build plumbing and
     * no way for a release to switch the leak hunt's instruments on by accident.
     */
    private val debuggable = (appContext.applicationInfo.flags and
        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * The journal on disk, in every build.
     *
     * Debug builds only until now, and what changed is the bug reports. Somebody on a release
     * build whose tunnel died in the night has nothing to send in the morning: the in-memory
     * journal no longer reaches back that far, and the save button on the logs screen — which
     * exists for precisely this handover — answered "nothing to send" because the file it copies
     * was never written. A fault nobody can hand over is a fault nobody fixes.
     *
     * The privacy cost that kept it debug-only is real and has not gone away; see [DiagnosticLog].
     * It is answered differently here rather than ignored: the file sits in the app's private
     * directory where no other app can read it, it is bounded and rotated, the screen's own
     * clear button empties it, and it leaves the phone only when its owner saves it and attaches
     * it somewhere by hand.
     *
     * Smaller in release than in debug, because fifty megabytes is a fair price on the phone of
     * somebody hunting a bug and not on everybody's.
     */
    val diagnostics = DiagnosticLog(
        directory = java.io.File(context.filesDir, "diagnostics"),
        enabled = true,
        maxBytes = if (debuggable) DiagnosticLog.MAX_BYTES else DiagnosticLog.RELEASE_MAX_BYTES,
    )

    /**
     * Lines written by cores that should no longer exist, and nothing else.
     *
     * Debug builds only — this is an instrument for finding one fault, not a feature. It stays
     * empty on a healthy phone, which is itself the reading: a file with anything in it is a leak
     * that happened, with the time it started and how old the leaked core was.
     *
     * Separate from [diagnostics] because that journal is the casualty. A leak writes hundreds of
     * lines a second, so a record kept inside the ring it is overflowing is a record that rotates
     * itself away before anybody looks — which is exactly what happened on the third of September,
     * where fifty megabytes of journal covered two minutes and said nothing about the three cores
     * that filled it.
     */
    val coreLeakLog = DiagnosticLog(
        directory = java.io.File(context.filesDir, "diagnostics"),
        enabled = debuggable,
        name = DiagnosticLog.CORES_FILE_NAME,
        maxBytes = 4L * 1024 * 1024,
    )

    /** True in debug builds, where the leak hunt's instruments are allowed to run. */
    val diagnosticBuild: Boolean get() = debuggable

    /** Says out loud what the app would otherwise fix in silence. */
    val alerts = AlertNotifier(appContext, strings, enabled = { kind ->
        settings.value.let {
            when (kind) {
                AlertKind.Server -> it.alertServer
                AlertKind.Dns -> it.alertDns
                AlertKind.Lists -> it.alertLists
            }
        }
    })

    val logs = LogRepository(strings, diagnostics)

    /** Where each connection went; fed by the service while developer mode is on. */
    val connectionLog = ConnectionLog()

    /** Which resolvers have been answering; fed alongside [connectionLog]. */
    val dnsAnswers = DnsAnswerLog()
    val profiles = ProfileRepository(context.filesDir, applicationScope, writeFailure)
    val latencyTester = LatencyTester()
    val tunnelHealth = TunnelHealthCheck(logs)

    /** Developer mode's step-by-step server check; lazy, because [tunnel] is built further down. */
    val serverDiagnostics by lazy { ServerDiagnostics(tunnel, tunnelHealth, dnsAnswers) }

    /**
     * Identity handed to panels that count devices. Created on first use and kept in settings, so
     * it survives restarts — a panel that saw a new identifier on every refresh would burn through
     * the user's device limit in a week.
     */
    val subscriptionService = SubscriptionService(
        logs = logs,
        strings = strings,
        probeEndpoint = { tunnelConfigs.probe.value },
        // Named like every other client does it — version, platform, model. Panels read the agent
        // to decide which format to emit, and the caching front-ends in front of them key on it
        // too, so a bare "Yumi (Android)" shares one cache entry with every other install and can
        // be served an answer that was refused to somebody else.
        // The trailing " AVACA-Pod" is the marker the AVACA augmenter matches (as a substring) to
        // inject this install's personal openflux:// line into the subscription; the "Yumi/..."
        // prefix is left intact so the panel still picks the same format it already serves us. Must
        // equal the augmenter's POD_UA_MARKER (default "AVACA-Pod").
        userAgent = "Avaca/$versionName (Android ${android.os.Build.VERSION.RELEASE}; " +
            "${android.os.Build.MODEL}) AVACA-Pod",
        identity = { deviceIdentity() },
    )

    val tunnelConfigs = TunnelConfigBuilder(
        context = context.applicationContext,
        settings = settings,
        logs = logs,
        selectedDns = { profiles.selectedDnsProfile()?.url },
        switchableGroup = ::switchableGroup,
        // A lambda, so the two can depend on each other: the store needs the tunnel's loopback
        // inbound to download through, and the builder needs to know whether the files are there.
        // Neither is asked until a configuration is actually being built.
        geoAvailable = { geoAssets.available() },
        geoCatalog = { geoAssets.catalog() },
        // Late-bound for the same reason: the guard needs the launcher, which needs the tunnel,
        // which needs this builder.
        ipv6Allowed = { active, members -> ipv6Guard.allows(active, members) },
        providerOf = { node ->
            profiles.state.value.subscriptions.firstOrNull { it.id == node.subscriptionId }?.provider
        },
    )

    /**
     * Every server the tunnel may be moved onto without a restart.
     *
     * Deliberately not the whole subscription: each member is an outbound in the document the core
     * parses at startup, and three hundred of them would be paid for on every connect by everyone,
     * to make instant a switch that will never happen. The membership is exactly the places the
     * app itself can decide to go — the chosen server and the failover pool —
     * and anything outside it still works, just the old expensive way.
     */
    private fun switchableGroup(selected: com.mydrop.vpn.core.model.ProxyNode):
        List<com.mydrop.vpn.core.model.ProxyNode> {
        val state = profiles.state.value
        val failover = com.mydrop.vpn.core.model.FailoverGroup.candidates(
            nodes = state.nodes,
            selected = selected,
            latencies = state.latencies,
            limit = com.mydrop.vpn.core.model.FailoverGroup.SWITCHABLE,
            chosen = settings.value.failoverNodeIds,
        )
        return (listOf(selected) + failover).distinctBy { it.id }.take(SWITCHABLE_LIMIT)
    }

    /**
     * The real Xray tunnel. [SimulatedTunnelController] is kept around deliberately: it is the
     * only way to exercise the connect screen's states without a working server, and swapping it
     * back in is a one-line change.
     */
    init {
        // The other half of the id move. The servers live in the profile store and the two lists
        // the user curated live in the settings store, and only this class holds both — so the
        // mapping is computed there and applied here, before anything reads either.
        val moved = profiles.nodeIdMigration
        if (moved.isNotEmpty()) {
            settings.update { current ->
                current.copy(
                    failoverNodeIds = NodeIdMigration.follow(current.failoverNodeIds, moved),
                )
            }
            logs.debug(R.string.log_node_ids_migrated, moved.size)
        }
    }

    val tunnel: TunnelController = XrayTunnelController(
        context = context.applicationContext,
        configs = tunnelConfigs,
        logs = logs,
    )

    /**
     * Fetches the geo databases the first time a tunnel comes up, and never again while running.
     *
     * After a connection rather than at startup, and the reason is the whole design of the store:
     * the files live on GitHub, the networks that make this app worth having are the ones where
     * GitHub does not answer, and the only road that works there is the tunnel itself. Until it is
     * up there is nothing to fall back to.
     *
     * A failure is not retried in a loop and not surfaced beyond the journal. Without the databases
     * the routing rules that name them are simply left out of the configuration, which the factory
     * already does — the tunnel comes up either way, carrying everything through the proxy.
     */
    val geoAssets: GeoAssetStore =
        GeoAssetStore(context.applicationContext, logs) { tunnelConfigs.probe.value }

    private val geoOnFirstConnection = applicationScope.launch {
        tunnel.state.first { it is VpnState.Connected }
        if (!geoAssets.available()) geoAssets.refresh()
    }

    /**
     * Shared by every entry point that can raise the tunnel: the UI, the Quick Settings tile,
     * the boot receiver and the service's own restart path.
     */
    val tunnelLauncher = TunnelLauncher(
        profiles = profiles,
        settings = settings,
        tunnel = tunnel,
        latencyTester = latencyTester,
        logs = logs,
        strings = strings,
    )

    /**
     * Moves the tunnel off a server that has stopped answering. Runs for the life of the process
     * and does nothing until a tunnel is up and the setting is on.
     */
    val failoverWatchdog = FailoverWatchdog(
        profiles = profiles,
        settings = settings,
        tunnel = tunnel,
        launcher = tunnelLauncher,
        latencyTester = latencyTester,
        tunnelHealth = tunnelHealth,
        configs = tunnelConfigs,
        logs = logs,
        alerts = alerts,
        scope = applicationScope,
    )

    /** Hands applications IPv6 only through servers that were seen to carry it. */
    val ipv6Guard: Ipv6Guard = Ipv6Guard(
        filesDir = context.filesDir,
        settings = settings,
        profiles = profiles,
        tunnel = tunnel,
        launcher = tunnelLauncher,
        configs = tunnelConfigs,
        logs = logs,
        scope = applicationScope,
        onWriteFailure = writeFailure,
    )

    val subscriptionRefresher = SubscriptionRefresher(
        profiles = profiles,
        service = subscriptionService,
        logs = logs,
        strings = strings,
    )

    /** Re-reads server lists on the cadence chosen in settings, and once on opening a set. */
    val subscriptionScheduler = SubscriptionScheduler(
        profiles = profiles,
        settings = settings,
        refresher = subscriptionRefresher,
        logs = logs,
        scope = applicationScope,
        refreshOnStart = false,
    )

    /** Hands a running tunnel a new configuration when a setting it was built from changes. */
    val tunnelSettingsApplier = TunnelSettingsApplier(
        settings = settings,
        profiles = profiles,
        tunnel = tunnel,
        launcher = tunnelLauncher,
        logs = logs,
        scope = applicationScope,
    )

    /** Keeps the failover list free of servers a subscription refresh has taken away. */
    val staleSelectionPruner = StaleSelectionPruner(
        profiles = profiles,
        settings = settings,
        logs = logs,
        alerts = alerts,
        scope = applicationScope,
    )

    private companion object {
        /** Members past this buy nothing: the app only ever chooses from the lists above. */
        const val SWITCHABLE_LIMIT = com.mydrop.vpn.core.model.FailoverGroup.SWITCHABLE
    }

    init {
        // A store that cannot write is losing the user's servers silently; now it says so.
        reportWriteFailure = { error ->
            logs.error(R.string.log_store_write_failed, error.message ?: error::class.simpleName.orEmpty())
        }
        failoverWatchdog.start()
        ipv6Guard.start()
        staleSelectionPruner.start()
        tunnelSettingsApplier.start()
        subscriptionScheduler.start()
    }

    /** Whether the current connection is metered; see MainViewModel.speedTestIsMetered. */
    fun isActiveNetworkMetered(): Boolean = runCatching {
        appContext.getSystemService(android.net.ConnectivityManager::class.java)
            ?.isActiveNetworkMetered == true
    }.getOrDefault(false)

    private fun deviceIdentity(): DeviceIdentity {
        val stored = settings.value.deviceId.ifEmpty {
            java.util.UUID.randomUUID().toString().also { generated ->
                settings.update { it.copy(deviceId = generated) }
            }
        }
        return DeviceIdentity(
            hwid = stored,
            os = "Android",
            osVersion = android.os.Build.VERSION.RELEASE.orEmpty(),
            model = android.os.Build.MODEL.orEmpty(),
        )
    }
}
