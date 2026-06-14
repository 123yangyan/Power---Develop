package com.owner.mindbody.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors

@Composable
fun MoodHistoryScreen(
    viewModel: MoodHistoryViewModel = viewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val editingEntry by viewModel.editingEntry.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    val allRows = remember(entries) { viewModel.buildRows(entries) }
    val totalPages = viewModel.totalPages(allRows.size)
    val pageRows = remember(allRows, currentPage) { viewModel.pageRows(allRows, currentPage) }
    val pageIds = pageRows.map { it.entry.id }
    val pagerButtons = remember(currentPage, totalPages) {
        viewModel.pagerButtons(currentPage, totalPages)
    }

    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }
    var deleteSelectedConfirm by remember { mutableStateOf(false) }
    var jumpInput by remember(currentPage) { mutableStateOf(currentPage.toString()) }

    LaunchedEffect(totalPages) {
        if (currentPage > totalPages) viewModel.goToPage(totalPages, totalPages)
    }

    LaunchedEffect(currentPage) {
        jumpInput = currentPage.toString()
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        SectionHeader(eyebrow = "心情", title = "历史记录")
        Text(
            text = "共 ${entries.size} 条",
            fontSize = 13.sp,
            color = MindBodyColors.OnBackgroundSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (toastMessage != null) {
            Text(
                text = toastMessage!!,
                color = MindBodyColors.Emerald,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无记录，去「记录」页写下第一条吧", color = MindBodyColors.OnBackgroundSecondary)
            }
            return
        }

        HistoryToolbar(
            pageIds = pageIds,
            selectedIds = selectedIds,
            onTogglePage = { viewModel.togglePageSelect(pageIds) },
            onClear = viewModel::clearSelection,
            onDeleteSelected = { deleteSelectedConfirm = true }
        )

        val listItems = remember(pageRows) {
            buildList {
                pageRows.forEachIndexed { index, row ->
                    val showDate = index == 0 || pageRows[index - 1].view.dateKey != row.view.dateKey
                    if (showDate) add(HistoryListItem.DateHeader(formatDiaryDateLabel(row.view.dateKey)))
                    add(HistoryListItem.Entry(row))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = listItems,
                key = { item ->
                    when (item) {
                        is HistoryListItem.DateHeader -> "date-${item.label}"
                        is HistoryListItem.Entry -> item.row.entry.id
                    }
                }
            ) { item ->
                when (item) {
                    is HistoryListItem.DateHeader -> {
                        Text(
                            text = item.label,
                            fontSize = 13.sp,
                            color = MindBodyColors.OnBackgroundSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    is HistoryListItem.Entry -> {
                        HistoryEntryCard(
                            row = item.row,
                            checked = item.row.entry.id in selectedIds,
                            onCheckedChange = { viewModel.toggleSelect(item.row.entry.id) },
                            onEdit = { viewModel.startEdit(item.row.entry) },
                            onDelete = { deleteConfirmId = item.row.entry.id }
                        )
                    }
                }
            }
        }

        HistoryPager(
            currentPage = currentPage,
            totalPages = totalPages,
            pagerButtons = pagerButtons,
            jumpInput = jumpInput,
            onJumpInputChange = { jumpInput = it.filter { c -> c.isDigit() }.ifEmpty { "" } },
            onPageChange = { viewModel.goToPage(it, totalPages) },
            onJumpSubmit = {
                jumpInput.toIntOrNull()?.let { viewModel.goToPage(it, totalPages) }
            }
        )
    }

    DeleteDialogs(
        deleteConfirmId = deleteConfirmId,
        onDismissSingle = { deleteConfirmId = null },
        onConfirmSingle = {
            viewModel.deleteEntry(deleteConfirmId!!)
            deleteConfirmId = null
        },
        deleteSelectedConfirm = deleteSelectedConfirm,
        selectedCount = selectedIds.size,
        onDismissBatch = { deleteSelectedConfirm = false },
        onConfirmBatch = {
            viewModel.deleteSelected()
            deleteSelectedConfirm = false
        }
    )

    if (editingEntry != null) {
        val recordViewModel: MoodRecordViewModel = viewModel()
        LaunchedEffect(editingEntry) { recordViewModel.loadForEdit(editingEntry!!) }
        Dialog(
            onDismissRequest = viewModel::closeEdit,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .background(MindBodyColors.Background, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "编辑记录 · ${
                            EmotionRoles.findById(editingEntry!!.roleId)?.displayName
                                ?: getQuadrantLabel(editingEntry!!.coordX, editingEntry!!.coordY)
                        }",
                        style = CardTitle,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    MoodEditForm(
                        viewModel = recordViewModel,
                        onSave = {
                            recordViewModel.updateEntry(editingEntry!!) { viewModel.onEntryUpdated() }
                        },
                        onCancel = viewModel::closeEdit
                    )
                }
            }
        }
    }
}

private sealed class HistoryListItem {
    data class DateHeader(val label: String) : HistoryListItem()
    data class Entry(val row: MoodHistoryListRow) : HistoryListItem()
}

@Composable
private fun HistoryToolbar(
    pageIds: List<Long>,
    selectedIds: Set<Long>,
    onTogglePage: () -> Unit,
    onClear: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val pageAllSelected = pageIds.isNotEmpty() && pageIds.all { it in selectedIds }
    val pageSomeSelected = pageIds.any { it in selectedIds }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = pageAllSelected,
            onCheckedChange = { onTogglePage() }
        )
        Text("本页全选", fontSize = 13.sp)
        if (pageSomeSelected && !pageAllSelected) {
            Text("(部分)", fontSize = 11.sp, color = MindBodyColors.OnBackgroundSecondary)
        }
        Text("已选 ${selectedIds.size}", fontSize = 13.sp, color = MindBodyColors.OnBackgroundSecondary)
        TextButton(onClick = onClear, enabled = selectedIds.isNotEmpty()) { Text("清除") }
        TextButton(onClick = onDeleteSelected, enabled = selectedIds.isNotEmpty()) {
            Text("删除所选", color = MindBodyColors.HeartRed)
        }
    }
}

