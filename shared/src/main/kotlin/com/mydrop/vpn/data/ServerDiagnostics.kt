package com.mydrop.vpn.data

import com.mydrop.vpn.core.model.AppSettings
import com.mydrop.vpn.core.model.ProbeEndpoint
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.core.model.ProxySettings
import com.mydrop.vpn.core.model.TransportOptions
import com.mydrop.vpn.core.model.dialed
import com.mydrop.vpn.core.net.isNumericAddress
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import com.mydrop.vpn.core.xray.XrayConfigFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Walks a connection to one server step by step — the way a VPN operator would by hand — and says
 * at which step it breaks.
 *
 * DNS through the phone's resolver, DNS over HTTPS, a TCP connection, the TLS or REALITY handshake,
 * then — while the tunnel is up — whether traffic actually crosses the server, whether names resolve
 * inside the tunnel, and which address the internet sees. "The connection is up but nothing loads"
 * stops being one symptom: a refused port, a firewall that drops silently, a certificate for another
 * name, a REALITY server that does not answer as its SNI, and a server that accepts the handshake
 * but rejects the user (wrong UUID, mismatched flow, a clock off on VMess) each fail a different
 * step, and each failure carries a hint at what to check on the server.
 *
 * The app is excluded from its own tunnel, so the first four steps go out over the phone's own
 * network, as the core's own connection to the server does; the last three go through the tunnel
 * via its loopback probe inbound.
 */
