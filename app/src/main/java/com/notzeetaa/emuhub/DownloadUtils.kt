package com.notzeetaa.emuhub

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "EmuHubDownload"
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val PROGRESS_UPDATE_INTERVAL_MS = 120L

// Reuse the HTTP client instead of creating a new connection pool for every file.
private val downloadClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

// Used when resuming a persisted download after the Activity/app UI was recreated.
private val persistedDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private data class DownloadControl(
    @Volatile var paused: Boolean = false,
    @Volatile var cancelled: Boolean = false,
    @Volatile var call: Call? = null
)

private class DownloadPausedException : IOException("Download paused")

private val downloadControls = ConcurrentHashMap<String, DownloadControl>()

/**
 * Pause by closing the current HTTP call. The partial file and metadata stay on disk.
 * Resume opens a new HTTP Range request, so pausing also survives process death.
 */
fun pauseActiveDownload(fileName: String) {
    val control = downloadControls[fileName] ?: return
    control.paused = true
    DownloadsManager.pauseDownload(fileName)
    control.call?.cancel()
}

/** Resume either an in-memory pause or a download restored after relaunching the app. */
fun resumeActiveDownload(context: Context, fileName: String) {
    val restored = DownloadsManager.activeDownloads[fileName] ?: return
    if (restored.status != DownloadStatus.PAUSED) return

    DownloadsManager.resumeDownload(fileName)
    val appContext = context.applicationContext

    persistedDownloadScope.launch {
        // If Pause just cancelled the previous call, let its finally block release this name first.
        while (downloadControls.containsKey(fileName)) {
            delay(50)
        }

        val current = withContext(Dispatchers.Main) {
            DownloadsManager.activeDownloads[fileName]
        } ?: return@launch

        if (current.status == DownloadStatus.CANCELLING) return@launch
        transferExistingDownload(appContext, current, resume = true)
    }
}

/** Cancel immediately and remove both the persisted state and partial file. */
fun cancelActiveDownload(context: Context, fileName: String) {
    val state = DownloadsManager.activeDownloads[fileName] ?: return
    DownloadsManager.markCancelling(fileName)

    val control = downloadControls[fileName]
    if (control != null) {
        control.cancelled = true
        control.call?.cancel()
        return
    }

    // Restored paused downloads have no live OkHttp call, so clean them up directly.
    val appContext = context.applicationContext
    persistedDownloadScope.launch {
        deletePartialOutput(appContext, state.outputUri.takeIf { it.isNotBlank() }?.let(Uri::parse))
        withContext(Dispatchers.Main) {
            DownloadsManager.failDownload(fileName)
            Toast.makeText(appContext, "Download cancelled: $fileName", Toast.LENGTH_SHORT).show()
        }
    }
}

suspend fun downloadAsset(context: Context, release: GithubRelease, asset: GithubAsset) {
    val desiredName = sanitizeFileName("${release.tagName}_${asset.name}")
    downloadFileWithProgress(context.applicationContext, asset.downloadUrl, desiredName)
}

suspend fun downloadComponent(context: Context, component: Component) {
    val fileName = sanitizeFileName(
        Uri.decode(component.remoteUrl.substringAfterLast("/"))
    )
    downloadFileWithProgress(context.applicationContext, component.remoteUrl, fileName)
}

private fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

private suspend fun getUniqueFileName(
    context: Context,
    folderUri: Uri?,
    desiredName: String
): String = withContext(Dispatchers.IO) {
    val lastDot = desiredName.lastIndexOf('.')
    val nameWithoutExt = if (lastDot > 0) desiredName.substring(0, lastDot) else desiredName
    val extension = if (lastDot > 0) desiredName.substring(lastDot) else ""

    var counter = 1
    var newName = desiredName

    while (true) {
        val exists = if (folderUri != null && DocumentsContract.isTreeUri(folderUri)) {
            DocumentFile.fromTreeUri(context, folderUri)?.findFile(newName) != null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(newName),
                null
            )?.use { cursor -> cursor.count > 0 } ?: false
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, newName).exists()
        }

        if (!exists) break
        newName = "$nameWithoutExt ($counter)$extension"
        counter++
    }

    newName
}

