package com.freevibe.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityPolicyCopyTest {

    @Test
    fun `upload policy copy declares public visibility and rights takedown outcomes`() {
        val soundCopy = communityUploadPolicyCopy(CommunityUploadPolicyKind.SOUND)
        val wallpaperCopy = communityUploadPolicyCopy(CommunityUploadPolicyKind.WALLPAPER)

        assertTrue(soundCopy.publicBody.contains("becomes public"))
        assertTrue(soundCopy.publicBody.contains("license"))
        assertTrue(soundCopy.publicBody.contains("public download URL"))
        assertTrue(soundCopy.publicBody.contains("sanitized file name"))
        assertTrue(soundCopy.takedownBody.contains("hide or delete"))
        assertTrue(soundCopy.takedownBody.contains("uploaded audio file"))
        assertTrue(wallpaperCopy.publicBody.contains("becomes public"))
        assertTrue(wallpaperCopy.takedownBody.contains("uploaded image file"))
    }

    @Test
    fun `attestation copy stays content specific`() {
        assertEquals(
            "I own or have rights to share this sound under the selected license.",
            communityUploadPolicyCopy(CommunityUploadPolicyKind.SOUND).attestation,
        )
        assertEquals(
            "I own or have rights to share this wallpaper under the selected license.",
            communityUploadPolicyCopy(CommunityUploadPolicyKind.WALLPAPER).attestation,
        )
    }

    @Test
    fun `owner delete copy explains public removal and private retention`() {
        val copy = communityOwnerDeleteConfirmationCopy(CommunityUploadPolicyKind.SOUND)

        assertTrue(copy.contains("public listing"))
        assertTrue(copy.contains("owner index"))
        assertTrue(copy.contains("Private deletion or takedown records may remain"))
    }

    @Test
    fun `block copy explains private hiding and no notification`() {
        val copy = communityBlockConfirmationCopy(CommunityUploadPolicyKind.WALLPAPER)

        assertTrue(copy.contains("hides community wallpapers"))
        assertTrue(copy.contains("for your account"))
        assertTrue(copy.contains("not notified"))
    }

    @Test
    fun `report copy directs rights reports to takedown review`() {
        assertTrue(COMMUNITY_REPORT_TAKEDOWN_COPY.contains("Rights or license"))
        assertTrue(COMMUNITY_REPORT_TAKEDOWN_COPY.contains("private to admins"))
        assertTrue(COMMUNITY_REPORT_TAKEDOWN_COPY.contains("hide or delete"))
    }
}
