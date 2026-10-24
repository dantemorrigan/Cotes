package io.github.dantemorrigan.cotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NoteImportTest {
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val simplenoteJson = """
        {"activeNotes":[
          {"id":"a","content":"Покупки\r\nМолоко\nХлеб","creationDate":"2023-01-02T03:04:05.000Z",
           "lastModified":"2023-02-03T04:05:06.000Z","pinned":true,"markdown":false,"tags":["дом","work list"]},
          {"id":"b","content":"Одна строка","creationDate":"2023-01-01T00:00:00.000Z","lastModified":"2023-01-01T00:00:00.000Z"}
        ],"trashedNotes":[{"id":"c","content":"Удалённая","creationDate":"2023-01-01T00:00:00.000Z"}]}
    """.trimIndent()

    @Test
    fun simplenote_json() {
        val (source, notes) = NoteImport.fromJson(simplenoteJson)!!
        assertEquals(ImportSource.Simplenote, source)
        assertEquals(2, notes.size)
        val a = notes[0]
        assertEquals("Покупки", a.title)
        assertEquals("Молоко\nХлеб\n\n#дом #work_list", a.content)
        assertTrue(a.pinned)
        assertEquals(Instant.parse("2023-01-02T03:04:05Z").toEpochMilli(), a.created)
        assertEquals(Instant.parse("2023-02-03T04:05:06Z").toEpochMilli(), a.updated)
        assertEquals("Одна строка", notes[1].title)
        assertEquals("", notes[1].content)
    }

    @Test
    fun simplenote_zip_usesJson_skipsTxtCopiesAndTrash() {
        val bytes = zip(
            "source/notes.json" to simplenoteJson,
            "Покупки.txt" to "Покупки\nМолоко\nХлеб",
            "Одна строка.txt" to "Одна строка",
            "trash/Удалённая.txt" to "Удалённая",
        )
        val result = NoteImport.fromFile("notes.zip", bytes)
        assertEquals(setOf(ImportSource.Simplenote), result.sources)
        assertEquals(listOf("Покупки", "Одна строка"), result.notes.map { it.title })
    }

    private fun keepNote(title: String, trashed: Boolean = false) = """
        {"color":"DEFAULT","isTrashed":$trashed,"isPinned":true,"isArchived":false,
         "textContent":"Текст $title","title":"$title",
         "userEditedTimestampUsec":1690000000000000,"createdTimestampUsec":1680000000000000,
         "labels":[{"name":"Идеи"}]}
    """.trimIndent()

    @Test
    fun googleKeep_note_andChecklist() {
        val (source, notes) = NoteImport.fromJson(keepNote("Первая"))!!
        assertEquals(ImportSource.GoogleKeep, source)
        val n = notes.single()
        assertEquals("Первая", n.title)
        assertEquals("Текст Первая\n\n#Идеи", n.content)
        assertTrue(n.pinned)
        assertEquals(1_690_000_000_000L, n.updated)
        assertEquals(1_680_000_000_000L, n.created)

        val list = """{"title":"Список","isTrashed":false,"userEditedTimestampUsec":1690000000000000,
            "listContent":[{"text":"Молоко","isChecked":true},{"text":"Хлеб","isChecked":false}]}"""
        assertEquals("☑ Молоко\n☐ Хлеб", NoteImport.fromJson(list)!!.second.single().content)
        assertNull(NoteImport.fromJson(keepNote("Корзина", trashed = true)))
    }

    @Test
    fun googleKeep_takeoutZip() {
        val bytes = zip(
            "Takeout/Keep/Первая.json" to keepNote("Первая"),
            "Takeout/Keep/Первая.html" to "<html>Первая</html>",
            "Takeout/Keep/Вторая.json" to keepNote("Вторая"),
            "Takeout/Keep/Удалённая.json" to keepNote("Удалённая", trashed = true),
            "Takeout/Keep/Labels.txt" to "Идеи",
        )
        val result = NoteImport.fromFile("takeout.zip", bytes)
        assertEquals(setOf(ImportSource.GoogleKeep), result.sources)
        assertEquals(setOf("Первая", "Вторая"), result.notes.map { it.title }.toSet())
    }

    @Test
    fun standardNotes_backup_evenAsTxt() {
        val json = """
            {"version":"004","items":[
              {"uuid":"n1","content_type":"Note","created_at":"2022-05-01T10:00:00.000Z","updated_at":"2022-05-02T10:00:00.000Z",
               "deleted":false,"content":{"title":"Заметка","text":"Текст","references":[],
               "appData":{"org.standardnotes.sn":{"pinned":true}}}},
              {"uuid":"n2","content_type":"Note","created_at":"2022-05-01T10:00:00.000Z","updated_at":"2022-05-01T10:00:00.000Z",
               "content":{"title":"В корзине","text":"x","trashed":true,"references":[]}},
              {"uuid":"n3","content_type":"Note","deleted":true,"content":{"title":"Удалена","text":"x"}},
              {"uuid":"t1","content_type":"Tag","content":{"title":"Работа","references":[{"uuid":"n1","content_type":"Note"}]}},
              {"uuid":"x","content_type":"SN|Component","content":{"name":"editor"}}
            ]}
        """.trimIndent()
        val result = NoteImport.fromFile("Standard Notes Backup and Import File.txt", json.toByteArray())
        assertEquals(setOf(ImportSource.StandardNotes), result.sources)
        val n = result.notes.single()
        assertEquals("Заметка", n.title)
        assertEquals("Текст\n\n#Работа", n.content)
        assertTrue(n.pinned)
        assertEquals(Instant.parse("2022-05-02T10:00:00Z").toEpochMilli(), n.updated)
    }

    @Test
    fun cotesBackup_keepsIds() {
        val backup = NotesJson.encode(listOf(Note(id = "keep-me", title = "T", content = "C", created = 5)))
        val (source, notes) = NoteImport.fromJson(backup)!!
        assertEquals(ImportSource.Cotes, source)
        assertEquals("keep-me", notes.single().id)
    }

    @Test
    fun genericJson_otherApps() {
        val json = """{"notes":[
            {"name":"Joplin-like","body":"Тело","created_time":1600000000000,"updated_time":1600000001000},
            {"title":"Секунды","text":"т","createdAt":1600000000},
            {"title":"Удалена","content":"x","deleted":true},
            {"foo":"bar"}
        ]}"""
        val (source, notes) = NoteImport.fromJson(json)!!
        assertEquals(ImportSource.Json, source)
        assertEquals(listOf("Joplin-like", "Секунды"), notes.map { it.title })
        assertEquals("Тело", notes[0].content)
        assertEquals(1_600_000_001_000L, notes[0].updated)
        assertEquals(1_600_000_000_000L, notes[1].created)
        assertNull(NoteImport.fromJson("""{"settings":{"a":1}}"""))
        assertNull(NoteImport.fromJson("не json"))
    }

    @Test
    fun text_titleRules() {
        val heading = NoteImport.fromText("vault/Idea.md", "# Большая идея\n\nТекст", 1000)!!
        assertEquals("Большая идея", heading.title)
        assertEquals("Текст", heading.content)
        assertEquals(1000L, heading.updated)

        val firstLine = NoteImport.fromText("Список дел.txt", "Список дел на неделю\nпервое\nвторое")!!
        assertEquals("Список дел на неделю", firstLine.title)
        assertEquals("первое\nвторое", firstLine.content)

        val byName = NoteImport.fromText("Рецепт.md", "мука\nсахар")!!
        assertEquals("Рецепт", byName.title)
        assertEquals("мука\nсахар", byName.content)

        val frontMatter = NoteImport.fromText("a.md", "---\ntags: [x]\n---\n# Заголовок\nтело")!!
        assertEquals("Заголовок", frontMatter.title)
        assertEquals("тело", frontMatter.content)

        assertNull(NoteImport.fromText("empty.txt", " \n "))
    }

    @Test
    fun textStartingWithBracket_isNotLostAsJson() {
        val result = NoteImport.fromFile("todo.txt", "[x] купить молоко\n[ ] позвонить".toByteArray())
        assertEquals(setOf(ImportSource.Text), result.sources)
        val note = result.notes.single()
        assertEquals("todo", note.title)
        assertEquals("[x] купить молоко\n[ ] позвонить", note.content)
    }

    @Test
    fun markdownZip_fromObsidian() {
        val bytes = zip(
            "Vault/Первая.md" to "# Первая\nтекст",
            "Vault/sub/Вторая.md" to "без заголовка",
            "Vault/.obsidian/app.json" to "{}",
            "__MACOSX/Vault/._Первая.md" to "мусор",
            "Vault/picture.png" to "png",
        )
        val result = NoteImport.fromFile("vault.zip", bytes)
        assertEquals(setOf(ImportSource.Text), result.sources)
        assertEquals(setOf("Первая", "Вторая"), result.notes.map { it.title }.toSet())
    }

    @Test
    fun exportThenImport_roundTrip() {
        val notes = listOf(
            Note(id = "1", title = "Покупки", content = "Молоко\nХлеб", created = 1, updated = 1_700_000_000_000),
            Note(id = "2", title = "", content = "Без заголовка\nвторая строка", created = 1),
            Note(id = "3", title = "Покупки", content = "другие", created = 1),
            Note(id = "4", title = "a/b:c", content = "x", created = 1),
        )
        val out = ByteArrayOutputStream()
        NoteExport.markdownZip(notes, out)
        val back = NoteImport.fromFile("export.zip", out.toByteArray())
        assertEquals(4, back.notes.size)
        assertEquals(
            setOf("Покупки" to "Молоко\nХлеб", "Без заголовка" to "вторая строка", "Покупки" to "другие", "a/b:c" to "x"),
            back.notes.map { it.title to it.content }.toSet(),
        )
    }

    @Test
    fun time_scales() {
        assertEquals(1_600_000_000_000L, NoteImport.time(1_600_000_000))
        assertEquals(1_600_000_000_000L, NoteImport.time(1_600_000_000_000L))
        assertEquals(1_600_000_000_000L, NoteImport.time(1_600_000_000_000_000L))
        assertEquals(1_600_000_000_000L, NoteImport.time("1600000000000"))
        assertEquals(Instant.parse("2020-09-13T12:26:40Z").toEpochMilli(), NoteImport.time("2020-09-13T12:26:40Z"))
        assertEquals(Instant.parse("2020-09-13T12:26:40Z").toEpochMilli(), NoteImport.time("2020-09-13T15:26:40+03:00"))
        assertNull(NoteImport.time("вчера"))
        assertNull(NoteImport.time(null))
    }

    @Test
    fun html_toText() {
        val enml = """<?xml version="1.0"?><!DOCTYPE en-note SYSTEM "x"><en-note><div>Привет&nbsp;&amp; пока</div>""" +
            """<div><en-todo checked="true"/>готово</div><div><en-todo checked="false"/>нет</div>""" +
            """<ul><li>раз</li><li>два</li></ul><div>a<br/>b &#8212; &#x41;</div></en-note>"""
        assertEquals("Привет & пока\n☑ готово\n☐ нет\n• раз\n• два\na\nb — A", Html.toText(enml))
    }

    @Test
    fun unknownBinary_isIgnored() {
        assertFalse(NoteImport.fromFile("photo.jpg", byteArrayOf(1, 2, 3)).notes.isNotEmpty())
    }
}
