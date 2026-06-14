package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 首发点将台：3×3 网格展示皮克斯 9 人（或任意角色列表）。
 */
@Composable
fun PriorityDock(
    roles: List<EmotionRole>,
    selectedRoleId: String?,
    onSelectRole: (EmotionRole) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    iconSize: Dp = 64.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        roles.chunked(columns).forEach { rowRoles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowRoles.forEach { role ->
                    EmotionRoleIcon(
                        role = role,
                        size = iconSize,
                        selected = role.id == selectedRoleId,
                        idleAnimation = true,
                        onClick = { onSelectRole(role) }
                    )
                }
            }
        }
    }
}
