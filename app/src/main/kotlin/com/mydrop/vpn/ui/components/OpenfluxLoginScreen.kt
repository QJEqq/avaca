package com.mydrop.vpn.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.content.Context
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mydrop.vpn.vpn.OpenfluxCore
import kotlinx.coroutines.launch

private const val TAG = "OpenfluxLogin"

/**
 * Full-screen sign-in wall for a document transport (Mail.ru / Yandex). Shown as an overlay the
 * moment [OpenfluxCore.pendingPrompt] turns non-null, over everything else, in the same window
 * (not a Dialog) so the sign-in form gets the keyboard the normal way.
 *
 * The user signs into THEIR OWN account inside this WebView; the cookies the page sets live only
 * in the app's own cookie jar on the device. When they tap "Готово", the Cookie header for both
 * the page we opened and wherever the sign-in left them is read out and handed to [onSubmit],
 * which passes it to the engine ([OpenfluxCore.submitCookies]) — the only place those cookies go.
 * Nothing is sent to the node.
 *
 * [onSubmit] returns "" on success (the engine then drops the prompt and this overlay disappears
 * on its own) or a message to show here. [onCancel] (close button / system back) abandons the
 * prompt.
 *
 * The WebView is set up like a real browser tab on purpose: Yandex's sign-in and its SmartCaptcha
 * are heavy JS pages that blank out in a bare WebView. A [WebChromeClient] (progress, dialogs,
 * popups) and multiple-window support are what make them render. The UA is the device's OWN browser
 * string ([browserUserAgent]) rather than a spoofed desktop one: SmartCaptcha fingerprints the page,
 * and a desktop-Firefox UA sitting on a mobile Chromium engine reads as a bot and gets the check
 * rejected — the same page opens fine in the phone's Chrome, which is what this now looks like.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenfluxLoginScreen(
    prompt: OpenfluxCore.Prompt,
    onSubmit: suspend (cookieHeader: String) -> String,
    onCancel: () -> Unit,
) {
    // This screen only drives the cookie sign-in path (a real sign-in page). A script transport's
    // own setup page (html / own) or a check the node must pass from its address (proxy) submits a
    // different way and is not wired into the app yet — be honest about it rather than show a
    // broken WebView.
    val cookieLogin = prompt.html.isEmpty() && !prompt.own && prompt.proxy.isEmpty()
    if (!cookieLogin) {
        UnsupportedPrompt(prompt, onCancel)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var currentUrl by remember { mutableStateOf(prompt.url) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val webView = remember {
        val web = WebView(context)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The device's own browser UA (see [browserUserAgent]) — a consistent mobile identity,
            // so SmartCaptcha treats this like the phone's Chrome, which opens the doc fine.
            userAgentString = browserUserAgent(context)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            // Yandex sign-in / SmartCaptcha open the challenge in a popup; let the page do it.
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentUrl = url
                loading = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                currentUrl = url
                loading = false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                // Only the main page matters; sub-resource failures are normal and noisy.
                if (request.isForMainFrame) {
                    Log.w(TAG, "load error ${error.errorCode} '${error.description}' @ ${request.url}")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame) {
                    Log.w(TAG, "http ${errorResponse.statusCode} @ ${request.url}")
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            // A window.open() / target=_blank (SmartCaptcha, some sign-in steps): run it in this
            // same WebView rather than dropping it on the floor, which shows as a blank page.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = view
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d(TAG, "web: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
        web.loadUrl(prompt.url)
        web
    }

    BackHandler { if (webView.canGoBack()) webView.goBack() else onCancel() }

    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(promptTitle(prompt)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                },
                actions = {
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = promptHint(prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            scope.launch {
                                val header = cookieHeader(prompt.url, currentUrl)
                                val error = when {
                                    header.isEmpty() -> "Не вижу входа: завершите вход на странице выше и попробуйте снова"
                                    else -> onSubmit(header)
                                }
                                busy = false
                                if (error.isNotEmpty()) snackbar.showSnackbar(error)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Готово")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AndroidView(
                factory = {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The Cookie header for every URL the sign-in touched: the page we opened and where it ended up. */
private fun cookieHeader(promptUrl: String, currentUrl: String): String {
    val cookies = CookieManager.getInstance()
    cookies.flush()
    return listOf(promptUrl, currentUrl)
        .filter { it.isNotBlank() }
        .distinct()
        .mapNotNull { cookies.getCookie(it)?.takeIf(String::isNotEmpty) }
        .joinToString("; ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnsupportedPrompt(prompt: OpenfluxCore.Prompt, onCancel: () -> Unit) {
    BackHandler { onCancel() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(promptTitle(prompt)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Этот транспорт просит настройку, которую приложение пока не умеет показывать.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onCancel) { Text("Понятно") }
        }
    }
}

private fun promptTitle(prompt: OpenfluxCore.Prompt): String {
    val service = serviceName(prompt.transport)
    return if (prompt.reason == "login") "Вход в $service" else "Проверка · $service"
}

private fun promptHint(prompt: OpenfluxCore.Prompt): String = if (prompt.reason == "login") {
    "Войдите в свой аккаунт и нажмите «Готово». Данные входа остаются только на этом устройстве."
} else {
    "Пройдите проверку на странице и нажмите «Готово». Данные остаются только на этом устройстве."
}

private fun serviceName(transport: String): String = when (transport.lowercase()) {
    "mailru" -> "Mail.ru"
    "yandex", "vyandex" -> "Яндекс"
    "boards" -> "Яндекс.Доску"
    else -> transport.ifEmpty { "сервис" }
}

/**
 * The device's own browser User-Agent, cleaned so it reads as the phone's Chrome rather than a
 * WebView: the "; wv" marker and the WebView-only "Version/x.x" token are both tells that anti-bot
 * fingerprinting (Yandex SmartCaptcha) flags. Keeps the real Chrome/Android version so the UA stays
 * consistent with the engine actually rendering the page.
 */
private fun browserUserAgent(context: Context): String =
    WebSettings.getDefaultUserAgent(context)
        .replace("; wv", "")
        .replace(Regex("""Version/\d+\.\d+ """), "")
