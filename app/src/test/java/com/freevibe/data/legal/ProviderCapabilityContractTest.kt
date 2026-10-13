package com.freevibe.data.legal

import com.freevibe.BuildConfig
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.providerNetworkPolicies
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gate that keeps provider truth single-source.
 *
 * Every fact about a content source — is it fetched, in which builds, on which
 * channels, behind which switch, needing which key or permission — lives once in
 * [providerCapabilities]. The disclosure list, the runtime-control list, and the
 * network-endpoint manifest are all checked against it here, so a source can no
 * longer read "dormant" in one file while another still fetches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProviderCapabilityContractTest {

    private val endpointManifest: JSONObject by lazy {
        JSONObject(File("../docs/security/network-endpoints.json").readText())
    }

    private val providerManifest: JSONObject by lazy {
        JSONObject(File("../docs/providers/provider-manifest.json").readText())
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = getJSONArray(key)
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }

    private fun providerManifestById(): Map<String, JSONObject> {
        val array = providerManifest.getJSONArray("providers")
        return (0 until array.length())
            .map { array.getJSONObject(it) }
            .associateBy { it.getString("id") }
    }

    private fun endpointsById(): Map<String, JSONObject> {
        val array = endpointManifest.getJSONArray("endpoints")
        return (0 until array.length())
            .map { array.getJSONObject(it) }
            .associateBy { it.getString("id") }
    }

    @Test
    fun `every content source appears exactly once`() {
        val sources = providerCapabilities.map { it.source }

        assertEquals(
            "duplicate registry entries: " +
                sources.groupBy { it }.filterValues { it.size > 1 }.keys,
            sources.size,
            sources.toSet().size,
        )
        assertEquals(ContentSource.entries.toSet(), sources.toSet())
    }

    @Test
    fun `checked provider manifest matches every production capability`() {
        val manifestById = providerManifestById()
        assertEquals(ContentSource.entries.map { it.name }.toSet(), manifestById.keys)

        providerCapabilities.forEach { capability ->
            val row = manifestById.getValue(capability.source.name)
            val priorities = row.getJSONObject("defaultPriority")
            val manifestPriorities = priorities.keys().asSequence()
                .associateWith { priorities.getInt(it) }
            val disclosure = providerDisclosuresBySource.getValue(capability.source)

            assertEquals(capability.source.name, disclosure.displayName, row.getString("displayName"))
            assertEquals(capability.mediaTypes.map { it.name }.toSet(), row.stringSet("mediaTypes"))
            assertEquals(
                capability.defaultPriority.mapKeys { it.key.name },
                manifestPriorities,
            )
            assertEquals(
                capability.permittedActions.map { it.name }.toSet(),
                row.stringSet("permittedActions"),
            )
            assertEquals(capability.lifecycle.name, row.getString("lifecycle"))
            assertEquals(capability.builds.map { it.name }.toSet(), row.stringSet("buildFlavors"))
            assertEquals(capability.channels.map { it.name }.toSet(), row.stringSet("releaseChannels"))
            assertEquals(capability.configuration.name, row.getString("credentialNeed"))
            assertEquals(capability.permission.name, row.getString("permission"))
            assertEquals(capability.health.name, row.getString("health"))
            assertEquals(capability.requiresAttribution, row.getBoolean("requiresAttribution"))
            assertEquals(capability.enabledByDefault, row.getBoolean("enabledByDefault"))
            assertEquals(
                capability.killSwitchKey,
                if (row.isNull("killSwitchKey")) null else row.getString("killSwitchKey"),
            )
            assertEquals(capability.endpointIds, row.stringSet("endpointIds"))
        }
    }

    @Test
    fun `manifest priorities put Reddit first wherever Reddit is available`() {
        ProviderBuild.entries.forEach { build ->
            ProviderChannel.entries.forEach { channel ->
                listOf(ProviderMediaType.WALLPAPER, ProviderMediaType.VIDEO).forEach { media ->
                    val ordered = orderedProviderCapabilities(media, build, channel)
                    if (ordered.any { it.source == ContentSource.REDDIT }) {
                        assertEquals(ContentSource.REDDIT, ordered.first().source)
                    }
                }
            }
        }
        assertEquals(
            ContentSource.YOUTUBE,
            orderedProviderCapabilities(
                ProviderMediaType.SOUND,
                ProviderBuild.FULL,
                ProviderChannel.GITHUB,
            ).first().source,
        )
        assertEquals(
            ContentSource.BUNDLED,
            orderedProviderCapabilities(
                ProviderMediaType.SOUND,
                ProviderBuild.FULL,
                ProviderChannel.PLAY,
            ).first().source,
        )
    }

    @Test
    fun `legacy providers expose saved attribution actions only`() {
        providerCapabilities
            .filter { it.lifecycle == ProviderLifecycle.LEGACY }
            .forEach { capability ->
                assertEquals(ProviderConfiguration.NONE, capability.configuration)
                assertEquals(ProviderHealth.OFFLINE, capability.health)
                assertEquals(
                    setOf(
                        ProviderAction.VIEW_SAVED,
                        ProviderAction.REMOVE_SAVED,
                        ProviderAction.OPEN_SOURCE,
                    ),
                    capability.permittedActions,
                )
            }
    }

    @Test
    fun `disclosure status is derived from lifecycle`() {
        providerCapabilities.forEach { capability ->
            val disclosure = providerDisclosuresBySource.getValue(capability.source)
            assertEquals(
                "${capability.source} disclosure status must match its lifecycle",
                capability.lifecycle.disclosureStatus(),
                disclosure.status,
            )
        }
    }

    @Test
    fun `every source has a runtime control entry`() {
        assertEquals(
            ContentSource.entries.toSet(),
            providerRuntimeControls.map { it.source }.toSet(),
        )
        assertEquals(
            providerRuntimeControls.size,
            providerRuntimeControls.map { it.source }.toSet().size,
        )
    }

    @Test
    fun `impossible active and legacy combinations fail the gate`() {
        providerCapabilities.forEach { capability ->
            when (capability.lifecycle) {
                ProviderLifecycle.LEGACY -> assertFalse(
                    "${capability.source} is legacy, so it must not be able to fetch",
                    capability.canFetch,
                )
                ProviderLifecycle.ACTIVE -> {
                    if (capability.health == ProviderHealth.NETWORKED) {
                        assertTrue(
                            "${capability.source} is an active networked source and must declare endpoints",
                            capability.endpointIds.isNotEmpty(),
                        )
                    }
                }
                ProviderLifecycle.LOCAL -> {
                    assertTrue(
                        "${capability.source} is local and must not declare network endpoints",
                        capability.endpointIds.isEmpty(),
                    )
                    assertEquals(ProviderHealth.OFFLINE, capability.health)
                }
                ProviderLifecycle.COMMUNITY, ProviderLifecycle.GENERATED -> assertTrue(
                    "${capability.source} must declare the endpoints it calls",
                    capability.endpointIds.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `offline sources are never described as networked`() {
        providerCapabilities
            .filter { it.health == ProviderHealth.OFFLINE }
            .forEach { assertFalse("${it.source} cannot fetch while offline", it.canFetch) }
    }

    @Test
    fun `a required key is never paired with an on-by-default source`() {
        providerCapabilities
            .filter { it.configuration == ProviderConfiguration.REQUIRED_KEY }
            .forEach {
                assertFalse(
                    "${it.source} needs a user credential, so it cannot ship enabled",
                    it.enabledByDefault,
                )
            }
    }

    @Test
    fun `a permission-gated source is never on by default`() {
        providerCapabilities
            .filter { it.permission == ProviderPermission.APPROXIMATE_LOCATION }
            .forEach {
                assertFalse(
                    "${it.source} needs a runtime permission, so it cannot ship enabled",
                    it.enabledByDefault,
                )
            }
    }

    @Test
    fun `declared endpoints exist in the network manifest`() {
        val endpoints = endpointsById()
        providerCapabilities.forEach { capability ->
            capability.endpointIds.forEach { id ->
                assertTrue(
                    "${capability.source} declares unknown endpoint id $id",
                    endpoints.containsKey(id),
                )
            }
        }
    }

    @Test
    fun `every manifest endpoint is owned by exactly one source`() {
        val declared = providerCapabilities.flatMap { it.endpointIds } + appEndpointIds
        assertEquals(
            "an endpoint may not be claimed twice",
            declared.size,
            declared.toSet().size,
        )
        assertEquals(
            "every documented endpoint must belong to a registry entry",
            endpointsById().keys,
            declared.toSet(),
        )
    }

    @Test
    fun `kill switch keys match the manifest`() {
        val endpoints = endpointsById()
        providerCapabilities
            .filter { it.killSwitchKey != null }
            .forEach { capability ->
                val key = capability.killSwitchKey!!
                val owns = capability.endpointIds.any { id ->
                    endpoints.getValue(id).getString("killSwitch").contains(key)
                }
                assertTrue(
                    "${capability.source} declares kill switch $key that no endpoint documents",
                    owns,
                )
            }
    }

    @Test
    fun `FOSS builds drop exactly the full-only sources`() {
        val fossExcluded = providerCapabilities
            .filterNot { it.availableIn(ProviderBuild.FOSS) }
            .map { it.source }
            .toSet()

        assertEquals(setOf(ContentSource.COMMUNITY, ContentSource.AI_GENERATED), fossExcluded)
    }

    @Test
    fun `the Play channel drops exactly the sources Aura's own risk profile forbids`() {
        val playExcluded = providerCapabilities
            .filterNot { it.availableOn(ProviderChannel.PLAY) }
            .map { it.source }
            .toSet()

        assertEquals(setOf(ContentSource.YOUTUBE, ContentSource.TIKTOK), playExcluded)
        assertTrue(
            providerCapability(ContentSource.TIKTOK).availableIn(
                ProviderBuild.FULL,
                ProviderChannel.GITHUB,
            ),
        )
        assertTrue(
            providerCapability(ContentSource.YOUTUBE).availableIn(
                ProviderBuild.FULL,
                ProviderChannel.GITHUB,
            ),
        )
        assertFalse(
            providerCapability(ContentSource.YOUTUBE).availableIn(
                ProviderBuild.FULL,
                ProviderChannel.PLAY,
            ),
        )
    }

    @Test
    fun `current artifact availability uses the compiled release channel`() {
        assertEquals(providerChannel(BuildConfig.AURA_RELEASE_CHANNEL), currentProviderChannel)
        assertEquals(
            currentProviderChannel == ProviderChannel.GITHUB,
            isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE),
        )
    }

    @Test
    fun `provider action ceiling covers legacy and bundled sound actions`() {
        assertFalse(isProviderActionPermitted(ContentSource.FREESOUND, ProviderAction.APPLY))
        assertFalse(isProviderActionPermitted(ContentSource.FREESOUND, ProviderAction.BUNDLE))
        assertTrue(isProviderActionPermitted(ContentSource.FREESOUND, ProviderAction.OPEN_SOURCE))
        assertTrue(isProviderActionPermitted(ContentSource.BUNDLED, ProviderAction.BUNDLE))
        assertTrue(
            isProviderActionPermittedIn(
                ContentSource.YOUTUBE,
                ProviderAction.PREVIEW,
                ProviderBuild.FULL,
                ProviderChannel.GITHUB,
            ),
        )
        assertFalse(
            isProviderActionPermittedIn(
                ContentSource.YOUTUBE,
                ProviderAction.PREVIEW,
                ProviderBuild.FULL,
                ProviderChannel.PLAY,
            ),
        )
    }

    @Test
    fun `attribution is required for every source that is not the user's own files`() {
        providerCapabilities.forEach { capability ->
            if (capability.source == ContentSource.LOCAL) {
                assertFalse(capability.requiresAttribution)
            } else {
                assertTrue(
                    "${capability.source} must carry provider attribution",
                    capability.requiresAttribution,
                )
            }
        }
    }

    @Test
    fun `every source has a network policy whose diagnostics carry registry truth`() {
        assertEquals(
            ContentSource.entries.toSet(),
            providerNetworkPolicies.map { it.source }.toSet(),
        )
        providerNetworkPolicies.forEach { policy ->
            val capability = providerCapability(policy.source)
            assertTrue(
                "${policy.source} diagnostics must report its lifecycle",
                policy.capabilitySummary.contains(capability.lifecycle.name.lowercase()),
            )
            assertTrue(
                "${policy.source} diagnostics must report its media types",
                policy.capabilitySummary.contains("media "),
            )
            assertTrue(
                "${policy.source} diagnostics must report its feed priority",
                policy.capabilitySummary.contains("priority "),
            )
            assertTrue(
                "${policy.source} diagnostics must report its permitted actions",
                policy.capabilitySummary.contains("actions "),
            )
            assertTrue(
                "${policy.source} diagnostics must be part of the support summary",
                policy.diagnosticSummary.startsWith(policy.capabilitySummary),
            )
        }
    }

    @Test
    fun `lookup covers every enum value`() {
        ContentSource.entries.forEach { source ->
            assertEquals(source, providerCapability(source).source)
        }
    }
}
