package space.neoma.beats

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NeomaOfflineManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val offlineDir: File
        get() {
            val dir = File(context.filesDir, "offline_tracks")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val indexFile: File
        get() = File(offlineDir, "offline_index.json")

    @Synchronized
    fun getOfflineTracksJson(): String {
        return try {
            if (!indexFile.exists()) return "[]"
            indexFile.readText()
        } catch (_: Exception) {
            "[]"
        }
    }

    @Synchronized
    fun hasTrack(trackId: String): Boolean {
        val file = File(offlineDir, "$trackId.mp3")
        return file.exists() && file.length() > 1000
    }

    @Synchronized
    fun getTrackFilePath(trackId: String): String? {
        val file = File(offlineDir, "$trackId.mp3")
        return if (file.exists() && file.length() > 1000) file.absolutePath else null
    }

    suspend fun downloadTrack(
        trackId: String,
        title: String,
        artist: String,
        thumbnail: String,
        audioUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(audioUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            val destFile = File(offlineDir, "$trackId.mp3")

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            // Save to index
            saveTrackToIndex(trackId, title, artist, thumbnail, destFile.absolutePath)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Synchronized
    private fun saveTrackToIndex(
        trackId: String,
        title: String,
        artist: String,
        thumbnail: String,
        localPath: String
    ) {
        try {
            val jsonArray = if (indexFile.exists()) {
                JSONArray(indexFile.readText())
            } else {
                JSONArray()
            }

            // Remove if already exists
            val newArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                if (item.optString("id") != trackId && item.optString("videoId") != trackId) {
                    newArray.put(item)
                }
            }

            val newItem = JSONObject().apply {
                put("id", trackId)
                put("videoId", trackId)
                put("title", title)
                put("artist", artist)
                put("thumbnail", thumbnail)
                put("localPath", localPath)
                put("savedAt", System.currentTimeMillis())
            }
            newArray.put(newItem)
            indexFile.writeText(newArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun deleteTrack(trackId: String): Boolean {
        return try {
            val file = File(offlineDir, "$trackId.mp3")
            if (file.exists()) file.delete()

            if (indexFile.exists()) {
                val jsonArray = JSONArray(indexFile.readText())
                val newArray = JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    if (item.optString("id") != trackId && item.optString("videoId") != trackId) {
                        newArray.put(item)
                    }
                }
                indexFile.writeText(newArray.toString())
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
