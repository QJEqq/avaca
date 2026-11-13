package com.mydrop.vpn.vpn

import android.util.Log
import com.mydrop.vpn.xray.mobile.Mobile
import com.mydrop.vpn.xray.mobile.NetworkBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Kotlin face of the OpenFlux engine — the sibling of [XrayCore].
 *
 * OpenFlux is a second gomobile core (bound under `com.mydrop.vpn.xray.mobile`). Where Xray owns
 * the TUN and routes everything, this engine is only a local SOCKS5 server on 127.0.0.1:1080: it
 * digs a tunnel to the exit through whatever carriers the `openflux://` link names (plain `direct`
 * TCP, the anonymous `boards`/`avito` carriers, or a signed-in document such as yandex/mailru),
 * and hands each connection out over SOCKS. Xray then reaches it as an ordinary `socks` outbound.
 *
 * Like [XrayCore], everything the binding generates is static and process-wide, so this is an
 * `object`; and a Go `int` crosses the boundary as a Java `long`.
 *
 * ## Sign-in prompt flow (doc transports)
 * A document carrier (mailru/yandex) can only open its doc once signed in. When the engine needs
 * that, it exposes a page to open ([Mobile.pendingPromptURL]). This object polls for it and
 * publishes it on [pendingPrompt]; the UI opens a WebView at that page, the user signs into THEIR
 * OWN account there, and the Cookie header the page collects goes back through [submitCookies].
 * The user's cookies live only on the device — the app never hands them to the node. Solved
 * cookies are persisted (see [initCookieStore]) so the sign-in survives restarts.
 */
object OpenfluxCore {

    private const val TAG = "OpenfluxCore"

    /** Where the engine listens — the same address XrayConfigFactory points the socks outbound at. */
    private const val SOCKS_ADDRESS = "127.0.0.1:1080"

    /** One page the engine is waiting on: the user must open it to sign in or pass a check. */
    data class Prompt(
        /** A real site to open (e.g. the Mail.ru/Yandex sign-in), or "" when [html] is set instead. */
        val url: String,
        /** A script transport's own inline page to load directly, or "" for a real site. */
        val html: String,
        /** The carrier that asked (mailru, yandex, …), for the dialog title. */
        val transport: String,
        /** "login" or "smartcaptcha". */
        val reason: String,
        /** Non-empty: open the page through this HTTP proxy (a check the exit must pass from its IP). */
        val proxy: String,
        /** The page is the script's own (show as-is, submit via window.openfluxSubmit), not a real site. */
        val own: Boolean,
    )

    private val _pendingPrompt = MutableStateFlow<Prompt?>(null)

    /** The page the UI must open now, or null when nothing is pending. Observe from the ViewModel. */
    val pendingPrompt: StateFlow<Prompt?> = _pendingPrompt.asStateFlow()

    // The prompt watcher runs while the engine is up; cancelled in stop().
    private var scope: CoroutineScope? = null

    /**
     * Enables persisting solved-prompt cookies across app restarts, so the user signs in once.
     * Call ONCE before [start] (e.g. from the Application / the service's onCreate); [path] is a
     * private file the app owns, e.g. `context.filesDir.resolve("openflux-cookies.json").path`.
     */
    fun initCookieStore(path: String) {
        val err = runCatching { Mobile.setCookieStorePath(path) }.getOrElse { it.message.orEmpty() }
        if (err.isNotEmpty()) Log.w(TAG, "cookie store: $err")
    }

    /**
     * Brings the engine up for [link]. It blocks nothing: the binding binds the listener
     * synchronously (so Xray can dial :1080 the moment this returns) and serves on its own thread.
     *
     * [protect] is the same socket-protector handed to Xray ([MyDropVpnService.protectSocket] ->
     * `VpnService.protect`), and it MUST be installed before the engine starts: Xray owns the TUN,
     * so a carrier socket not excluded from it would loop its own packets back in and hang.
     *
     * @throws IllegalStateException carrying the engine's own message when the link or a carrier
     *   is rejected; the caller's start path catches it and surfaces it as a failed connect.
     */
    fun start(link: String, protect: (Int) -> Boolean) {
        Mobile.setNetworkBinder(object : NetworkBinder {
            override fun bindSocket(network: String, fd: Long) {
                if (!protect(fd.toInt())) throw Exception("openflux: protect($fd) refused")
            }
        })
        val error = Mobile.startSocksLink(link, SOCKS_ADDRESS)
        if (error.isNotEmpty()) throw IllegalStateException(error)
        Log.i(TAG, "engine up on $SOCKS_ADDRESS")
        startPromptWatcher()
    }

    /** Safe when nothing is running — teardown calls it unconditionally, like [XrayCore.stop]. */
    fun stop() {
        scope?.cancel()
        scope = null
        _pendingPrompt.value = null
        runCatching { Mobile.stop() }
            .onFailure { Log.i(TAG, "stop: ${it.message}") }
    }

    /** Whether the engine's tunnel to the exit is currently up. */
    val running: Boolean
        get() = runCatching { Mobile.isConnected() }.getOrDefault(false)

    /**
     * Hands the engine the Cookie header ("a=1; b=2") the WebView collected after the user signed
     * in. The engine applies it to the waiting carrier and saves it for next time. "" on success,
     * else a user-readable error.
     */
    fun submitCookies(cookieHeader: String): String =
        runCatching { Mobile.submitPromptCookies(cookieHeader) }.getOrElse { it.message.orEmpty() }

    /** The user closed the sign-in page without finishing. Clears the pending prompt. */
    fun cancelPrompt() {
        runCatching { Mobile.cancelPrompt() }
        _pendingPrompt.value = null
    }

    // Mirrors the engine's pending page into [pendingPrompt]. The Go side keeps a prompt sticky
    // until cookies are submitted or it is cancelled, so a 500ms poll is plenty and cheap.
    private fun startPromptWatcher() {
        scope?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch {
            while (isActive) {
                val url = runCatching { Mobile.pendingPromptURL() }.getOrDefault("")
                val html = runCatching { Mobile.pendingPromptHTML() }.getOrDefault("")
                _pendingPrompt.value = if (url.isEmpty() && html.isEmpty()) {
                    null
                } else {
                    Prompt(
                        url = url,
                        html = html,
                        transport = runCatching { Mobile.pendingPromptTransport() }.getOrDefault(""),
                        reason = runCatching { Mobile.pendingPromptReason() }.getOrDefault(""),
                        proxy = runCatching { Mobile.pendingPromptProxy() }.getOrDefault(""),
                        own = runCatching { Mobile.pendingPromptOwn() }.getOrDefault(false),
                    )
                }
                delay(500)
            }
        }
    }
}
