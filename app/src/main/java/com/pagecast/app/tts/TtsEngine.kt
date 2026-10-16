package com.pagecast.app.tts

import com.pagecast.app.domain.model.Emotion
import kotlinx.coroutines.flow.Flow
import java.util.Locale

/**
 * 음성 합성 엔진 추상화.
 *
 * 1단계 구현체는 안드로이드 내장 TextToSpeech([com.pagecast.app.tts.android.AndroidTtsEngine])이고,
 * 4단계에서 클라우드 엔진(서버 함수가 오디오 + 단어 타이밍을 반환)으로 교체할 수 있다.
 * 호출자는 [events]의 utteranceId로 진행 상황을 추적한다.
 */
interface TtsEngine {

    /** 엔진 식별자. [com.pagecast.app.domain.model.VoiceCast.engineId]에 저장된다. */
    val id: String

    val events: Flow<TtsEvent>

    /** 엔진을 준비한다. 이미 준비됐으면 즉시 성공을 반환한다. */
    suspend fun initialize(): Result<Unit>

    fun speak(utterance: Utterance, mode: QueueMode)

    /** 재생 중인 발화와 대기열을 모두 버린다. */
    fun stop()

    fun availableVoices(): List<VoiceInfo>

    fun release()
}

enum class QueueMode {
    /** 대기열을 비우고 바로 말한다. */
    FLUSH,

    /** 대기열 끝에 추가한다. 문장 사이 공백을 줄이기 위해 다음 문장을 미리 넣을 때 쓴다. */
    ADD,
}

data class Utterance(
    val id: String,
    val text: String,
    val voice: VoiceSpec? = null,
    /** 내장 엔진은 무시한다. 감정 연기 가능한 클라우드 엔진이 사용한다. */
    val emotion: Emotion = Emotion.NEUTRAL,
    val rate: Float = 1f,
    val pitch: Float = 1f,
)

data class VoiceSpec(val voiceId: String)

data class VoiceInfo(
    val id: String,
    val locale: Locale,
    val requiresNetwork: Boolean,
)

sealed interface TtsEvent {
    val utteranceId: String

    data class Started(override val utteranceId: String) : TtsEvent

    /** 지금 말하는 단어의 범위. [start] 포함, [end] 미포함, 발화 텍스트 기준. */
    data class WordRange(override val utteranceId: String, val start: Int, val end: Int) : TtsEvent

    data class Done(override val utteranceId: String) : TtsEvent

    data class Error(override val utteranceId: String, val code: Int) : TtsEvent
}

class TtsInitException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { ENGINE_UNAVAILABLE, LANGUAGE_UNAVAILABLE }
}
