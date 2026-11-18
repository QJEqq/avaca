package com.mydrop.vpn.ui.screens.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import com.mydrop.vpn.ui.components.Segmented
import com.mydrop.vpn.data.ConnectionLog
import com.mydrop.vpn.core.xray.XrayConfigFactory
import com.mydrop.vpn.core.model.CustomRules
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydrop.vpn.core.model.ProxyNode
import com.mydrop.vpn.data.ServerDiagnostics
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.ui.MainUiState
import com.mydrop.vpn.ui.components.PillSwitch
import com.mydrop.vpn.ui.components.ScreenHeader
import com.mydrop.vpn.ui.components.SettingsGroup
import com.mydrop.vpn.ui.components.TonalIconButton
import com.mydrop.vpn.ui.theme.AvacaPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Developer mode: the server walked step by step — DNS, TCP, TLS or REALITY, then traffic, DNS and
 * the exit address through the tunnel — with a hint at what to check on the server for the step
 * that fails. For people who run their own VPN and need to know *where* it breaks.
 */
@Composable
fun DiagnosticsScreen(
    state: MainUiState,
    onDiagnose: suspend (ProxyNode, Boolean, (ServerDiagnostics.Step) -> Unit) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    fun t(ru: String, en: String) = if (russian) ru else en
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var node by remember { mutableStateOf(state.selectedNode ?: state.nodes.firstOrNull()) }
    val steps = remember { mutableStateListOf<ServerDiagnostics.Step>() }
    var running by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    fun start() {
        val chosen = node ?: return
        if (running) return
        running = true
        steps.clear()
        scope.launch {
            onDiagnose(chosen, russian) { step ->
                // Each step arrives twice: when it starts and when it ends.
                scope.launch {
                    val at = steps.indexOfFirst { it.key == step.key }
                    if (at >= 0) steps[at] = step else steps.add(step)
                }
            }
            running = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("header") {
            ScreenHeader(
                title = t("Диагностика", "Diagnostics"),
                subtitle = t(
                    "Проверка сервера по шагам: где именно ломается соединение",
                    "The connection checked step by step: where exactly it breaks",
                ),
                actions = {
                    TonalIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack)
                    Spacer(Modifier.weight(1f))
                },
            )
        }

        item("server") {
            SettingsGroup(title = t("Сервер", "Server"), modifier = Modifier.padding(horizontal = 16.dp)) {
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = state.nodes.size > 1) { picking = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(node?.name ?: t("Нет серверов", "No servers"), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            node?.let {
                                Text(
                                    serverSummary(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AvacaPalette.TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (state.nodes.size > 1) Icon(Icons.Rounded.ExpandMore, null, tint = AvacaPalette.TextMuted)
                    }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        state.nodes.take(200).forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    node = candidate
                                    steps.clear()
                                    picking = false
                                },
                            )
                        }
                    }
                }
            }
        }

        item("run") {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = ::start,
                    enabled = node != null && !running,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AvacaPalette.Sand,
                        contentColor = AvacaPalette.Graphite,
                        disabledContainerColor = AvacaPalette.Well,
                        disabledContentColor = AvacaPalette.TextMuted,
                    ),
                ) {
                    Text(if (running) t("Проверяю…", "Checking…") else t("Проверить", "Check"), style = MaterialTheme.typography.titleMedium)
                }
                if (steps.isNotEmpty() && !running) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(report(node, steps, russian))) },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AvacaPalette.Well, contentColor = AvacaPalette.Text),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = t("Скопировать отчёт", "Copy the report"), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (steps.isNotEmpty()) {
            item("steps") {
                SettingsGroup(title = t("Шаги", "Steps"), modifier = Modifier.padding(horizontal = 16.dp)) {
                    steps.forEachIndexed { index, step ->
                        if (index > 0) HorizontalDivider(thickness = 1.dp, color = AvacaPalette.Well, modifier = Modifier.padding(start = 34.dp))
                        StepRow(step, russian)
                    }
                }
            }
        }

        item("tail") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StepRow(step: ServerDiagnostics.Step, russian: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                when (step.status) {
                    ServerDiagnostics.Status.Running -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AvacaPalette.Connecting)
                    ServerDiagnostics.Status.Ok -> Icon(Icons.Rounded.CheckCircle, null, tint = AvacaPalette.Connected, modifier = Modifier.size(20.dp))
                    ServerDiagnostics.Status.Warn -> Icon(Icons.Rounded.Warning, null, tint = AvacaPalette.Connecting, modifier = Modifier.size(20.dp))
                    ServerDiagnostics.Status.Fail -> Icon(Icons.Rounded.Error, null, tint = AvacaPalette.Failed, modifier = Modifier.size(20.dp))
                    ServerDiagnostics.Status.Skipped -> Icon(Icons.Rounded.RemoveCircleOutline, null, tint = AvacaPalette.TextMuted, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(step.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            step.millis?.let {
                Text(if (russian) "$it мс" else "$it ms", style = MaterialTheme.typography.labelLarge, color = AvacaPalette.TextMuted)
            }
        }
        if (step.detail.isNotBlank()) {
            SelectionContainer {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.status == ServerDiagnostics.Status.Fail) AvacaPalette.Failed else AvacaPalette.TextMuted,
                    modifier = Modifier.padding(start = 34.dp, top = 2.dp),
                )
            }
        }
        step.hint?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = AvacaPalette.Text,
                modifier = Modifier
                    .padding(start = 34.dp, top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AvacaPalette.Sand.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            )
        }
    }
}

