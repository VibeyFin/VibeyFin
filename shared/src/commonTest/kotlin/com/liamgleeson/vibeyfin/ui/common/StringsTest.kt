package com.liamgleeson.vibeyfin.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    @Test
    fun cardLabelNamesTheTitleAndYear() {
        assertEquals("Heat, 1995", Strings.cardLabel("Heat", 1995))
    }

    @Test
    fun cardLabelLeavesOutAMissingYear() {
        assertEquals("Movies", Strings.cardLabel("Movies", null))
    }
}
