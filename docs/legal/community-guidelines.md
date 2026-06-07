# Aura Community Guidelines

Aura community features are for sharing device-personalization content and
community metadata: wallpapers, sounds, creator profile text, source links,
votes, follows, blocks, reports, and collection/share records.

Users must accept the current Community Guidelines version before using
community uploads, votes, reports, blocks, follows, creator profiles, or
community feeds. Acceptance is separate from the Privacy Policy and is stored
locally as `community_guidelines_accepted_version`.

## Rules

- Share only wallpapers, sounds, profile text, and source links you own or have
  permission to share.
- Do not upload illegal, hateful, harassing, sexual, violent, deceptive,
  malware, or privacy-invasive content.
- Do not impersonate others, expose private information, spam, manipulate
  votes, or evade blocks and moderation.
- Use report, block, owner delete, and takedown routes when content or behavior
  breaks these rules.

## Moderation

Aura can hide, delete, or preserve moderation records for content that breaks
these guidelines. Reports are private to admins. Confirmed rights reports can
hide or delete community uploads and their uploaded media files.

## Evidence

- Runtime copy: `app/src/main/java/com/freevibe/data/model/CommunityGuidelinesPolicy.kt`
- Consent storage: `app/src/main/java/com/freevibe/data/local/PreferencesManager.kt`
- Consent UI: `app/src/main/java/com/freevibe/ui/components/CommunityGuidelinesDialog.kt`
- Settings entry: `app/src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt`
- Community surfaces: `app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt`,
  `app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt`
- Backend gates: `app/src/main/java/com/freevibe/data/repository/UploadRepository.kt`,
  `app/src/main/java/com/freevibe/data/repository/WallpaperUploadRepository.kt`,
  `app/src/main/java/com/freevibe/data/repository/CommunityBlockRepository.kt`,
  `app/src/main/java/com/freevibe/data/repository/CreatorProfileRepository.kt`,
  `app/src/main/java/com/freevibe/data/repository/VoteRepository.kt`

Reference policy source:
https://support.google.com/googleplay/android-developer/answer/12923286
