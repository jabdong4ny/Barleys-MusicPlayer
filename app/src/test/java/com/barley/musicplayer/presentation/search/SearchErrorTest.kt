package com.barley.musicplayer.presentation.search

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SearchErrorTest {

    @Test
    fun `호스트를 찾지 못하면 네트워크 오류다`() {
        val error = UnknownHostException("Unable to resolve host \"www.youtube.com\"")
        assertEquals(SearchError.Network, error.toSearchError())
    }

    @Test
    fun `다른 예외로 감싸진 시간 초과도 네트워크 오류다`() {
        val error = ExtractionException("search failed", SocketTimeoutException("timeout"))
        assertEquals(SearchError.Network, error.toSearchError())
    }

    @Test
    fun `봇 확인 요청은 네트워크 오류가 아니다`() {
        val error = ReCaptchaException("reCAPTCHA challenge requested", "https://www.youtube.com")
        assertEquals(SearchError.Unknown, error.toSearchError())
    }

    @Test
    fun `원인이 없는 일반 예외는 알 수 없는 오류다`() {
        assertEquals(SearchError.Unknown, IllegalStateException("parse").toSearchError())
    }
}
