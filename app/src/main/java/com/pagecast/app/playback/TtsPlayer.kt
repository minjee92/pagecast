package com.pagecast.app.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pagecast.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * [NarrationController]를 Media3 [Player]로 보이게 하는 어댑터.
 * MediaSession이 이 Player를 통해 잠금화면·알림·블루투스 버튼 명령을 전달한다.
 *
 * - 책 한 권을 MediaItem 하나로 표현하고, 이전·다음 버튼은 seekCommand를 보고 문장 이동으로 바꾼다.
 *   (media3 1.11.1 소스 확인: 항목이 하나여도 seekToNext/Previous는 해당 seekCommand로 handleSeek를 호출한다.)
 * - 잠금화면·알림에는 책 제목과 "문장 n / N"만 노출하고 본문 문장은 넣지 않는다.
 * - 오디오 포커스와 헤드폰 분리(noisy)를 직접 처리한다(SimpleBasePlayer는 처리하지 않음).
 */
@UnstableApi
class TtsPlayer(
    context: Context,
    private val narration: NarrationController,
    scope: CoroutineScope,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val stateJob: Job

    private var resumeOnFocusGain = false
    private var hasAudioFocus = false
    private var noisyReceiverRegistered = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        // 낭독은 소리를 줄여(duck) 들으면 놓치므로, duck 요청도 일시정지로 처리한다.
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(::onAudioFocusChange)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeOnFocusGain = false
                narration.pause()
            }
        }
    }

    init {
        stateJob = scope.launch {
            narration.state.collect { state ->
                updateAudioSideEffects(state.isPlaying)
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val narrationState = narration.state.value
        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaybackParameters(PlaybackParameters(narrationState.speed))

        if (!narrationState.hasContent) {
            return builder
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(narrationState.bookTitle)
            .setArtist(
                appContext.getString(
                    R.string.reader_position,
                    narrationState.currentIndex + 1,
                    narrationState.segmentCount,
                ),
            )
            .build()
        val item = MediaItemData.Builder("book-${narrationState.bookId}")
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaId("book-${narrationState.bookId}")
                    .setMediaMetadata(metadata)
                    .build(),
            )
            .setMediaMetadata(metadata)
            .build()

        return builder
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(if (narrationState.finished) Player.STATE_ENDED else Player.STATE_READY)
            .setPlayWhenReady(narrationState.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            if (requestAudioFocus()) narration.play()
        } else {
            resumeOnFocusGain = false
            narration.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT -> narration.next()
            Player.COMMAND_SEEK_TO_PREVIOUS -> narration.previous()
            // 그 밖의 seek 명령은 AVAILABLE_COMMANDS에 없어 호출되지 않는다.
            else -> Unit
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        narration.setSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        resumeOnFocusGain = false
        narration.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stateJob.cancel()
        updateAudioSideEffects(isPlaying = false)
        // NarrationController는 앱 스코프 객체이므로 여기서 해제하지 않는다.
        return Futures.immediateVoidFuture()
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        hasAudioFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    narration.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 전화·내비 안내 등 잠깐의 중단: 끝나면 이어서 읽는다.
                resumeOnFocusGain = narration.state.value.isPlaying
                narration.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 다른 앱이 재생을 시작함: 자동으로 재개하지 않는다.
                hasAudioFocus = false
                resumeOnFocusGain = false
                narration.pause()
            }
        }
    }

    /** 재생 중일 때만 헤드폰 분리 브로드캐스트를 받고, 완전히 멈추면 포커스를 돌려준다. */
    private fun updateAudioSideEffects(isPlaying: Boolean) {
        if (isPlaying && !noisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                appContext,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        } else if (!isPlaying && noisyReceiverRegistered) {
            appContext.unregisterReceiver(noisyReceiver)
            noisyReceiverRegistered = false
        }
        if (!isPlaying && !resumeOnFocusGain && hasAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasAudioFocus = false
        }
    }

    companion object {
        /** 이 Player가 지원하는 명령. 외부 컨트롤러에는 [PlaybackService]에서 더 좁혀 준다. */
        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
