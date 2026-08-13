package com.notzeetaa.emuhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_SOURCE_CATALOG_URL =
    "https://raw.githubusercontent.com/Rodrig02005/EmuHub-APP/main/sources.json"

data class TurnipSource(
    val id: String,
    val name: String,
    val apiUrl: String,
    val description: String,
    val experimental: Boolean,
    val supportedSeries: Set<String>,
    val filters: Map<String, List<String>>,
    val assetIncludes: Map<String, List<String>> = emptyMap(),
    val assetExcludes: Map<String, List<String>> = emptyMap()
)

data class QualcommSource(
    val id: String,
    val name: String,
    val apiUrl: String,
    val description: String,
    val experimental: Boolean,
    val filters: List<String>
)

data class ComponentSource(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val description: String,
    val experimental: Boolean
)

data class SourceCatalog(
    val turnipSources: List<TurnipSource>,
    val qualcommSources: List<QualcommSource>,
    val componentSources: List<ComponentSource>,
    val isRemote: Boolean = false
) {
    fun compatibleTurnipSources(adrenoSeries: String?): List<TurnipSource> {
        if (adrenoSeries.isNullOrBlank()) return turnipSources
        return turnipSources.filter { source ->
            source.supportedSeries.isEmpty() || adrenoSeries in source.supportedSeries
        }.ifEmpty { turnipSources }
    }
}

private val sourceCatalogClient by lazy { OkHttpClient() }

