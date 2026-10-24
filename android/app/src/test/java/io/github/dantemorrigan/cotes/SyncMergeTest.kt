package io.github.dantemorrigan.cotes

import io.github.dantemorrigan.cotes.sync.RemoteEntry
import io.github.dantemorrigan.cotes.sync.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun note(id: String, changed: Long, text: String = "текст $id") =
        Note(id = id, title = id, content = text, created = 1, updated = changed, changed = changed)

    private fun remote(id: String, changed: Long, deleted: Boolean = false) =
        RemoteEntry(noteId = id, fileId = "f-$id-$changed", changed = changed, deleted = deleted)

    @Test
    fun firstSignIn_uploadsLocal_downloadsRemote() {
        val plan = SyncMerge.plan(
            local = listOf(note("a", 10)),
            deleted = emptyMap(),
            remote = listOf(remote("b", 20)),
            base = emptyMap(),
        )
        assertEquals(listOf("a"), plan.upload.map { it.first.id })
        assertEquals(listOf("b"), plan.download.map { it.noteId })
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun sameVersion_isInSync() {
        val plan = SyncMerge.plan(listOf(note("a", 10)), emptyMap(), listOf(remote("a", 10)), emptyMap())
        assertTrue(plan.isEmpty)
        assertEquals(mapOf("a" to 10L), plan.inSync)
    }

    @Test
    fun editedHereOnly_uploads_editedThereOnly_downloads() {
        val base = mapOf("a" to 10L, "b" to 10L)
        val plan = SyncMerge.plan(
            local = listOf(note("a", 30), note("b", 10)),
            deleted = emptyMap(),
            remote = listOf(remote("a", 10), remote("b", 40)),
            base = base,
        )
        assertEquals("a", plan.upload.single().first.id)
        assertEquals("f-a-10", plan.upload.single().second?.fileId)
        assertEquals("b", plan.download.single().noteId)
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun editedOnBothSides_isConflict() {
        val plan = SyncMerge.plan(listOf(note("a", 30)), emptyMap(), listOf(remote("a", 40)), mapOf("a" to 10L))
        assertEquals("a", plan.conflicts.single().local.id)
        assertTrue(plan.upload.isEmpty() && plan.download.isEmpty())
    }

    @Test
    fun deletedHere_uploadsDeletion_deletedThere_deletesHere() {
        val base = mapOf("a" to 10L, "b" to 10L)
        val plan = SyncMerge.plan(
            local = listOf(note("b", 10)),
            deleted = mapOf("a" to 50L),
            remote = listOf(remote("a", 10), remote("b", 60, deleted = true)),
            base = base,
        )
        assertEquals(("a" to 50L), plan.uploadDeleted.single().first)
        assertEquals(mapOf("b" to 60L), plan.applyDeleted)
    }

    @Test
    fun editNewerThanDeletion_wins() {
        // Удалили там в 20, а здесь правили в 30 — заметка оживает.
        val plan = SyncMerge.plan(listOf(note("a", 30)), emptyMap(), listOf(remote("a", 20, deleted = true)), mapOf("a" to 10L))
        assertEquals("a", plan.upload.single().first.id)
        assertTrue(plan.applyDeleted.isEmpty())
    }

    @Test
    fun remoteDeletion_ofUnknownNote_isIgnored() {
        val plan = SyncMerge.plan(emptyList(), emptyMap(), listOf(remote("x", 20, deleted = true)), emptyMap())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun duplicateFiles_newestWins_restAreCleanedUp() {
        val plan = SyncMerge.plan(emptyList(), emptyMap(), listOf(remote("a", 10), remote("a", 20)), emptyMap())
        assertEquals(20L, plan.download.single().changed)
        assertEquals(10L, plan.duplicates.single().changed)
    }

    @Test
    fun blankLocalNotes_areNotUploaded() {
        val blank = Note(id = "e", created = 1)
        val plan = SyncMerge.plan(listOf(blank), emptyMap(), emptyList(), emptyMap())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun hasLocalChanges() {
        val base = mapOf("a" to 10L, "d" to 5L)
        assertFalse(SyncMerge.hasLocalChanges(listOf(note("a", 10)), mapOf("d" to 5L), base))
        assertTrue(SyncMerge.hasLocalChanges(listOf(note("a", 11)), mapOf("d" to 5L), base))
        assertTrue(SyncMerge.hasLocalChanges(listOf(note("a", 10)), mapOf("d" to 5L, "z" to 1L), base))
    }

    @Test
    fun notesJson_keepsChangedAndDeleted() {
        val notes = listOf(note("a", 10).copy(changed = 15))
        val text = NotesJson.encode(notes, mapOf("gone" to 99L))
        assertEquals(15L, NotesJson.decode(text)!!.single().changed)
        assertEquals(mapOf("gone" to 99L), NotesJson.decodeDeleted(text))
        // Старые файлы без changed: берётся время правки.
        assertEquals(7L, NotesJson.decode("""[{"id":"x","title":"t","created":1,"updated":7}]""")!!.single().changed)
    }
}
