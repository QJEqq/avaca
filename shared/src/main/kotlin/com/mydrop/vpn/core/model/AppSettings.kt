package com.mydrop.vpn.core.model

import androidx.annotation.StringRes
import com.mydrop.vpn.shared.R
import kotlinx.serialization.Serializable

/*
 * Why these enums carry resource ids rather than text.
 *
 * They used to hold the Russian words themselves, which put user-visible copy in the one layer
 * that has no way to know what language the user reads. Serialisation is unaffected: kotlinx
 * writes the entry name (`Rules`), never the constructor arguments, so a stored profile survives
 * this change and would survive a retranslation too.
 */

@Serializable
enum class ThemeMode(@StringRes val labelRes: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark),
}

/**
 * The language the interface is drawn in.
 *
 * [System] follows the phone, which resolves to Russian on a Russian phone and to English
 * everywhere else — those are the two the app ships. A named choice overrides that, because the
 * language of a phone and the language its owner wants an app in are not always the same.
 *
 * The tag is what [java.util.Locale.forLanguageTag] takes; null means "do not override".
 */
@Serializable
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    System(null, R.string.language_system),
    Russian("ru", R.string.language_russian),
    English("en", R.string.language_english),
}

/**
 * What fills the figure on the connect screen while the tunnel is up.
 *
 * A choice rather than a replacement, because the two answer different questions: the waves say
 * how much is moving, the planet says where it is going. Neither is the better one.
 */
/**
 * How the app looks: the layout of the main screen and the palette of every screen.
 *
 * [Avaca] is the graphite planet drawn in coastlines with a sand accent; [Git] is a dotted globe on
 * black with a blue accent. Only the main screen changes its layout — settings and the add sheet
 * keep theirs and take the palette.
 */
@Serializable
enum class AppDesign(@StringRes val labelRes: Int) {
    Avaca(R.string.design_avaca),
    Git(R.string.design_git),
}

/** The order of the servers inside each group on the Git main screen. */
@Serializable
enum class ServerSort(@StringRes val labelRes: Int) {
    AsListed(R.string.sort_as_listed),
    Latency(R.string.sort_latency),
    Name(R.string.sort_name),
    Protocol(R.string.sort_protocol),
}

@Serializable
enum class Visualizer(@StringRes val labelRes: Int) {
    Waves(R.string.visualizer_waves),
    Planet(R.string.visualizer_planet),
}

@Serializable
enum class RoutingMode(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    Global(R.string.routing_global, R.string.routing_global_description),
    Rules(R.string.routing_rules, R.string.routing_rules_description),
    Direct(R.string.routing_direct, R.string.routing_direct_description),
}

@Serializable
enum class SplitTunnelMode(@StringRes val labelRes: Int) {
    Off(R.string.split_tunnel_off),
    AllowList(R.string.split_tunnel_allow_list),
    BlockList(R.string.split_tunnel_block_list),
}

@Serializable
enum class LogLevel { Trace, Debug, Info, Warn, Error }

/**
 * How a server's latency gets measured. QUIC nodes ignore this and always use a UDP probe —
 * they have no TCP port to handshake with.
 */
@Serializable
enum class PingMode(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    Tcp(R.string.ping_tcp, R.string.ping_tcp_description),
    Tls(R.string.ping_tls, R.string.ping_tls_description),
    Median(R.string.ping_median, R.string.ping_median_description),

    /**
     * A real request through the server itself, timed end to end: the only measurement that
     * notices a server whose port answers while nothing crosses it. Falls back to [Tls] where it
     * cannot be taken.
     */
    Proxy(R.string.ping_proxy, R.string.ping_proxy_description),
}

