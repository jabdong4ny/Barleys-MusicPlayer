package com.barley.musicplayer.presentation.search

import java.io.IOException

/** 검색 실패 원인. 예외 메시지를 그대로 보여주지 않고 사용자에게 맞는 안내 문구를 고르는 데 쓴다. */
enum class SearchError {
    /** 비행기 모드, 와이파이 끊김, 시간 초과 등으로 서버에 닿지 못한 경우 */
    Network,

    /** 그 밖의 실패(유튜브 응답 형식 변경, 봇 확인 요청 등) */
    Unknown,
}

/** NewPipe가 네트워크 예외를 다른 예외로 감싸서 던지기도 하므로 원인 체인 전체를 확인한다. */
fun Throwable.toSearchError(): SearchError =
    if (generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is IOException }) {
        SearchError.Network
    } else {
        SearchError.Unknown
    }

private const val MAX_CAUSE_DEPTH = 10
