package com.freevibe.ui.localization

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationExtractionContractTest {

    @Test
    fun `community moderation dialogs use string resources for local UI copy`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val expectations = listOf(
            SourceExpectation(
                path = "src/main/java/com/freevibe/ui/components/CommunityGuidelinesDialog.kt",
                requiredResourceNames = listOf(
                    "community_guidelines_intro",
                    "community_guidelines_retention_note",
                    "community_guidelines_accept",
                    "common_reset",
                    "common_cancel",
                ),
                forbiddenCopy = listOf(
                    "Read and accept these rules",
                    "Aura may hide, remove",
                    "I agree",
                    "Cancel",
                ),
            ),
            SourceExpectation(
                path = "src/main/java/com/freevibe/ui/components/CommunityReportDialog.kt",
                requiredResourceNames = listOf(
                    "community_report_reason_prompt",
                    "community_report_details_optional",
                    "community_report_note_counter",
                    "community_report_submit",
                    "common_cancel",
                ),
                forbiddenCopy = listOf(
                    "Choose the closest reason",
                    "Details optional",
                    "Submit report",
                    "Cancel",
                ),
            ),
        )

        expectations.forEach { expectation ->
            val source = File(expectation.path).readText()
            expectation.requiredResourceNames.forEach { resourceName ->
                assertTrue("${expectation.path} should use $resourceName", source.contains("R.string.$resourceName"))
                assertTrue("strings.xml should define $resourceName", strings.contains("name=\"$resourceName\""))
            }
            expectation.forbiddenCopy.forEach { copy ->
                assertFalse("${expectation.path} should not hardcode $copy", source.contains(copy))
            }
        }
    }

    private data class SourceExpectation(
        val path: String,
        val requiredResourceNames: List<String>,
        val forbiddenCopy: List<String>,
    )
}
