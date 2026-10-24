package io.github.dantemorrigan.cotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Хранилище: испорченный файл не теряется, обычный цикл записи и чтения работает. */
@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "storage-test")
    private val file = File(dir, "notes.json")

    @Before
    fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun damagedFile_isSetAside_notOverwritten() {
        val garbage = "{\"notes\": [ broken"
        file.writeText(garbage)
        val repo = NotesRepository(file)
        assertTrue(repo.notes.value.isEmpty())
        val kept = repo.damaged.value
        assertNotNull("damaged copy is reported", kept)
        assertEquals(garbage, kept!!.readText())

        // Новые заметки пишутся в чистый файл, отложенная копия остаётся как была.
        val note = repo.create("после сбоя")
        repo.flush()
        assertTrue(file.readText().contains("после сбоя"))
        assertEquals(garbage, kept.readText())
        assertFalse(repo.writeFailed.value)
        assertEquals(note.id, NotesRepository(file).notes.value.single().id)
    }

    @Test
    fun missingOrEmptyFile_isJustEmpty() {
        assertTrue(NotesRepository(file).notes.value.isEmpty())
        file.writeText("")
        val repo = NotesRepository(file)
        assertTrue(repo.notes.value.isEmpty())
        assertEquals(null, repo.damaged.value)
    }

    @Test
    fun writeAndReadBack() {
        val repo = NotesRepository(file)
        val a = repo.create()
        repo.edit(a.id, "Заголовок", "Текст")
        repo.togglePin(a.id)
        repo.flush()
        val again = NotesRepository(file).notes.value.single()
        assertEquals("Заголовок", again.title)
        assertEquals("Текст", again.content)
        assertTrue(again.pinned)
    }

    @Test
    fun longNote_savesQuickly() {
        val repo = NotesRepository(file)
        val text = (1..5_000).joinToString("\n") { "Строка $it: немного текста, чтобы заметка была длинной." }
        val note = repo.create()
        val started = System.nanoTime()
        repeat(20) { repo.edit(note.id, "Длинная", text + it); repo.flush() }
        val perSaveMs = (System.nanoTime() - started) / 20 / 1_000_000
        assertTrue("one save of a ~300 KB note takes $perSaveMs ms", perSaveMs < 500)
        assertEquals(text + 19, NotesRepository(file).notes.value.single().content)
    }
}
