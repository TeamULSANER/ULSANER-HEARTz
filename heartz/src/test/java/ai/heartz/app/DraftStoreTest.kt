package ai.heartz.app

import ai.heartz.app.data.BackendMode
import ai.heartz.app.data.DraftIntent
import ai.heartz.app.data.DraftStore
import ai.heartz.app.data.PendingDraft
import ai.heartz.core.model.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun draft(id: String, user: String = "u1", error: String? = null) = PendingDraft(
        clientDraftId = id, userId = user, backendMode = BackendMode.LOCAL_DEMO, audioPath = "/x/$id.m4a",
        placeId = "p-ichon", mood = Mood.WARM, durationMs = 20_000, intent = DraftIntent.SUBMIT_PUBLIC,
        createdAtMillis = 1, lastError = error,
    )

    @Test
    fun emptyWhenNoFile() {
        assertTrue(DraftStore(tmp.root.resolve("drafts.json")).list("u1").isEmpty())
    }

    @Test
    fun upsertReplacesSameClientIdAndSurvivesReload() {
        val f = tmp.root.resolve("drafts.json")
        DraftStore(f).upsert(draft("d1"))
        DraftStore(f).upsert(draft("d1", error = "network"))
        DraftStore(f).upsert(draft("d2"))
        val list = DraftStore(f).list("u1")
        assertEquals(listOf("d1", "d2"), list.map { it.clientDraftId })
        assertEquals("network", list.first().lastError)
    }

    @Test
    fun listIsPerUserAndRemoveDeletes() {
        val store = DraftStore(tmp.root.resolve("drafts.json"))
        store.upsert(draft("a", user = "u1"))
        store.upsert(draft("b", user = "u2"))
        assertEquals(listOf("b"), store.list("u2").map { it.clientDraftId })
        store.remove("a")
        assertTrue(store.list("u1").isEmpty())
    }
}
