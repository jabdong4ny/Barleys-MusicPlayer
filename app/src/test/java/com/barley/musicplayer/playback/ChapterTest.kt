package com.barley.musicplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterTest {

    @Test
    fun `대괄호 타임스탬프를 챕터로 읽는다`() {
        val parsed = parseDescriptionChapters("믹스\n[00:00] 첫 곡\n[03:48] 계절의 끝\n[1:02:03] 마지막")

        assertEquals("믹스", parsed.preamble)
        assertEquals(listOf(0L, 228L, 3723L), parsed.chapters.map { it.startSeconds })
        assertEquals("계절의 끝", parsed.chapters[1].title)
    }

    @Test
    fun `대괄호 없는 타임스탬프와 nbsp 구분자도 챕터로 읽는다`() {
        val description = "▶ 순서\n\n0:08 할로윈 초콜릿 vs 젤리\n2:43 누가 누가 사탕을 가져갔나?\n" +
            "10:26  Colorful Halloween Desserts\n51:10 - 할로윈 다섯 손가락\n\n#주니토니"

        val parsed = parseDescriptionChapters(description)

        assertEquals("▶ 순서", parsed.preamble)
        assertEquals(listOf(8L, 163L, 626L, 3070L), parsed.chapters.map { it.startSeconds })
        assertEquals(
            listOf("할로윈 초콜릿 vs 젤리", "누가 누가 사탕을 가져갔나?", "Colorful Halloween Desserts", "할로윈 다섯 손가락"),
            parsed.chapters.map { it.title },
        )
    }

    @Test
    fun `타임스탬프가 하나뿐이면 챕터로 보지 않는다`() {
        val parsed = parseDescriptionChapters("공지\n3:00 부터 시작합니다")

        assertEquals(emptyList<Chapter>(), parsed.chapters)
    }
}
