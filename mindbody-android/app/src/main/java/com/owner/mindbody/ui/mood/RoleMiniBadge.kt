package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors

/** 历史卡片右侧：角色微缩图标 + 名称；无 roleId 时回退到 CoordMiniBadge */
@Composable
fun RoleMiniBadge(
    roleId: String?,
    coordX: Int,
    coordY: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp
) {
    val role = EmotionRoles.findById(roleId)
    if (role != null) {
        Column(
            modifier = modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmotionRoleIcon(
                role = role,
                size = iconSize,
                selected = false,
                idleAnimation = false
            )
            Text(
                text = role.displayName,
                fontSize = 10.sp,
                color = MindBodyColors.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    } else {
        CoordMiniBadge(coordX = coordX, coordY = coordY, modifier = modifier)
    }
}
