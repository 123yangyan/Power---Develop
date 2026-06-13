package com.owner.mindbody.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.theme.StatValue

data class StatItem(
    val label: String,
    val value: String,
    val valueColor: Color = MindBodyColors.OnBackground
)

@Composable
fun StatGrid(
    items: List<StatItem>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MindBodyShapes.StatCell)
                    .background(MindBodyColors.StatCellBg)
                    .border(1.dp, MindBodyColors.StatCellBorder, MindBodyShapes.StatCell)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = item.value, style = StatValue.copy(color = item.valueColor))
                Text(
                    text = item.label,
                    style = StatLabel,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