private suspend fun createOutputUri(
    context: Context,
    folderUri: Uri?,
    uniqueFileName: String
): Uri? = withContext(Dispatchers.IO) {
    if (folderUri != null) {
        val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
        if (folderDoc != null && folderDoc.canWrite()) {
            folderDoc.createFile("application/octet-stream", uniqueFileName)?.uri
        } else {
            Log.e(TAG, "Cannot write to custom folder: $folderUri")
            null
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) return@withContext null
        Uri.fromFile(File(downloadsDir, uniqueFileName))
    }
}

private fun openOutputStream(context: Context, uri: Uri, append: Boolean): OutputStream {
    return if (uri.scheme == "content") {
        val mode = if (append) "wa" else "w"
        context.contentResolver.openOutputStream(uri, mode)
            ?: throw IOException("Cannot open output stream")
    } else {
        val file = File(uri.path ?: throw IOException("Invalid output path"))
        FileOutputStream(file, append)
    }
}

private suspend fun persistAndThrowPause(
    output: OutputStream,
    fileName: String,
    downloadedBytes: Long
): Nothing {
    runCatching { output.flush() }
    withContext(Dispatchers.Main) {
        DownloadsManager.updateProgress(fileName, downloadedBytes)
        DownloadsManager.pauseDownload(fileName)
    }
    throw DownloadPausedException()
}

private suspend fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    fileName: String,
    control: DownloadControl,
    startingBytes: Long
): Long {
    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
    var totalRead = startingBytes.coerceAtLeast(0L)
    var lastUiUpdate = 0L

    while (true) {
        currentCoroutineContext().ensureActive()

        if (control.cancelled) throw CancellationException("Download cancelled")
        if (control.paused) persistAndThrowPause(output, fileName, totalRead)

        val bytesRead = try {
            input.read(buffer)
        } catch (error: IOException) {
            when {
                control.cancelled -> throw CancellationException("Download cancelled")
                control.paused -> persistAndThrowPause(output, fileName, totalRead)
                else -> throw error
            }
        }
        if (bytesRead == -1) break

        output.write(buffer, 0, bytesRead)
        totalRead += bytesRead

        val now = SystemClock.elapsedRealtime()
        if (now - lastUiUpdate >= PROGRESS_UPDATE_INTERVAL_MS) {
            withContext(Dispatchers.Main) {
                DownloadsManager.updateProgress(fileName, totalRead)
            }
            lastUiUpdate = now
        }
    }

    output.flush()
    withContext(Dispatchers.Main) {
        DownloadsManager.updateProgress(fileName, totalRead)
    }
    return totalRead
}

private fun publishMediaStoreFile(context: Context, uri: Uri, usesMediaStore: Boolean) {
    if (usesMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }
}

private fun deletePartialOutput(context: Context, uri: Uri?) {
    if (uri == null) return
    runCatching {
        if (uri.scheme == "content") {
            context.contentResolver.delete(uri, null, null)
        } else {
            File(uri.path.orEmpty()).delete()
        }
    }.onFailure { Log.w(TAG, "Could not remove partial download", it) }
}

private fun parseContentRangeTotal(contentRange: String?): Long {
    if (contentRange.isNullOrBlank()) return 0L
    val totalPart = contentRange.substringAfter('/', "").trim()
    return totalPart.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
}

private fun outputPath(uri: Uri): String {
    return if (uri.scheme == "content") {
        uri.toString()
    } else {
        File(uri.path ?: throw IOException("Invalid output path")).absolutePath
    }
}

