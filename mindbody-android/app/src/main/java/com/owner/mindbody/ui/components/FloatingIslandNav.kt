package com.owner.mindbody.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.NavLabel

data class NavTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun FloatingIslandNav(
    tabs: List<NavTabItem>,
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .height(64.dp)
            .clip(MindBodyShapes.NavIsland)
            .background(MindBodyColors.NavBarSurface)
            .border(1.dp, MindBodyColors.NavBarBorder, MindBodyShapes.NavIsland)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 26.dp)
                            .clip(MindBodyShapes.NavPill)
                            .background(
                                if (selected) MindBodyColors.PrimaryIndigoLight
                                else MindBodyColors.Background.copy(alpha = 0f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MindBodyColors.PrimaryIndigo
                            else MindBodyColors.OnBackgroundSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = tab.label,
                        style = NavLabel.copy(
                            color = if (selected) MindBodyColors.PrimaryIndigo
                            else MindBodyColors.OnBackgroundSecondary
                        )
                    )
                }
            }
        }
    }
}

val DefaultNavTabs = listOf(
    NavTabItem("heart_rate",   "心率",  Icons.Default.Favorite),
    NavTabItem("physio_state", "状态",  Icons.Default.MonitorHeart),
    NavTabItem("mood_record",  "记录",  Icons.Default.Edit),
    NavTabItem("timeline",     "时间",  Icons.Default.Timeline),
    NavTabItem("device",       "设置",  Icons.Default.Settings)
)
