package com.owner.mindbody.ui.mood

/**
 * 日记 Enter 时有序/无序列表自动续号，对齐 emotion diaryListContinue.ts。
 */
data class ListContinueResult(
    val nextValue: String,
    val nextCursor: Int
)

private data class LineBounds(
    val lineStart: Int,
    val lineEnd: Int,
    val line: String
)

private fun getCurrentLineBounds(value: String, cursor: Int): LineBounds {
    val before = value.substring(0, cursor.coerceIn(0, value.length))
    val lineStart = before.lastIndexOf('\n') + 1
    val afterLine = value.substring(lineStart)
    val nl = afterLine.indexOf('\n')
    val lineEnd = if (nl == -1) value.length else lineStart + nl
    return LineBounds(lineStart, lineEnd, value.substring(lineStart, lineEnd))
}

private val ORDERED_RE = Regex("""^(\s*)(\d+)\.\s(.*)$""")
private val UNORDERED_RE = Regex("""^(\s*)-\s(.*)$""")

/**
 * Enter 时应用列表续号；无需续号时返回 null。
 * - 有序列表：下一行 `n+1. `
 * - 无序列表：下一行 `- `
 * - 空列表项：删除当前行前缀，普通换行（退出列表）
 */
fun applyListContinuation(
    value: String,
    selectionStart: Int,
    selectionEnd: Int
): ListContinueResult? {
    if (selectionStart != selectionEnd) return null

    val bounds = getCurrentLineBounds(value, selectionStart)
    val ordered = ORDERED_RE.matchEntire(bounds.line)
    if (ordered != null) {
        val indent = ordered.groupValues[1]
        val num = ordered.groupValues[2].toIntOrNull() ?: return null
        val content = ordered.groupValues[3]
        if (content.trim().isEmpty()) {
            val nextValue = value.substring(0, bounds.lineStart) + value.substring(bounds.lineEnd)
            return ListContinueResult(nextValue, bounds.lineStart)
        }
        val insert = "\n${indent}${num + 1}. "
        val nextValue = value.substring(0, selectionStart) + insert + value.substring(selectionEnd)
        return ListContinueResult(nextValue, selectionStart + insert.length)
    }

    val unordered = UNORDERED_RE.matchEntire(bounds.line)
    if (unordered != null) {
        val indent = unordered.groupValues[1]
        val content = unordered.groupValues[2]
        if (content.trim().isEmpty()) {
            val nextValue = value.substring(0, bounds.lineStart) + value.substring(bounds.lineEnd)
            return ListContinueResult(nextValue, bounds.lineStart)
        }
        val insert = "\n${indent}- "
        val nextValue = value.substring(0, selectionStart) + insert + value.substring(selectionEnd)
        return ListContinueResult(nextValue, selectionStart + insert.length)
    }

    return null
}
