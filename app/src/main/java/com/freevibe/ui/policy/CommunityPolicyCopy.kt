package com.freevibe.ui.policy

enum class CommunityUploadPolicyKind(
    val displayName: String,
    val publicListingName: String,
    val uploadedFileName: String,
) {
    SOUND(
        displayName = "sound",
        publicListingName = "community sound",
        uploadedFileName = "uploaded audio file",
    ),
    WALLPAPER(
        displayName = "wallpaper",
        publicListingName = "community wallpaper",
        uploadedFileName = "uploaded image file",
    ),
}

data class CommunityUploadPolicyCopy(
    val publicTitle: String,
    val publicBody: String,
    val takedownBody: String,
    val attestation: String,
)

fun communityUploadPolicyCopy(kind: CommunityUploadPolicyKind): CommunityUploadPolicyCopy =
    CommunityUploadPolicyCopy(
        publicTitle = "Public community listing",
        publicBody = "This ${kind.displayName} becomes public after upload. Its selected license, source link, uploader label, and tags are stored with the listing.",
        takedownBody = "Rights holders can report the listing. Confirmed rights reports can hide or delete the ${kind.publicListingName} and its ${kind.uploadedFileName}.",
        attestation = "I own or have rights to share this ${kind.displayName} under the selected license.",
    )

fun communityOwnerDeleteConfirmationCopy(kind: CommunityUploadPolicyKind): String =
    "This removes your ${kind.publicListingName}, public listing, owner index, and ${kind.uploadedFileName}. Private deletion or takedown records may remain for moderation and abuse review."

fun communityBlockConfirmationCopy(kind: CommunityUploadPolicyKind): String =
    "This hides community ${kind.displayName}s from this uploader across Aura for your account. The uploader is not notified, and admins can still review reports separately."

const val COMMUNITY_REPORT_TAKEDOWN_COPY =
    "Use Rights or license for takedown requests. Reports are private to admins; confirmed rights reports can hide or delete community uploads."