@Serializable
data class AppSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.System,
    val visualizer: Visualizer = Visualizer.Waves,
    // Off by default: wallpaper colours replace the ice accent, and the tunnel screen leans on
    // that accent meaning "protected". Still a switch — the choice belongs to the user.
    val dynamicColor: Boolean = false,
    /**
     * The built-in accent, used wherever wallpaper colours are not.
     *
     * [Palette.Glacier] is the app's own ice blue and the default everywhere, so this changes
     * nothing until somebody chooses otherwise. It exists mainly for the television, which has no
     * wallpaper to take a scheme from; on a phone with dynamic colour on, the wallpaper wins and
     * this is not read at all.
     */
    val palette: Palette = Palette.Glacier,
    val amoled: Boolean = false,
    val language: AppLanguage = AppLanguage.System,
    /** See [AppDesign]. */
    val appDesign: AppDesign = AppDesign.Avaca,
    /** Whether the design was picked. Until it is, the app opens on the picker, not the main screen. */
    val designChosen: Boolean = false,

    // Routing
    val routingMode: RoutingMode = RoutingMode.Rules,
    /**
     * Whether a server from an operator's Xray document also brings that document's routing rules
     * and DNS servers, the way Happ applies them. The outbound itself is the operator's either
     * way; this decides only where traffic goes and who resolves names. On by default, because
     * the operator wrote the rules for the servers they run.
     */
    val operatorRouting: Boolean = true,
    /** The user's own rules, which come before everyone else's; see [CustomRules]. */
    val customRules: CustomRules = CustomRules(),
    val bypassLan: Boolean = true,
    val blockAds: Boolean = false,
    /**
     * Rejects QUIC on 443 so browsers fall back to TLS over TCP.
     *
     * UDP is throttled hard enough on Russian networks that a QUIC request usually neither
     * succeeds nor fails quickly — it sits until a timeout, which is what
     * `listen packet connection ... context deadline exceeded` in the journal is. A rejected
     * attempt is answered immediately and Chrome switches protocol on the spot, so blocking it
     * is faster than letting it through. Off by default: on a network that does not throttle
     * UDP, QUIC is the better transport and this only takes it away.
     */
    val blockQuic: Boolean = false,

    // Tunnel
    val enableIpv6: Boolean = false,
    val mtu: Int = 9000,
    /**
     * Xray mux on servers that can carry it: several connections share one connection to the
     * server, so a page that opens dozens of them waits for fewer handshakes. Off by default — the
     * server has to speak it too — and skipped where it cannot work (XTLS Vision, XHTTP, QUIC-based
     * protocols, WireGuard, OpenFlux).
     */
    val muxEnabled: Boolean = false,
    /** Connections sharing one mux connection (Xray `concurrency`). */
    val muxConcurrency: Int = 8,
    /** UDP sessions over mux (Xray `xudpConcurrency`). */
    val muxXudpConcurrency: Int = 16,
    /**
     * Splits the TLS ClientHello into pieces on the way to the server, so DPI that reads the site's
     * name out of the first packet finds nothing whole there. Xray `fragment` on a freedom outbound
     * the proxy connection is dialled through.
     */
    val fragmentEnabled: Boolean = false,
    /** Which packets to split: "tlshello", or a range of packet numbers such as "1-3". */
    val fragmentPackets: String = "tlshello",
    /** Piece sizes in bytes, "min-max". */
    val fragmentLength: String = "100-200",
    /** Pause between pieces in milliseconds, "min-max". */
    val fragmentInterval: String = "10-20",
    /**
     * Resolve server domains through DNS over HTTPS instead of the operator's resolver, which can
     * answer for a blocked server with an address that goes nowhere. See ServerAddressResolver.
     */
    val resolveServerAddress: Boolean = false,
    /** The DoH endpoint asked, in wire format. */
    val resolveDohUrl: String = "https://1.1.1.1/dns-query",
    /** Shows the tools for people who run their own servers: diagnostics, the core's config, log level. */
    val developerMode: Boolean = false,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.Off,
    val splitTunnelPackages: Set<String> = emptySet(),

    // DNS
    val remoteDns: String = "https://1.1.1.1/dns-query",
    // Matches XrayConfigFactory.DEFAULT_DIRECT_DNS — see the comment there for why "local" is
    // not this default: nothing implements the Android bridge it needs, so every install would
    // start with a direct resolver that answers nothing.
    val directDns: String = "8.8.8.8",
    val hijackDns: Boolean = true,
    /**
     * Whether a resolver that stops answering is replaced with a known-good one.
     *
     * A dead resolver is the one outage the app cannot fix by changing servers, and the one the
     * user is least likely to diagnose: every name stops resolving at once, so the symptom is
     * "the internet is gone" while the tunnel itself is provably carrying traffic. On, the app
     * notices and swaps in a numeric DoH endpoint until the connection is next restarted.
     *
     * On by default, but a switch rather than a certainty: a resolver is chosen for what it does
     * beyond resolving — ad filtering, family rules, something a workplace requires — and quietly
     * substituting a plain one takes all of that away. Somebody who chose theirs for a reason may
     * prefer the outage.
     */
    val dnsFallback: Boolean = true,

    /**
     * Identifier some subscription panels require before they hand over the server list, sent as
     * `x-hwid`. Generated once per installation and random — a real device identifier would
     * follow the user across every panel they ever subscribe to.
     */
    val deviceId: String = "",

    // Behaviour
    /**
     * TLS rather than TCP, because a bare handshake answers the wrong question here.
     *
     * Russian DPI routinely lets the TCP connection to a blocked server complete and kills the
     * session after the ClientHello. A TCP probe sees the SYN-ACK, calls the server alive, and
     * [com.mydrop.vpn.data.FailoverWatchdog] never moves off a server that carries nothing. A
     * full handshake is the cheapest probe that fails when the thing the user cares about fails —
     * and for REALITY it is the difference between "the port is open" and "the disguise works".
     */
    val pingMode: PingMode = PingMode.Proxy,
    val autoConnectOnBoot: Boolean = false,
    val autoSelectFastest: Boolean = false,
    // Hands the core a group of servers instead of one, so a server that stops answering is
    // stepped over without the tunnel dropping. Off by default: it makes the core probe several
    // servers on a schedule, and the server actually carrying traffic stops being the one the
    // user picked.
    val autoFailover: Boolean = false,
    /**
     * Whether an automatic switch is provisional or final.
     *
     * On, the server the user chose is remembered while the watchdog is riding a replacement, and
     * the tunnel goes back to it once it answers again — otherwise one bad minute silently becomes
     * permanent, and the only clue is that the connect screen now names a server nobody picked,
     * possibly in another country.
     *
     * Off, a switch is simply where the tunnel now lives until someone chooses otherwise. Which is
     * a defensible preference rather than a wrong one: going back interrupts a tunnel that is
     * working, on the evidence of a probe that only proves a port is open.
     */
    val returnHome: Boolean = true,
    /**
     * Servers the tunnel may switch to. Empty means "decide for me", and the group is filled with
     * neighbours from the chosen server's own subscription.
     */
    val failoverNodeIds: Set<String> = emptySet(),
    val subscriptionAutoUpdate: Boolean = true,
    /**
     * How often subscriptions refresh themselves, in minutes.
     *
     * Kept here rather than per subscription: the question a user has is "how fresh should my
     * server lists be", asked once, not once per provider. The per-subscription field that used to
     * sit in [Subscription] answered it in hours and was never read by anything.
     */
    val subscriptionUpdateMinutes: Int = 360,
    val logLevel: LogLevel = LogLevel.Info,
    /*
     * Whether the app says out loud that it fixed something, one kind of event at a time.
     *
     * The whole value of automatic failover is that nobody is watching when it fires, which is
     * exactly why the user never learns that it did: the only lasting symptom is a connect screen
     * naming a server they did not choose, discovered hours later.
     *
     * Split rather than one switch, because the four events differ in how often they happen and in
     * how much they matter. A server being replaced can happen several times on a bad evening; a
     * hand-picked list emptying itself happens once and disarms something the user set up
     * deliberately. Someone who has silenced the first still wants the second.
     *
     * All on by default: how chatty a VPN should be about its own plumbing is a matter of taste,
     * and the honest way to settle a matter of taste is to let the person whose phone it is settle
     * it — but only after they have seen what there is to silence.
     */
    val alertServer: Boolean = true,
    val alertDns: Boolean = true,
    val alertLists: Boolean = true,

    // Server list
    /**
     * Subscription groups the user has folded shut on the servers tab.
     *
     * Persisted rather than remembered, because the state it holds is a decision and not a scroll
     * position: a subscription of three hundred servers folds away once and should stay that way
     * across tab switches and restarts. Held here by group id, so a subscription that goes away
     * takes its entry with it the moment nothing renders that group any more.
     */
    val collapsedGroupIds: Set<String> = emptySet(),
    /** See [ServerSort]. */
    val serverSort: ServerSort = ServerSort.AsListed,

    // Background
    /**
     * Whether the owner of the phone has already been asked to spare the app from battery
     * optimisation.
     *
     * Asked once, and remembered whatever the answer was. Declining is a legitimate choice — a
     * tunnel that only works while the screen is on is still a tunnel — and re-asking every launch
     * would turn one question into nagging.
     */
    val batteryPromptShown: Boolean = false,
)