/**
 * The config the core would be handed for one server, pretty-printed, with every credential masked
 * until asked for — so a screenshot of it can be posted to a chat without giving the server away.
 */
@Composable
fun XrayConfigScreen(
    state: MainUiState,
    buildConfig: (ProxyNode) -> String?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    fun t(ru: String, en: String) = if (russian) ru else en
    val clipboard = LocalClipboardManager.current
    val node = state.selectedNode
    var reveal by remember { mutableStateOf(false) }
    var raw by remember(node?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(node?.id) { raw = node?.let { withContext(Dispatchers.Default) { buildConfig(it) } } }
    val shown = remember(raw, reveal) { raw?.let { pretty(it, mask = !reveal) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("header") {
            ScreenHeader(
                title = t("Конфиг Xray", "Xray config"),
                subtitle = node?.name ?: t("Сервер не выбран", "No server selected"),
                actions = {
                    TonalIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack)
                    Spacer(Modifier.weight(1f))
                    shown?.let { text ->
                        TonalIconButton(
                            Icons.Rounded.ContentCopy,
                            t("Скопировать", "Copy"),
                            onClick = { clipboard.setText(AnnotatedString(text)) },
                        )
                    }
                },
            )
        }
        item("reveal") {
            SettingsGroup(title = null, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toggleable(value = reveal, role = Role.Switch) { reveal = it }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Показать ключи и пароли", "Show keys and passwords"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            t("Не делитесь скриншотом с открытыми ключами", "Do not share a screenshot with keys shown"),
                            style = MaterialTheme.typography.bodySmall,
                            color = AvacaPalette.TextMuted,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    PillSwitch(checked = reveal)
                }
            }
        }
        item("config") {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AvacaPalette.Sunken)
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = shown ?: t("Не удалось собрать конфиг для этого сервера", "Could not build a config for this server"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                        color = AvacaPalette.Text,
                        softWrap = false,
                    )
                }
            }
        }
        item("tail") { Spacer(Modifier.height(24.dp)) }
    }
}

private fun serverSummary(node: ProxyNode): String = buildList {
    add(node.protocol.label)
    if (node.server != "openflux") add("${node.server}:${node.port}")
    node.transport?.label?.let(::add)
    node.tls?.takeIf { it.enabled }?.let { tls ->
        add(if (tls.reality != null) "REALITY" else "TLS")
        tls.serverName?.takeIf { it.isNotBlank() }?.let { add("SNI $it") }
    }
}.joinToString(" · ")

private fun report(node: ProxyNode?, steps: List<ServerDiagnostics.Step>, russian: Boolean): String = buildString {
    appendLine("Avaca — ${if (russian) "диагностика" else "diagnostics"}: ${node?.let(::serverSummary).orEmpty()}")
    steps.forEach { step ->
        val mark = when (step.status) {
            ServerDiagnostics.Status.Ok -> "✓"
            ServerDiagnostics.Status.Warn -> "!"
            ServerDiagnostics.Status.Fail -> "✗"
            ServerDiagnostics.Status.Skipped -> "–"
            ServerDiagnostics.Status.Running -> "…"
        }
        append(mark).append(' ').append(step.title)
        step.millis?.let { append(" — ").append(it).append(if (russian) " мс" else " ms") }
        if (step.detail.isNotBlank()) append(" — ").append(step.detail)
        appendLine()
        step.hint?.let { appendLine("  ${it.replace("\n", "\n  ")}") }
    }
}

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** Credentials in an Xray document, by key. */
private val SECRET_KEYS = setOf("id", "password", "publicKey", "privateKey", "secretKey", "shortId", "psk", "auth", "preSharedKey", "pass", "user")

