package com.freevibe.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoilMigrationContractTest {

    @Test
    fun `image pipeline is fully migrated to Coil 3`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val appBuild = File("build.gradle.kts").readText()
        val app = File("src/main/java/com/freevibe/FreeVibeApp.kt").readText()
        val widget = File("src/main/java/com/freevibe/widget/FreeVibeWidget.kt").readText()
        val mainSources = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(catalog.contains("coil = \"3."))
        assertTrue(catalog.contains("group = \"io.coil-kt.coil3\""))
        assertTrue(catalog.contains("coil-network-okhttp"))
        assertTrue(appBuild.contains("implementation(libs.coil.network.okhttp)"))
        assertFalse(mainSources.contains("import coil."))
        assertTrue(app.contains("SingletonImageLoader.Factory"))
        assertTrue(app.contains("OkHttpNetworkFetcherFactory"))
        assertTrue(app.contains("AnimatedImageDecoder.Factory()"))
        assertTrue(widget.contains("as? SuccessResult"))
        assertTrue(widget.contains("result.image.toBitmap()"))
    }

    @Test
    fun `every Compose artifact takes its version from one BOM`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val appBuild = File("build.gradle.kts").readText()

        // The BOM version itself is not pinned here. It moves with the toolchain
        // ceiling — 2026.07+ needs compileSdk 37 — and a test that names one
        // version only ever fails on the upgrade that was the point.
        assertTrue(catalog.contains("compose-bom = \""))
        // What must hold is that nothing pins a Compose artifact beside the BOM,
        // which is how a split Compose version gets in.
        assertFalse(catalog.contains("compose-ui-test ="))
        assertFalse(catalog.contains("material3 = \""))
        assertTrue(appBuild.split("platform(libs.compose.bom)").size - 1 >= 3)
    }
}
