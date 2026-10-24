package io.github.dantemorrigan.cotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** Импорт на устройстве: Evernote (нужен XmlPullParser Android) и защита от повторного импорта. */
@RunWith(AndroidJUnit4::class)
class ImportTest {
    private val enex = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE en-export SYSTEM "http://xml.evernote.com/pub/evernote-export3.dtd">
<en-export export-date="20240101T000000Z" application="Evernote" version="10">
  <note>
    <title>Путешествие</title>
    <created>20230102T030405Z</created>
    <updated>20230203T040506Z</updated>
    <tag>отпуск</tag>
    <note-attributes><author>me</author></note-attributes>
    <content><![CDATA[<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE en-note SYSTEM "http://xml.evernote.com/pub/enml2.dtd"><en-note><div>Билеты &amp; отель</div><div><en-todo checked="true"/>паспорт</div></en-note>]]></content>
    <resource><data encoding="base64">AAAA</data><mime>image/png</mime></resource>
  </note>
  <note><title>Вторая</title><content><![CDATA[<en-note>текст</en-note>]]></content></note>
</en-export>"""

    @Test
    fun evernote_enex() {
        val result = NoteImport.fromFile("Мои заметки.enex", enex.toByteArray())
        assertEquals(setOf(ImportSource.Evernote), result.sources)
        assertEquals(2, result.notes.size)
        val n = result.notes[0]
        assertEquals("Путешествие", n.title)
        assertEquals("Билеты & отель\n☑ паспорт\n\n#отпуск", n.content)
        assertEquals(Instant.parse("2023-01-02T03:04:05Z").toEpochMilli(), n.created)
        assertEquals(Instant.parse("2023-02-03T04:05:06Z").toEpochMilli(), n.updated)
        assertEquals("текст", result.notes[1].content)
    }

    @Test
    fun repeatedImport_addsNothing() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "import-test")
        dir.deleteRecursively()
        dir.mkdirs()
        val repo = NotesRepository(File(dir, "notes.json"))
        val notes = NoteImport.fromFile("Мои заметки.enex", enex.toByteArray()).notes
        assertEquals(2, repo.importNotes(notes))
        // Тот же файл ещё раз — новые id, но тот же текст: ничего не добавляется.
        assertEquals(0, repo.importNotes(NoteImport.fromFile("Мои заметки.enex", enex.toByteArray()).notes))
        assertEquals(2, repo.notes.value.size)
        assertTrue(repo.notes.value.all { it.id.isNotEmpty() })
        dir.deleteRecursively()
    }
}
