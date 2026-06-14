package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 全部角色的网格选择区（默认 3 列） */
@Composable
fun EmotionRoleGrid(
    roles: List<EmotionRole>,
    selectedRoleId: String?,
    onSelectRole: (EmotionRole) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(roles, key = { it.id }) { role ->
            EmotionRoleIcon(
                role = role,
                size = 72.dp,
                selected = role.id == selectedRoleId,
                idleAnimation = false,
                onClick = { onSelectRole(role) }
            )
        }
    }
}
