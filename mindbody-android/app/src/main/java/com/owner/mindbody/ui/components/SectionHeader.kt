package com.owner.mindbody.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.PageTitle
import com.owner.mindbody.ui.theme.SectionEyebrow

@Composable
fun SectionHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(text = eyebrow.uppercase(), style = SectionEyebrow)
            Text(text = title, style = PageTitle)
        }
        trailing?.invoke()
    }
}

@Composable
fun StreamStatusBadge(
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MindBodyShapes.Badge)
            .background(MindBodyColors.PrimaryIndigoSurface)
            .border(1.dp, MindBodyColors.PrimaryIndigo.copy(alpha = 0.12f), MindBodyShapes.Badge)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (connected) MindBodyColors.Emerald else MindBodyColors.OnBackgroundSecondary)
        )
        Text(
            text = if (connected) "Loop BLE Stream" else "BLE 未连接",
            style = CardTitle.copy(
                fontSize = CardTitle.fontSize * 0.75f,
                color = MindBodyColors.PrimaryIndigo
            )
        )
    }
}