@Composable
private fun HistoryEntryCard(
    row: MoodHistoryListRow,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val view = row.view
    val cardTint = when {
        view.isAvoidance -> Color(0xFFFFF7ED)
        view.polarity == MoodPolarity.POSITIVE -> MindBodyColors.EmeraldSurface
        view.polarity == MoodPolarity.NEGATIVE -> Color(0xFFFFF1F2)
        else -> MindBodyColors.CardSurfaceSolid
    }
    val borderColor = when {
        checked -> MindBodyColors.PrimaryIndigo.copy(alpha = 0.4f)
        view.isAvoidance -> Color(0xFFF59E0B).copy(alpha = 0.4f)
        else -> MindBodyColors.CardBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardTint)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(view.time, style = CardTitle.copy(fontSize = 15.sp))
                        row.dailyIndex?.let { meta ->
                            Text(
                                text = formatHistoryDailyIndexShort(meta),
                                fontSize = 11.sp,
                                color = MindBodyColors.OnBackgroundSecondary
                            )
                        }
                    }
                    if (view.isAvoidance) {
                        Text(
                            text = "自动写入",
                            fontSize = 10.sp,
                            color = Color(0xFFB45309),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33F59E0B))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val diaryText = if (view.diaryBody.isBlank()) "（无日记）" else view.diaryBody
                        Text(diaryText, fontSize = 14.sp, lineHeight = 20.sp)
                        view.hrLabel?.let {
                            Text(
                                it,
                                fontSize = 12.sp,
                                color = MindBodyColors.OnBackgroundSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    RoleMiniBadge(
                        roleId = view.roleId,
                        coordX = view.coordX,
                        coordY = view.coordY
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("编辑", color = MindBodyColors.PrimaryIndigo, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onEdit))
                    Text("删除", color = MindBodyColors.HeartRed, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onDelete))
                }
            }
        }
    }
}

@Composable
private fun HistoryPager(
    currentPage: Int,
    totalPages: Int,
    pagerButtons: List<Int>,
    jumpInput: String,
    onJumpInputChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onJumpSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onPageChange(1) }, enabled = currentPage > 1) { Text("«") }
            TextButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 1) { Text("<") }
            pagerButtons.forEach { page ->
                TextButton(onClick = { onPageChange(page) }) {
                    Text(
                        text = page.toString(),
                        color = if (page == currentPage) MindBodyColors.PrimaryIndigo else MindBodyColors.OnBackground
                    )
                }
            }
            TextButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage < totalPages) { Text(">") }
            TextButton(onClick = { onPageChange(totalPages) }, enabled = currentPage < totalPages) { Text("»") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("第", fontSize = 13.sp)
            BasicTextField(
                value = jumpInput,
                onValueChange = onJumpInputChange,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(48.dp)
                    .border(1.dp, MindBodyColors.StatCellBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                singleLine = true
            )
            Text("/ $totalPages 页", fontSize = 13.sp)
            TextButton(onClick = onJumpSubmit) { Text("跳转") }
        }
    }
}

@Composable
private fun DeleteDialogs(
    deleteConfirmId: Long?,
    onDismissSingle: () -> Unit,
    onConfirmSingle: () -> Unit,
    deleteSelectedConfirm: Boolean,
    selectedCount: Int,
    onDismissBatch: () -> Unit,
    onConfirmBatch: () -> Unit
) {
    if (deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = onDismissSingle,
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定吗？") },
            confirmButton = {
                TextButton(onClick = onConfirmSingle) { Text("删除", color = MindBodyColors.HeartRed) }
            },
            dismissButton = { TextButton(onClick = onDismissSingle) { Text("取消") } }
        )
    }
    if (deleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = onDismissBatch,
            title = { Text("批量删除") },
            text = { Text("确定删除 $selectedCount 条记录？") },
            confirmButton = {
                TextButton(onClick = onConfirmBatch) { Text("删除", color = MindBodyColors.HeartRed) }
            },
            dismissButton = { TextButton(onClick = onDismissBatch) { Text("取消") } }
        )
    }
}