object SourceCatalogRepository {
    suspend fun load(url: String = SettingsManager.getSourceCatalogUrl()): SourceCatalog =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                sourceCatalogClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext builtInCatalog()
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext builtInCatalog()
                    parseCatalog(body).copy(isRemote = true)
                }
            } catch (_: Exception) {
                builtInCatalog()
            }
        }

    private fun parseCatalog(json: String): SourceCatalog {
        val root = JSONObject(json)
        val turnip = root.optJSONArray("turnipSources") ?: JSONArray()
        val qualcomm = root.optJSONArray("qualcommSources") ?: JSONArray()
        val components = root.optJSONArray("componentSources") ?: JSONArray()

        val turnipSources = buildList {
            for (i in 0 until turnip.length()) {
                val obj = turnip.optJSONObject(i) ?: continue
                if (!obj.optBoolean("enabled", true)) continue

                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                val apiUrl = obj.optString("apiUrl").trim()
                if (id.isBlank() || name.isBlank() || apiUrl.isBlank()) continue

                val seriesArray = obj.optJSONArray("supportedSeries") ?: JSONArray()
                val supportedSeries = buildSet {
                    for (s in 0 until seriesArray.length()) {
                        seriesArray.optString(s).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }

                val filtersObject = obj.optJSONObject("filters") ?: JSONObject()
                val filters = parseStringListMap(filtersObject)
                val assetIncludes = parseStringListMap(obj.optJSONObject("assetIncludes") ?: JSONObject())
                val assetExcludes = parseStringListMap(obj.optJSONObject("assetExcludes") ?: JSONObject())

                add(
                    TurnipSource(
                        id = id,
                        name = name,
                        apiUrl = apiUrl,
                        description = obj.optString("description"),
                        experimental = obj.optBoolean("experimental", false),
                        supportedSeries = supportedSeries,
                        filters = filters,
                        assetIncludes = assetIncludes,
                        assetExcludes = assetExcludes
                    )
                )
            }
        }

        val qualcommSources = buildList {
            for (i in 0 until qualcomm.length()) {
                val obj = qualcomm.optJSONObject(i) ?: continue
                if (!obj.optBoolean("enabled", true)) continue

                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                val apiUrl = obj.optString("apiUrl").trim()
                if (id.isBlank() || name.isBlank() || apiUrl.isBlank()) continue

                val filterArray = obj.optJSONArray("filters") ?: JSONArray()
                val filters = buildList {
                    for (f in 0 until filterArray.length()) {
                        filterArray.optString(f).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }

                add(
                    QualcommSource(
                        id = id,
                        name = name,
                        apiUrl = apiUrl,
                        description = obj.optString("description"),
                        experimental = obj.optBoolean("experimental", false),
                        filters = filters
                    )
                )
            }
        }

        val componentSources = buildList {
            for (i in 0 until components.length()) {
                val obj = components.optJSONObject(i) ?: continue
                if (!obj.optBoolean("enabled", true)) continue

                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                val manifestUrl = obj.optString("manifestUrl").trim()
                if (id.isBlank() || name.isBlank() || manifestUrl.isBlank()) continue

                add(
                    ComponentSource(
                        id = id,
                        name = name,
                        manifestUrl = manifestUrl,
                        description = obj.optString("description"),
                        experimental = obj.optBoolean("experimental", false)
                    )
                )
            }
        }

        val fallback = builtInCatalog()
        return SourceCatalog(
            turnipSources = turnipSources.ifEmpty { fallback.turnipSources },
            qualcommSources = qualcommSources.ifEmpty { fallback.qualcommSources },
            componentSources = componentSources.ifEmpty { fallback.componentSources }
        )
    }

    private fun parseStringListMap(obj: JSONObject): Map<String, List<String>> = buildMap {
        obj.keys().forEach { key ->
            val values = obj.optJSONArray(key) ?: return@forEach
            put(
                key,
                buildList {
                    for (i in 0 until values.length()) {
                        values.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            )
        }
    }

    fun builtInCatalog(): SourceCatalog = SourceCatalog(
        turnipSources = listOf(
            TurnipSource(
                id = "stevenmxz",
                name = "StevenMXZ",
                apiUrl = "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases",
                description = "Stable and Gen8 Turnip builds, plus Qualcomm drivers.",
                experimental = false,
                supportedSeries = setOf("6xx", "7xx", "8xx"),
                filters = mapOf(
                    "6xx" to listOf("Turnip v26"),
                    "7xx" to listOf("Turnip v26"),
                    "8xx" to listOf("Turnip Gen8")
                )
            ),
            TurnipSource(
                id = "whitebelyash",
                name = "whitebelyash",
                apiUrl = "https://api.github.com/repos/whitebelyash/AdrenoToolsDrivers/releases",
                description = "Mainline/Stable Turnip builds with broad Adreno support.",
                experimental = true,
                supportedSeries = setOf("6xx", "7xx", "8xx"),
                filters = mapOf(
                    "default" to listOf("A8XX", "Mainline Turnip", "Stable Turnip", "tu_", "stu_")
                )
            ),
            TurnipSource(
                id = "k11mch1",
                name = "K11MCH1",
                apiUrl = "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases",
                description = "Long-running AdrenoTools Turnip driver repository.",
                experimental = false,
                supportedSeries = setOf("6xx", "7xx"),
                filters = mapOf("default" to listOf("Turnip"))
            ),
            TurnipSource(
                id = "mrpurple",
                name = "MrPurple",
                apiUrl = "https://api.github.com/repos/MrPurple666/purple-turnip/releases",
                description = "Unified custom Turnip builds for A6xx/A7xx/A8xx.",
                experimental = true,
                supportedSeries = setOf("6xx", "7xx", "8xx"),
                filters = mapOf("default" to listOf("Turnip", "vturnip", "turnip_mrpurple"))
            ),
            TurnipSource(
                id = "banner",
                name = "The412Banner",
                apiUrl = "https://api.github.com/repos/The412Banner/Banners-Turnip/releases",
                description = "Automated bleeding-edge Mesa Turnip builds.",
                experimental = true,
                supportedSeries = setOf("6xx", "7xx", "8xx"),
                filters = mapOf("default" to listOf("Turnip", "Mesa", "Adreno")),
                assetIncludes = mapOf("8xx" to listOf("A8xx")),
                assetExcludes = mapOf(
                    "6xx" to listOf("A8xx", "710-720-Test"),
                    "7xx" to listOf("A8xx", "710-720-Test")
                )
            )
        ),
        qualcommSources = listOf(
            QualcommSource(
                id = "stevenmxz-qualcomm",
                name = "StevenMXZ",
                apiUrl = "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases",
                description = "Qualcomm proprietary driver packages published by StevenMXZ.",
                experimental = false,
                filters = listOf("Qualcomm")
            ),
            QualcommSource(
                id = "k11mch1-qualcomm",
                name = "K11MCH1",
                apiUrl = "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases",
                description = "Qualcomm driver packages extracted from Qualcomm-based devices.",
                experimental = false,
                filters = listOf("Qualcomm Driver", "Qualcomm")
            )
        ),
        componentSources = listOf(
            ComponentSource(
                id = "winnative",
                name = "WinNative-Emu",
                manifestUrl = "https://raw.githubusercontent.com/WinNative-Emu/Components/refs/heads/main/contents.json",
                description = "EmuHub default component catalog.",
                experimental = false
            ),
            ComponentSource(
                id = "xnick",
                name = "Xnick Nightly",
                manifestUrl = "https://raw.githubusercontent.com/nicholasx417/WinNative-Components/refs/heads/main/contents.json",
                description = "Automated WinNative/Winlator component builds.",
                experimental = true
            ),
            ComponentSource(
                id = "banner-components",
                name = "The412Banner",
                manifestUrl = "https://raw.githubusercontent.com/The412Banner/winlator-contents/main/contents.json",
                description = "Nightly Winlator component catalog.",
                experimental = true
            )
        )
    )
}
