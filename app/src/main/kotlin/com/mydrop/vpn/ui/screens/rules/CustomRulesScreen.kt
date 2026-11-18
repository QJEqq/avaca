package com.mydrop.vpn.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydrop.vpn.core.model.AppSettings
import com.mydrop.vpn.core.model.CustomRules
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.ui.components.ScreenHeader
import com.mydrop.vpn.ui.components.Segmented
import com.mydrop.vpn.ui.components.SettingsGroup
import com.mydrop.vpn.ui.components.TonalIconButton
import com.mydrop.vpn.ui.theme.AvacaPalette

/**
 * The user's own routing: three lists — through the tunnel, direct, blocked — that outrank the
 * routing mode and the operator's rules. Edits apply at once; a running tunnel is rebuilt by
 * TunnelSettingsApplier.
 */
@Composable
fun CustomRulesScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    fun t(ru: String, en: String) = if (russian) ru else en

    val actions = CustomRules.Action.entries
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf("") }
    var rejected by rememberSaveable { mutableStateOf(false) }
    val action = actions[selected]
    val entries = settings.customRules.of(action)

    fun add() {
        val entry = CustomRules.normalize(draft)
        if (entry == null) {
            rejected = draft.isNotBlank()
            return
        }
        onUpdate { it.copy(customRules = it.customRules.adding(action, entry)) }
        draft = ""
        rejected = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("header") {
            ScreenHeader(
                title = t("Свои правила", "Own rules"),
                subtitle = t(
                    "Приоритет выше режима маршрутизации и правил провайдера",
                    "Priority over the routing mode and provider rules",
                ),
                actions = {
                    TonalIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack)
                },
            )
        }

        item("lists") {
            Segmented(
                options = listOf(t("Через VPN", "Via VPN"), t("Напрямую", "Direct"), t("Блок", "Block")),
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item("input") {
            SettingsGroup(
                title = null,
                modifier = Modifier.padding(horizontal = 16.dp),
                footer = if (rejected) {
                    t("Неверный формат", "Invalid format")
                } else {
                    t(
                        "Сайт или ссылка (youtube.com), geosite:категория, IP или подсеть (1.2.3.0/24), geoip:код страны",
                        "A site or link (youtube.com), geosite:category, an IP or subnet (1.2.3.0/24), geoip:country",
                    )
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (draft.isEmpty()) {
                            Text(
                                t("Добавить в список", "Add to the list"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AvacaPalette.TextMuted,
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                rejected = false
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AvacaPalette.Text),
                            cursorBrush = SolidColor(AvacaPalette.Sand),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = ::add, enabled = draft.isNotBlank()) {
                        Text(t("Добавить", "Add"), color = if (draft.isNotBlank()) AvacaPalette.Sand else AvacaPalette.TextMuted)
                    }
                }
            }
        }

        item("entries") {
            SettingsGroup(
                title = "${listOf(t("Через VPN", "Via VPN"), t("Напрямую", "Direct"), t("Блок", "Block"))[selected]} · ${entries.size}",
                modifier = Modifier.padding(horizontal = 16.dp),
                footer = t(
                    "Порядок применения: блок, VPN, напрямую.",
                    "Order: block, VPN, direct.",
                ),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        t("Нет записей", "No entries"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AvacaPalette.TextMuted,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = AvacaPalette.Divider)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.removePrefix("domain:"),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onUpdate { it.copy(customRules = it.customRules.removing(action, entry)) } },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Rounded.RemoveCircleOutline,
                                contentDescription = t("Удалить", "Remove"),
                                tint = AvacaPalette.Failed,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