class ServerDiagnostics(
    private val tunnel: TunnelController,
    private val tunnelHealth: TunnelHealthCheck,
    /** Which resolvers have been answering; see [DnsAnswerLog]. */
    private val dnsAnswers: DnsAnswerLog? = null,
) {

    enum class Status { Running, Ok, Warn, Fail, Skipped }

    data class Step(
        val key: String,
        val title: String,
        val status: Status,
        val millis: Long? = null,
        val detail: String = "",
        val hint: String? = null,
    )

    /**
     * Runs every check for [node] in order, reporting each step when it starts and when it ends
     * (same [Step.key]). Never throws: a failing step is a result, not an error.
     */
    suspend fun run(
        node: ProxyNode,
        settings: AppSettings,
        probe: ProbeEndpoint?,
        connected: Boolean,
        activeNodeId: String?,
        russian: Boolean,
        onStep: (Step) -> Unit,
    ) = withContext(Dispatchers.IO) {
        fun t(ru: String, en: String) = if (russian) ru else en
        // A group answers for its first member; the checks below need one concrete endpoint.
        val target = node.dialed().firstOrNull() ?: node
        val openflux = target.settings is ProxySettings.Openflux
        val host = target.server.trim()
        val port = target.port
        val quic = target.protocol.isQuicBased
        val tls = target.tls?.takeIf { it.enabled }
        val reality = tls?.reality != null

        // ---------------------------------------------------------------- 1. DNS, the phone's own
        var systemAddresses: List<String> = emptyList()
        step(onStep, "dns", t("DNS через оператора", "DNS via the operator")) {
            when {
                openflux -> skipped(t("OpenFlux: адрес выхода внутри ссылки, его ищет движок", "OpenFlux: the exit is inside the link"))
                isNumericAddress(host) -> skipped(t("Адрес сервера — IP, DNS не нужен", "The server address is an IP"))
                else -> try {
                    systemAddresses = InetAddress.getAllByName(host).mapNotNull { it.hostAddress }
                    ok(systemAddresses.joinToString(", "))
                } catch (e: UnknownHostException) {
                    fail(
                        t("Домен $host не находится", "$host does not resolve"),
                        t(
                            "Проверьте A-запись домена. Если в браузере домен открывается, а здесь нет — оператор режет DNS: включите «Адрес сервера через DoH».",
                            "Check the domain's A record. If it resolves elsewhere, the operator filters DNS: turn on DoH lookup.",
                        ),
                    )
                }
            }
        }

        // ---------------------------------------------------------------- 2. DNS over HTTPS
        var dohAddress: String? = null
        step(onStep, "doh", t("DNS через DoH", "DNS over HTTPS")) {
            when {
                openflux || isNumericAddress(host) -> skipped(t("Не нужен", "Not needed"))
                else -> {
                    dohAddress = ServerAddressResolver.resolve(host, settings.resolveDohUrl)
                    val doh = dohAddress
                    when {
                        doh == null -> warn(
                            t("DoH не ответил (${settings.resolveDohUrl})", "DoH did not answer (${settings.resolveDohUrl})"),
                            t("DoH-сервер недоступен из этой сети — укажите другой в настройках.", "The DoH server is unreachable from this network."),
                        )
                        systemAddresses.isNotEmpty() && doh !in systemAddresses -> warn(
                            t("DoH: $doh, оператор: ${systemAddresses.joinToString()}", "DoH: $doh, operator: ${systemAddresses.joinToString()}"),
                            t(
                                "Ответы различаются. Для CDN это нормально, для своего сервера — признак подмены DNS оператором: включите «Адрес сервера через DoH».",
                                "The answers differ. Normal for a CDN; for your own server it suggests DNS tampering.",
                            ),
                        )
                        else -> ok(doh)
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 3. TCP to the server
        val address = when {
            openflux -> null
            isNumericAddress(host) -> host
            else -> dohAddress ?: systemAddresses.firstOrNull()
        }
        var tcpOk = false
        step(onStep, "tcp", t("TCP-соединение", "TCP connection") + (address?.let { " $it:$port" } ?: "")) {
            when {
                openflux -> skipped(t("OpenFlux ходит через Яндекс, а не к адресу сервера", "OpenFlux goes through Yandex, not to the server"))
                quic -> skipped(t("${target.protocol.label} работает по UDP (QUIC) — TCP не используется", "${target.protocol.label} runs over UDP"))
                address == null -> skipped(t("Нет адреса — DNS не ответил", "No address to dial"))
                else -> try {
                    Socket().use { it.connect(InetSocketAddress(address, port), TIMEOUT_MILLIS) }
                    tcpOk = true
                    ok(t("Порт $port открыт", "Port $port is open"))
                } catch (e: ConnectException) {
                    fail(
                        t("Соединение отклонено (порт закрыт)", "Connection refused"),
                        t(
                            "На $port никто не слушает или порт режет фаервол сервера. Проверьте, что inbound запущен на этом порту (ss -ltnp) и порт открыт в ufw/iptables и у хостера.",
                            "Nothing listens on $port, or the server firewall rejects it. Check the inbound and ufw/iptables.",
                        ),
                    )
                } catch (e: SocketTimeoutException) {
                    fail(
                        t("Нет ответа за ${TIMEOUT_MILLIS / 1000} с", "No answer in ${TIMEOUT_MILLIS / 1000} s"),
                        t(
                            "Пакеты теряются по дороге: фаервол молча отбрасывает их, IP или подсеть сервера заблокированы у оператора, либо сервер выключен. Проверьте с другой сети (Wi-Fi/мобильная).",
                            "Packets are dropped on the way: a silent firewall, a blocked IP or subnet, or the server is down.",
                        ),
                    )
                } catch (e: NoRouteToHostException) {
                    fail(t("Нет маршрута до сервера", "No route to the server"), t("Сеть телефона не видит этот адрес.", "The phone's network cannot reach this address."))
                } catch (e: Exception) {
                    fail("${e::class.simpleName}: ${e.message.orEmpty()}")
                }
            }
        }

        // ---------------------------------------------------------------- 4. TLS or REALITY
        var handshakeOk = false
        step(onStep, "tls", if (reality) "REALITY" else "TLS") {
            when {
                openflux || quic -> skipped(t("Не применимо", "Not applicable"))
                tls == null -> skipped(t("Сервер без TLS — шифрует сам протокол", "No TLS on this server"))
                !tcpOk || address == null -> skipped(t("TCP не прошёл", "TCP failed"))
                else -> {
                    val sni = tls.serverName?.takeIf { it.isNotBlank() } ?: host
                    // This is the phone's own handshake: no fragmentation, no uTLS fingerprint. A
                    // server that leans on either is not refused by it — and not proved by it.
                    val fragmented = XrayConfigFactory.fragmentsHandshake(target, settings)
                    var began = 0L
                    try {
                        val factory = if (reality || tls.insecure) trustAll() else HttpsURLConnection.getDefaultSSLSocketFactory()
                        Socket().use { raw ->
                            raw.connect(InetSocketAddress(address, port), TIMEOUT_MILLIS)
                            raw.soTimeout = TIMEOUT_MILLIS
                            began = System.nanoTime()
                            (factory.createSocket(raw, sni, port, true) as SSLSocket).use { ssl ->
                                ssl.startHandshake()
                                val session = ssl.session
                                val cert = session.peerCertificates.firstOrNull() as? X509Certificate
                                val until = cert?.notAfter?.let { SimpleDateFormat("dd.MM.yyyy", Locale.US).format(it) }
                                val summary = buildString {
                                    append(session.protocol).append(", SNI ").append(sni)
                                    cert?.let { append(", ").append(t("сертификат", "certificate")).append(' ').append(cn(it)) }
                                    until?.let { append(t(" до ", " until ")).append(it) }
                                }
                                handshakeOk = true
                                when {
                                    reality -> ok(t("Сервер отвечает как $sni · ", "Answers as $sni · ") + summary)
                                    cert != null && cert.notAfter.before(Date()) -> fail(
                                        t("Сертификат истёк $until", "The certificate expired $until"),
                                        t("Обновите сертификат на сервере (certbot renew / acme.sh).", "Renew the certificate on the server."),
                                    )
                                    !tls.insecure && !HttpsURLConnection.getDefaultHostnameVerifier().verify(sni, session) -> fail(
                                        t("Сертификат не для $sni", "The certificate is not for $sni"),
                                        t("SNI в ссылке и домен в сертификате сервера должны совпадать.", "The link's SNI must match the server certificate."),
                                    )
                                    else -> ok(summary)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val elapsed = if (began == 0L) null else (System.nanoTime() - began) / 1_000_000
                        handshakeFailed(e, elapsed, reality, sni, port, fragmented, russian)
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 5. traffic through it
        val isActive = connected && activeNodeId != null && (activeNodeId == node.id || node.dialed().any { it.id == activeNodeId })
        step(onStep, "traffic", t("Трафик через сервер", "Traffic through the server")) {
            when {
                !connected -> skipped(t("Нужно подключение к этому серверу", "Requires a connection to this server"))
                isActive -> {
                    val started = System.nanoTime()
                    when (tunnelHealth.passes(probe)) {
                        true -> ok(t("Запрос прошёл", "The request went through"))
                        false -> fail(t("Сервер принял соединение, но трафик не идёт", "Connected, but nothing goes through"), protocolHint(target, russian, handshakeOk))
                        null -> skipped(t("Туннель ещё поднимается", "The tunnel is still starting"))
                    }.copy(millis = (System.nanoTime() - started) / 1_000_000)
                }
                else -> {
                    val measured = tunnel.measureThroughTunnel(listOf(node))[node.id]
                    if (measured != null) {
                        ok(t("Запрос через этот сервер прошёл", "A request through this server went through")).copy(millis = measured.toLong())
                    } else {
                        skipped(t("Этого сервера нет в текущем туннеле — подключитесь к нему", "This server is not in the running tunnel"))
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 6. DNS inside the tunnel
        step(onStep, "tunnel-dns", t("DNS внутри туннеля", "DNS inside the tunnel")) {
            if (!isActive) {
                skipped(t("Только для сервера, к которому подключены", "Only for the connected server"))
            } else {
                val resolves = tunnelHealth.resolves(probe)
                // Who has been answering, from the core's own lines: the difference between "DNS is
                // broken" and "one of your resolvers is silent through this node".
                val heard = dnsAnswers?.stats(DNS_WINDOW_MILLIS).orEmpty()
                val silent = heard.filter { it.responded == 0 && it.silent > 0 }.map { DnsAnswerLog.displayName(it.server) }
                val summary = heard.take(4).joinToString("\n") { describe(it, russian) }
                fun withSummary(head: String) = if (summary.isEmpty()) head else head + "\n" + summary
                val silentHint = t(
                    "Молчат: ${silent.joinToString()}. Проверить адрес этой ноды в белом списке DNS-сервера (UFW, разрешённые клиенты AdGuard).",
                    "Silent: ${silent.joinToString()}. Check this node's address on the DNS server's allow-list.",
                )
                when (resolves) {
                    true -> if (silent.isEmpty()) {
                        ok(withSummary(t("Имена резолвятся", "Names resolve")))
                    } else {
                        warn(withSummary(t("Имена резолвятся, но не все DNS отвечают", "Names resolve, but not every resolver answers")), silentHint)
                    }
                    false -> fail(
                        withSummary(t("Имена внутри туннеля не резолвятся", "Names do not resolve in the tunnel")),
                        if (silent.isNotEmpty()) {
                            silentHint
                        } else {
                            t("Трафик есть, а DNS нет: проверьте DNS в настройках (удалённый DoH доступен ли через сервер) и DNS-настройки самого сервера.", "Check the remote DNS setting and the server's DNS.")
                        },
                    )
                    null -> skipped(t("Нет проверочного входа", "No probe inbound"))
                }
            }
        }

        // ---------------------------------------------------------------- 7. what the internet sees
        step(onStep, "exit-ip", t("Внешний IP", "Exit IP")) {
            if (!isActive || probe == null) {
                skipped(t("Только для сервера, к которому подключены", "Only for the connected server"))
            } else {
                val ip = runCatching { exitAddress(probe) }.getOrNull()
                if (ip != null) ok(ip) else warn(t("Не удалось узнать", "Could not find out"))
            }
        }
    }

    private suspend fun step(
        onStep: (Step) -> Unit,
        key: String,
        title: String,
        body: suspend StepScope.() -> Step,
    ) {
        onStep(Step(key, title, Status.Running))
        val started = System.nanoTime()
        val result = runCatching { StepScope(key, title).body() }.getOrElse {
            Step(key, title, Status.Fail, detail = "${it::class.simpleName}: ${it.message.orEmpty()}")
        }
        onStep(if (result.millis == null && result.status != Status.Skipped) result.copy(millis = (System.nanoTime() - started) / 1_000_000) else result)
    }

    private class StepScope(val key: String, val title: String) {
        fun ok(detail: String = "") = Step(key, title, Status.Ok, detail = detail)
        fun warn(detail: String, hint: String? = null) = Step(key, title, Status.Warn, detail = detail, hint = hint)
        fun fail(detail: String, hint: String? = null) = Step(key, title, Status.Fail, detail = detail, hint = hint)
        fun skipped(detail: String) = Step(key, title, Status.Skipped, detail = detail)
    }

    /** How a failed handshake looked from here — the difference between a censor and a server. */
    enum class HandshakeFailure { Silent, Reset, Closed, Refused }

    private fun StepScope.handshakeFailed(
        error: Exception,
        elapsedMillis: Long?,
        reality: Boolean,
        sni: String,
        port: Int,
        fragmented: Boolean,
        russian: Boolean,
    ): Step {
        fun t(ru: String, en: String) = if (russian) ru else en
        val after = elapsedMillis?.let { t(" через $it мс", " after $it ms") }.orEmpty()
        val onServer = t(
            "Проверка на сервере: openssl s_client -connect 127.0.0.1:$port -servername $sni. Не проходит — проблема на сервере.",
            "Check on the server: openssl s_client -connect 127.0.0.1:$port -servername $sni. If it fails, the problem is on the server.",
        )
        // A check without the server's fragmentation cannot condemn it: the real handshake is cut
        // differently. Said as a warning, with the step that does use it.
        val unproved = t(
            "Проверка без фрагментации. С фрагментацией проверяет шаг «Трафик через сервер» при подключении к этому серверу.",
            "Checked without fragmentation. The Traffic step checks with it while connected to this server.",
        )
        return when (classify(error)) {
            HandshakeFailure.Silent -> {
                val detail = t("Рукопожатие не закончилось за ${TIMEOUT_MILLIS / 1000} с", "The handshake timed out")
                if (fragmented) {
                    warn(t("Без фрагментации: ", "Without fragmentation: ") + detail, unproved)
                } else {
                    fail(
                        detail,
                        t(
                            "Нет ответа после TCP: DPI или недоступный ${if (reality) "target" else "бэкенд"} на сервере. ",
                            "No answer after TCP: DPI, or the server cannot reach its ${if (reality) "target" else "backend"}. ",
                        ) + onServer + t(" Проходит — фрагментация TLS или другой SNI.", " If it passes, use TLS fragmentation or another SNI."),
                    )
                }
            }
            HandshakeFailure.Reset -> {
                val detail = t("Соединение сброшено (RST)", "The connection was reset") + after
                if (fragmented) {
                    warn(t("Без фрагментации: ", "Without fragmentation: ") + detail, unproved)
                } else {
                    fail(
                        detail,
                        t("RST во время рукопожатия: DPI или на порту нет ${if (reality) "REALITY" else "TLS"}. ", "RST during the handshake: DPI, or no ${if (reality) "REALITY" else "TLS"} on this port. ") + onServer,
                    )
                }
            }
            HandshakeFailure.Closed -> fail(
                t("Сервер сам закрыл рукопожатие", "The server closed the handshake") + after,
                if (reality) {
                    t(
                        "Ядро сервера не разрешает или не достаёт target. Проверить DNS в контейнере ноды (Remnawave: docker exec remnanode cat /etc/resolv.conf, без 127.0.0.53) и «$sni» в serverNames. ",
                        "The server core cannot resolve or reach its target. Check DNS in the node container (no 127.0.0.53) and $sni in serverNames. ",
                    ) + onServer
                } else {
                    t("Проверить TLS-инбаунд на порту $port и сертификат для «$sni». ", "Check the TLS inbound on port $port and the certificate for $sni. ") + onServer
                },
            )
            HandshakeFailure.Refused -> fail(
                "${error::class.simpleName}: ${error.message.orEmpty()}".take(160),
                if (reality) {
                    t(
                        "REALITY: «$sni» должен быть в serverNames, target — доступен с сервера по TLS 1.3. ",
                        "REALITY: $sni must be in serverNames and the target reachable over TLS 1.3. ",
                    ) + onServer
                } else {
                    t(
                        "Сертификат не доверенный или не для этого имени. Самоподписанный сертификат Xray принимает только с pinnedPeerCertSha256.",
                        "The certificate is not trusted or not for this name.",
                    )
                },
            )
        }
    }

    private fun describe(stat: DnsAnswerLog.ServerStat, russian: Boolean): String {
        fun t(ru: String, en: String) = if (russian) ru else en
        val name = DnsAnswerLog.displayName(stat.server)
        return when {
            stat.responded == 0 -> t("$name — молчит (${stat.silent} без ответа)", "$name — silent (${stat.silent} unanswered)")
            stat.silent == 0 -> t("$name — отвечает", "$name — answers") + (stat.typicalMillis?.let { t(", ~$it мс", ", ~$it ms") }.orEmpty())
            else -> t("$name — ${stat.responded} ответов, ${stat.silent} без ответа", "$name — ${stat.responded} answered, ${stat.silent} not")
        }
    }

    /** What to check when the server takes the connection but carries nothing. */
    private fun protocolHint(node: ProxyNode, russian: Boolean, handshakeOk: Boolean): String {
        fun t(ru: String, en: String) = if (russian) ru else en
        val items = buildList {
            when (val s = node.settings) {
                is ProxySettings.Vless -> {
                    add(t("UUID клиента есть в clients inbound'а", "the UUID is among the inbound's clients"))
                    add(t("flow совпадает: у клиента «${s.flow.ifBlank { "нет" }}» — на сервере так же", "the flow matches the server's"))
                    if (node.tls?.reality != null) add(t("publicKey и shortId REALITY — от того же inbound'а", "REALITY publicKey and shortId belong to the inbound"))
                }
                is ProxySettings.Vmess -> {
                    add(t("UUID есть на сервере", "the UUID exists on the server"))
                    add(t("часы сервера точные: расхождение больше 2 минут ломает VMess", "the server clock is right (VMess fails beyond 2 min)"))
                }
                is ProxySettings.Trojan -> add(t("пароль Trojan совпадает", "the Trojan password matches"))
                is ProxySettings.Shadowsocks -> add(t("метод шифрования и пароль совпадают", "the cipher and password match"))
                else -> Unit
            }
            when (val transport = node.transport) {
                is TransportOptions.WebSocket -> add(t("путь WS «${transport.path}» и Host совпадают с сервером/прокси", "the WS path and Host match"))
                is TransportOptions.Grpc -> add(t("serviceName gRPC «${transport.serviceName}» совпадает", "the gRPC serviceName matches"))
                is TransportOptions.Xhttp -> add(t("путь XHTTP «${transport.path}» и mode совпадают", "the XHTTP path and mode match"))
                is TransportOptions.HttpUpgrade -> add(t("путь HTTPUpgrade «${transport.path}» совпадает", "the HTTPUpgrade path matches"))
                else -> Unit
            }
            add(t("на сервере маршрутизация не уводит трафик в blackhole, а outbound freedom работает", "server routing does not send traffic to blackhole"))
            add(t("журнал сервера (journalctl / docker logs) в момент подключения — там видно, почему он отказал", "the server log at connect time shows why it refused"))
        }
        val lead = if (handshakeOk) {
            t("Рукопожатие прошло, значит порт и TLS в порядке — сервер не пускает именно пользователя. Проверьте:", "The handshake passed, so the server rejects the user. Check:")
        } else {
            t("Проверьте:", "Check:")
        }
        return lead + "\n" + items.joinToString("\n") { "• $it" }
    }

    /** The address the internet sees, asked through the tunnel's own probe inbound. */
    private fun exitAddress(probe: ProbeEndpoint): String? = Socket().use { socket ->
        socket.soTimeout = TIMEOUT_MILLIS
        socket.connect(InetSocketAddress("127.0.0.1", probe.port), TIMEOUT_MILLIS)
        val credentials = Base64.getEncoder().encodeToString("${probe.username}:${probe.password}".toByteArray())
        socket.outputStream.write(
            (
                "GET http://api.ipify.org/ HTTP/1.1\r\nHost: api.ipify.org\r\n" +
                    "Proxy-Authorization: Basic $credentials\r\nUser-Agent: Avaca\r\nConnection: close\r\n\r\n"
                ).toByteArray(),
        )
        socket.outputStream.flush()
        val response = socket.inputStream.readBytes().toString(Charsets.UTF_8)
        if (!response.startsWith("HTTP/1.1 200") && !response.startsWith("HTTP/1.0 200")) return null
        response.substringAfter("\r\n\r\n").trim().takeIf { it.length in 7..45 }
    }

    private fun cn(cert: X509Certificate): String =
        Regex("CN=([^,]+)").find(cert.subjectX500Principal.name)?.groupValues?.get(1) ?: cert.subjectX500Principal.name

    /** For REALITY and "skip verification" servers: the handshake is the question, not the chain. */
    private fun trustAll(): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(TrustEverything), SecureRandom())
        return context.socketFactory
    }

    private object TrustEverything : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        /** How far back the resolver summary looks: long enough to hold a page load's lookups. */
        private const val DNS_WINDOW_MILLIS = 3 * 60_000L

        /** Read from what the platform threw; messages are matched because the types are not stable across TLS stacks. */
        internal fun classify(error: Throwable): HandshakeFailure {
            val chain = generateSequence(error) { it.cause }.toList()
            if (chain.any { it is SocketTimeoutException }) return HandshakeFailure.Silent
            val text = chain.mapNotNull { it.message }.joinToString(" ").lowercase()
            return when {
                "reset" in text || "broken pipe" in text -> HandshakeFailure.Reset
                chain.any { it is java.io.EOFException } || "closed by peer" in text || "connection closed" in text ||
                    "terminated the handshake" in text || "end of stream" in text || "unexpected eof" in text ||
                    "eof" in text.split(' ', ':', ',') -> HandshakeFailure.Closed
                else -> HandshakeFailure.Refused
            }
        }

        const val TIMEOUT_MILLIS = 6_000
    }
}
