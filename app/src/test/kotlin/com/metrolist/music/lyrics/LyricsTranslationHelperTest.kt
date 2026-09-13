/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LyricsTranslationHelperTest {
    private lateinit var internalDb: InternalDatabase
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        internalDb =
            Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        database = MusicDatabase(internalDb)

        // Reset singleton helper state using existing public APIs
        LyricsTranslationHelper.cancelTranslation()
        LyricsTranslationHelper.triggerClearTranslations()
        LyricsTranslationHelper.setCompositionActive(true)
    }

    @After
    fun tearDown() {
        LyricsTranslationHelper.cancelTranslation()
        LyricsTranslationHelper.triggerClearTranslations()
        internalDb.close()
    }

    @Test
    fun `saveAndApplyManualTranslation matching lines persists to Room`() = runBlocking {
        val initialLyrics = LyricsEntity(
            id = "song1",
            lyrics = "First line\nSecond line",
        )
        database.upsert(initialLyrics)

        val snapshot = LyricsTranslationHelper.ManualLyricsSource(
            songId = "song1",
            sourceLines = listOf("First line", "Second line"),
        )
        val translated = listOf("First line trans", "Second line trans")

        val result = LyricsTranslationHelper.saveAndApplyManualTranslation(
            database = database,
            sourceSnapshot = snapshot,
            translatedLines = translated,
            targetLanguageCode = "id",
            mode = "Literal",
            activeSongIdProvider = { "song1" },
        )

        assertEquals(LyricsTranslationHelper.ManualImportResult.Success, result)
        val updated = database.lyrics("song1").first()
        assertEquals("First line trans\nSecond line trans", updated?.translatedLyrics)
        assertEquals("id", updated?.translationLanguage)
        assertEquals("Literal", updated?.translationMode)
    }

    @Test
    fun `saveAndApplyManualTranslation mismatch rejects import and leaves Room unmodified`() = runBlocking {
        val initialLyrics = LyricsEntity(
            id = "song1",
            lyrics = "Different line 1\nDifferent line 2",
        )
        database.upsert(initialLyrics)

        val snapshot = LyricsTranslationHelper.ManualLyricsSource(
            songId = "song1",
            sourceLines = listOf("Original line 1", "Original line 2"),
        )
        val translated = listOf("Trans 1", "Trans 2")

        val result = LyricsTranslationHelper.saveAndApplyManualTranslation(
            database = database,
            sourceSnapshot = snapshot,
            translatedLines = translated,
            targetLanguageCode = "id",
            mode = "Literal",
            activeSongIdProvider = { "song1" },
        )

        assertEquals(LyricsTranslationHelper.ManualImportResult.SourceMismatch, result)
        val after = database.lyrics("song1").first()
        assertEquals(initialLyrics, after)
    }

    @Test
    fun `saveAndApplyManualTranslation activeSongId mismatch does not set hasActiveTranslations`() = runBlocking {
        val initialLyrics = LyricsEntity(
            id = "songA",
            lyrics = "Line 1\nLine 2",
        )
        database.upsert(initialLyrics)

        val snapshot = LyricsTranslationHelper.ManualLyricsSource(
            songId = "songA",
            sourceLines = listOf("Line 1", "Line 2"),
        )
        val translated = listOf("Trans 1", "Trans 2")

        val result = LyricsTranslationHelper.saveAndApplyManualTranslation(
            database = database,
            sourceSnapshot = snapshot,
            translatedLines = translated,
            targetLanguageCode = "id",
            mode = "Literal",
            activeSongIdProvider = { "songB" },
        )

        assertEquals(LyricsTranslationHelper.ManualImportResult.Success, result)
        val updated = database.lyrics("songA").first()
        assertEquals("Trans 1\nTrans 2", updated?.translatedLyrics)
        assertEquals(false, LyricsTranslationHelper.hasActiveTranslations.value)
    }

    @Test
    fun `saveAndApplyManualTranslation activeSongId match sets hasActiveTranslations`() = runBlocking {
        val initialLyrics = LyricsEntity(
            id = "songA",
            lyrics = "Line 1\nLine 2",
        )
        database.upsert(initialLyrics)

        val snapshot = LyricsTranslationHelper.ManualLyricsSource(
            songId = "songA",
            sourceLines = listOf("Line 1", "Line 2"),
        )
        val translated = listOf("Trans 1", "Trans 2")

        val result = LyricsTranslationHelper.saveAndApplyManualTranslation(
            database = database,
            sourceSnapshot = snapshot,
            translatedLines = translated,
            targetLanguageCode = "id",
            mode = "Literal",
            activeSongIdProvider = { "songA" },
        )

        assertEquals(LyricsTranslationHelper.ManualImportResult.Success, result)
        assertEquals(true, LyricsTranslationHelper.hasActiveTranslations.value)
    }

    @Test
    fun `cancelTranslation resets Translating status to Idle`() {
        val entry = LyricsEntry(time = 0L, text = "Test")
        val context = ApplicationProvider.getApplicationContext<Context>()

        LyricsTranslationHelper.translateLyrics(
            lyrics = listOf(entry),
            targetLanguage = "id",
            apiKey = "dummy",
            baseUrl = "http://localhost",
            model = "test",
            mode = "Literal",
            scope = CoroutineScope(Dispatchers.IO),
            context = context,
        )

        LyricsTranslationHelper.cancelTranslation()
        assertEquals(LyricsTranslationHelper.TranslationStatus.Idle, LyricsTranslationHelper.status.value)
    }
}
