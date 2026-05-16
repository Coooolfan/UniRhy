package com.coooolfan.unirhy.service.task

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ScanTaskTitleParserTest {

    @Test
    fun `parses trailing ascii bracket as work title and label`() {
        val parsed = parseRecordingTitleMetadata("睡公主 (G.E.M.重生版)")

        assertEquals("睡公主", parsed.workTitle)
        assertEquals(listOf("G.E.M.重生版"), parsed.labels)
    }

    @Test
    fun `parses trailing chinese bracket as work title and label`() {
        val parsed = parseRecordingTitleMetadata("睡公主（G.E.M.重生版）")

        assertEquals("睡公主", parsed.workTitle)
        assertEquals(listOf("G.E.M.重生版"), parsed.labels)
    }

    @Test
    fun `parses multiple trailing brackets in order`() {
        val parsed = parseRecordingTitleMetadata("跳楼机 (粤语) (DJ迷人磊版)")

        assertEquals("跳楼机", parsed.workTitle)
        assertEquals(listOf("粤语", "DJ迷人磊版"), parsed.labels)
    }

    @Test
    fun `splits whitespace separated labels inside one bracket`() {
        val parsed = parseRecordingTitleMetadata("跳楼机 (WUKONG Remix)")

        assertEquals("跳楼机", parsed.workTitle)
        assertEquals(listOf("WUKONG", "Remix"), parsed.labels)
    }

    @Test
    fun `parses ordinary trailing bracket as recording label`() {
        val parsed = parseRecordingTitleMetadata("篝火旁（再启程）")

        assertEquals("篝火旁", parsed.workTitle)
        assertEquals(listOf("再启程"), parsed.labels)
    }

    @Test
    fun `ignores non trailing brackets`() {
        val parsed = parseRecordingTitleMetadata("跳楼机 (Live) 2025")

        assertEquals("跳楼机 (Live) 2025", parsed.workTitle)
        assertEquals(emptyList(), parsed.labels)
    }

    @Test
    fun `splits artists separated by spaced slash`() {
        val artists = parseRecordingArtists("A / B")

        assertEquals(listOf("A", "B"), artists)
    }

    @Test
    fun `splits artists separated by slash`() {
        val artists = parseRecordingArtists("A/B")

        assertEquals(listOf("A", "B"), artists)
    }

    @Test
    fun `splits artists separated by semicolon`() {
        val artists = parseRecordingArtists("A;B")

        assertEquals(listOf("A", "B"), artists)
    }

    @Test
    fun `splits slash without surrounding spaces inside artist name`() {
        val artists = parseRecordingArtists("AC/DC")

        assertEquals(listOf("AC", "DC"), artists)
    }

    @Test
    fun `ignores blank artists and removes duplicates`() {
        val artists = parseRecordingArtists(" A ; ; B ; A ")

        assertEquals(listOf("A", "B"), artists)
    }
}
