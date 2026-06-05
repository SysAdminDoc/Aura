package com.freevibe.data.legal

import com.freevibe.data.model.ContentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDisclosureTest {

    @Test
    fun `provider disclosures cover every content source exactly once`() {
        val coveredSources = providerDisclosures.map { it.source }

        assertEquals(ContentSource.entries.toSet(), coveredSources.toSet())
        assertEquals(coveredSources.size, coveredSources.toSet().size)
    }

    @Test
    fun `provider disclosures include user visible policy fields`() {
        providerDisclosures.forEach { disclosure ->
            assertTrue("${disclosure.source} displayName", disclosure.displayName.isNotBlank())
            assertTrue("${disclosure.source} content", disclosure.content.isNotBlank())
            assertTrue("${disclosure.source} termsUrl", disclosure.termsUrl.startsWith("https://"))
            assertTrue("${disclosure.source} licenseSummary", disclosure.licenseSummary.isNotBlank())
            assertTrue("${disclosure.source} attribution", disclosure.attribution.isNotBlank())
            assertTrue("${disclosure.source} cachePolicy", disclosure.cachePolicy.isNotBlank())
            assertTrue("${disclosure.source} userActions", disclosure.userActions.isNotBlank())
            assertTrue("${disclosure.source} storeDisclosure", disclosure.storeDisclosure.isNotBlank())
        }
    }
}
