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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
        // The permission is persisted when the user chooses the folder in Settings.
        // Calling takePersistableUriPermission() again from a stored URI can fail
        // because there is no new transient grant attached to this request.
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

private fun openOutputStream(context: Context, uri: Uri): OutputStream {
    return if (uri.scheme == "content") {
        context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Cannot open output stream")
    } else {
        val file = File(uri.path ?: throw IOException("Invalid output path"))
        FileOutputStream(file)
    }
}

private suspend fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    fileName: String
): Long {
    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
    var totalRead = 0L
    var lastUiUpdate = 0L

    while (true) {
        currentCoroutineContext().ensureActive()
        val bytesRead = input.read(buffer)
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

private suspend fun downloadFileWithProgress(
    context: Context,
    url: String,
    originalDesiredName: String
) = withContext(Dispatchers.IO) {
    val folderUri = SettingsManager.getDownloadFolderUri()?.let(Uri::parse)
    val uniqueFileName = getUniqueFileName(context, folderUri, originalDesiredName)
    val usesMediaStore = folderUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var outputUri: Uri? = null

    withContext(Dispatchers.Main) {
        DownloadsManager.startDownload(uniqueFileName)
        Toast.makeText(context, "Download started: $uniqueFileName", Toast.LENGTH_SHORT).show()
    }

    try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "EmuHub-Android/1.0")
            .header("Accept-Encoding", "identity")
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("No data received")
            val contentLength = body.contentLength().coerceAtLeast(0L)

            withContext(Dispatchers.Main) {
                DownloadsManager.updateTotalBytes(uniqueFileName, contentLength)
            }

            outputUri = createOutputUri(context, folderUri, uniqueFileName)
                ?: throw IOException("Cannot create output file")

            val totalWritten = body.byteStream().use { input ->
                openOutputStream(context, outputUri!!).use { output ->
                    copyWithProgress(input, output, uniqueFileName)
                }
            }

            publishMediaStoreFile(context, outputUri!!, usesMediaStore)
            val outputPath = if (outputUri!!.scheme == "content") {
                outputUri.toString()
            } else {
                File(outputUri!!.path ?: throw IOException("Invalid output path")).absolutePath
            }

            withContext(Dispatchers.Main) {
                DownloadsManager.completeDownload(uniqueFileName, outputPath, totalWritten)
                Toast.makeText(context, "Download complete: $uniqueFileName", Toast.LENGTH_LONG).show()
            }
        }
    } catch (cancelled: CancellationException) {
        // A cancelled screen/navigation coroutine must never leave a fake active download behind.
        withContext(NonCancellable + Dispatchers.IO) {
            deletePartialOutput(context, outputUri)
        }
        withContext(NonCancellable + Dispatchers.Main) {
            DownloadsManager.failDownload(uniqueFileName)
        }
        throw cancelled
    } catch (error: Exception) {
        Log.e(TAG, "Download failed: $url", error)
        deletePartialOutput(context, outputUri)
        withContext(Dispatchers.Main) {
            DownloadsManager.failDownload(uniqueFileName)
            Toast.makeText(
                context,
                "Download failed: ${error.message ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
