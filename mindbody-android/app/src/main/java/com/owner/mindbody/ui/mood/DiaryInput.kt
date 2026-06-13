package com.owner.mindbody.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.ui.theme.MindBodyColors

/**
 * 日记多行输入框，支持 Enter 列表续号（对齐 emotion DiaryInput）。
 */
@Composable
fun DiaryInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 4,
    scrollable: Boolean = false,
    autoFocus: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }

    val scrollState = rememberScrollState()
    val fieldModifier = modifier
        .fillMaxWidth()
        .then(
            if (scrollable) {
                Modifier
                    .height((minLines * 22 + 24).dp)
                    .verticalScroll(scrollState)
            } else {
                Modifier.heightIn(min = (minLines * 22).dp)
            }
        )
        .background(MindBodyColors.StatCellBg, shape)
        .border(1.dp, MindBodyColors.StatCellBorder, shape)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return@onPreviewKeyEvent false
            val selection = textFieldValue.selection
            val result = applyListContinuation(
                textFieldValue.text,
                selection.start,
                selection.end
            ) ?: return@onPreviewKeyEvent false
            textFieldValue = TextFieldValue(result.nextValue, TextRange(result.nextCursor))
            onValueChange(result.nextValue)
            true
        }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { updated ->
            textFieldValue = updated
            onValueChange(updated.text)
        },
        modifier = fieldModifier,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = MindBodyColors.OnBackground,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        cursorBrush = SolidColor(MindBodyColors.PrimaryIndigo),
        decorationBox = { inner ->
            if (textFieldValue.text.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MindBodyColors.OnBackgroundSecondary,
                    fontSize = 15.sp
                )
            }
            inner()
        }
    )
}
