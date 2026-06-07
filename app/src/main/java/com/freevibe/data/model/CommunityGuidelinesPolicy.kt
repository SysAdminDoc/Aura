package com.freevibe.data.model

const val COMMUNITY_GUIDELINES_VERSION = 1
const val COMMUNITY_GUIDELINES_REQUIRED_MESSAGE =
    "Accept Community Guidelines in Settings before using community features"

object CommunityGuidelinesPolicy {
    const val currentVersion: Int = COMMUNITY_GUIDELINES_VERSION
    const val title: String = "Community Guidelines"
    const val acceptanceLabel: String = "I agree to follow the Community Guidelines"

    val postingRules: List<String> = listOf(
        "Share only wallpapers, sounds, profile text, and source links you own or have permission to share.",
        "Do not upload illegal, hateful, harassing, sexual, violent, deceptive, malware, or privacy-invasive content.",
        "Do not impersonate others, expose private information, spam, manipulate votes, or evade blocks and moderation.",
        "Use report, block, owner delete, and takedown routes when content or behavior breaks these rules.",
    )
}

fun hasAcceptedCommunityGuidelinesVersion(version: Int): Boolean =
    version >= COMMUNITY_GUIDELINES_VERSION
