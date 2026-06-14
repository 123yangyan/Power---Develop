package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors

/** 角色选择区布局模式 */
enum class RoleSelectorLayout {
    /** 编辑弹窗：胶囊工具栏 */
    EditModal
}

/**
 * 编辑弹窗角色选择（记录页主路径已改用 ActorStage + 场景 B 胶囊栏）。
 */
@Composable
fun KeyboardAwareRoleSelector(
    selectedRoleId: String?,
    onSelectRole: (EmotionRole) -> Unit,
    modifier: Modifier = Modifier,
    layout: RoleSelectorLayout = RoleSelectorLayout.EditModal
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "选择此刻的角色",
            style = CardTitle.copy(fontSize = 14.sp),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        EmotionCapsuleToolbar(
            roles = EmotionRoles.all,
            selectedRoleId = selectedRoleId,
            onSelectRole = onSelectRole,
            showExpandAffordance = false
        )
        selectedRoleId?.let { id ->
            EmotionRoles.findById(id)?.let { role ->
                Text(
                    text = role.displayName,
                    fontSize = 12.sp,
                    color = MindBodyColors.PrimaryIndigo,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