private suspend fun transferExistingDownload(
    context: Context,
    state: DownloadsManager.ActiveDownload,
    resume: Boolean
) = withContext(Dispatchers.IO) transfer@{
    val fileName = state.fileName
    val outputUri = state.outputUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: run {
            withContext(Dispatchers.Main) {
                DownloadsManager.failDownload(fileName)
                Toast.makeText(context, "Cannot resume $fileName: partial file is missing", Toast.LENGTH_LONG).show()
            }
            return@transfer
        }

    val control = DownloadControl()
    downloadControls[fileName] = control

    try {
        var requestedOffset = if (resume) state.downloadedBytes.coerceAtLeast(0L) else 0L
        val requestBuilder = Request.Builder()
            .url(state.url)
            .header("User-Agent", "EmuHub-Android/1.0")
            .header("Accept-Encoding", "identity")

        if (requestedOffset > 0L) {
            requestBuilder.header("Range", "bytes=$requestedOffset-")
        }

        withContext(Dispatchers.Main) {
            DownloadsManager.markConnecting(fileName)
        }

        val call = downloadClient.newCall(requestBuilder.build())
        control.call = call

        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("No data received")
            val serverAcceptedResume = requestedOffset > 0L && response.code == 206

            // Some hosts ignore Range and return 200. Truncate and safely restart instead of
            // appending a full response to an existing partial file.
            if (requestedOffset > 0L && !serverAcceptedResume) {
                requestedOffset = 0L
                withContext(Dispatchers.Main) {
                    DownloadsManager.updateProgress(fileName, 0L)
                    Toast.makeText(
                        context,
                        "This source cannot resume $fileName; restarting from 0%",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val bodyLength = body.contentLength().coerceAtLeast(0L)
            val contentRangeTotal = parseContentRangeTotal(response.header("Content-Range"))
            val totalBytes = when {
                contentRangeTotal > 0L -> contentRangeTotal
                bodyLength > 0L && requestedOffset > 0L -> requestedOffset + bodyLength
                else -> bodyLength
            }

            withContext(Dispatchers.Main) {
                DownloadsManager.updateTotalBytes(fileName, totalBytes)
                DownloadsManager.markDownloading(fileName)
            }

            val totalWritten = body.byteStream().use { input ->
                openOutputStream(context, outputUri, append = requestedOffset > 0L).use { output ->
                    copyWithProgress(
                        input = input,
                        output = output,
                        fileName = fileName,
                        control = control,
                        startingBytes = requestedOffset
                    )
                }
            }

            if (control.cancelled) throw CancellationException("Download cancelled")
            if (control.paused) throw DownloadPausedException()

            publishMediaStoreFile(context, outputUri, state.usesMediaStore)
            withContext(Dispatchers.Main) {
                DownloadsManager.completeDownload(fileName, outputPath(outputUri), totalWritten)
                Toast.makeText(context, "Download complete: $fileName", Toast.LENGTH_LONG).show()
            }
        }
    } catch (_: DownloadPausedException) {
        // Keep the partial URI + byte count. It can be resumed after app/process recreation.
        withContext(NonCancellable + Dispatchers.Main) {
            DownloadsManager.pauseDownload(fileName)
        }
    } catch (cancelled: CancellationException) {
        if (control.cancelled) {
            withContext(NonCancellable + Dispatchers.IO) {
                deletePartialOutput(context, outputUri)
            }
            withContext(NonCancellable + Dispatchers.Main) {
                DownloadsManager.failDownload(fileName)
                Toast.makeText(context, "Download cancelled: $fileName", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Activity/process lifecycle cancellation should preserve the partial download.
            withContext(NonCancellable + Dispatchers.Main) {
                DownloadsManager.pauseDownload(fileName)
            }
            throw cancelled
        }
    } catch (error: Exception) {
        when {
            control.paused -> {
                withContext(NonCancellable + Dispatchers.Main) {
                    DownloadsManager.pauseDownload(fileName)
                }
            }

            control.cancelled -> {
                deletePartialOutput(context, outputUri)
                withContext(Dispatchers.Main) {
                    DownloadsManager.failDownload(fileName)
                    Toast.makeText(context, "Download cancelled: $fileName", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                Log.e(TAG, "Download failed: ${state.url}", error)
                deletePartialOutput(context, outputUri)
                withContext(Dispatchers.Main) {
                    DownloadsManager.failDownload(fileName)
                    Toast.makeText(
                        context,
                        "Download failed: ${error.message ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    } finally {
        control.call = null
        downloadControls.remove(fileName, control)
    }
}

private suspend fun downloadFileWithProgress(
    context: Context,
    url: String,
    originalDesiredName: String
) = withContext(Dispatchers.IO) download@{
    val folderUri = SettingsManager.getDownloadFolderUri()?.let(Uri::parse)
    val uniqueFileName = getUniqueFileName(context, folderUri, originalDesiredName)
    val usesMediaStore = folderUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val outputUri = createOutputUri(context, folderUri, uniqueFileName)

    if (outputUri == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Cannot create output file", Toast.LENGTH_LONG).show()
        }
        return@download
    }

    withContext(Dispatchers.Main) {
        DownloadsManager.startDownload(
            fileName = uniqueFileName,
            url = url,
            outputUri = outputUri.toString(),
            usesMediaStore = usesMediaStore
        )
        Toast.makeText(context, "Download started: $uniqueFileName", Toast.LENGTH_SHORT).show()
    }

    val state = withContext(Dispatchers.Main) {
        DownloadsManager.activeDownloads[uniqueFileName]
    } ?: return@download

    transferExistingDownload(context, state, resume = false)
}
