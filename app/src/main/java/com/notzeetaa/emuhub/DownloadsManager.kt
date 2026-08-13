package com.notzeetaa.emuhub

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "emu_hub_prefs"
private const val KEY_COMPLETED_DOWNLOADS = "completed_downloads"

object DownloadsManager {
    data class ActiveDownload(val fileName: String, var progress: Int, val totalBytes: Long, var downloadedBytes: Long)
    data class CompletedDownload(val id: String, val fileName: String, val filePath: String, val sizeBytes: Long, val timestamp: Long) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("fileName", fileName)
            put("filePath", filePath)
            put("sizeBytes", sizeBytes)
            put("timestamp", timestamp)
        }
        companion object {
            fun fromJson(json: JSONObject): CompletedDownload {
                // Use optString para evitar exceção se o campo não existir
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

    val activeDownloads: Map<String, ActiveDownload> get() = _activeDownloads
    val completedDownloads: List<CompletedDownload> get() = _completedDownloads

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCompletedDownloads()
    }

    private fun saveCompletedDownloads() {
        val jsonArray = JSONArray()
        _completedDownloads.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_COMPLETED_DOWNLOADS, jsonArray.toString()).apply()
    }

    private fun loadCompletedDownloads() {
        val jsonString = prefs.getString(KEY_COMPLETED_DOWNLOADS, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        _completedDownloads.clear()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            _completedDownloads.add(CompletedDownload.fromJson(obj))
        }
    }

    fun startDownload(fileName: String, totalBytes: Long = 0L) {
        _activeDownloads[fileName] = ActiveDownload(
            fileName = fileName,
            progress = 0,
            totalBytes = totalBytes.coerceAtLeast(0L),
            downloadedBytes = 0L
        )
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
        }
    }

    fun completeDownload(fileName: String, filePath: String, sizeBytes: Long) {
        _activeDownloads.remove(fileName)
        val uniqueId = UUID.randomUUID().toString()
        _completedDownloads.add(0, CompletedDownload(uniqueId, fileName, filePath, sizeBytes, System.currentTimeMillis()))
        saveCompletedDownloads()
    }

    fun failDownload(fileName: String) {
        _activeDownloads.remove(fileName)
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