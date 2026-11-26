package com.airbombal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbombal.app.ui.theme.Amber
import com.airbombal.app.ui.theme.Danger
import com.airbombal.app.ui.theme.Line
import com.airbombal.app.ui.theme.Mist
import com.airbombal.app.ui.theme.MistDim
import com.airbombal.app.ui.theme.Mono
import com.airbombal.app.ui.theme.Neon
import com.airbombal.app.ui.theme.Panel

/** Bordered dark surface used for every section of the UI. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, accent ?: Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(
    text: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            color = Neon,
            fontFamily = Mono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun MonoText(
    text: String,
    color: Color = Mist,
    size: Int = 12,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontFamily = Mono,
        fontSize = size.sp,
        fontWeight = weight,
        modifier = modifier,
    )
}

@Composable
fun Pill(
    text: String,
    color: Color = MistDim,
    filled: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            fontFamily = Mono,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Four ascending bars coloured by RSSI. */
@Composable
fun SignalBars(rssi: Int?, modifier: Modifier = Modifier) {
    val level = when {
        rssi == null -> 0
        rssi >= -50 -> 4
        rssi >= -65 -> 3
        rssi >= -78 -> 2
        else -> 1
    }
    val color = when (level) {
        4, 3 -> Neon
        2 -> Amber
        1 -> Danger
        else -> Line
    }
    Row(
        modifier = modifier.height(18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((6 + index * 4).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index < level) color else Line),
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Line, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = Mist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            color = MistDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
        )
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Mist) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key.uppercase(),
            color = MistDim,
            fontFamily = Mono,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.width(88.dp),
        )
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = Mono)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.6f)))
}

@Composable
fun Hint(text: String, color: Color = MistDim) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        style = MaterialTheme.typography.bodyMedium,
    )
}
