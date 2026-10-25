package com.barley.musicplayer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.barley.musicplayer.data.youtube.NewPipeDownloaderImpl
import com.barley.musicplayer.playback.YoutubeDataSourceResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_search")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideRecentSearchDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.recentSearchDataStore

    // 앱 종료 시 서비스가 플레이어를 release()하므로 싱글톤으로 두면 재실행 때 해제된 플레이어를 받게 된다.
    // 서비스 인스턴스마다 새 플레이어를 만들도록 스코프를 두지 않는다.
    @Provides
    @UnstableApi
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        resolver: YoutubeDataSourceResolver,
    ): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloaderImpl.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(httpDataSourceFactory, resolver)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(resolvingDataSourceFactory)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                // 오디오 전용 스트림이 없어 오디오+비디오 스트림으로 재생할 때도 비디오는 디코딩하지 않는다.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
    }
}
