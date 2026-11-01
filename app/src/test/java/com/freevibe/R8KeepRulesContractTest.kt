package com.freevibe

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class R8KeepRulesContractTest {
    private val rules = File("proguard-rules.pro").readText().replace("\r\n", "\n")

    @Test
    fun `every app enum keeps its field names for Moshi's enum adapter`() {
        // Renamed fields crash at adapter creation with NoSuchFieldException, and only in a
        // minified build: RedditFeedMediaType took down the Videos tab that way.
        assertTrue(
            rules.contains(
                """
                -keepclassmembers enum com.freevibe.** {
                    <fields>;
                    **[] values();
                }
                """.trimIndent(),
            ),
        )
    }
}
