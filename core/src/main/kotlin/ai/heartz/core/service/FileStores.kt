package ai.heartz.core.service

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

val HeartzJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** JSON 스냅샷 저장소. 임시 파일에 쓴 뒤 rename해 부분 기록을 남기지 않는다. */
class FileHeartzStore(private val file: File) : HeartzStore {
    override fun load(): HeartzDb? {
        if (!file.exists()) return null
        return HeartzJson.decodeFromString(HeartzDb.serializer(), file.readText())
    }

    override fun save(db: HeartzDb) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(HeartzJson.encodeToString(HeartzDb.serializer(), db))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

/** blobId 하나당 파일 하나. blobId는 경로 문자를 허용하지 않는다. */
class FileBlobStore(private val dir: File) : BlobStore {
    override fun put(blobId: String, bytes: ByteArray) {
        val target = fileOf(blobId)
        dir.mkdirs()
        val tmp = File(dir, ".$blobId.tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun get(blobId: String): ByteArray? = fileOf(blobId).takeIf { it.isFile }?.readBytes()

    override fun delete(blobId: String) {
        fileOf(blobId).delete()
    }

    private fun fileOf(blobId: String): File {
        require(SAFE_ID.matches(blobId)) { "invalid blob id" }
        return File(dir, blobId)
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    }
}
