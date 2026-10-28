package com.pagecast.app.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.pagecast.app.ui.theme.ReaderBodyStyle

/**
 * 문단 하나를 하나의 Text로 그린다. 문장마다 클릭 영역(LinkAnnotation)을 두어
 * 탭하면 그 문장부터 재생하고, 현재 문장은 배경색, 현재 단어는 굵게·강조색으로 표시한다.
 *
 * [activeSentence]/[wordRange]는 이 문단에 현재 문장이 있을 때만 값이 들어온다.
 * 그래서 하이라이트가 움직여도 다른 문단은 다시 그려지지 않는다.
 */
@Composable
fun ParagraphText(
    paragraph: ParagraphUi,
    activeSentence: Int?,
    wordRange: IntRange?,
    sentenceHighlight: Color,
    onSentenceTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val wordColor = MaterialTheme.colorScheme.primary
    val text = remember(paragraph, activeSentence, wordRange, sentenceHighlight, wordColor) {
        buildAnnotatedString {
            paragraph.sentences.forEachIndexed { i, sentence ->
                val global = paragraph.firstSentence + i
                if (i > 0) append(' ')
                withLink(LinkAnnotation.Clickable(tag = "s$global") { onSentenceTap(global) }) {
                    if (global == activeSentence) {
                        withStyle(SpanStyle(background = sentenceHighlight)) {
                            appendWithWord(sentence, wordRange, SpanStyle(fontWeight = FontWeight.Bold, color = wordColor, textDecoration = TextDecoration.Underline))
                        }
                    } else {
                        append(sentence)
                    }
                }
            }
        }
    }
    Text(
        text = text,
        style = ReaderBodyStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier,
        onTextLayout = onTextLayout,
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendWithWord(
    sentence: String,
    wordRange: IntRange?,
    wordStyle: SpanStyle,
) {
    val start = wordRange?.first?.coerceIn(0, sentence.length)
    val end = wordRange?.let { (it.last + 1).coerceIn(0, sentence.length) }
    if (start == null || end == null || start >= end) {
        append(sentence)
        return
    }
    append(sentence, 0, start)
    withStyle(wordStyle) { append(sentence, start, end) }
    append(sentence, end, sentence.length)
}
