package com.wabbo.healthconnectcoach

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.net.URI

internal data class MarkdownBlock(val kind: String, val text: String, val prefix: String = "", val level: Int = 0,val rows: List<List<String>> = emptyList())

/** Bounded Markdown subset. No HTML, scripts, remote images or model-controlled actions are executed. */
internal fun markdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>(); val paragraph = mutableListOf<String>(); val code = mutableListOf<String>(); var fence = false
    fun flush() { if(paragraph.isNotEmpty()) { blocks += MarkdownBlock("paragraph", paragraph.joinToString("\n")); paragraph.clear() } }
    val lines=text.take(32_000).replace("\r\n", "\n").lines()
    var index=0
    while(index<lines.size) {
        val line=lines[index++]
        if(!fence&&index<lines.size&&line.contains('|')) {
            val header=markdownTableCells(line);val separator=markdownTableCells(lines[index])
            if(header.size in 2..8&&separator.size==header.size&&separator.all {Regex(":?-{3,}:?").matches(it)}) {
                flush();index++
                val rows=mutableListOf(header)
                while(index<lines.size&&lines[index].isNotBlank()&&lines[index].contains('|')&&rows.size<51) {
                    val cells=markdownTableCells(lines[index++]);rows+=List(header.size) {cells.getOrElse(it) {""}}
                }
                blocks+=MarkdownBlock("table","",rows=rows)
                continue
            }
        }
        if(line.trimStart().startsWith("```")) { flush(); if(fence) { blocks += MarkdownBlock("code", code.joinToString("\n")); code.clear() }; fence = !fence; continue }
        if(fence) { code += line; continue }
        val heading = Regex("^(#{1,6})\\s+(.+)$").find(line)
        val list = Regex("^\\s*([-+*]|[0-9]{1,3}[.)])\\s+(.+)$").find(line)
        when {
            line.isBlank() -> flush()
            heading != null -> { flush(); blocks += MarkdownBlock("heading", heading.groupValues[2].removeSuffix(" #"), level = heading.groupValues[1].length) }
            list != null -> { flush(); blocks += MarkdownBlock("list", list.groupValues[2], if(list.groupValues[1].first().isDigit()) list.groupValues[1] else "•") }
            line.trimStart().startsWith("> ") -> { flush(); blocks += MarkdownBlock("quote", line.trimStart().removePrefix("> ")) }
            Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$").matches(line) -> { flush(); blocks += MarkdownBlock("divider", "") }
            else -> paragraph += line
        }
    }
    flush(); if(code.isNotEmpty()) blocks += MarkdownBlock("code", code.joinToString("\n"))
    return blocks
}

internal fun markdownTableCells(line: String): List<String> = line.trim().replace("\\|","\u0000")
    .removePrefix("|").removeSuffix("|").split('|').map {it.replace('\u0000','|').trim()}

internal fun safeChatLink(value: String): Boolean = runCatching {
    val uri = URI(value); uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

internal fun markdownInline(text: String): AnnotatedString = buildAnnotatedString {
    fun appendInline(value: String, depth: Int) {
        if(depth > 8) { append(value); return }
        var i = 0
        while(i < value.length) {
            if(value[i] == '\\' && i + 1 < value.length) { append(value[i + 1]); i += 2; continue }
            val marker = when {
                value.startsWith("**", i) -> "**"; value.startsWith("__", i) -> "__"
                value[i] == '`' -> "`"; value[i] == '*' -> "*"; value[i] == '_' && (i == 0 || !value[i-1].isLetterOrDigit()) -> "_"
                else -> null
            }
            if(marker != null) {
                val end = value.indexOf(marker, i + marker.length)
                if(end > i + marker.length) {
                    val inner = value.substring(i + marker.length, end)
                    val style = when(marker) {
                        "**", "__" -> SpanStyle(fontWeight = FontWeight.Bold)
                        "`" -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFEEF0E8))
                        else -> SpanStyle(fontStyle = FontStyle.Italic)
                    }
                    withStyle(style) { if(marker == "`") append(inner) else appendInline(inner, depth + 1) }
                    i = end + marker.length; continue
                }
            }
            if(value[i] == '[') {
                val mid = value.indexOf("](", i + 1); val end = if(mid >= 0) value.indexOf(')', mid + 2) else -1
                if(end > mid && mid > i) {
                    val url = value.substring(mid + 2, end).trim()
                    if(safeChatLink(url)) {
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Color(0xFF22835D), textDecoration = TextDecoration.Underline)))) { appendInline(value.substring(i + 1, mid), depth + 1) }
                        i = end + 1; continue
                    }
                }
            }
            append(value[i]); i++
        }
    }
    appendInline(text, 0)
}

@Composable internal fun CoachMarkdown(text: String) {
    val blocks = remember(text) { markdownBlocks(text) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                val styled = remember(block.text) { markdownInline(block.text) }
                when(block.kind) {
                    "table" -> Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        block.rows.forEachIndexed {rowIndex,cells->
                            Row(Modifier.background(if(rowIndex==0) Color(0xFFE8EFDF) else if(rowIndex%2==0) Color(0xFFF5F7F0) else Color.White)) {
                                cells.forEach {cell->Text(markdownInline(cell),modifier=Modifier.width(155.dp).padding(12.dp),
                                    style=MaterialTheme.typography.bodyMedium,fontWeight=if(rowIndex==0) FontWeight.Bold else FontWeight.Normal)}
                            }
                        }
                    }
                    "heading" -> Text(styled, style = if(block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    "list" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(block.prefix, fontWeight = FontWeight.Bold); Text(styled, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)) }
                    "quote" -> Text(styled, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.background(Color(0xFFF0F3EA)).padding(14.dp))
                    "code" -> Box(Modifier.fillMaxWidth().background(Color(0xFFF0F2EB)).horizontalScroll(rememberScrollState()).padding(14.dp)) { Text(block.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
                    "divider" -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE1E7DC)))
                    else -> Text(styled, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
