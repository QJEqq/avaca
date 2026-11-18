package com.mydrop.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydrop.vpn.ui.theme.AvacaPalette

/*
 * Settings-style controls in the manner of iOS and One UI: grouped cards with a quiet caption
 * above them, a pill toggle with a sliding knob, and a segmented control. Drawn by hand rather than
 * taken from Material 3, whose switch and toggle buttons speak a different design language.
 */

/** The fill of a grouped card: one step above the graphite ground, like iOS's grouped cells. */
val GroupFill: Color get() = AvacaPalette.GroupFill

/** Off track of the toggle and the segmented control's well. */
private val Well: Color get() = AvacaPalette.Well

/**
 * A group: a small caption above, and a rounded card holding the rows — the shape of the iOS and
 * One UI settings screens. [footer], if any, is the explanation under the card.
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.7.sp),
                color = AvacaPalette.TextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(GroupFill)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = AvacaPalette.TextMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/**
 * The pill toggle: a sand track with a white knob sliding to the right when on, a dark well when
 * off. Display only — the row it sits in owns the click, so the whole row is the target.
 */
@Composable
fun PillSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "pill-switch",
    )
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(lerp(Well, AvacaPalette.Sand, progress)),
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .offset(x = 20.dp * progress)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * A segmented control: the options side by side in one well, the chosen one on a sand pad that
 * slides between them.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
) {
    if (options.isEmpty()) return
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(Well)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val padX by animateDpAsState(segment * selected.coerceIn(0, options.lastIndex), tween(220), label = "segment-pad")
        Box(
            Modifier
                .offset(x = padX)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(AvacaPalette.Sand),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val chosen = index == selected
                val color by animateColorAsState(
                    if (chosen) AvacaPalette.Graphite else AvacaPalette.Text,
                    tween(220),
                    label = "segment-text",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}
