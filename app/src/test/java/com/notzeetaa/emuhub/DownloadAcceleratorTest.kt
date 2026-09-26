package com.notzeetaa.emuhub

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadAcceleratorTest {

    private val ghproxy = DownloadAccelerator.GHPROXY_NET

    @Test
    fun `prefixes github release downloads`() {
        val url = "https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/download/v26/turnip.zip"
        assertEquals("https://ghproxy.net/$url", applyDownloadAccelerator(url, ghproxy))
    }

    @Test
    fun `prefixes raw and api github hosts`() {
        val raw = "https://raw.githubusercontent.com/Rodrig02005/EmuHub-APP/main/sources.json"
        val api = "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases"
        assertEquals("https://ghproxy.net/$raw", applyDownloadAccelerator(raw, ghproxy))
        assertEquals("https://ghproxy.net/$api", applyDownloadAccelerator(api, ghproxy))
    }

    @Test
    fun `leaves non github urls untouched`() {
        val url = "https://example.com/files/turnip.zip"
        assertEquals(url, applyDownloadAccelerator(url, ghproxy))
    }

    @Test
    fun `null accelerator disables rewrite`() {
        val url = "https://github.com/foo/bar/releases/download/1/a.zip"
        assertEquals(url, applyDownloadAccelerator(url, null))
    }

    @Test
    fun `does not prefix an already accelerated url`() {
        val prefixed = "https://ghproxy.net/https://github.com/foo/bar/releases/download/1/a.zip"
        assertEquals(prefixed, applyDownloadAccelerator(prefixed, ghproxy))
    }

    @Test
    fun `fromId falls back to default`() {
        assertEquals(DownloadAccelerator.DEFAULT, DownloadAccelerator.fromId("does_not_exist"))
        assertEquals(DownloadAccelerator.GHFAST_TOP, DownloadAccelerator.fromId("ghfast_top"))
    }

    @Test
    fun `custom prefix is normalized and applied`() {
        val url = "https://github.com/foo/bar/releases/download/1/a.zip"
        assertEquals(
            "https://ghfast.top/$url",
            applyDownloadAccelerator(url, DownloadAccelerator.CUSTOM, "https://ghfast.top/")
        )
        assertEquals(
            "https://ghfast.top/$url",
            applyDownloadAccelerator(url, DownloadAccelerator.CUSTOM, "ghfast.top")
        )
    }

    @Test
    fun `blank custom prefix disables rewrite`() {
        val url = "https://github.com/foo/bar/releases/download/1/a.zip"
        assertEquals(url, applyDownloadAccelerator(url, DownloadAccelerator.CUSTOM, "   "))
    }

    @Test
    fun `custom prefix is ignored for built in schemes`() {
        val url = "https://github.com/foo/bar/releases/download/1/a.zip"
        assertEquals(
            "https://gh-proxy.com/$url",
            applyDownloadAccelerator(url, DownloadAccelerator.GH_PROXY_COM, "https://ghfast.top/")
        )
    }

    @Test
    fun `speed test targets include built in schemes and direct`() {
        val targets = speedTestTargets(customPrefix = "")
        val schemes = targets.mapNotNull { it.accelerator }
        assertEquals(DownloadAccelerator.entries.count { it != DownloadAccelerator.CUSTOM }, schemes.size)
        assertEquals(null, targets.last().accelerator)
        assertEquals(true, targets.last().isDirect)
    }

    @Test
    fun `speed test targets add custom only when prefix is set`() {
        val without = speedTestTargets(customPrefix = "   ")
        assertEquals(0, without.count { it.accelerator == DownloadAccelerator.CUSTOM })

        val with = speedTestTargets(customPrefix = "ghfast.top")
        val custom = with.firstOrNull { it.accelerator == DownloadAccelerator.CUSTOM }
        assertEquals("ghfast.top", custom?.customPrefix)
    }
}
