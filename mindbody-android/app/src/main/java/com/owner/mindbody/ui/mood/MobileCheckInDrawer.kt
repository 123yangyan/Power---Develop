package com.owner.mindbody.ui.mood



import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ModalBottomSheet

import androidx.compose.material3.SheetValue

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.owner.mindbody.ui.theme.CardTitle

import com.owner.mindbody.ui.theme.MindBodyColors

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch



/**

 * 场景 A：定时探查窗口 —— Bottom Sheet 极速盲操（PRD MobileCheckInDrawer）。

 */

@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun MobileCheckInDrawer(

    dateLabel: String,

    dailyIndexLabel: String?,

    priorityRoles: List<EmotionRole>,

    overflowRoles: List<EmotionRole>,

    saving: Boolean,

    onCaptureRole: (EmotionRole) -> Unit,

    onSnooze: () -> Unit,

    onOpenJournaling: () -> Unit,

    modifier: Modifier = Modifier,

    softDismiss: Boolean = false,

    onSoftDismiss: () -> Unit = {}

) {

    val haptic = LocalHapticFeedback.current

    var expandedAll by remember { mutableStateOf(false) }

    var capturingRoleId by remember { mutableStateOf<String?>(null) }



    // 跳过 PartiallyExpanded，避免只显示 dragHandle 空条

    val sheetState = rememberModalBottomSheetState(

        skipPartiallyExpanded = true,

        confirmValueChange = { it != SheetValue.Hidden }

    )

    val scope = rememberCoroutineScope()



    val onMaskClick = if (softDismiss) onSoftDismiss else onSnooze

    val onDismissRequest = if (softDismiss) onSoftDismiss else onSnooze



    Box(modifier = modifier.fillMaxSize()) {

        Box(

            modifier = Modifier

                .fillMaxSize()

                .background(Color.Black.copy(alpha = 0.4f))

                .clickable(

                    interactionSource = remember { MutableInteractionSource() },

                    indication = null

                ) { onMaskClick() }

        )



        ModalBottomSheet(

            onDismissRequest = onDismissRequest,

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

            containerColor = MindBodyColors.Background,

            modifier = Modifier.fillMaxWidth()

        ) {

            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .verticalScroll(rememberScrollState())

                    .padding(horizontal = 20.dp)

                    .padding(bottom = 24.dp),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                Text(text = dateLabel, style = CardTitle)

                dailyIndexLabel?.let {

                    Text(

                        text = it,

                        fontSize = 13.sp,

                        color = MindBodyColors.PrimaryIndigo,

                        modifier = Modifier.padding(top = 4.dp)

                    )

                }

                Text(

                    text = "到时间了，留意一下此刻",

                    fontSize = 13.sp,

                    color = MindBodyColors.OnBackgroundSecondary,

                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),

                    textAlign = TextAlign.Center

                )



                if (!expandedAll) {

                    PriorityDock(

                        roles = priorityRoles,

                        selectedRoleId = capturingRoleId,

                        onSelectRole = { role ->

                            if (saving) return@PriorityDock

                            capturingRoleId = role.id

                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            scope.launch {

                                delay(300)

                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                onCaptureRole(role)

                            }

                        }

                    )



                    Spacer(modifier = Modifier.height(20.dp))



                    TextButton(onClick = { expandedAll = true }) {

                        Text(

                            text = "扩展角色 · ${overflowRoles.size} 个",

                            color = MindBodyColors.OnBackgroundSecondary.copy(alpha = 0.7f),

                            fontSize = 13.sp

                        )

                    }

                } else {

                    EmotionRoleGrid(

                        roles = overflowRoles,

                        selectedRoleId = capturingRoleId,

                        onSelectRole = { role ->

                            if (saving) return@EmotionRoleGrid

                            capturingRoleId = role.id

                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            scope.launch {

                                delay(300)

                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                onCaptureRole(role)

                            }

                        },

                        modifier = Modifier.height(480.dp)

                    )

                    TextButton(onClick = { expandedAll = false }) {

                        Text("收起", fontSize = 13.sp)

                    }

                }



                Spacer(modifier = Modifier.height(8.dp))



                RowActions(

                    saving = saving,

                    softDismiss = softDismiss,

                    onSnooze = onSnooze,

                    onSoftDismiss = onSoftDismiss,

                    onOpenJournaling = onOpenJournaling

                )



                Text(

                    text = if (softDismiss) {

                        "关闭 / 点遮罩：返回继续写作；稍后：记录逃避并 20 分钟后再次提醒"

                    } else {

                        "点击遮罩 / 稍后：记录逃避并 20 分钟后再次提醒"

                    },

                    fontSize = 11.sp,

                    color = MindBodyColors.OnBackgroundSecondary,

                    modifier = Modifier.padding(top = 8.dp),

                    textAlign = TextAlign.Center

                )

            }

        }

    }

}



@Composable

private fun RowActions(

    saving: Boolean,

    softDismiss: Boolean,

    onSnooze: () -> Unit,

    onSoftDismiss: () -> Unit,

    onOpenJournaling: () -> Unit

) {

    androidx.compose.foundation.layout.Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly

    ) {

        if (softDismiss) {

            TextButton(onClick = onSoftDismiss, enabled = !saving) {

                Text("关闭")

            }

        }

        TextButton(onClick = onSnooze, enabled = !saving) {

            Text("稍后")

        }

        TextButton(onClick = onOpenJournaling, enabled = !saving) {

            Text("记一笔", color = MindBodyColors.PrimaryIndigo)

        }

    }

}

