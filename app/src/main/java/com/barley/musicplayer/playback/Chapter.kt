package com.barley.musicplayer.playback

/** 한 영상(재생목록을 합쳐놓은 긴 믹스 영상 등) 안에서 특정 곡이 시작되는 지점. */
data class Chapter(
    val index: Int,
    val title: String,
    val startSeconds: Long,
)

data class ParsedDescription(
    val preamble: String,
    val chapters: List<Chapter>,
)

// "[00:03:48] 02 계절의 끝", "[03:48] 계절의 끝", "0:08 할로윈 초콜릿 vs 젤리", "2:34 - 제목" 같은,
// 유튜브 설명에서 흔히 쓰이는 타임스탬프 줄 형식. 대괄호는 있어도 없어도 되고, HTML의 &nbsp;가
// 줄바꿈 없는 공백( )으로 바뀌어 들어오는 경우도 있어서 공백으로 같이 취급한다.
private val chapterLineRegex =
    Regex("""^\[?(?:(\d{1,2}):)?(\d{1,2}):(\d{2})]?[\s ]*(?:[-–—|:.][\s ]*)?(.+)$""")

/**
 * 영상 설명에서 타임스탬프 목록(챕터)을 뽑아낸다. 긴 믹스 영상의 설명에는 보통
 * 챕터 목록 앞에 곡과 무관한 안내문(제목/해시태그 등)이 있어서, 그 부분은 [ParsedDescription.preamble]로 따로 둔다.
 * 타임스탬프가 하나뿐이면 목차가 아니라 우연히 섞인 시각 표기로 보고 챕터로 취급하지 않는다.
 */
fun parseDescriptionChapters(description: String): ParsedDescription {
    val lines = description.lines()
    val matches = lines.map { line -> chapterLineRegex.find(line.trim()) }
    val firstChapterLine = matches.indexOfFirst { it != null }
    if (firstChapterLine == -1) {
        return ParsedDescription(preamble = description.trim(), chapters = emptyList())
    }

    val chapters = matches
        .mapNotNull { match ->
            match ?: return@mapNotNull null
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
            val title = match.groupValues[4].trim()
            (hours * 3600 + minutes * 60 + seconds) to title
        }
        .mapIndexed { index, (startSeconds, title) -> Chapter(index, title, startSeconds) }

    if (chapters.size < 2) {
        return ParsedDescription(preamble = description.trim(), chapters = emptyList())
    }

    val preamble = lines.subList(0, firstChapterLine).joinToString("\n").trim()
    return ParsedDescription(preamble = preamble, chapters = chapters)
}
