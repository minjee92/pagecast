package com.pagecast.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pagecast.app.util.AppLog
import java.util.concurrent.ExecutionException

/**
 * 화면에서 [PlaybackService]의 세션에 명령을 보내는 MediaController 연결.
 * 연결은 비동기이므로 연결 전에 들어온 명령은 연결이 끝난 뒤 순서대로 실행된다.
 * 화면 수명에 맞춰 [release]를 호출해야 한다.
 */
@OptIn(UnstableApi::class) // PlaybackService(SimpleBasePlayer 기반)를 가리키는 세션 토큰 생성
class PlaybackClient(context: Context) {

    private val appContext = context.applicationContext
    private val future: ListenableFuture<MediaController> = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()

    fun send(command: (MediaController) -> Unit) {
        future.addListener(
            {
                val controller = try {
                    future.get()
                } catch (e: ExecutionException) {
                    AppLog.w("media_controller_connect_failed")
                    null
                } catch (e: java.util.concurrent.CancellationException) {
                    null
                }
                controller?.let(command)
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun release() {
        MediaController.releaseFuture(future)
    }
}
