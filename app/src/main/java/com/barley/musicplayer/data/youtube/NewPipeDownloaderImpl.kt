package com.barley.musicplayer.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import okhttp3.Request as OkHttpRequest

/**
 * NewPipeExtractor가 내부적으로 쓰는 HTTP 클라이언트를 OkHttp로 구현한 것.
 * NewPipeExtractor 자체는 HTTP 통신 기능이 없고, 앱이 Downloader를 제공해야 동작한다.
 */
class NewPipeDownloaderImpl(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val dataToSend = request.dataToSend()
        val requestBody = dataToSend?.toRequestBody()

        val requestBuilder = OkHttpRequest.Builder()
            .method(request.httpMethod(), requestBody)
            .url(request.url())
            .header("User-Agent", USER_AGENT)

        for ((headerName, headerValues) in request.headers()) {
            if (headerValues.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValues.forEach { requestBuilder.addHeader(headerName, it) }
            } else if (headerValues.size == 1) {
                requestBuilder.header(headerName, headerValues[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCAPTCHA challenge requested", request.url())
        }

        val responseBody = response.body?.string()
        val latestUrl = response.request.url.toString()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            latestUrl,
        )
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
