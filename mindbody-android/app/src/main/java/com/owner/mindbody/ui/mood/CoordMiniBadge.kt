package com.owner.mindbody.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors

private val QUADRANT_COLORS = mapOf(
    "tl" to Color(0xFF8B3A5A),
    "tr" to Color(0xFFB05530),
    "bl" to Color(0xFF4A6080),
    "br" to Color(0xFF3A8F5A)
)

/** 历史卡片右侧：象限名 + 坐标 + 四象限色块，对齐 CoordMiniBadge.tsx */
@Composable
fun CoordMiniBadge(
    coordX: Int,
    coordY: Int,
    modifier: Modifier = Modifier
) {
    val quadrantId = assignQuadrant(coordX, coordY)
    val label = getQuadrantLabel(coordX, coordY)
    val coordStr = formatCoord(coordX, coordY)
    val intensity = coordIntensity(coordY)
    val activeColor = QUADRANT_COLORS[quadrantId] ?: MindBodyColors.PrimaryIndigo

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MindBodyColors.StatCellBg)
            .border(1.dp, MindBodyColors.StatCellBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                QuadCell("tl", quadrantId, QUADRANT_COLORS["tl"]!!)
                QuadCell("bl", quadrantId, QUADRANT_COLORS["bl"]!!)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                QuadCell("tr", quadrantId, QUADRANT_COLORS["tr"]!!)
                QuadCell("br", quadrantId, QUADRANT_COLORS["br"]!!)
            }
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = activeColor)
        Text(coordStr, fontSize = 10.sp, color = MindBodyColors.OnBackgroundSecondary)
        Text("${intensity}分", fontSize = 10.sp, color = MindBodyColors.OnBackgroundSecondary)
    }
}

@Composable
private fun QuadCell(id: String, activeId: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (id == activeId) color.copy(alpha = 0.85f)
                else color.copy(alpha = 0.2f)
            )
    )
}
