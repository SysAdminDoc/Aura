package com.freevibe.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryDropdownTest {

    @Test
    fun `recent search actions include the query`() {
        assertEquals("Search for amoled forest", recentSearchActionLabel("amoled forest"))
        assertEquals(
            "Remove amoled forest from recent searches",
            removeRecentSearchLabel("amoled forest"),
        )
    }
}
