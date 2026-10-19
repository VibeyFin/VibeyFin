package com.liamgleeson.vibeyfin.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerAddressesTest {
    private fun candidates(input: String) = ServerAddresses.candidates(input)

    @Test
    fun aBareHostTriesHttpsThenHttpEachWithAndWithoutTheDefaultPort() {
        assertEquals(
            listOf("https://nas", "https://nas:8920", "http://nas", "http://nas:8096"),
            candidates("nas"),
        )
    }

    @Test
    fun anIpAddressIsTreatedLikeAHostname() {
        assertEquals(
            listOf(
                "https://192.168.1.20",
                "https://192.168.1.20:8920",
                "http://192.168.1.20",
                "http://192.168.1.20:8096",
            ),
            candidates("192.168.1.20"),
        )
    }

    @Test
    fun aGivenPortIsUsedAsIsWithBothSchemes() {
        assertEquals(listOf("https://nas:8096", "http://nas:8096"), candidates("nas:8096"))
    }

    @Test
    fun aGivenSchemeIsNeverSwapped() {
        assertEquals(listOf("http://nas:8096"), candidates("http://nas:8096"))
        assertEquals(listOf("https://jelly.example.com:8920"), candidates("https://jelly.example.com:8920"))
    }

    @Test
    fun aSchemeWithoutAPortAddsJellyfinsDefaultForThatScheme() {
        assertEquals(
            listOf("https://jelly.example.com", "https://jelly.example.com:8920"),
            candidates("https://jelly.example.com"),
        )
        assertEquals(listOf("http://nas", "http://nas:8096"), candidates("http://nas"))
    }

    @Test
    fun theSchemesDefaultPortIsTheBareHost() {
        assertEquals(listOf("http://nas"), candidates("http://nas:80"))
        assertEquals(listOf("https://nas"), candidates("https://nas:443"))
        assertEquals(listOf("https://nas:80", "http://nas"), candidates("nas:80"))
    }

    @Test
    fun hostAndSchemeAreLowerCasedAndTheTrailingSlashDropped() {
        assertEquals(listOf("http://nas.local:8096"), candidates("HTTP://NAS.Local:8096/"))
    }

    @Test
    fun surroundingSpacesAndATrailingDotAreIgnored() {
        assertEquals(candidates("nas"), candidates("  nas  "))
        assertEquals(candidates("nas"), candidates("nas."))
    }

    @Test
    fun theWebSuffixIsStrippedInAnyCase() {
        val expected = listOf("https://jelly.example.com", "https://jelly.example.com:8920")

        assertEquals(expected, candidates("https://Jelly.Example.com/web"))
        assertEquals(expected, candidates("https://jelly.example.com/web/"))
        assertEquals(expected, candidates("https://jelly.example.com/web/index.html"))
        assertEquals(expected, candidates("https://jelly.example.com/Web/Index.HTML"))
    }

    @Test
    fun aBasePathBehindAReverseProxyIsKept() {
        assertEquals(
            listOf("https://example.com/jellyfin", "https://example.com:8920/jellyfin"),
            candidates("https://example.com/jellyfin/web/"),
        )
        assertEquals(listOf("https://demo.jellyfin.org/stable"), candidates("https://demo.jellyfin.org:443/stable"))
    }

    @Test
    fun queryAndFragmentAreDropped() {
        assertEquals(candidates("https://nas"), candidates("https://nas?x=1#top"))
    }

    @Test
    fun ipv6AddressesKeepTheirBrackets() {
        assertEquals(
            listOf("https://[::1]", "https://[::1]:8920", "http://[::1]", "http://[::1]:8096"),
            candidates("[::1]"),
        )
        assertEquals(listOf("https://[fd00::1]:8096", "http://[fd00::1]:8096"), candidates("[fd00::1]:8096"))
        assertEquals(listOf("http://[::1]:8096"), candidates("http://[::1]:8096"))
    }

    @Test
    fun noCandidateAppearsTwice() {
        val all = candidates("nas:80")

        assertEquals(all.distinct(), all)
    }

    @Test
    fun inputThatCannotBeAnAddressGivesNoCandidates() {
        val invalid = listOf(
            "",
            "   ",
            "http://",
            "https://",
            "ftp://nas",
            "nas:abc",
            "nas:",
            "nas:0",
            "nas:65536",
            "nas:8096:1",
            "::1",
            "[::1",
            "[::1]x",
            "user:pass@nas",
            "my nas",
            "na\$s",
            ".nas",
        )

        for (input in invalid) {
            assertEquals(emptyList(), candidates(input), "'$input' should not be an address")
        }
    }
}
