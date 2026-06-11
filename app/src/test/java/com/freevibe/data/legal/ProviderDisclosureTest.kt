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

    @Test
    fun `open meteo disclosure carries cc by attribution requirements`() {
        val disclosure = providerDisclosuresBySource.getValue(ContentSource.OPEN_METEO)

        assertEquals("Open-Meteo", disclosure.displayName)
        assertTrue(disclosure.termsUrl.contains("open-meteo.com"))
        assertTrue(disclosure.licenseSummary.contains("CC BY 4.0"))
        assertTrue(disclosure.attribution.contains("Open-Meteo.com"))
    }

    @Test
    fun `provider runtime controls cover every content source exactly once`() {
        val coveredSources = providerRuntimeControls.map { it.source }

        assertEquals(ContentSource.entries.toSet(), coveredSources.toSet())
        assertEquals(coveredSources.size, coveredSources.toSet().size)
    }

    @Test
    fun `provider runtime controls include disabled behavior and follow ups`() {
        providerRuntimeControls.forEach { control ->
            assertTrue("${control.source} surfaces", control.surfaces.isNotBlank())
            assertTrue("${control.source} currentControl", control.currentControl.isNotBlank())
            assertTrue("${control.source} disabledBehavior", control.disabledBehavior.isNotBlank())
            assertTrue("${control.source} followUp", control.followUp.isNotBlank())
            if (control.status == ProviderRuntimeControlStatus.PARTIAL ||
                control.status == ProviderRuntimeControlStatus.MISSING
            ) {
                assertTrue("${control.source} unresolved followUp", control.followUp != "None.")
            }
        }
    }

    @Test
    fun `active network sources have runtime control decisions`() {
        val controlledStatuses = setOf(
            ProviderRuntimeControlStatus.COVERED,
            ProviderRuntimeControlStatus.PARTIAL,
            ProviderRuntimeControlStatus.MISSING,
        )
        providerDisclosures
            .filter { it.status in setOf(ProviderStatus.ACTIVE, ProviderStatus.COMMUNITY, ProviderStatus.GENERATED) }
            .forEach { disclosure ->
                val control = providerRuntimeControlsBySource.getValue(disclosure.source)
                assertTrue("${disclosure.source} runtime status", control.status in controlledStatuses)
            }
    }
}
