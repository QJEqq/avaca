package com.mydrop.vpn.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mydrop.vpn.core.model.AddKind
import com.mydrop.vpn.shared.R
import com.mydrop.vpn.ui.components.Segmented
import com.mydrop.vpn.ui.components.SettingsGroup
import com.mydrop.vpn.ui.theme.AvacaPalette

/**
 * Adding a subscription or a server, laid out like the iOS and One UI settings sheets: the two
 * quickest ways in first (the clipboard and a QR code), then the link itself in a grouped card
 * with what it accepts written under it, what kind of link it is as a segmented choice, and one
 * large button at the bottom, under the thumb.
 */
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onAdd: (text: String, name: String?, kind: AddKind) -> Unit,
    onScan: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(AddKind.Auto) }

    fun submit() {
        val link = url.trim()
        if (link.isEmpty()) return
        onAdd(link, name.trim().takeIf(String::isNotEmpty), kind)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AvacaPalette.Graphite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AvacaPalette.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = AvacaPalette.Text,
                modifier = Modifier.padding(start = 4.dp, bottom = 18.dp),
            )

            SettingsGroup(title = null) {
                ActionRow(
                    icon = Icons.Rounded.ContentPaste,
                    label = stringResource(R.string.add_paste_from_clipboard),
                    onClick = { clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { url = it } },
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Rounded.QrCodeScanner,
                    label = stringResource(R.string.add_scan_qr),
                    onClick = onScan,
                )
            }

            Spacer(Modifier.height(24.dp))

            SettingsGroup(
                title = stringResource(R.string.add_sheet_link_label),
                footer = stringResource(R.string.add_sheet_hint),
            ) {
                SheetField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = stringResource(R.string.add_link_placeholder),
                    icon = Icons.Rounded.Link,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                RowDivider()
                SheetField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.add_sheet_name_label),
                    icon = Icons.Rounded.Edit,
                    keyboard = KeyboardOptions(imeAction = ImeAction.Done),
                    onDone = ::submit,
                )
            }

            Spacer(Modifier.height(24.dp))

            // The same https link can be a subscription or a resolver, and only its owner knows
            // which, so the guess stays a default and the answer stays with the user.
            SettingsGroup(title = stringResource(R.string.add_kind_title)) {
                Spacer(Modifier.height(10.dp))
                Segmented(
                    options = AddKind.entries.map { stringResource(it.labelRes) },
                    selected = AddKind.entries.indexOf(kind),
                    onSelect = { kind = AddKind.entries[it] },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = ::submit,
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AvacaPalette.Sand,
                    contentColor = AvacaPalette.Graphite,
                    disabledContainerColor = AvacaPalette.Well,
                    disabledContentColor = AvacaPalette.TextMuted,
                ),
            ) {
                Text(stringResource(R.string.action_add), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AvacaPalette.Sand, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = AvacaPalette.Text,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = AvacaPalette.TextMuted,
        )
    }
}

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    keyboard: KeyboardOptions,
    onDone: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AvacaPalette.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AvacaPalette.TextMuted,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = AvacaPalette.Text),
                cursorBrush = SolidColor(AvacaPalette.Sand),
                keyboardOptions = keyboard,
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Cancel,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = AvacaPalette.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 40.dp),
        thickness = 1.dp,
        color = AvacaPalette.Well,
    )
}
