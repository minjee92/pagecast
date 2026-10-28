package com.pagecast.app.playback

import com.pagecast.app.tts.QueueMode
import com.pagecast.app.tts.TtsEngine
import com.pagecast.app.tts.TtsEvent
import com.pagecast.app.tts.Utterance
import com.pagecast.app.tts.VoiceInfo
import kotlinx.coroutines.flow.MutableSharedFlow

/** 호출 기록만 남기고, 이벤트는 테스트가 직접 내보내는 가짜 엔진. */
class FakeTtsEngine(
    private val initResult: Result<Unit> = Result.success(Unit),
) : TtsEngine {
    override val id = "fake"
    override val events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 64)

    val spoken = mutableListOf<Pair<Utterance, QueueMode>>()
    var stopCount = 0
    var released = false

    override suspend fun initialize(): Result<Unit> = initResult

    override fun speak(utterance: Utterance, mode: QueueMode) {
        spoken += utterance to mode
    }

    override fun stop() {
        stopCount++
    }

    override fun availableVoices(): List<VoiceInfo> = emptyList()

    override fun release() {
        released = true
    }

    fun lastFlushed(): Utterance = spoken.last { it.second == QueueMode.FLUSH }.first

    suspend fun emit(event: TtsEvent) = events.emit(event)
}
