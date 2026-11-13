package ai.heartz.core

import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.service.FileBlobStore
import ai.heartz.core.service.FileHeartzStore
import ai.heartz.core.service.HeartzDb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileStoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun missingFileLoadsAsNull() {
        assertNull(FileHeartzStore(tmp.root.resolve("db.json")).load())
    }

    @Test
    fun savedDbRoundTripsAndLeavesNoTempFile() {
        val file = tmp.root.resolve("data/db.json")
        val db = HeartzDb(users = listOf(User("u1", "청취자 (테스트)", UserRole.LISTENER)))
        FileHeartzStore(file).save(db)
        assertEquals(db, FileHeartzStore(file).load())
        assertEquals(listOf("db.json"), file.parentFile.list()!!.toList())
    }

    @Test
    fun blobRoundTripAndDelete() {
        val blobs = FileBlobStore(tmp.root.resolve("blobs"))
        blobs.put("audio-1", byteArrayOf(9, 8, 7))
        assertArrayEquals(byteArrayOf(9, 8, 7), blobs.get("audio-1"))
        blobs.delete("audio-1")
        assertNull(blobs.get("audio-1"))
    }

    @Test
    fun blobIdWithPathCharactersIsRejected() {
        val blobs = FileBlobStore(tmp.root.resolve("blobs"))
        for (bad in listOf("../x", "a/b", "", ".")) {
            val thrown = runCatching { blobs.put(bad, byteArrayOf(1)) }.exceptionOrNull()
            assertTrue(bad, thrown is IllegalArgumentException)
        }
        assertTrue(tmp.root.resolve("x").exists().not())
    }
}
