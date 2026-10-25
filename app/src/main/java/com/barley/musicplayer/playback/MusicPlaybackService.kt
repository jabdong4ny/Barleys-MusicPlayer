package com.barley.musicplayer.playback

import android.app.PendingIntent
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.barley.musicplayer.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var exoPlayer: ExoPlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // 백그라운드 재생 중에도 알림에서 바로 앱을 완전히 종료할 수 있도록 종료 버튼을 노출한다.
        val exitButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_exit)
            .setDisplayName(getString(R.string.app_exit))
            .setSessionCommand(EXIT_COMMAND)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(listOf(exitButton))
            .apply { buildOpenAppIntent()?.let(::setSessionActivity) }
            .build()
    }

    /** 알림을 누르면 런처 아이콘과 똑같이 기존 화면을 앞으로 가져오거나, 없으면 새로 연다. */
    private fun buildOpenAppIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /** 재생을 멈추고, 열려 있는 화면에 종료를 알린 뒤 서비스를 내린다. */
    private fun exitApp() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        mediaSession?.broadcastCustomCommand(EXIT_COMMAND, Bundle.EMPTY)
        pauseAllPlayersAndStopSelf()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(EXIT_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != ACTION_EXIT) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            exitApp()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val ACTION_EXIT = "com.barley.musicplayer.action.EXIT"
        val EXIT_COMMAND = SessionCommand(ACTION_EXIT, Bundle.EMPTY)
    }
}
