package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTranslationFormatTest {

    @Test
    fun `valid raw JSON object parses successfully`() {
        val json = """{"lines": ["First line", "Second line"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(json, 2)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        assertEquals(listOf("First line", "Second line"), (result as LyricsTranslationFormat.ValidationResult.Success).lines)
    }

    @Test
    fun `whole response wrapped in json markdown code fence parses successfully`() {
        val fenced = """
            ```json
            {
              "lines": [
                "Baris satu",
                "Baris dua"
              ]
            }
            ```
        """.trimIndent()
        val result = LyricsTranslationFormat.validateAndParseResponse(fenced, 2)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        assertEquals(listOf("Baris satu", "Baris dua"), (result as LyricsTranslationFormat.ValidationResult.Success).lines)
    }

    @Test
    fun `whole response wrapped in generic code fence parses successfully`() {
        val fenced = """
            ```
            {
              "lines": [
                "Line 1",
                "Line 2"
              ]
            }
            ```
        """.trimIndent()
        val result = LyricsTranslationFormat.validateAndParseResponse(fenced, 2)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        assertEquals(listOf("Line 1", "Line 2"), (result as LyricsTranslationFormat.ValidationResult.Success).lines)
    }

    @Test
    fun `arbitrary markdown fence tag is rejected`() {
        val fenced = """
            ```python
            {
              "lines": [
                "Line 1"
              ]
            }
            ```
        """.trimIndent()
        val result = LyricsTranslationFormat.validateAndParseResponse(fenced, 1)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.InvalidJson,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `commentary outside code fence is rejected`() {
        val withCommentary = """
            Here is your translation:
            ```json
            {
              "lines": ["Line 1"]
            }
            ```
            Hope this helps!
        """.trimIndent()
        val result = LyricsTranslationFormat.validateAndParseResponse(withCommentary, 1)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.InvalidJson,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `whitespace and blank lines around response are handled cleanly`() {
        val padded = "\n\n  \t  {\"lines\": [\"Padded\"]}  \n\n  "
        val result = LyricsTranslationFormat.validateAndParseResponse(padded, 1)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        assertEquals(listOf("Padded"), (result as LyricsTranslationFormat.ValidationResult.Success).lines)
    }

    @Test
    fun `unicode characters across various scripts and emojis are preserved`() {
        val unicodeJson = """
            {
              "lines": [
                "日本語の歌詞",
                "한국어 가사",
                "كلمات الأغنية",
                "Русский текст",
                "हिन्दी गीत के बोल",
                "🎵 Beautiful vibes ✨"
              ]
            }
        """.trimIndent()
        val result = LyricsTranslationFormat.validateAndParseResponse(unicodeJson, 6)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        val lines = (result as LyricsTranslationFormat.ValidationResult.Success).lines
        assertEquals("日本語の歌詞", lines[0])
        assertEquals("한국어 가사", lines[1])
        assertEquals("كلمات الأغنية", lines[2])
        assertEquals("Русский текст", lines[3])
        assertEquals("हिन्दी गीत के बोल", lines[4])
        assertEquals("🎵 Beautiful vibes ✨", lines[5])
    }

    @Test
    fun `escaped quotes and json control characters in lines are preserved`() {
        val escapedJson = """{"lines": ["He said \"Hello!\"", "Backslash \\ test", "Tab \t test"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(escapedJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        val lines = (result as LyricsTranslationFormat.ValidationResult.Success).lines
        assertEquals("He said \"Hello!\"", lines[0])
        assertEquals("Backslash \\ test", lines[1])
        assertEquals("Tab \t test", lines[2])
    }

    @Test
    fun `individual empty string entry in mixed content is permitted`() {
        val mixedJson = """{"lines": ["First", "", "Third"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(mixedJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        assertEquals(listOf("First", "", "Third"), (result as LyricsTranslationFormat.ValidationResult.Success).lines)
    }

    @Test
    fun `response with all blank lines is rejected`() {
        val allBlankJson = """{"lines": ["", "   ", ""]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(allBlankJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.AllLinesBlank,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `embedded newline in translated entry is rejected with line index`() {
        val newlineJson = """{"lines": ["First line", "Second\nSplit line", "Third line"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(newlineJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.EmbeddedNewline(lineIndex = 1),
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `embedded carriage return in translated entry is rejected with line index`() {
        val crJson = """{"lines": ["First line\rSecond line"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(crJson, 1)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.EmbeddedNewline(lineIndex = 0),
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `unexpected extra root properties are rejected`() {
        val extraPropertiesJson = """
            {
              "lines": ["Line 1", "Line 2"],
              "note": "Translation notes",
              "model": "gpt-4"
            }
        """.trimIndent()
        val result = LyricsTranslationFormat.validateAndParseResponse(extraPropertiesJson, 2)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        val error = (result as LyricsTranslationFormat.ValidationResult.Error).reason
        assertTrue(error is LyricsTranslationFormat.ValidationError.UnexpectedProperties)
        assertEquals(setOf("note", "model"), (error as LyricsTranslationFormat.ValidationError.UnexpectedProperties).extraKeys)
    }

    @Test
    fun `missing lines property is rejected`() {
        val missingLinesJson = """{"translations": ["A", "B"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(missingLinesJson, 2)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.MissingLinesArray,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `lines property not an array is rejected`() {
        val stringLinesJson = """{"lines": "Not an array"}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(stringLinesJson, 1)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.MissingLinesArray,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `non-string element in lines array is rejected`() {
        val nonStringJson = """{"lines": ["First line", 123, true]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(nonStringJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.NonStringElement,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `line count mismatch too few lines is rejected`() {
        val shortJson = """{"lines": ["Line 1", "Line 2"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(shortJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.LineCountMismatch(expected = 3, received = 2),
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `line count mismatch too many lines is rejected`() {
        val longJson = """{"lines": ["Line 1", "Line 2", "Line 3", "Line 4"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(longJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.LineCountMismatch(expected = 3, received = 4),
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `empty input is rejected`() {
        val result = LyricsTranslationFormat.validateAndParseResponse("   ", 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.EmptyInput,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `malformed json syntax is rejected`() {
        val malformed = """{"lines": ["Missing closing bracket""""
        val result = LyricsTranslationFormat.validateAndParseResponse(malformed, 1)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Error)
        assertEquals(
            LyricsTranslationFormat.ValidationError.InvalidJson,
            (result as LyricsTranslationFormat.ValidationResult.Error).reason
        )
    }

    @Test
    fun `repeated chorus lines are preserved without deduplication`() {
        val chorusJson = """{"lines": ["I love you", "I love you", "I love you"]}"""
        val result = LyricsTranslationFormat.validateAndParseResponse(chorusJson, 3)
        assertTrue(result is LyricsTranslationFormat.ValidationResult.Success)
        assertEquals(listOf("I love you", "I love you", "I love you"), (result as LyricsTranslationFormat.ValidationResult.Success).lines)
    }

    @Test
    fun `prompt builder formats prompt with target language name and payload`() {
        val lines = listOf("First song line", "Second song line")
        val prompt = LyricsTranslationFormat.buildManualPrompt(lines, "Indonesian", "Literal")

        assertTrue(prompt.contains("into Indonesian"))
        assertTrue(prompt.contains("There are exactly 2 lyric lines"))
        assertTrue(prompt.contains("The output must contain exactly 2 translated strings"))
        assertTrue(prompt.contains("\"target_language\": \"Indonesian\""))
        assertTrue(prompt.contains("\"line_count\": 2"))
        assertTrue(prompt.contains("\"text\": \"First song line\""))
        assertTrue(prompt.contains("\"text\": \"Second song line\""))
        assertTrue(prompt.contains("Do not include newline characters (\\n or \\r)"))
    }

    @Test
    fun `prompt builder formats transcribed mode prompt accurately`() {
        val lines = listOf("こんにちは")
        val prompt = LyricsTranslationFormat.buildManualPrompt(lines, "Hindi", "Transcribed")

        assertTrue(prompt.contains("into Hindi script"))
        assertTrue(prompt.contains("Transcribe/transliterate the lyrics phonetically"))
        assertTrue(prompt.contains("\"target_language\": \"Hindi\""))
    }

    @Test
    fun `line extraction parity with synced and unsynced lyrics`() {
        val syncedLyrics = """
            [00:01.00]First line
            [00:02.50]
            [00:04.00]Second line
        """.trimIndent()
        val syncedExtracted = LyricsUtils.getTranslatableLyricLines(syncedLyrics)
        assertEquals(listOf("First line", "Second line"), syncedExtracted)

        val unsyncedLyrics = """
            Verse 1
            
            Verse 2
        """.trimIndent()
        val unsyncedExtracted = LyricsUtils.getTranslatableLyricLines(unsyncedLyrics)
        assertEquals(listOf("Verse 1", "Verse 2"), unsyncedExtracted)

        val emptyExtracted = LyricsUtils.getTranslatableLyricLines("")
        assertEquals(emptyList<String>(), emptyExtracted)
    }
}
