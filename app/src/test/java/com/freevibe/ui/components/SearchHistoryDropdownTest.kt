package com.freevibe.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryDropdownTest {

    @Test
    fun `recent search actions are resource backed`() {
        val source = File("src/main/java/com/freevibe/ui/components/SearchHistoryDropdown.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        listOf(
            "search_history_title",
            "search_history_clear_all",
            "search_history_recent_query_cd",
            "search_history_action_search_for",
            "search_history_remove_query",
        ).forEach { resourceName ->
            assertTrue(source.contains("R.string.$resourceName"))
            assertTrue(strings.contains("name=\"$resourceName\""))
        }
        listOf(
            "Recent searches",
            "Clear all",
            "Recent search:",
            "Search for",
            "from recent searches",
        ).forEach { hardcodedCopy ->
            assertFalse(source.contains(hardcodedCopy))
        }
    }
}
