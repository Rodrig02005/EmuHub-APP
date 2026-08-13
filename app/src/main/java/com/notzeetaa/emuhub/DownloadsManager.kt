package com.notzeetaa.emuhub

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "emu_hub_prefs"
private const val KEY_COMPLETED_DOWNLOADS = "completed_downloads"
private const val KEY_ACTIVE_DOWNLOADS = "active_downloads"
private const val ACTIVE_PERSIST_INTERVAL_MS = 1_000L

enum class DownloadStatus {
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    CANCELLING
}

object DownloadsManager {
    data class ActiveDownload(
        val fileName: String,
        val progress: Int,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val status: DownloadStatus = DownloadStatus.CONNECTING,
        val url: String = "",
        val outputUri: String = "",
        val usesMediaStore: Boolean = false
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("fileName", fileName)
            put("progress", progress)
            put("totalBytes", totalBytes)
            put("downloadedBytes", downloadedBytes)
            put("status", status.name)
            put("url", url)
            put("outputUri", outputUri)
            put("usesMediaStore", usesMediaStore)
        }

        companion object {
            fun fromJson(json: JSONObject): ActiveDownload? {
                val fileName = json.optString("fileName")
                val url = json.optString("url")
                val outputUri = json.optString("outputUri")
                if (fileName.isBlank() || url.isBlank() || outputUri.isBlank()) return null

                val totalBytes = json.optLong("totalBytes", 0L).coerceAtLeast(0L)
                val downloadedBytes = json.optLong("downloadedBytes", 0L).coerceAtLeast(0L)
                val progress = if (totalBytes > 0L) {
                    ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                } else {
                    json.optInt("progress", 0).coerceIn(0, 100)
                }

                // No HTTP stream survives process death. Anything restored is therefore paused,
                // even if the last persisted in-memory state was CONNECTING/DOWNLOADING.
                return ActiveDownload(
                    fileName = fileName,
                    progress = progress,
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    status = DownloadStatus.PAUSED,
                    url = url,
                    outputUri = outputUri,
                    usesMediaStore = json.optBoolean("usesMediaStore", false)
                )
            }
        }
    }

    data class CompletedDownload(
        val id: String,
        val fileName: String,
        val filePath: String,
        val sizeBytes: Long,
        val timestamp: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("fileName", fileName)
            put("filePath", filePath)
            put("sizeBytes", sizeBytes)
            put("timestamp", timestamp)
        }

        companion object {
            fun fromJson(json: JSONObject): CompletedDownload {
                val id = json.optString("id", "")
                val fileName = json.getString("fileName")
                val filePath = json.getString("filePath")
                val sizeBytes = json.optLong("sizeBytes", 0L)
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                return CompletedDownload(
                    id = if (id.isNotEmpty()) id else UUID.randomUUID().toString(),
                    fileName = fileName,
                    filePath = filePath,
                    sizeBytes = sizeBytes,
                    timestamp = timestamp
                )
            }
        }
    }

    private val _activeDownloads = mutableStateMapOf<String, ActiveDownload>()
    private val _completedDownloads = mutableStateListOf<CompletedDownload>()
    private lateinit var prefs: SharedPreferences
    private var lastActivePersistAt = 0L

    val activeDownloads: Map<String, ActiveDownload> get() = _activeDownloads
    val completedDownloads: List<CompletedDownload> get() = _completedDownloads

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCompletedDownloads()
        loadActiveDownloads()
    }

    private fun saveCompletedDownloads() {
        if (!::prefs.isInitialized) return
        val jsonArray = JSONArray()
        _completedDownloads.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_COMPLETED_DOWNLOADS, jsonArray.toString()).apply()
    }

    private fun loadCompletedDownloads() {
        val jsonString = prefs.getString(KEY_COMPLETED_DOWNLOADS, "[]") ?: "[]"
        val jsonArray = runCatching { JSONArray(jsonString) }.getOrElse { JSONArray() }
        _completedDownloads.clear()
        for (i in 0 until jsonArray.length()) {
            runCatching { CompletedDownload.fromJson(jsonArray.getJSONObject(i)) }
                .getOrNull()
                ?.let(_completedDownloads::add)
        }
    }

    private fun saveActiveDownloads(force: Boolean = false) {
        if (!::prefs.isInitialized) return

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastActivePersistAt < ACTIVE_PERSIST_INTERVAL_MS) return
        lastActivePersistAt = now

        val jsonArray = JSONArray()
        _activeDownloads.values.forEach { jsonArray.put(it.toJson()) }
        val editor = prefs.edit().putString(KEY_ACTIVE_DOWNLOADS, jsonArray.toString())
        if (force) {
            // Pause/cancel/start should be durable even if the user immediately swipes the app away.
            editor.commit()
        } else {
            editor.apply()
        }
    }

    private fun loadActiveDownloads() {
        val jsonString = prefs.getString(KEY_ACTIVE_DOWNLOADS, "[]") ?: "[]"
        val jsonArray = runCatching { JSONArray(jsonString) }.getOrElse { JSONArray() }
        _activeDownloads.clear()

        for (i in 0 until jsonArray.length()) {
            runCatching { ActiveDownload.fromJson(jsonArray.getJSONObject(i)) }
                .getOrNull()
                ?.let { restored -> _activeDownloads[restored.fileName] = restored }
        }

        // Normalize persisted CONNECTING/DOWNLOADING states to PAUSED immediately.
        saveActiveDownloads(force = true)
    }

    fun startDownload(
        fileName: String,
        url: String,
        outputUri: String,
        usesMediaStore: Boolean,
        totalBytes: Long = 0L
    ) {
        _activeDownloads[fileName] = ActiveDownload(
            fileName = fileName,
            progress = 0,
            totalBytes = totalBytes.coerceAtLeast(0L),
            downloadedBytes = 0L,
            status = DownloadStatus.CONNECTING,
            url = url,
            outputUri = outputUri,
            usesMediaStore = usesMediaStore
        )
        saveActiveDownloads(force = true)
    }

    /** Update the size after the server sends the response headers without resetting progress. */
    fun updateTotalBytes(fileName: String, totalBytes: Long) {
        _activeDownloads[fileName]?.let { current ->
            val safeTotal = totalBytes.coerceAtLeast(0L)
            val progress = if (safeTotal > 0L) {
                ((current.downloadedBytes.toDouble() / safeTotal) * 100).toInt().coerceIn(0, 100)
            } else {
                current.progress
            }
            _activeDownloads[fileName] = current.copy(
                progress = progress,
                totalBytes = safeTotal
            )
            saveActiveDownloads()
        }
    }

    fun markConnecting(fileName: String) {
        _activeDownloads[fileName]?.let { current ->
            if (current.status != DownloadStatus.CANCELLING) {
                _activeDownloads[fileName] = current.copy(status = DownloadStatus.CONNECTING)
                saveActiveDownloads(force = true)
            }
        }
    }

    fun markDownloading(fileName: String) {
        _activeDownloads[fileName]?.let { current ->
            if (current.status != DownloadStatus.PAUSED && current.status != DownloadStatus.CANCELLING) {
                _activeDownloads[fileName] = current.copy(status = DownloadStatus.DOWNLOADING)
                saveActiveDownloads(force = true)
            }
        }
    }

    fun pauseDownload(fileName: String) {
        _activeDownloads[fileName]?.let { current ->
            if (current.status != DownloadStatus.CANCELLING) {
                _activeDownloads[fileName] = current.copy(status = DownloadStatus.PAUSED)
                saveActiveDownloads(force = true)
            }
        }
    }

    fun resumeDownload(fileName: String) {
        markConnecting(fileName)
    }

    fun markCancelling(fileName: String) {
        _activeDownloads[fileName]?.let { current ->
            _activeDownloads[fileName] = current.copy(status = DownloadStatus.CANCELLING)
            saveActiveDownloads(force = true)
        }
    }

    fun updateProgress(fileName: String, downloadedBytes: Long) {
        _activeDownloads[fileName]?.let { current ->
            val safeDownloaded = downloadedBytes.coerceAtLeast(0L)
            val progress = if (current.totalBytes > 0L) {
                ((safeDownloaded.toDouble() / current.totalBytes) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            _activeDownloads[fileName] = current.copy(
                progress = progress,
                downloadedBytes = safeDownloaded
            )
            saveActiveDownloads()
        }
    }

    fun completeDownload(fileName: String, filePath: String, sizeBytes: Long) {
        _activeDownloads.remove(fileName)
        saveActiveDownloads(force = true)

        val uniqueId = UUID.randomUUID().toString()
        _completedDownloads.add(
            0,
            CompletedDownload(
                uniqueId,
                fileName,
                filePath,
                sizeBytes,
                System.currentTimeMillis()
            )
        )
        saveCompletedDownloads()
    }

    fun failDownload(fileName: String) {
        _activeDownloads.remove(fileName)
        saveActiveDownloads(force = true)
    }

    fun removeCompleted(id: String) {
        _completedDownloads.removeAll { it.id == id }
        saveCompletedDownloads()
    }

    fun clearCompleted() {
        _completedDownloads.clear()
        saveCompletedDownloads()
    }
}
