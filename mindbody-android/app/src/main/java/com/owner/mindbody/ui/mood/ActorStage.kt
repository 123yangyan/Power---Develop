package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors

/**
 * 场景 A：2×2 角色舞台 —— 感性角色全面接管前台，坐标退居幕后。
 */
@Composable
fun ActorStage(
    roles: List<EmotionRole>,
    selectedRoleId: String?,
    onSelectRole: (EmotionRole) -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumCard(contentPadding = 16.dp, modifier = modifier) {
        Text(
            text = "你此刻更接近剧场里的哪位主演？",
            style = CardTitle.copy(fontSize = 15.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        PriorityDock(
            roles = roles,
            selectedRoleId = selectedRoleId,
            onSelectRole = onSelectRole,
            columns = 2,
            iconSize = 80.dp
        )
    }
}
