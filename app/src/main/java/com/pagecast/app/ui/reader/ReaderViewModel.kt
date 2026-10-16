package com.pagecast.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pagecast.app.domain.model.Segment
import com.pagecast.app.domain.repository.BookRepository
import com.pagecast.app.playback.NarrationController
import com.pagecast.app.playback.NarrationState
import com.pagecast.app.ui.navigation.ReaderRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val narration: NarrationController,
) : ViewModel() {

    private val bookId: Long = savedStateHandle.toRoute<ReaderRoute>().bookId

    private val _content = MutableStateFlow<ReaderContent>(ReaderContent.Loading)
    val content: StateFlow<ReaderContent> = _content.asStateFlow()

    val narrationState: StateFlow<NarrationState> = narration.state

    init {
        narration.initialize()
        viewModelScope.launch {
            val book = repository.getBookContent(bookId)
            if (book == null || book.segments.isEmpty()) {
                _content.value = ReaderContent.NotFound
                return@launch
            }
            val progress = repository.getProgress(bookId)
            val layout = withContext(Dispatchers.Default) { ReaderLayout.from(book.segments) }
            narration.load(
                bookId = bookId,
                title = book.book.title,
                newSegments = book.segments,
                startIndex = progress?.segmentIndex ?: 0,
                startOffset = progress?.charOffset ?: 0,
            )
            _content.value = ReaderContent.Ready(title = book.book.title, layout = layout)
        }
    }

    fun onPlayPause() = narration.togglePlayPause()

    fun onNext() = narration.next()

    fun onPrevious() = narration.previous()

    fun onSpeedChange(speed: Float) = narration.setSpeed(speed)

    fun onSentenceTap(index: Int) = narration.seekTo(index, autoPlay = true)

    override fun onCleared() {
        // 백그라운드 재생 서비스가 생기기 전까지는 화면을 떠나면 낭독을 멈춘다.
        narration.pause()
    }
}

sealed interface ReaderContent {
    data object Loading : ReaderContent
    data object NotFound : ReaderContent
    data class Ready(val title: String, val layout: ReaderLayout) : ReaderContent
}

/** 문장 하나가 화면의 어느 문단, 몇 번째 글자에 있는지. */
class ParagraphUi(
    val index: Int,
    val firstSentence: Int,
    val sentences: List<String>,
) {
    /** 문단 텍스트 안에서 각 문장의 시작 위치. 문장 사이는 공백 하나로 잇는다. */
    val sentenceStarts: IntArray = IntArray(sentences.size).also { starts ->
        var pos = 0
        sentences.forEachIndexed { i, s ->
            starts[i] = pos
            pos += s.length + 1
        }
    }
    val lastSentence: Int get() = firstSentence + sentences.size - 1

    operator fun contains(sentenceIndex: Int): Boolean = sentenceIndex in firstSentence..lastSentence
}

class ReaderLayout(
    val paragraphs: List<ParagraphUi>,
    private val sentenceToParagraph: IntArray,
) {
    fun paragraphOf(sentenceIndex: Int): Int =
        sentenceToParagraph.getOrElse(sentenceIndex) { 0 }

    companion object {
        fun from(segments: List<Segment>): ReaderLayout {
            val paragraphs = ArrayList<ParagraphUi>()
            val sentenceToParagraph = IntArray(segments.size)
            var start = 0
            while (start < segments.size) {
                val paragraphIndex = segments[start].paragraphIndex
                var end = start
                while (end < segments.size && segments[end].paragraphIndex == paragraphIndex) {
                    sentenceToParagraph[end] = paragraphs.size
                    end++
                }
                paragraphs += ParagraphUi(
                    index = paragraphs.size,
                    firstSentence = start,
                    sentences = segments.subList(start, end).map { it.text },
                )
                start = end
            }
            return ReaderLayout(paragraphs, sentenceToParagraph)
        }
    }
}
