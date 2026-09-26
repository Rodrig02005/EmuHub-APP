package com.notzeetaa.emuhub

import java.net.URI

/**
 * Free GitHub accelerators used to speed up downloads from mainland China.
 *
 * Every entry is a prefix-style proxy: the original GitHub URL is appended
 * verbatim to [prefix] (e.g. `https://ghproxy.net/https://github.com/...`).
 * The list is intentionally small and only contains mirrors that were verified
 * to answer range requests.
 */
enum class DownloadAccelerator(val id: String, val prefix: String) {
    GH_PROXY_COM("gh_proxy_com", "https://gh-proxy.com/"),
    GHPROXY_NET("ghproxy_net", "https://ghproxy.net/"),
    GHFAST_TOP("ghfast_top", "https://ghfast.top/"),
    GH_LLKK_CC("gh_llkk_cc", "https://gh.llkk.cc/"),
    GH_PROXY_VIP("ghproxy.vip", "https://ghproxy.vip/"),
    CUSTOM("custom", "");

    companion object {
        /** First scheme in the list; the default in Chinese regions. */
        val DEFAULT: DownloadAccelerator = GH_PROXY_COM

        fun fromId(id: String?): DownloadAccelerator =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Normalizes a user-entered mirror into a usable prefix: trims spaces, adds a
 * missing scheme and guarantees a trailing slash so `prefix + url` stays valid.
 */
fun normalizeProxyPrefix(raw: String): String {
    var value = raw.trim()
    if (value.isEmpty()) return ""
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "https://$value"
    }
    if (!value.endsWith("/")) value += "/"
    return value
}

/** Resolves the prefix for [accelerator], using [customPrefix] for [DownloadAccelerator.CUSTOM]. */
fun DownloadAccelerator.effectivePrefix(customPrefix: String = ""): String? = when (this) {
    DownloadAccelerator.CUSTOM -> normalizeProxyPrefix(customPrefix).takeIf { it.isNotEmpty() }
    else -> prefix
}

private val GITHUB_HOSTS = setOf(
    "github.com",
    "www.github.com",
    "api.github.com",
    "codeload.github.com",
    "raw.githubusercontent.com",
    "objects.githubusercontent.com",
    "gist.githubusercontent.com"
)

/** True when [url] points at GitHub and can be safely routed through a prefix proxy. */
fun isAccelerableGithubUrl(url: String): Boolean {
    val host = try {
        URI(url).host?.lowercase()
    } catch (_: Exception) {
        null
    }
    return host != null && host in GITHUB_HOSTS
}

/**
 * Pure URL rewrite (unit-testable): prefix GitHub URLs and leave everything else
 * untouched. Already-prefixed URLs have the proxy as their host, so they are never
 * prefixed twice. Passing a null [accelerator] disables the rewrite; a custom mirror
 * only applies when [accelerator] is [DownloadAccelerator.CUSTOM].
 */
fun applyDownloadAccelerator(
    url: String,
    accelerator: DownloadAccelerator?,
    customPrefix: String = ""
): String {
    if (accelerator == null || url.isBlank()) return url
    if (!isAccelerableGithubUrl(url)) return url
    val prefix = accelerator.effectivePrefix(customPrefix) ?: return url
    return prefix + url
}

/** Reads the user's settings and rewrites [url] accordingly. */
fun accelerateGithubUrl(url: String): String {
    val accelerator = if (SettingsManager.isDownloadAccelerationEnabled()) {
        SettingsManager.getDownloadAccelerator()
    } else {
        null
    }
    return applyDownloadAccelerator(url, accelerator, SettingsManager.getCustomAcceleratorPrefix())
}
