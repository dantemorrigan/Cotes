package io.github.dantemorrigan.cotes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesJsonTest {
    private val sample = listOf(
        Note(id = "a1", title = "Покупки", content = "Молоко\nХлеб", created = 1_000, updated = 2_000, pinned = true),
        Note(id = "b2", title = "", content = "Без заголовка «кавычки» \"и\" \\слэши\\ 😺", created = 3_000),
    )

    @Test
    fun roundTrip_keepsEveryField() {
        assertEquals(sample, NotesJson.decode(NotesJson.encode(sample)))
    }

    @Test
    fun encode_writesBackupHeader() {
        val o = JSONObject(NotesJson.encode(sample))
        assertEquals("Cotes", o.getString("app"))
        assertEquals(1, o.getInt("version"))
        assertTrue(o.getString("exported").isNotEmpty())
        assertEquals(2, o.getJSONArray("notes").length())
    }

    @Test
    fun decode_bareArrayWithoutIdsOrDates() {
        val notes = NotesJson.decode("""[{"title":"Один","content":"1"},{"title":"Два","content":"2"}]""")!!
        assertEquals(listOf("Один", "Два"), notes.map { it.title })
        assertTrue(notes.all { it.id.isNotEmpty() && it.created > 0 && it.updated == it.created })
        assertNotEquals(notes[0].id, notes[1].id)
        // Чем позже в массиве, тем новее.
        assertTrue(notes[1].created > notes[0].created)
    }

    @Test
    fun decode_duplicateIds_getFreshOnes() {
        val notes = NotesJson.decode("""{"notes":[{"id":"x","title":"A"},{"id":"x","title":"B"}]}""")!!
        assertEquals(2, notes.map { it.id }.toSet().size)
        assertEquals("x", notes[0].id)
    }

    @Test
    fun decode_wrongTypes_areTolerated() {
        val notes = NotesJson.decode("""{"notes":[{"id":5,"title":null,"content":["x"],"pinned":"yes"}, 7, "s"]}""")!!
        assertEquals(1, notes.size)
        assertEquals("", notes[0].title)
        assertEquals("", notes[0].content)
        assertFalse(notes[0].pinned)
    }

    @Test
    fun decode_rejectsNonBackups() {
        assertNull(NotesJson.decode(""))
        assertNull(NotesJson.decode("hello"))
        assertNull(NotesJson.decode("{\"notes\": [ broken"))
        assertNull(NotesJson.decode("{\"something\": 1}"))
    }

    @Test
    fun displayTitle_fallsBackToFirstLine() {
        assertEquals("Первая строка", Note(id = "1", content = "\n  Первая строка  \nвторая", created = 1).displayTitle)
        assertEquals("Заголовок", Note(id = "1", title = " Заголовок ", content = "текст", created = 1).displayTitle)
        assertTrue(Note(id = "1", title = " ", content = "\n", created = 1).isBlank)
    }
}
