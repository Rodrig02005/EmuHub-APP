package com.notzeetaa.emuhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class GithubRelease(
    val tagName: String,
    val name: String,
    val assets: List<GithubAsset>,
    val publishedAt: String = ""
)

data class GithubAsset(val name: String, val downloadUrl: String, val sizeBytes: Long)
data class Component(val type: String, val verName: String, val verCode: String, val remoteUrl: String)

private val githubClient by lazy { OkHttpClient() }

suspend fun fetchGithubReleasesFromUrl(apiUrl: String): List<GithubRelease> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(apiUrl)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()

    try {
        githubClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val json = response.body?.string() ?: return@withContext emptyList()
            val releasesArray = JSONArray(json)

            (0 until releasesArray.length()).mapNotNull { idx ->
                val obj = releasesArray.getJSONObject(idx)
                if (obj.optBoolean("draft", false)) return@mapNotNull null

                val tagName = obj.optString("tag_name")
                if (tagName.isBlank()) return@mapNotNull null

                val name = obj.optString("name").ifBlank { tagName }
                val assetsArray = obj.optJSONArray("assets") ?: JSONArray()
                val assets = (0 until assetsArray.length()).map { assetIdx ->
                    val asset = assetsArray.getJSONObject(assetIdx)
                    GithubAsset(
                        name = asset.optString("name"),
                        downloadUrl = asset.optString("browser_download_url"),
                        sizeBytes = asset.optLong("size", 0L)
                    )
                }.filter { it.name.isNotBlank() && it.downloadUrl.isNotBlank() }

                GithubRelease(
                    tagName = tagName,
                    name = name,
                    assets = assets,
                    publishedAt = obj.optString("published_at", obj.optString("created_at", ""))
                )
            }.sortGithubReleasesNewestFirst()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun fetchTurnipReleases(source: TurnipSource, adrenoSeries: String): List<GithubRelease> {
    val allReleases = fetchGithubReleasesFromUrl(source.apiUrl)
    if (allReleases.isEmpty()) return emptyList()

    val patterns = source.filters[adrenoSeries]
        ?: source.filters["default"]
        ?: emptyList()

    // A source can intentionally omit filters when every release in the repo is relevant.
    val filtered = if (patterns.isEmpty()) {
        allReleases
    } else {
        allReleases.filter { release ->
            val searchable = buildString {
                append(release.name)
                append(' ')
                append(release.tagName)
                release.assets.forEach { asset ->
                    append(' ')
                    append(asset.name)
                }
            }
            patterns.any { pattern -> searchable.contains(pattern, ignoreCase = true) }
        }
    }

    // If an upstream maintainer changes naming, prefer showing Turnip-looking releases
    // rather than presenting an empty page. This keeps remote catalogs resilient.
    val fallback = allReleases.filter { release ->
        val text = "${release.name} ${release.tagName} ${release.assets.joinToString { it.name }}"
        text.contains("turnip", ignoreCase = true) ||
            text.contains("mesa", ignoreCase = true) ||
            text.contains("adreno", ignoreCase = true)
    }

    val includeAssetPatterns = source.assetIncludes[adrenoSeries]
        ?: source.assetIncludes["default"]
        ?: emptyList()
    val excludeAssetPatterns = source.assetExcludes[adrenoSeries]
        ?: source.assetExcludes["default"]
        ?: emptyList()

    return (filtered.ifEmpty { fallback }.ifEmpty { allReleases })
        .map { release ->
            val compatibleAssets = release.assets.filter { asset ->
                val included = includeAssetPatterns.isEmpty() ||
                    includeAssetPatterns.any { asset.name.contains(it, ignoreCase = true) }
                val excluded = excludeAssetPatterns.any { asset.name.contains(it, ignoreCase = true) }
                included && !excluded
            }
            release.copy(assets = compatibleAssets)
        }
        .filter { it.assets.isNotEmpty() }
        .sortGithubReleasesNewestFirst()
}

suspend fun fetchQualcommReleases(source: QualcommSource): List<GithubRelease> {
    val releases = fetchGithubReleasesFromUrl(source.apiUrl)
    if (source.filters.isEmpty()) return releases

    return releases.filter { release ->
        val searchable = buildString {
            append(release.name)
            append(' ')
            append(release.tagName)
            release.assets.forEach { asset ->
                append(' ')
                append(asset.name)
            }
        }
        source.filters.any { filter -> searchable.contains(filter, ignoreCase = true) }
    }.sortGithubReleasesNewestFirst()
}

/** Backwards-compatible default used by older call sites/tests. */
suspend fun loadQualcommDriver(): GithubRelease? {
    val source = SourceCatalogRepository.builtInCatalog().qualcommSources.first()
    return fetchQualcommReleases(source).firstOrNull()
}

suspend fun fetchComponentsFromUrl(manifestUrl: String): Map<String, List<Component>> =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(manifestUrl).build()

        try {
            githubClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyMap()
                val jsonArray = JSONArray(response.body?.string() ?: return@withContext emptyMap())
                val map = mutableMapOf<String, MutableList<Component>>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val type = obj.optString("type").trim()
                    val verName = obj.optString("verName").trim()
                    val remoteUrl = obj.optString("remoteUrl").trim()
                    if (type.isBlank() || verName.isBlank() || remoteUrl.isBlank()) continue

                    val component = Component(
                        type = type,
                        verName = verName,
                        verCode = obj.opt("verCode")?.toString().orEmpty(),
                        remoteUrl = remoteUrl
                    )
                    map.getOrPut(component.type) { mutableListOf() }.add(component)
                }

                map.forEach { (_, list) ->
                    list.sortWith { a, b -> naturalVersionCompare(b.verName, a.verName) }
                }
                map
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

/** Backwards-compatible default used by older call sites/tests. */
suspend fun fetchComponentsFromUrl(): Map<String, List<Component>> =
    fetchComponentsFromUrl(SourceCatalogRepository.builtInCatalog().componentSources.first().manifestUrl)

private fun List<GithubRelease>.sortGithubReleasesNewestFirst(): List<GithubRelease> =
    sortedWith { a, b ->
        val dateResult = b.publishedAt.compareTo(a.publishedAt)
        if (dateResult != 0) dateResult else naturalVersionCompare(b.tagName, a.tagName)
    }

private fun naturalVersionCompare(v1: String, v2: String): Int {
    fun parseVersion(value: String): List<Long> =
        Regex("\\d+").findAll(value).map { it.value.toLongOrNull() ?: 0L }.toList()

    val parts1 = parseVersion(v1)
    val parts2 = parseVersion(v2)
    val maxLen = maxOf(parts1.size, parts2.size)

    for (i in 0 until maxLen) {
        val num1 = parts1.getOrNull(i) ?: 0L
        val num2 = parts2.getOrNull(i) ?: 0L
        if (num1 != num2) return num1.compareTo(num2)
    }

    return v1.compareTo(v2, ignoreCase = true)
}
