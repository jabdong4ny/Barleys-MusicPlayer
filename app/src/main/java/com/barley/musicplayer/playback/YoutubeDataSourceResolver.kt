package com.barley.musicplayer.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.barley.musicplayer.data.youtube.YoutubeExtractorService
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExoPlayer가 재생을 시작하기 바로 전, [SCHEME]로 시작하는 placeholder URI(videoId)를
 * 실제 오디오 스트림 URL로 바꿔준다. 곡마다 재생 시점에 늦게(lazy) 추출해야
 * NewPipeExtractor가 돌려주는 스트림 URL의 만료 전에 쓸 수 있다.
 */
@UnstableApi
@Singleton
class YoutubeDataSourceResolver @Inject constructor(
    private val youtubeExtractorService: YoutubeExtractorService,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME) return dataSpec

        val videoId = uri.schemeSpecificPart
        Log.d(TAG, "resolveDataSpec called for videoId=$videoId")
        val streamUrl = runBlocking { youtubeExtractorService.resolveAudioStreamUrl(videoId) }
        return dataSpec.withUri(Uri.parse(streamUrl))
    }

    companion object {
        private const val TAG = "YoutubeDataSourceResolver"
        const val SCHEME = "ytmusic"

        fun placeholderUri(videoId: String): Uri = Uri.parse("$SCHEME:$videoId")
    }
}
