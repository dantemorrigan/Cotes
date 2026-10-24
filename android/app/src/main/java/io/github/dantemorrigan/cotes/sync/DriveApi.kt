package io.github.dantemorrigan.cotes.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Профиль из Google: имя, почта и ссылка на фото. */
data class GoogleProfile(val name: String, val email: String, val photoUrl: String?)

/** Токен больше не действует: нужно получить новый. */
class AuthExpiredException : IOException("auth expired")

/** Ответ Drive с ошибкой: [reason] — машинная причина из ответа (например, accessNotConfigured). */
class DriveException(val code: Int, val reason: String?, message: String) : IOException(message)

/**
 * Скрытая папка приложения в Google Drive (appDataFolder): пользователь её не видит,
 * а приложение не видит остальные его файлы. Одна заметка — один файл «<id>.json»;
 * время изменения и признак удаления лежат в appProperties, поэтому список всех заметок
 * приходит одним запросом без скачивания текстов.
 */
class DriveApi(private val token: String) {

    suspend fun profile(): GoogleProfile {
        val o = JSONObject(request("GET", "$API/about?fields=user(displayName,emailAddress,photoLink)"))
        val user = o.getJSONObject("user")
        return GoogleProfile(
            name = user.optString("displayName"),
            email = user.optString("emailAddress"),
            photoUrl = user.optString("photoLink").takeIf { it.isNotEmpty() },
        )
    }

    suspend fun list(): List<RemoteEntry> {
        val out = ArrayList<RemoteEntry>()
        var page: String? = null
        do {
            val url = buildString {
                append("$API/files?spaces=appDataFolder&pageSize=1000")
                append("&fields=").append(enc("nextPageToken,files(id,name,appProperties)"))
                if (page != null) append("&pageToken=").append(enc(page))
            }
            val o = JSONObject(request("GET", url))
            val files = o.optJSONArray("files")
            if (files != null) for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val name = f.optString("name")
                if (!name.endsWith(SUFFIX)) continue
                val props = f.optJSONObject("appProperties") ?: continue
                val changed = props.optString("changed").toLongOrNull() ?: continue
                out += RemoteEntry(
                    noteId = name.removeSuffix(SUFFIX),
                    fileId = f.getString("id"),
                    changed = changed,
                    deleted = props.optString("deleted") == "1",
                )
            }
            page = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (page != null)
        return out
    }

    suspend fun download(fileId: String): String = request("GET", "$API/files/${enc(fileId)}?alt=media")

    /** Создаёт или обновляет файл заметки; возвращает id файла. */
    suspend fun put(noteId: String, existingFileId: String?, changed: Long, deleted: Boolean, body: String): String {
        val props = JSONObject().put("changed", changed.toString())
        // null в appProperties снимает метку удаления с ожившей заметки.
        if (deleted) props.put("deleted", "1") else if (existingFileId != null) props.put("deleted", JSONObject.NULL)
        val meta = JSONObject().put("appProperties", props)
        if (existingFileId == null) {
            meta.put("name", noteId + SUFFIX).put("parents", JSONArray().put("appDataFolder"))
        }
        val boundary = "cotes-" + UUID.randomUUID()
        val multipart = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta.toString()).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(body).append("\r\n")
            append("--").append(boundary).append("--")
        }
        val url = if (existingFileId == null) "$UPLOAD/files?uploadType=multipart&fields=id"
        else "$UPLOAD/files/${enc(existingFileId)}?uploadType=multipart&fields=id"
        val res = request(
            if (existingFileId == null) "POST" else "PATCH",
            url,
            multipart.toByteArray(Charsets.UTF_8),
            "multipart/related; boundary=$boundary",
        )
        return JSONObject(res).getString("id")
    }

    suspend fun delete(fileId: String) {
        try {
            request("DELETE", "$API/files/${enc(fileId)}")
        } catch (e: DriveException) {
            if (e.code != 404) throw e
        }
    }

    /** Запрос с повтором при перегрузке сервера (429, 5xx) и паузой, которая растёт. */
    private suspend fun request(method: String, url: String, body: ByteArray? = null, type: String? = null): String {
        var wait = 1_000L
        repeat(4) { attempt ->
            try {
                return once(method, url, body, type)
            } catch (e: DriveException) {
                val retry = e.code == 429 || e.code >= 500 ||
                    e.code == 403 && (e.reason == "rateLimitExceeded" || e.reason == "userRateLimitExceeded")
                if (!retry || attempt == 3) throw e
            }
            delay(wait)
            wait *= 2
        }
        error("unreachable")
    }

    private suspend fun once(method: String, url: String, body: ByteArray?, type: String?): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                // HttpURLConnection не умеет PATCH: Google понимает подмену через заголовок.
                c.requestMethod = if (method == "PATCH") "POST" else method
                if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", type)
                    c.setFixedLengthStreamingMode(body.size)
                    c.outputStream.use { it.write(body) }
                }
                val code = c.responseCode
                if (code == 401) throw AuthExpiredException()
                if (code !in 200..299) {
                    val text = c.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                    val reason = runCatching {
                        JSONObject(text).getJSONObject("error").getJSONArray("errors").getJSONObject(0).optString("reason")
                    }.getOrNull()
                    throw DriveException(code, reason, "Drive $code ${reason.orEmpty()}")
                }
                c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                c.disconnect()
            }
        }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val SUFFIX = ".json"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}
