package com.pagecast.app.playback

import com.pagecast.app.domain.model.Segment
import com.pagecast.app.tts.QueueMode
import com.pagecast.app.tts.TtsEngine
import com.pagecast.app.tts.TtsEvent
import com.pagecast.app.tts.TtsInitException
import com.pagecast.app.tts.Utterance
import com.pagecast.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 문장 단위 낭독 상태 머신. Android 의존성이 없어 가짜 엔진으로 단위 테스트한다.
 *
 * 모든 public 메서드와 이벤트 처리는 [scope]의 디스패처(앱에서는 Main) 위에서 실행된다고 가정한다.
 *
 * 동작 방식
 * - 현재 문장을 FLUSH로 말하고, 문장 사이 공백을 줄이기 위해 다음 문장 하나를 ADD로 미리 넣는다.
 * - 내장 TTS에는 일시정지가 없으므로 일시정지 = stop(), 재개 = 마지막 단어 시작 위치부터 다시 말하기.
 * - stop() 뒤에도 이전 발화의 이벤트가 늦게 올 수 있어, 발화 ID에 세대(generation)를 넣고
 *   현재 세대가 아닌 이벤트는 버린다.
 */
class NarrationController(
    private val engine: TtsEngine,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NarrationState())
    val state: StateFlow<NarrationState> = _state.asStateFlow()

    private var segments: List<Segment> = emptyList()
    private var generation = 0
    private var initJob: Job? = null
    private var playWhenReady = false
    private var wordEventSeenInCurrentSentence = false

    init {
        scope.launch { engine.events.collect(::onEngineEvent) }
    }

    fun initialize() {
        val status = _state.value.engineStatus
        if (status == EngineStatus.READY || status == EngineStatus.INITIALIZING) return
        _state.update { it.copy(engineStatus = EngineStatus.INITIALIZING) }
        initJob = scope.launch {
            val result = engine.initialize()
            val newStatus = result.fold(
                onSuccess = { EngineStatus.READY },
                onFailure = { error ->
                    when ((error as? TtsInitException)?.reason) {
                        TtsInitException.Reason.LANGUAGE_UNAVAILABLE -> EngineStatus.LANGUAGE_UNAVAILABLE
                        TtsInitException.Reason.ENGINE_UNAVAILABLE -> EngineStatus.ENGINE_UNAVAILABLE
                        null -> EngineStatus.ERROR
                    }
                },
            )
            _state.update { it.copy(engineStatus = newStatus) }
            if (newStatus == EngineStatus.READY && playWhenReady) {
                playWhenReady = false
                play()
            }
        }
    }

    /**
     * 책을 불러온다. 이미 같은 책이 로드되어 있으면 아무것도 하지 않는다(화면 재생성 시 재생 유지).
     */
    fun load(bookId: Long, title: String, newSegments: List<Segment>, startIndex: Int = 0, startOffset: Int = 0) {
        if (_state.value.bookId == bookId && segments.size == newSegments.size) return
        stopSpeaking()
        segments = newSegments
        val index = startIndex.coerceIn(0, (newSegments.size - 1).coerceAtLeast(0))
        val offset = newSegments.getOrNull(index)?.text?.let { startOffset.coerceIn(0, it.length) } ?: 0
        _state.update {
            it.copy(
                bookId = bookId,
                bookTitle = title,
                segmentCount = newSegments.size,
                currentIndex = index,
                wordRange = null,
                resumeOffset = offset,
                isPlaying = false,
                finished = false,
            )
        }
    }

    fun play() {
        val current = _state.value
        if (!current.hasContent || current.isPlaying) return
        if (current.engineStatus != EngineStatus.READY) {
            playWhenReady = true
            initialize()
            return
        }
        // 끝까지 읽은 뒤 다시 재생하면 처음부터.
        val (index, offset) = if (current.finished) 0 to 0 else current.currentIndex to current.resumeOffset
        _state.update { it.copy(isPlaying = true, finished = false, currentIndex = index, resumeOffset = offset) }
        speakFrom(index, offset)
    }

    fun pause() {
        playWhenReady = false
        val current = _state.value
        if (!current.isPlaying) return
        stopSpeaking()
        _state.update {
            it.copy(isPlaying = false, resumeOffset = it.wordRange?.first ?: it.resumeOffset)
        }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    /** [index] 문장으로 이동한다. 재생 중이면 그 문장부터 계속 읽는다. */
    fun seekTo(index: Int, autoPlay: Boolean = _state.value.isPlaying) {
        if (segments.isEmpty()) return
        val target = index.coerceIn(0, segments.lastIndex)
        val wasPlaying = _state.value.isPlaying
        if (wasPlaying) stopSpeaking()
        _state.update {
            it.copy(currentIndex = target, resumeOffset = 0, wordRange = null, finished = false, isPlaying = false)
        }
        if (autoPlay) play()
    }

    fun next() {
        val current = _state.value
        if (current.currentIndex < current.segmentCount - 1) seekTo(current.currentIndex + 1)
    }

    fun previous() {
        seekTo(_state.value.currentIndex - 1)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        val current = _state.value
        if (clamped == current.speed) return
        _state.update { it.copy(speed = clamped) }
        if (current.isPlaying) {
            // 새 속도는 다음 speak 호출부터 적용되므로 지금 단어부터 다시 말한다.
            val offset = current.wordRange?.first ?: current.resumeOffset
            stopSpeaking()
            _state.update { it.copy(resumeOffset = offset) }
            speakFrom(current.currentIndex, offset)
        }
    }

    fun release() {
        stopSpeaking()
        initJob?.cancel()
        engine.release()
    }

    private fun stopSpeaking() {
        generation++
        engine.stop()
    }

    private fun speakFrom(index: Int, offset: Int) {
        generation++
        wordEventSeenInCurrentSentence = false
        val text = segments[index].text
        // 남은 부분이 공백뿐이면(문장 끝에서 일시정지) 문장 처음부터 다시 읽는다.
        val start = offset.coerceIn(0, text.length).takeIf { text.substring(it).isNotBlank() } ?: 0
        engine.speak(utteranceFor(index, start, text.substring(start)), QueueMode.FLUSH)
        enqueueNext(index + 1)
    }

    private fun enqueueNext(index: Int) {
        val segment = segments.getOrNull(index) ?: return
        engine.speak(utteranceFor(index, 0, segment.text), QueueMode.ADD)
    }

    private fun utteranceFor(index: Int, offset: Int, text: String): Utterance {
        val segment = segments[index]
        return Utterance(
            id = UtteranceId(generation, index, offset).encode(),
            text = text,
            emotion = segment.emotion,
            rate = _state.value.speed,
        )
    }

    private fun onEngineEvent(event: TtsEvent) {
        val id = UtteranceId.decode(event.utteranceId) ?: return
        if (id.generation != generation || !_state.value.isPlaying) return

        when (event) {
            is TtsEvent.Started -> {
                if (id.index != _state.value.currentIndex) {
                    // 미리 넣어 둔 다음 문장이 시작됨
                    wordEventSeenInCurrentSentence = false
                    _state.update { it.copy(currentIndex = id.index, resumeOffset = id.offset, wordRange = null) }
                }
            }

            is TtsEvent.WordRange -> {
                if (id.index != _state.value.currentIndex) return
                wordEventSeenInCurrentSentence = true
                val start = event.start + id.offset
                val end = event.end + id.offset
                _state.update {
                    it.copy(
                        wordRange = start until end,
                        resumeOffset = start,
                        wordEventsSupported = true,
                    )
                }
            }

            is TtsEvent.Done -> onUtteranceDone(id.index)

            is TtsEvent.Error -> {
                AppLog.w("tts_utterance_error", "code" to event.code, "index" to id.index)
                stopSpeaking()
                _state.update { it.copy(isPlaying = false, engineStatus = EngineStatus.ERROR) }
            }
        }
    }

    private fun onUtteranceDone(index: Int) {
        if (!wordEventSeenInCurrentSentence && _state.value.wordEventsSupported == null) {
            _state.update { it.copy(wordEventsSupported = false) }
        }
        val nextIndex = index + 1
        if (nextIndex >= segments.size) {
            _state.update { it.copy(isPlaying = false, finished = true, wordRange = null, resumeOffset = 0) }
            return
        }
        wordEventSeenInCurrentSentence = false
        _state.update { it.copy(currentIndex = nextIndex, resumeOffset = 0, wordRange = null) }
        // 방금 시작된(또는 곧 시작될) 다음 문장 뒤에 그다음 문장을 미리 넣는다.
        enqueueNext(nextIndex + 1)
    }

    /** "세대:문장index:시작offset" 형식의 발화 ID. 본문은 들어가지 않는다. */
    internal data class UtteranceId(val generation: Int, val index: Int, val offset: Int) {
        fun encode(): String = "$generation:$index:$offset"

        companion object {
            fun decode(raw: String): UtteranceId? {
                val parts = raw.split(':')
                if (parts.size != 3) return null
                return UtteranceId(
                    generation = parts[0].toIntOrNull() ?: return null,
                    index = parts[1].toIntOrNull() ?: return null,
                    offset = parts[2].toIntOrNull() ?: return null,
                )
            }
        }
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
    }
}
