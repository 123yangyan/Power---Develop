package com.owner.mindbody.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors

/**
 * 情绪胶囊工具栏：键盘弹起时的单行横向滚动角色选择（PRD EmotionCapsuleToolbar）。
 */
@Composable
fun EmotionCapsuleToolbar(
    roles: List<EmotionRole>,
    selectedRoleId: String?,
    onSelectRole: (EmotionRole) -> Unit,
    modifier: Modifier = Modifier,
    showExpandAffordance: Boolean = false,
    onExpandClick: (() -> Unit)? = null
) {
    LazyRow(
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(roles, key = { it.id }) { role ->
            EmotionRoleIcon(
                role = role,
                size = 40.dp,
                selected = role.id == selectedRoleId,
                idleAnimation = false,
                onClick = { onSelectRole(role) }
            )
        }
        if (showExpandAffordance && onExpandClick != null) {
            item(key = "expand_affordance") {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MindBodyColors.StatCellBg)
                        .border(1.dp, MindBodyColors.CardBorder, CircleShape)
                        .clickable { onExpandClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "➕",
                        fontSize = 16.sp,
                        color = MindBodyColors.PrimaryIndigo
                    )
                }
            }
        }
    }
}
