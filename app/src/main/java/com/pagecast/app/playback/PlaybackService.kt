package com.pagecast.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.pagecast.app.MainActivity
import com.pagecast.app.PagecastApp

/**
 * 백그라운드 재생과 잠금화면·알림·블루투스 컨트롤을 위한 Media3 세션 서비스.
 * 재생 중이면 Media3가 알아서 포그라운드 서비스(mediaPlayback)로 올리고 미디어 알림을 띄운다.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as PagecastApp).container
        val player = TtsPlayer(this, container.narrationController, container.applicationScope)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * 이 서비스는 Media3 규약상 exported 되어 있으므로 다른 앱도 연결할 수 있다.
     * 우리 앱과 미디어 알림이 아닌 컨트롤러(잠금화면, 블루투스, 다른 앱)에는
     * 재생 관련 명령만 허용하고 커스텀 세션 명령은 주지 않는다.
     */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val isOwnApp = controller.packageName == packageName || session.isMediaNotificationController(controller)
            if (isOwnApp) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(EXTERNAL_PLAYER_COMMANDS)
                .setAvailableSessionCommands(SessionCommands.EMPTY)
                .build()
        }
    }

    private companion object {
        val EXTERNAL_PLAYER_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
            )
            .build()
    }
}
