package com.pagecast.app.tts.android

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pagecast.app.tts.QueueMode
import com.pagecast.app.tts.TtsEngine
import com.pagecast.app.tts.TtsEvent
import com.pagecast.app.tts.TtsInitException
import com.pagecast.app.tts.Utterance
import com.pagecast.app.tts.VoiceInfo
import com.pagecast.app.util.AppLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 안드로이드 내장 TextToSpeech 구현체. 무료이고 API 키가 필요 없다.
 *
 * - Google TTS(`com.google.android.tts`)가 설치되어 있으면 우선 사용한다.
 *   단어 위치 콜백(`onRangeStart`)을 지원하는 것이 확인된 엔진이기 때문이다.
 *   다른 엔진(예: Samsung TTS)은 지원 여부가 확실하지 않아, 호출자가 단어 이벤트 없이도 동작해야 한다.
 * - 콜백은 TTS 바인더 스레드에서 오므로 이벤트는 SharedFlow 버퍼로 넘긴다.
 */
class AndroidTtsEngine(
    context: Context,
    private val locale: Locale = Locale.US,
) : TtsEngine {

    override val id: String = ENGINE_ID

    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    @Volatile
    private var tts: TextToSpeech? = null

    private val _events = MutableSharedFlow<TtsEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<TtsEvent> = _events.asSharedFlow()

    override suspend fun initialize(): Result<Unit> = initMutex.withLock {
        if (tts != null) return@withLock Result.success(Unit)

        val enginePackage = preferredEnginePackage()
        val (instance, status) = suspendCancellableCoroutine { cont ->
            var created: TextToSpeech? = null
            created = TextToSpeech(appContext, { status ->
                if (cont.isActive) cont.resume(created to status)
            }, enginePackage)
            cont.invokeOnCancellation { created.shutdown() }
        }
        if (instance == null || status != TextToSpeech.SUCCESS) {
            instance?.shutdown()
            AppLog.w("tts_init_failed", "status" to status)
            return@withLock Result.failure(TtsInitException(TtsInitException.Reason.ENGINE_UNAVAILABLE))
        }

        val langResult = instance.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            instance.shutdown()
            AppLog.w("tts_language_unavailable", "result" to langResult)
            return@withLock Result.failure(TtsInitException(TtsInitException.Reason.LANGUAGE_UNAVAILABLE))
        }

        instance.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        instance.setOnUtteranceProgressListener(listener)
        tts = instance
        AppLog.d("tts_ready", "google" to if (enginePackage == GOOGLE_TTS_PACKAGE) 1 else 0)
        Result.success(Unit)
    }

    override fun speak(utterance: Utterance, mode: QueueMode) {
        val engine = tts
        if (engine == null) {
            _events.tryEmit(TtsEvent.Error(utterance.id, TextToSpeech.ERROR_NOT_INSTALLED_YET))
            return
        }
        // 속도·높낮이는 speak 호출 시점의 값이 그 발화에 적용된다.
        engine.setSpeechRate(utterance.rate)
        engine.setPitch(utterance.pitch)
        utterance.voice?.let { spec ->
            engine.voices?.firstOrNull { it.name == spec.voiceId }?.let { engine.voice = it }
        }
        val queueMode = when (mode) {
            QueueMode.FLUSH -> TextToSpeech.QUEUE_FLUSH
            QueueMode.ADD -> TextToSpeech.QUEUE_ADD
        }
        val result = engine.speak(utterance.text, queueMode, null, utterance.id)
        if (result != TextToSpeech.SUCCESS) {
            _events.tryEmit(TtsEvent.Error(utterance.id, result))
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun availableVoices(): List<VoiceInfo> =
        tts?.voices.orEmpty()
            .filter { it.locale.language == locale.language }
            .map { VoiceInfo(id = it.name, locale = it.locale, requiresNetwork = it.isNetworkConnectionRequired) }
            .sortedBy { it.id }

    override fun release() {
        tts?.shutdown()
        tts = null
    }

    private fun preferredEnginePackage(): String? {
        val services = appContext.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0,
        )
        return GOOGLE_TTS_PACKAGE.takeIf { pkg -> services.any { it.serviceInfo.packageName == pkg } }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            _events.tryEmit(TtsEvent.Started(utteranceId))
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            _events.tryEmit(TtsEvent.WordRange(utteranceId, start, end))
        }

        override fun onDone(utteranceId: String) {
            _events.tryEmit(TtsEvent.Done(utteranceId))
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            _events.tryEmit(TtsEvent.Error(utteranceId, TextToSpeech.ERROR))
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            _events.tryEmit(TtsEvent.Error(utteranceId, errorCode))
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            // stop()으로 끊긴 발화. 호출자가 이미 상태를 바꿨으므로 이벤트를 내보내지 않는다.
        }
    }

    companion object {
        const val ENGINE_ID = "android-builtin"
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    }
}