private fun pretty(raw: String, mask: Boolean): String = runCatching {
    val element = Json.parseToJsonElement(raw)
    prettyJson.encodeToString(JsonElement.serializer(), if (mask) masked(element) else element)
}.getOrDefault(raw)

private fun masked(element: JsonElement, key: String? = null): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { (k, v) -> masked(v, k) })
    is JsonArray -> JsonArray(element.map { masked(it, key) })
    is JsonPrimitive -> if (key in SECRET_KEYS && element.isString && element.content.isNotEmpty()) {
        JsonPrimitive("•••" + element.content.takeLast(4))
    } else {
        element
    }
}

/**
 * Where each connection went — through which server, direct, blocked — newest first, from the
 * core's own access lines. For checking that a rule matched and that DNS rode the tunnel. A tap on
 * a row sends its host into one of the user's own rule lists.
 */
@Composable
fun ConnectionLogScreen(
    connections: List<ConnectionLog.Entry>,
    nodes: List<ProxyNode>,
    onClear: () -> Unit,
    onAddRule: (CustomRules.Action, String) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    fun t(ru: String, en: String) = if (russian) ru else en
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableStateOf(0) }
    val names = remember(nodes) { nodes.flatMap { listOf(it) + it.group?.members.orEmpty() }.associate { it.id to it.name } }
    val shown = remember(connections, filter) {
        connections.filter { entry ->
            when (filter) {
                1 -> entry.route == ConnectionLog.Route.Proxy
                2 -> entry.route == ConnectionLog.Route.Direct
                3 -> entry.route == ConnectionLog.Route.Block || !entry.accepted
                else -> true
            }
        }
    }
    val clock = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            ScreenHeader(
                title = t("Журнал соединений", "Connection log"),
                subtitle = t("Последние ${connections.size}, только в памяти", "Last ${connections.size}, kept in memory only"),
                actions = {
                    TonalIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack)
                    Spacer(Modifier.weight(1f))
                    TonalIconButton(Icons.Rounded.DeleteSweep, t("Очистить", "Clear"), onClick = onClear)
                },
            )
        }
        item("filter") {
            Segmented(
                options = listOf(t("Все", "All"), "VPN", t("Напрямую", "Direct"), t("Блок", "Block")),
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (shown.isEmpty()) {
            item("empty") {
                Text(
                    t(
                        "Нет соединений. Запись ведётся при включённом режиме разработчика.",
                        "No connections. Recorded while developer mode is on.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AvacaPalette.TextMuted,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        items(shown.size, key = { index -> "${shown[index].atMillis}-$index" }) { index ->
            val entry = shown[index]
            var menu by remember { mutableStateOf(false) }
            val (label, tint) = when {
                !entry.accepted -> t("Отклонено", "Rejected") to AvacaPalette.Failed
                entry.route == ConnectionLog.Route.Proxy ->
                    (entry.nodeId?.let(names::get) ?: "VPN") to AvacaPalette.Sand
                entry.route == ConnectionLog.Route.Direct -> t("Напрямую", "Direct") to AvacaPalette.Idle
                entry.route == ConnectionLog.Route.Block -> t("Блок", "Blocked") to AvacaPalette.Failed
                entry.route == ConnectionLog.Route.Dns -> "DNS" to AvacaPalette.TextMuted
                else -> entry.outbound to AvacaPalette.TextMuted
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AvacaPalette.Selected.copy(alpha = 0.35f))
                        .clickable { menu = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(tint))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${entry.host}:${entry.port}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(entry.network.uppercase()).append(" · ").append(label)
                                when (entry.inbound) {
                                    XrayConfigFactory.DNS_IN_TAG -> append(t(" · запрос DNS ядра", " · core DNS lookup"))
                                    XrayConfigFactory.PROBE_TAG -> append(t(" · проверка приложения", " · app check"))
                                }
                                if (entry.reason.isNotEmpty() && !entry.accepted) append(" · ").append(entry.reason)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AvacaPalette.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(clock.format(java.util.Date(entry.atMillis)), style = MaterialTheme.typography.labelSmall, color = AvacaPalette.TextMuted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        CustomRules.Action.Proxy to t("Всегда через VPN", "Always via VPN"),
                        CustomRules.Action.Direct to t("Всегда напрямую", "Always direct"),
                        CustomRules.Action.Block to t("Блокировать", "Block"),
                    ).forEach { (action, title) ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                onAddRule(action, entry.host)
                                menu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(t("Скопировать адрес", "Copy address")) },
                        onClick = {
                            clipboard.setText(AnnotatedString(entry.host))
                            menu = false
                        },
                    )
                }
            }
        }
    }
}
