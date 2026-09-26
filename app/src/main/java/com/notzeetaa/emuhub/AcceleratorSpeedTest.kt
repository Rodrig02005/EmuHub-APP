package com.notzeetaa.emuhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// A stable, medium-sized file (~928 KB) on raw.githubusercontent.com. Raw files are
// proxied by every supported accelerator, so comparing them reflects real behaviour.
private const val SPEED_TEST_URL =
    "https://raw.githubusercontent.com/torvalds/linux/master/MAINTAINERS"
private const val SPEED_TEST_MAX_BYTES = 1_500_000L
private const val SPEED_TEST_CALL_TIMEOUT_MS = 8_000L

private val speedTestClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(SPEED_TEST_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

/**
 * One mirrored (or direct) target and its measured throughput.
 * [accelerator] is null for a direct, non-accelerated connection.
 * [bytesPerSecond] is null when the target could not be measured.
 */
data class AcceleratorSpeedResult(
    val accelerator: DownloadAccelerator?,
    val customPrefix: String = "",
    val bytesPerSecond: Long? = null
) {
    val isDirect: Boolean get() = accelerator == null
}

/**
 * Downloads up to [SPEED_TEST_MAX_BYTES] through [accelerator] and returns the
 * measured bytes per second, or null on failure. Reads are bounded by the client's
 * call timeout, so a hanging mirror cannot stall the whole test.
 */
suspend fun measureAcceleratorSpeed(
    accelerator: DownloadAccelerator?,
    customPrefix: String = ""
): Long? = withContext(Dispatchers.IO) {
    val url = applyDownloadAccelerator(SPEED_TEST_URL, accelerator, customPrefix)
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "EmuHub-Android/1.0")
        .header("Accept-Encoding", "identity")
        .header("Range", "bytes=0-${SPEED_TEST_MAX_BYTES - 1}")
        .build()

    try {
        speedTestClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null

            val startedAt = System.nanoTime()
            var totalRead = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (totalRead < SPEED_TEST_MAX_BYTES) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    totalRead += read
                }
            }
            val elapsedNanos = (System.nanoTime() - startedAt).coerceAtLeast(1L)
            if (totalRead <= 0L) null else totalRead * 1_000_000_000L / elapsedNanos
        }
    } catch (_: Exception) {
        null
    }
}

/** Targets to measure: every built-in scheme, an optional custom mirror, and direct. */
fun speedTestTargets(customPrefix: String): List<AcceleratorSpeedResult> = buildList {
    DownloadAccelerator.entries.forEach { accelerator ->
        if (accelerator == DownloadAccelerator.CUSTOM) {
            if (normalizeProxyPrefix(customPrefix).isNotEmpty()) {
                add(AcceleratorSpeedResult(accelerator, customPrefix))
            }
        } else {
            add(AcceleratorSpeedResult(accelerator))
        }
    }
    add(AcceleratorSpeedResult(accelerator = null))
}
