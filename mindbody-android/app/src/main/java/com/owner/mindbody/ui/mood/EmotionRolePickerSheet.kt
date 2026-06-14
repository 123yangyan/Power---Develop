package com.owner.mindbody.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors

/**
 * 记录页选角色 Bottom Sheet：15 宫格，选中后回调并关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionRolePickerSheet(
    visible: Boolean,
    selectedRoleId: String?,
    onSelectRole: (EmotionRole) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        MindBodyColors.OnBackgroundSecondary.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp)
                    )
            )
        },
        containerColor = MindBodyColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "选择此刻的角色",
                style = CardTitle,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "点选角色，保存时一并入库",
                fontSize = 12.sp,
                color = MindBodyColors.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            EmotionRoleGrid(
                roles = EmotionRoles.all,
                selectedRoleId = selectedRoleId,
                onSelectRole = { role ->
                    onSelectRole(role)
                    onDismiss()
                },
                modifier = Modifier.height(480.dp)
            )
        }
    }
}
