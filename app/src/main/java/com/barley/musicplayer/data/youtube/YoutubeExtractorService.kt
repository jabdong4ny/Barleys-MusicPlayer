package com.barley.musicplayer.data.youtube

import android.net.Uri
import android.text.Html
import com.barley.musicplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube 공식 API가 아닌 NewPipeExtractor를 이용해 검색/스트림 URL을 추출한다.
 * (YouTube 이용약관상 비공식 방법이며, 추출 방식이 YouTube 쪽 변경으로 깨질 수 있다)
 */
@Singleton
class YoutubeExtractorService @Inject constructor(okHttpClient: OkHttpClient) {

    private val youtubeService = ServiceList.YouTube

    // videoId -> 추출된 스트림 URL/설명. 스트림 추출은 YouTube 플레이어 JS를 내려받아 해석(Rhino)까지 하는
    // 무거운 작업이라, ExoPlayer가 같은 곡을 다시 열 때마다(탐색/재시도/프리버퍼 등) 매번 새로 돌리면
    // CPU 부하가 커진다. 그래서 짧은 시간 동안은 캐시된 결과를 재사용한다.
    private val streamDetailsCache = ConcurrentHashMap<String, CachedStreamDetails>()

    init {
        NewPipe.init(NewPipeDownloaderImpl(okHttpClient))
    }

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = youtubeService.getSearchExtractor(query)
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            // 라이브 방송은 일반 오디오 스트림이 없어 이 앱(HLS 미지원)에서는 재생할 수 없다.
            // streamType 플래그가 검색 단계에서는 부정확할 때가 있어, 재생시간 0(=라이브/미정)도 함께 걸러낸다.
            .filter {
                (it.streamType == StreamType.VIDEO_STREAM || it.streamType == StreamType.AUDIO_STREAM) &&
                    it.duration > 0
            }
            .map { it.toTrack() }
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String = resolveStreamDetails(videoId).url

    /**
     * 현재 재생 중인 영상의 설명을 가져온다. [resolveAudioStreamUrl]과 같은 추출 결과를 캐시로
     * 공유하므로, 둘 중 하나가 먼저 호출되면 나머지는 추가 네트워크 요청 없이 캐시를 재사용한다.
     */
    suspend fun fetchDescription(videoId: String): String? = resolveStreamDetails(videoId).description

    private suspend fun resolveStreamDetails(videoId: String): StreamDetails {
        streamDetailsCache[videoId]?.let { cached ->
            if (!cached.isExpired()) return cached.details
        }
        return withContext(Dispatchers.IO) {
            // ResolvingDataSource.Resolver는 IOException만 ExoPlayer의 정상적인 재생 오류 처리로
            // 이어진다. 다른 예외(예: 라이브 방송처럼 오디오 스트림이 없는 경우 등)가 그대로 새어나가면
            // 플레이어가 복구되지 않는 상태로 멈출 수 있어서, 여기서 전부 IOException으로 감싼다.
            try {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val extractor = youtubeService.getStreamExtractor(watchUrl)
                extractor.fetchPage()
                val bestAudio = extractor.audioStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .maxByOrNull { it.averageBitrate }
                    ?: extractor.audioStreams.maxByOrNull { it.averageBitrate }
                // '아동용'으로 지정된 영상(주니토니 등)은 오디오 전용 스트림 없이 오디오+비디오가 합쳐진
                // 360p MP4 하나만 내려올 때가 있다. 그럴 땐 그 스트림을 쓰고, 비디오 트랙은 플레이어에서 꺼둔다.
                val fallbackMuxed = if (bestAudio == null) {
                    extractor.videoStreams
                        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                        .minByOrNull { it.height }
                } else {
                    null
                }
                val streamUrl = (bestAudio?.content ?: fallbackMuxed?.content)
                    ?: throw IOException("오디오 스트림을 찾을 수 없습니다 (라이브 방송일 수 있어요)")
                val description = extractor.description?.toPlainText()?.takeIf { it.isNotBlank() }
                val details = StreamDetails(streamUrl, description)
                streamDetailsCache[videoId] = CachedStreamDetails(details, System.currentTimeMillis())
                details
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("스트림을 가져오지 못했습니다: ${e.message}", e)
            }
        }
    }

    // 유튜브 영상 설명은 보통 HTML(<br>, <a> 등)로 내려와서, 화면에는 일반 텍스트로 바꿔서 보여준다.
    private fun Description.toPlainText(): String? {
        val raw = content ?: return null
        return if (type == Description.HTML) {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            raw.trim()
        }
    }

    private data class StreamDetails(val url: String, val description: String?)

    private data class CachedStreamDetails(val details: StreamDetails, val resolvedAtMillis: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() - resolvedAtMillis > CACHE_TTL_MILLIS
    }

    private fun StreamInfoItem.toTrack(): Track {
        val videoId = Uri.parse(url).getQueryParameter("v") ?: url.substringAfterLast("/")
        return Track(
            videoId = videoId,
            title = name,
            uploaderName = uploaderName ?: "",
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
            durationSeconds = duration.coerceAtLeast(0),
        )
    }

    companion object {
        private const val CACHE_TTL_MILLIS = 4 * 60 * 60 * 1000L // 4시간 (YouTube 스트림 URL 평균 유효 기간보다 짧게)
    }
}
