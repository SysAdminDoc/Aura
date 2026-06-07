package com.freevibe.service

const val COMMUNITY_DELETION_REQUEST_SUBJECT = "Aura community deletion request"

fun communityDeletionRequestBody(summary: CommunityIdentitySummary): String {
    val requestCode = summary.deletionRequestCode.ifBlank { "Not available" }
    val suffix = if (summary.identitySuffix == "Not created") {
        summary.identitySuffix
    } else {
        "...${summary.identitySuffix}"
    }
    return listOf(
        COMMUNITY_DELETION_REQUEST_SUBJECT,
        "",
        "Request code: $requestCode",
        "Identity suffix: $suffix",
        "Auth state: ${summary.authLabel}",
        "",
        "I request deletion of my Aura community identity and associated community marker data.",
        "I understand public uploads and moderation records use the retained-data review path described by Aura.",
    ).joinToString("\n")
}
