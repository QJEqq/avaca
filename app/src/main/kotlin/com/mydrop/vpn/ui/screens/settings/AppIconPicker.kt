package com.mydrop.vpn.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mydrop.vpn.ui.AppIcon
import com.mydrop.vpn.ui.AppIcons
import com.mydrop.vpn.ui.theme.AvacaPalette

/** A scrolling row of the launcher icons; the chosen one is ringed in sand. */
@Composable
internal fun AppIconPicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon.entries.forEach { icon ->
            val selected = icon == current
            val title = stringResource(icon.title)
            Column(
                modifier = Modifier
                    .width(78.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !selected) {
                        AppIcons.apply(context, icon)
                        current = icon
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(
                            width = 2.dp,
                            color = if (selected) AvacaPalette.Sand else Color.Transparent,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(icon.preview),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) AvacaPalette.Text else AvacaPalette.TextMuted,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 2,
                )
            }
        }
    }
}
