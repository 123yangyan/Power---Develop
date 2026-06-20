package com.owner.mindbody.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import kotlin.math.roundToInt

/**
 * 价值感 × 耗能 四象限点选网格（已退役：记录主路径改角色化 UI，仅保留供历史 coord 只读参考）。
 */
@Deprecated(
    message = "记录主路径已退役；新记录请使用 EmotionRole / ActorStage。历史无 roleId 条目仍通过 CoordMiniBadge 展示 coord。",
    replaceWith = ReplaceWith(
        "ActorStage(onStageRoleTap = { role -> /* handle role selection */ })",
        "com.owner.mindbody.ui.mood.ActorStage"
    )
)
@Composable
fun ValueEnergyGrid(
    coordX: Int,
    coordY: Int,
    hasSelection: Boolean,
    onPick: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (hasSelection) getQuadrantLabel(coordX, coordY) else ""
    val coordStr = if (hasSelection) formatCoord(coordX, coordY) else ""

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .fillMaxHeight(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("高耗能", fontSize = 11.sp, color = MindBodyColors.OnBackgroundSecondary)
                Text("轻松", fontSize = 11.sp, color = MindBodyColors.OnBackgroundSecondary)
            }

            Column(modifier = Modifier.weight(1f)) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MindBodyShapes.PremiumCard)
                        .border(1.dp, MindBodyColors.CardBorder, MindBodyShapes.PremiumCard)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                // relX 0→1 映射 -4~+4；relY 顶部→底部 映射 +4~-4
                                val relX = offset.x / size.width
                                val relY = offset.y / size.height
                                val x = ((relX - 0.5) * 8).roundToInt().coerceIn(-4, 4)
                                val y = ((0.5 - relY) * 8).roundToInt().coerceIn(-4, 4)
                                onPick(x, y)
                            }
                        }
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f)) {
                            QuadrantCell("内耗陷阱", Color(0xFFFEF3C7), Modifier.weight(1f).fillMaxWidth())
                            QuadrantCell("机械区", Color(0xFFE0E7FF), Modifier.weight(1f).fillMaxWidth())
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            QuadrantCell("攻坚区", Color(0xFFFEE2E2), Modifier.weight(1f).fillMaxWidth())
                            QuadrantCell("心流区", Color(0xFFD1FAE5), Modifier.weight(1f).fillMaxWidth())
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .size(height = 1.dp, width = maxWidth)
                            .background(MindBodyColors.OnBackgroundSecondary.copy(alpha = 0.3f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(Alignment.Center)
                            .size(width = 1.dp, height = maxHeight)
                            .background(MindBodyColors.OnBackgroundSecondary.copy(alpha = 0.3f))
                    )

                    if (hasSelection) {
                        val dotLeftFraction = (coordX + 4) / 8f
                        val dotTopFraction = (4 - coordY) / 8f
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .offset(
                                    x = maxWidth * dotLeftFraction - 8.dp,
                                    y = maxHeight * dotTopFraction - 8.dp
                                )
                                .clip(CircleShape)
                                .background(MindBodyColors.PrimaryIndigo)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                ) {
                    Text("← 排斥", fontSize = 11.sp, color = MindBodyColors.OnBackgroundSecondary)
                    Text("愿意 →", fontSize = 11.sp, color = MindBodyColors.OnBackgroundSecondary)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasSelection) {
                Text(
                    text = "$label  $coordStr",
                    color = MindBodyColors.PrimaryIndigo,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MindBodyColors.OnBackgroundSecondary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
