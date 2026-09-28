package com.notzeetaa.emuhub

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import java.io.BufferedReader
import java.io.FileReader
import kotlin.math.abs

data class DeviceInfo(
    val androidVersion: String,
    val ram: String,
    val gpuRenderer: String,
    val adrenoSeries: String,
    val gpuModel: String
) {
    companion object {
        suspend fun collect(context: Context): DeviceInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val renderer = getGpuRenderer()
            DeviceInfo(
                "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                getTotalRam(context),
                renderer,
                detectAdrenoSeries(renderer),
                detectGpuModel(renderer)
            )
        }
    }
}

fun detectAdrenoSeries(gpuString: String): String {
    val model = Regex("Adreno.*?(\\d{3,4})", RegexOption.IGNORE_CASE).find(gpuString)?.groupValues?.get(1) ?: return "unknown"
    return when { model.startsWith("8") -> "8xx"; model.startsWith("7") -> "7xx"; model.startsWith("6") -> "6xx"; else -> "unknown" }
}

/**
 * Extracts the specific Adreno GPU model number (e.g. "740", "810", "830").
 * Returns "unknown" if the GPU is not Adreno or the model cannot be parsed.
 */
fun detectGpuModel(gpuString: String): String {
    return Regex("Adreno.*?(\\d{3,4})", RegexOption.IGNORE_CASE)
        .find(gpuString)?.groupValues?.get(1) ?: "unknown"
}

private fun getTotalRam(context: Context): String {
    val memTotalKB = readMemTotalFromProc()
    val totalRamMB = if (memTotalKB > 0) memTotalKB / 1024
    else {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        (memoryInfo.totalMem / (1024 * 1024)).toInt()
    }
    val totalRamGB = totalRamMB.toDouble() / 1024.0
    val roundedGB = roundToManufacturerRam(totalRamGB)
    return "$roundedGB GB"
}

private fun roundToManufacturerRam(valueGB: Double): Int {
    val commonSizes = listOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32)
    var best = commonSizes[0]
    var bestDiff = abs(valueGB - best)
    for (size in commonSizes) {
        val diff = abs(valueGB - size)
        if (diff < bestDiff - 0.01) {
            bestDiff = diff
            best = size
        } else if (abs(diff - bestDiff) < 0.01 && size > best) best = size
    }
    return best
}

private fun readMemTotalFromProc(): Long {
    return try {
        val reader = BufferedReader(FileReader("/proc/meminfo"))
        val line = reader.readLine()
        reader.close()
        if (line != null && line.startsWith("MemTotal:")) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) parts[1].toLong() else 0L
        } else 0L
    } catch (e: Exception) { 0L }
}

private fun getGpuRenderer(): String {
    var eglDisplay: android.opengl.EGLDisplay? = null
    var eglContext: android.opengl.EGLContext? = null
    var eglSurface: android.opengl.EGLSurface? = null
    return try {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return "Error: no display"
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return "Error: EGL init failed"
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfig = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfig, 0) || numConfig[0] == 0)
            return "Error: no EGL config"
        val config = configs[0] ?: return "Error: null config"
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) return "Error: context creation failed"
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) return "Error: surface creation failed"
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES20.glGetString(GLES20.GL_RENDERER)?.trim() ?: "Unknown"
    } catch (e: Exception) { "Error: ${e.message}" }
    finally {
        eglSurface?.let { EGL14.eglDestroySurface(eglDisplay, it) }
        eglContext?.let { EGL14.eglDestroyContext(eglDisplay, it) }
        eglDisplay?.let { EGL14.eglTerminate(it) }
    }
}