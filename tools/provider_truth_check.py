#!/usr/bin/env python3
"""Validate provider runtime policy and every public availability surface."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

README_START = "<!-- provider-manifest:start -->"
README_END = "<!-- provider-manifest:end -->"
STORE_START = "Provider availability:"
STORE_END = "Content licenses vary by source."

MEDIA_TYPES = {"WALLPAPER", "VIDEO", "SOUND"}
LIFECYCLES = {"ACTIVE", "LEGACY", "LOCAL", "COMMUNITY", "GENERATED"}
BUILD_FLAVORS = {"FULL", "FOSS"}
CHANNELS = {"GITHUB", "PLAY"}
CREDENTIALS = {"NONE", "OPTIONAL_KEY", "REQUIRED_KEY"}
PERMISSIONS = {"NONE", "APPROXIMATE_LOCATION", "MEDIA_ACCESS"}
HEALTH_STATES = {"NETWORKED", "OFFLINE"}
ACTIONS = {
    "BROWSE",
    "SEARCH",
    "PREVIEW",
    "FAVORITE",
    "DOWNLOAD",
    "APPLY",
    "EDIT",
    "SHARE",
    "IMPORT",
    "GENERATE",
    "UPLOAD",
    "VOTE",
    "REPORT",
    "OPEN_SOURCE",
    "VIEW_SAVED",
    "REMOVE_SAVED",
    "BUNDLE",
}
LEGACY_ACTIONS = {"VIEW_SAVED", "REMOVE_SAVED", "OPEN_SOURCE"}
LEGACY_PRIORITY = 1_000
RUNTIME_SURFACE_KEYS = (
    "buildChannel",
    "preferences",
    "application",
    "networkGate",
    "networkWiring",
    "licensesMenu",
    "settingsMenu",
    "diagnostics",
    "wallpaperFeed",
    "videoFeed",
    "soundFeed",
    "soundActions",
    "soundPlayback",
    "wallpaperActions",
    "wallpaperActionRuntime",
    "wallpaperActionUi",
    "wallpaperPreviewNavigation",
    "wallpaperPreviewUi",
    "videoActions",
    "videoRuntime",
    "videoFeedRuntime",
    "videoPreviewTransport",
    "youtubeRuntime",
    "releaseDryRun",
    "releaseSigning",
    "supplyChain",
)
RUNTIME_SURFACE_MARKERS = {
    "buildChannel": ("auraReleaseChannel", "AURA_RELEASE_CHANNEL"),
    "preferences": (
        "val youtubeProviderAvailable",
        "isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)",
        "flowOf(false)",
        "enabled && youtubeProviderAvailable",
        "retireLegacyProviderCredentials",
    ),
    "application": (
        ".retireLegacyProviderCredentials()",
        "if (!isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)) return",
    ),
    "networkGate": (
        "isProviderAvailableInCurrentArtifact",
        "providerSourceForHost",
        "throw IOException",
    ),
    "networkWiring": ("addInterceptor(ProviderAvailabilityInterceptor())",),
    "licensesMenu": ("providerCatalogCapabilities",),
    "settingsMenu": ("youtubeProviderAvailable",),
    "diagnostics": (
        "capabilitySummary",
        "ProviderLifecycle.LEGACY",
        "require(maxAutomaticPrefetch == 0)",
        "require(maxBatchDownloadPerUserAction == 0)",
        'quotaSummary = "Legacy attribution only. $quotaDetail"',
    ),
    "wallpaperFeed": ("mergeRedditFirstHomeResults",),
    "videoFeed": (
        "orderedCurrentProviderCapabilities",
        "currentProviderPriorityBonus",
    ),
    "soundFeed": (
        "orderedCurrentProviderCapabilities",
        "currentProviderPriorityBonus",
    ),
    "soundActions": ("isProviderActionPermitted",),
    "soundPlayback": (
        "soundLicenseCapabilities().capability(SoundAction.PREVIEW)",
        "previewCapability.decision == SoundActionDecision.DISABLED",
        ".filter { it.canUseSoundAction(SoundAction.PREVIEW) }",
    ),
    "wallpaperActions": ("isProviderActionPermitted",),
    "wallpaperActionRuntime": ("wallpaperLicenseCapabilities().capability(action)",),
    "wallpaperActionUi": (
        "wp.wallpaperLicenseCapabilities()",
        "canApply = actionCapabilities.canUse(WallpaperAction.APPLY)",
        "enabled = canApply && !state.isApplying",
        "canDownload = actionCapabilities.canUse(WallpaperAction.DOWNLOAD)",
        "canShare = actionCapabilities.canUse(WallpaperAction.SHARE)",
        "canEdit = actionCapabilities.canUse(WallpaperAction.EDIT)",
    ),
    "wallpaperPreviewNavigation": (
        "onApply = previewApply@{",
        "canApplyFromWallpaperPreview(wallpaper)",
        "wallpaperApplier().applyFromUrl",
    ),
    "wallpaperPreviewUi": (
        "canApplyFromWallpaperPreview(wallpaper)",
        "canApply = canApply",
        "enabled = canApply && !isApplying",
    ),
    "videoActions": ("isProviderActionPermitted",),
    "videoRuntime": (
        "canPreviewVideoInCurrentArtifact(item)",
        "item.canUseVideoAction(VideoWallpaperAction.APPLY)",
        "rememberStreamUrl(item, url)",
    ),
    "videoFeedRuntime": ("filterVideoMetadataForCurrentArtifact",),
    "videoPreviewTransport": (
        "ResolvingDataSource.Factory",
        "isVideoPreviewHostAllowed",
        "providerSourceForHost",
    ),
    "youtubeRuntime": (
        "isYouTubeRuntimeAvailable()",
        "NewPipe.init(DownloaderImpl.instance)",
        "throw java.io.IOException",
    ),
    "releaseDryRun": ("-PauraReleaseChannel=play :app:bundleFullRelease",),
    "releaseSigning": ("-PauraReleaseChannel=play :app:bundleFullRelease",),
    "supplyChain": ("-PauraReleaseChannel=play :app:bundleFullRelease",),
}
RUNTIME_REGISTRY_MARKERS = (
    "fun isProviderActionPermittedIn(",
    "action !in capability.permittedActions",
    "capability.lifecycle == ProviderLifecycle.LEGACY || capability.availableIn(build, channel)",
)


class ProviderTruthError(ValueError):
    """Raised when production provider behavior and public claims disagree."""


def _require_dict(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ProviderTruthError(f"{label} must be an object")
    return value


def _require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ProviderTruthError(f"{label} must be a non-empty string")
    return value.strip()


def _string_list(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    if not isinstance(value, list) or (not value and not allow_empty):
        qualifier = "a list" if allow_empty else "a non-empty list"
        raise ProviderTruthError(f"{label} must be {qualifier}")
    result: list[str] = []
    for index, item in enumerate(value):
        result.append(_require_string(item, f"{label}[{index}]"))
    if len(result) != len(set(result)):
        raise ProviderTruthError(f"{label} contains duplicates")
    return result


def _balanced_content(text: str, opening: int) -> tuple[str, int]:
    opening_char = text[opening]
    closing_char = {"(": ")", "[": "]", "{": "}"}.get(opening_char)
    if closing_char is None:
        raise ProviderTruthError("internal parser expected an opening delimiter")
    depth = 0
    quote: str | None = None
    escaped = False
    for index in range(opening, len(text)):
        char = text[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char == '"':
            quote = char
        elif char == opening_char:
            depth += 1
        elif char == closing_char:
            depth -= 1
            if depth == 0:
                return text[opening + 1 : index], index + 1
    raise ProviderTruthError("unterminated Kotlin expression in provider registry")


def _extract_calls(text: str, function_name: str) -> list[str]:
    calls: list[str] = []
    pattern = re.compile(rf"\b{re.escape(function_name)}\s*\(")
    for match in pattern.finditer(text):
        opening = text.find("(", match.start())
        content, _ = _balanced_content(text, opening)
        calls.append(content)
    return calls


def _checked_block(
    text: str,
    marker: str,
    label: str,
    errors: list[str],
) -> str:
    start = text.find(marker)
    opening = text.find("{", start) if start >= 0 else -1
    if start < 0 or opening < 0:
        errors.append(f"{label} is missing checked block: {marker}")
        return ""
    try:
        content, _ = _balanced_content(text, opening)
        return content
    except ProviderTruthError:
        errors.append(f"{label} has an unterminated checked block: {marker}")
        return ""


def _require_marker_order(
    text: str,
    markers: tuple[str, ...],
    label: str,
    errors: list[str],
) -> None:
    positions = [text.find(marker) for marker in markers]
    missing = [marker for marker, position in zip(markers, positions, strict=True) if position < 0]
    if missing:
        errors.append(f"{label} is missing behavior marker: {', '.join(missing)}")
    elif positions != sorted(positions):
        errors.append(f"{label} behavior markers are not in enforcement order")


def _field_expression(block: str, field: str) -> str:
    match = re.search(rf"\b{re.escape(field)}\s*=", block)
    if not match:
        raise ProviderTruthError(f"runtime provider entry is missing {field}")
    start = match.end()
    while start < len(block) and block[start].isspace():
        start += 1
    depth = 0
    quote: str | None = None
    escaped = False
    for index in range(start, len(block)):
        char = block[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char == '"':
            quote = char
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            return block[start:index].strip()
    return block[start:].strip()


def _enum_value(block: str, field: str, enum_name: str) -> str:
    expression = _field_expression(block, field)
    match = re.fullmatch(rf"{re.escape(enum_name)}\.([A-Z_]+)", expression)
    if not match:
        raise ProviderTruthError(f"cannot parse runtime {field}: {expression}")
    return match.group(1)


def _enum_set(expression: str, enum_name: str, constants: dict[str, set[str]] | None = None) -> set[str]:
    if constants and expression in constants:
        return constants[expression]
    if expression == "emptySet()":
        return set()
    values = set(re.findall(rf"{re.escape(enum_name)}\.([A-Z_]+)", expression))
    if not values or not expression.startswith("setOf("):
        raise ProviderTruthError(f"cannot parse runtime {enum_name} set: {expression}")
    return values


def _string_set(expression: str) -> set[str]:
    if expression == "emptySet()":
        return set()
    if not expression.startswith("setOf("):
        raise ProviderTruthError(f"cannot parse runtime string set: {expression}")
    return set(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', expression))


def _nullable_string(expression: str) -> str | None:
    if expression == "null":
        return None
    match = re.fullmatch(r'"([^"\\]*(?:\\.[^"\\]*)*)"', expression)
    if not match:
        raise ProviderTruthError(f"cannot parse runtime string: {expression}")
    return match.group(1)


def _priority_map(expression: str) -> dict[str, int]:
    if not expression.startswith("mapOf("):
        raise ProviderTruthError(f"cannot parse runtime priority map: {expression}")
    result: dict[str, int] = {}
    for media, raw_priority in re.findall(
        r"ProviderMediaType\.([A-Z_]+)\s+to\s+([0-9][0-9_]*|LEGACY_PRIORITY)",
        expression,
    ):
        result[media] = (
            LEGACY_PRIORITY
            if raw_priority == "LEGACY_PRIORITY"
            else int(raw_priority.replace("_", ""))
        )
    if not result:
        raise ProviderTruthError(f"runtime priority map is empty: {expression}")
    return result


def _parse_explicit_provider(block: str) -> dict[str, Any]:
    source = _enum_value(block, "source", "ContentSource")
    media_types = _enum_set(_field_expression(block, "mediaTypes"), "ProviderMediaType")
    actions_expression = _field_expression(block, "permittedActions")
    actions = (
        LEGACY_ACTIONS
        if actions_expression == "LEGACY_ACTIONS"
        else _enum_set(actions_expression, "ProviderAction")
    )
    build_constants = {
        "ALL_BUILDS": {"FULL", "FOSS"},
        "FULL_ONLY": {"FULL"},
    }
    channel_constants = {
        "ALL_CHANNELS": {"GITHUB", "PLAY"},
        "GITHUB_ONLY": {"GITHUB"},
    }
    enabled_expression = _field_expression(block, "enabledByDefault")
    attribution_expression = _field_expression(block, "requiresAttribution")
    if enabled_expression not in {"true", "false"} or attribution_expression not in {"true", "false"}:
        raise ProviderTruthError(f"runtime {source} has an invalid Boolean field")
    return {
        "id": source,
        "mediaTypes": media_types,
        "defaultPriority": _priority_map(_field_expression(block, "defaultPriority")),
        "permittedActions": actions,
        "lifecycle": _enum_value(block, "lifecycle", "ProviderLifecycle"),
        "buildFlavors": _enum_set(
            _field_expression(block, "builds"), "ProviderBuild", build_constants
        ),
        "releaseChannels": _enum_set(
            _field_expression(block, "channels"), "ProviderChannel", channel_constants
        ),
        "credentialNeed": _enum_value(block, "configuration", "ProviderConfiguration"),
        "permission": _enum_value(block, "permission", "ProviderPermission"),
        "health": _enum_value(block, "health", "ProviderHealth"),
        "requiresAttribution": attribution_expression == "true",
        "enabledByDefault": enabled_expression == "true",
        "killSwitchKey": _nullable_string(_field_expression(block, "killSwitchKey")),
        "endpointIds": _string_set(_field_expression(block, "endpointIds")),
    }


def _parse_runtime_registry(source: str) -> dict[str, dict[str, Any]]:
    marker = "val providerCapabilities: List<ProviderCapability> = listOf("
    start = source.find(marker)
    if start < 0:
        raise ProviderTruthError("providerCapabilities registry was not found")
    opening = source.find("(", start + marker.find("listOf"))
    registry, _ = _balanced_content(source, opening)
    entries = [_parse_explicit_provider(block) for block in _extract_calls(registry, "ProviderCapability")]
    for block in _extract_calls(registry, "legacy"):
        source_match = re.search(r"ContentSource\.([A-Z_]+)", block)
        media = set(re.findall(r"ProviderMediaType\.([A-Z_]+)", block))
        if not source_match or not media:
            raise ProviderTruthError(f"cannot parse legacy runtime entry: {block}")
        entries.append(
            {
                "id": source_match.group(1),
                "mediaTypes": media,
                "defaultPriority": {item: LEGACY_PRIORITY for item in media},
                "permittedActions": LEGACY_ACTIONS,
                "lifecycle": "LEGACY",
                "buildFlavors": {"FULL", "FOSS"},
                "releaseChannels": {"GITHUB", "PLAY"},
                "credentialNeed": "NONE",
                "permission": "NONE",
                "health": "OFFLINE",
                "requiresAttribution": True,
                "enabledByDefault": False,
                "killSwitchKey": None,
                "endpointIds": set(),
            }
        )
    ids = [entry["id"] for entry in entries]
    if len(ids) != len(set(ids)):
        raise ProviderTruthError("runtime provider registry contains duplicate source entries")
    return {entry["id"]: entry for entry in entries}


def _manifest_provider(provider: dict[str, Any], index: int) -> dict[str, Any]:
    label = f"providers[{index}]"
    identifier = _require_string(provider.get("id"), f"{label}.id")
    lifecycle = _require_string(provider.get("lifecycle"), f"{label}.lifecycle")
    credential = _require_string(provider.get("credentialNeed"), f"{label}.credentialNeed")
    permission = _require_string(provider.get("permission"), f"{label}.permission")
    health = _require_string(provider.get("health"), f"{label}.health")
    media = set(_string_list(provider.get("mediaTypes"), f"{label}.mediaTypes"))
    builds = set(_string_list(provider.get("buildFlavors"), f"{label}.buildFlavors"))
    channels = set(_string_list(provider.get("releaseChannels"), f"{label}.releaseChannels"))
    actions = set(_string_list(provider.get("permittedActions"), f"{label}.permittedActions"))
    endpoints = set(
        _string_list(provider.get("endpointIds"), f"{label}.endpointIds", allow_empty=True)
    )
    priorities_raw = _require_dict(provider.get("defaultPriority"), f"{label}.defaultPriority")
    priorities: dict[str, int] = {}
    for key, value in priorities_raw.items():
        if not isinstance(key, str) or not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise ProviderTruthError(f"{label}.defaultPriority entries must be non-negative integers")
        priorities[key] = value
    for value, allowed, field in (
        (lifecycle, LIFECYCLES, "lifecycle"),
        (credential, CREDENTIALS, "credentialNeed"),
        (permission, PERMISSIONS, "permission"),
        (health, HEALTH_STATES, "health"),
    ):
        if value not in allowed:
            raise ProviderTruthError(f"{label}.{field} has unknown value {value}")
    for values, allowed, field in (
        (media, MEDIA_TYPES, "mediaTypes"),
        (builds, BUILD_FLAVORS, "buildFlavors"),
        (channels, CHANNELS, "releaseChannels"),
        (actions, ACTIONS, "permittedActions"),
    ):
        if unknown := sorted(values - allowed):
            raise ProviderTruthError(f"{label}.{field} has unknown values: {', '.join(unknown)}")
    if set(priorities) != media:
        raise ProviderTruthError(f"{label}.defaultPriority keys must match mediaTypes")
    for boolean_field in ("requiresAttribution", "enabledByDefault"):
        if not isinstance(provider.get(boolean_field), bool):
            raise ProviderTruthError(f"{label}.{boolean_field} must be a Boolean")
    kill_switch = provider.get("killSwitchKey")
    if kill_switch is not None and (not isinstance(kill_switch, str) or not kill_switch.strip()):
        raise ProviderTruthError(f"{label}.killSwitchKey must be null or a non-empty string")
    for field in ("displayName", "publicUrl", "publicSummary"):
        _require_string(provider.get(field), f"{label}.{field}")
    return {
        "id": identifier,
        "mediaTypes": media,
        "defaultPriority": priorities,
        "permittedActions": actions,
        "lifecycle": lifecycle,
        "buildFlavors": builds,
        "releaseChannels": channels,
        "credentialNeed": credential,
        "permission": permission,
        "health": health,
        "requiresAttribution": provider["requiresAttribution"],
        "enabledByDefault": provider["enabledByDefault"],
        "killSwitchKey": kill_switch,
        "endpointIds": endpoints,
    }


def _display_media(values: list[str]) -> str:
    labels = {"WALLPAPER": "Wallpapers", "VIDEO": "Videos", "SOUND": "Sounds"}
    return ", ".join(labels[value] for value in values)


def _access_summary(provider: dict[str, Any]) -> str:
    credential = (
        "Saved items only"
        if provider["lifecycle"] == "LEGACY"
        else {
            "NONE": "No key",
            "OPTIONAL_KEY": "Optional key",
            "REQUIRED_KEY": "User key required",
        }[provider["credentialNeed"]]
    )
    builds = "Full + FOSS" if set(provider["buildFlavors"]) == BUILD_FLAVORS else "Full build"
    channels = (
        "GitHub/Obtainium + Play"
        if set(provider["releaseChannels"]) == CHANNELS
        else "GitHub/Obtainium"
    )
    return f"{credential}; {builds}; {channels}"


def render_readme_table(manifest: dict[str, Any]) -> str:
    lines = [
        README_START,
        "| Source | Media and role | Status | Access |",
        "|---|---|---|---|",
    ]
    for provider in manifest["providers"]:
        name = provider["displayName"]
        url = provider["publicUrl"]
        status = {
            "ACTIVE": "Active",
            "LEGACY": "Legacy attribution only",
            "LOCAL": "Local",
            "COMMUNITY": "Community (opt-in)",
            "GENERATED": "Generated (opt-in)",
        }[provider["lifecycle"]]
        role = f"{_display_media(provider['mediaTypes'])}. {provider['publicSummary']}"
        lines.append(f"| [{name}]({url}) | {role} | {status} | {_access_summary(provider)} |")
    lines.append(README_END)
    return "\n".join(lines)


def _english_list(values: list[str]) -> str:
    if not values:
        return "None"
    if len(values) == 1:
        return values[0]
    if len(values) == 2:
        return f"{values[0]} and {values[1]}"
    return f"{', '.join(values[:-1])}, and {values[-1]}"


def _available_providers(
    manifest: dict[str, Any], media_type: str, build: str, channel: str
) -> list[dict[str, Any]]:
    providers = [
        provider
        for provider in manifest["providers"]
        if media_type in provider["mediaTypes"]
        and build in provider["buildFlavors"]
        and channel in provider["releaseChannels"]
        and provider["lifecycle"] != "LEGACY"
    ]
    return sorted(providers, key=lambda item: (item["defaultPriority"][media_type], item["id"]))


def render_store_provider_block(manifest: dict[str, Any]) -> str:
    lines = [STORE_START]
    for media, label in (("WALLPAPER", "Wallpapers"), ("VIDEO", "Videos"), ("SOUND", "Sounds")):
        names = [
            provider["displayName"]
            for provider in _available_providers(manifest, media, "FULL", "PLAY")
        ]
        if names and names[0] == "Reddit":
            value = "Reddit first"
            if names[1:]:
                value += f", followed by {_english_list(names[1:])}"
        else:
            value = _english_list(names)
        lines.append(f"- {label}: {value}.")
    legacy = [
        provider["displayName"]
        for provider in manifest["providers"]
        if provider["lifecycle"] == "LEGACY"
    ]
    lines.append(
        f"- Legacy attribution only: {_english_list(legacy)}. "
        "Aura keeps source details for older saved items and does not fetch new media from these providers."
    )
    return "\n".join(lines)


def expected_provider_catalog(
    manifest: dict[str, Any], build: str, channel: str
) -> dict[str, Any]:
    return {
        "manifestPath": "docs/providers/provider-manifest.json",
        "buildFlavor": build,
        "releaseChannel": channel,
        "wallpaper": [item["id"] for item in _available_providers(manifest, "WALLPAPER", build, channel)],
        "video": [item["id"] for item in _available_providers(manifest, "VIDEO", build, channel)],
        "sound": [item["id"] for item in _available_providers(manifest, "SOUND", build, channel)],
        "legacyAttributionOnly": [
            item["id"]
            for item in manifest["providers"]
            if item["lifecycle"] == "LEGACY"
            and build in item["buildFlavors"]
            and channel in item["releaseChannels"]
        ],
    }


def _replace_section(text: str, start: str, end: str) -> str:
    start_index = text.find(start)
    end_index = text.find(end, start_index + len(start)) if start_index >= 0 else -1
    if start_index < 0 or end_index < 0:
        raise ProviderTruthError(f"public surface is missing checked section markers: {start} ... {end}")
    return text[start_index : end_index + len(end)]


def _network_policy_call(source: str, identifier: str) -> tuple[str, str] | None:
    marker = f"source = ContentSource.{identifier}"
    for function_name in ("legacyProviderNetworkPolicy", "ProviderNetworkPolicy"):
        for block in _extract_calls(source, function_name):
            if marker in block:
                return function_name, block
    return None


def _validate_runtime_surfaces(
    manifest_providers: list[dict[str, Any]],
    surface_texts: dict[str, str],
    errors: list[str],
) -> None:
    for surface_key, markers in RUNTIME_SURFACE_MARKERS.items():
        text = surface_texts[surface_key]
        for marker in markers:
            if marker not in text:
                errors.append(
                    f"{surface_key} runtime surface is missing checked marker: {marker}"
                )

    wallpaper_navigation = _checked_block(
        surface_texts["wallpaperPreviewNavigation"],
        "onApply = previewApply@{",
        "wallpaper preview navigation",
        errors,
    )
    _require_marker_order(
        wallpaper_navigation,
        ("if (!canApplyFromWallpaperPreview(wallpaper))", "wallpaperApplier().applyFromUrl"),
        "wallpaper preview navigation",
        errors,
    )

    wallpaper_preview_bar = _checked_block(
        surface_texts["wallpaperPreviewUi"],
        "private fun PreviewApplyBar(",
        "wallpaper preview UI",
        errors,
    )
    if wallpaper_preview_bar.count("enabled = canApply && !isApplying") != 3:
        errors.append("wallpaper preview UI must gate all three apply targets")

    video_runtime = surface_texts["videoRuntime"]
    video_resolve = _checked_block(
        video_runtime,
        "fun ensureStreamResolved(",
        "video stream resolution",
        errors,
    )
    _require_marker_order(
        video_resolve,
        ("if (!canPreviewVideoInCurrentArtifact(item))", "val cachedUrl = streamUrls[item.id]"),
        "video stream resolution",
        errors,
    )
    video_apply = _checked_block(
        video_runtime,
        "fun applyVideoWallpaper(",
        "video apply runtime",
        errors,
    )
    _require_marker_order(
        video_apply,
        ("if (!item.canUseVideoAction(VideoWallpaperAction.APPLY))", "viewModelScope.launch"),
        "video apply runtime",
        errors,
    )
    if "filterVideoMetadataForCurrentArtifact(cached.result)" not in surface_texts["videoFeedRuntime"]:
        errors.append("video cache restore must filter metadata for the current artifact")

    video_transport = _checked_block(
        surface_texts["videoPreviewTransport"],
        "private val dataSourceFactory by lazy",
        "video preview transport",
        errors,
    )
    _require_marker_order(
        video_transport,
        ("ResolvingDataSource.Factory", "if (!isVideoPreviewHostAllowed(host))"),
        "video preview transport",
        errors,
    )

    youtube_runtime = surface_texts["youtubeRuntime"]
    youtube_init = _checked_block(youtube_runtime, "init {", "YouTube extractor init", errors)
    _require_marker_order(
        youtube_init,
        ("if (isYouTubeRuntimeAvailable())", "NewPipe.init(DownloaderImpl.instance)"),
        "YouTube extractor init",
        errors,
    )
    youtube_execute = _checked_block(
        youtube_runtime,
        "override fun execute(",
        "YouTube direct transport",
        errors,
    )
    _require_marker_order(
        youtube_execute,
        ("if (!isYouTubeRuntimeAvailable())", "val url = java.net.URL"),
        "YouTube direct transport",
        errors,
    )

    diagnostics = surface_texts["diagnostics"]
    for provider in manifest_providers:
        identifier = provider["id"]
        entry = _network_policy_call(diagnostics, identifier)
        if entry is None:
            errors.append(f"diagnostics runtime surface is missing {identifier}")
            continue
        function_name, block = entry
        if provider["lifecycle"] == "LEGACY":
            if function_name != "legacyProviderNetworkPolicy":
                errors.append(
                    f"diagnostics exposes legacy provider {identifier} as an active network policy"
                )
        elif function_name == "legacyProviderNetworkPolicy" or "Legacy attribution only" in block:
            errors.append(
                f"diagnostics exposes active provider {identifier} as legacy attribution only"
            )

    nasa_entry = _network_policy_call(diagnostics, "NASA")
    if nasa_entry is not None:
        _, nasa_block = nasa_entry
        for forbidden in (
            "Legacy restored",
            "no active automatic fetching",
            "hidden from active source lists",
        ):
            if forbidden.lower() in nasa_block.lower():
                errors.append(f"diagnostics contradicts active NASA lifecycle with: {forbidden}")

    release_docs = "\n".join(
        surface_texts[key]
        for key in ("releaseDryRun", "releaseSigning", "supplyChain")
    )
    if any(
        "assembleFullRelease" in line and "bundleFullRelease" in line
        for line in release_docs.splitlines()
    ):
        errors.append("release docs must build GitHub APK and Play AAB in separate commands")


def validate_provider_truth(repo_root: Path, manifest_relative: str) -> dict[str, Any]:
    manifest_path = repo_root / manifest_relative
    if not manifest_path.is_file():
        raise ProviderTruthError(f"provider manifest not found: {manifest_relative}")
    manifest = _require_dict(json.loads(manifest_path.read_text(encoding="utf-8")), "manifest")
    if manifest.get("schemaVersion") != 1 or manifest.get("policyKind") != "providerCapabilityManifest":
        raise ProviderTruthError("provider manifest schemaVersion or policyKind is invalid")
    runtime_relative = _require_string(manifest.get("runtimeRegistryPath"), "runtimeRegistryPath")
    surfaces = _require_dict(manifest.get("publicSurfaces"), "publicSurfaces")
    for key in ("readme", "playStore", "playPacket", "alternativeStorePacket"):
        _require_string(surfaces.get(key), f"publicSurfaces.{key}")
    runtime_surfaces = _require_dict(manifest.get("runtimeSurfaces"), "runtimeSurfaces")
    for key in RUNTIME_SURFACE_KEYS:
        _require_string(runtime_surfaces.get(key), f"runtimeSurfaces.{key}")
    if set(runtime_surfaces) != set(RUNTIME_SURFACE_KEYS):
        raise ProviderTruthError("runtimeSurfaces must contain exactly the checked runtime surface keys")
    providers_raw = manifest.get("providers")
    if not isinstance(providers_raw, list) or not providers_raw:
        raise ProviderTruthError("providers must be a non-empty list")
    providers = [_require_dict(value, f"providers[{index}]") for index, value in enumerate(providers_raw)]
    normalized = [_manifest_provider(provider, index) for index, provider in enumerate(providers)]
    ids = [item["id"] for item in normalized]
    errors: list[str] = []
    if len(ids) != len(set(ids)):
        errors.append("provider manifest contains duplicate ids")
    if ids[0] != "REDDIT":
        errors.append("Reddit must be the first provider in the public catalog")

    surface_texts: dict[str, str] = {}
    for key in RUNTIME_SURFACE_KEYS:
        relative = runtime_surfaces[key]
        path = repo_root / relative
        if not path.is_file():
            raise ProviderTruthError(f"runtime surface not found: {relative}")
        surface_texts[key] = path.read_text(encoding="utf-8")
    _validate_runtime_surfaces(providers, surface_texts, errors)

    models_text = (repo_root / "app/src/main/java/com/freevibe/data/model/Models.kt").read_text(encoding="utf-8")
    enum_match = re.search(r"enum class ContentSource\s*\{([^}]*)}", models_text)
    if not enum_match:
        errors.append("ContentSource enum could not be read")
        enum_sources: set[str] = set()
    else:
        enum_sources = {value.strip() for value in enum_match.group(1).split(",") if value.strip()}
    if set(ids) != enum_sources:
        errors.append(
            "provider manifest ids do not match ContentSource: "
            f"missing={sorted(enum_sources - set(ids))}, extra={sorted(set(ids) - enum_sources)}"
        )

    manifest_by_id = {item["id"]: item for item in normalized}
    runtime_path = repo_root / runtime_relative
    runtime_text = runtime_path.read_text(encoding="utf-8")
    for marker in RUNTIME_REGISTRY_MARKERS:
        if marker not in runtime_text:
            errors.append(f"runtime registry is missing checked artifact-action marker: {marker}")
    runtime = _parse_runtime_registry(runtime_text)
    if set(runtime) != set(manifest_by_id):
        errors.append("runtime provider ids do not match the provider manifest")
    comparable_fields = (
        "mediaTypes",
        "defaultPriority",
        "permittedActions",
        "lifecycle",
        "buildFlavors",
        "releaseChannels",
        "credentialNeed",
        "permission",
        "health",
        "requiresAttribution",
        "enabledByDefault",
        "killSwitchKey",
        "endpointIds",
    )
    for identifier in sorted(set(runtime) & set(manifest_by_id)):
        for field in comparable_fields:
            if runtime[identifier][field] != manifest_by_id[identifier][field]:
                errors.append(
                    f"runtime {identifier}.{field} differs from provider manifest: "
                    f"{runtime[identifier][field]!r} != {manifest_by_id[identifier][field]!r}"
                )

    for provider, normalized_provider in zip(providers, normalized, strict=True):
        identifier = normalized_provider["id"]
        if normalized_provider["lifecycle"] == "LEGACY":
            if normalized_provider["credentialNeed"] != "NONE":
                errors.append(f"legacy provider {identifier} must not request credentials")
            if normalized_provider["health"] != "OFFLINE":
                errors.append(f"legacy provider {identifier} must be offline")
            if normalized_provider["enabledByDefault"]:
                errors.append(f"legacy provider {identifier} must default off")
            if normalized_provider["permittedActions"] != LEGACY_ACTIONS:
                errors.append(f"legacy provider {identifier} permits more than saved-item attribution actions")
            if not provider["publicSummary"].startswith("Legacy attribution only"):
                errors.append(f"legacy provider {identifier} publicSummary must say 'Legacy attribution only'")
        if normalized_provider["credentialNeed"] == "REQUIRED_KEY" and normalized_provider["enabledByDefault"]:
            errors.append(f"credential-gated provider {identifier} cannot default on")

    for build in BUILD_FLAVORS:
        for channel in CHANNELS:
            for media in ("WALLPAPER", "VIDEO"):
                ordered = _available_providers(manifest, media, build, channel)
                reddit = next((item for item in ordered if item["id"] == "REDDIT"), None)
                if reddit is not None and ordered[0]["id"] != "REDDIT":
                    errors.append(f"Reddit is not first for {build}/{channel}/{media}")
    github_sounds = _available_providers(manifest, "SOUND", "FULL", "GITHUB")
    if not github_sounds or github_sounds[0]["id"] != "YOUTUBE":
        errors.append("YouTube must remain the first GitHub sound provider")

    readme_path = repo_root / surfaces["readme"]
    readme_text = readme_path.read_text(encoding="utf-8")
    try:
        actual_readme = _replace_section(readme_text, README_START, README_END)
        expected_readme = render_readme_table(manifest)
        if actual_readme != expected_readme:
            errors.append("README.md provider table differs from the checked provider manifest")
    except ProviderTruthError as exc:
        errors.append(str(exc))

    store_path = repo_root / surfaces["playStore"]
    store_text = store_path.read_text(encoding="utf-8")
    try:
        actual_store = _replace_section(store_text, STORE_START, STORE_END)
        expected_store = render_store_provider_block(manifest) + "\n\n" + STORE_END
        if actual_store != expected_store:
            errors.append("Fastlane provider availability differs from the checked Play catalog")
    except ProviderTruthError as exc:
        errors.append(str(exc))
    for provider in providers:
        if provider["lifecycle"] == "LEGACY" and provider["displayName"].lower() in store_text.lower():
            legacy_line = next(
                (line for line in store_text.splitlines() if provider["displayName"].lower() in line.lower()),
                "",
            )
            if "legacy attribution only" not in legacy_line.lower():
                errors.append(f"Fastlane mentions legacy provider {provider['id']} outside legacy attribution copy")
        if "PLAY" not in provider["releaseChannels"] and provider["displayName"].lower() in store_text.lower():
            errors.append(f"Fastlane exposes non-Play provider {provider['id']}")

    for surface_key, build, channel in (
        ("playPacket", "FULL", "PLAY"),
        ("alternativeStorePacket", "FULL", "GITHUB"),
    ):
        packet_path = repo_root / surfaces[surface_key]
        packet = _require_dict(json.loads(packet_path.read_text(encoding="utf-8")), surface_key)
        if packet.get("providerCatalog") != expected_provider_catalog(manifest, build, channel):
            errors.append(f"{packet_path.relative_to(repo_root).as_posix()} providerCatalog differs from manifest")

    alt_packet_path = repo_root / surfaces["alternativeStorePacket"]
    alt_packet = json.loads(alt_packet_path.read_text(encoding="utf-8"))
    services = {
        service.get("id"): service.get("disclosure", "")
        for service in alt_packet.get("networkServices", [])
        if isinstance(service, dict)
    }
    for provider in providers:
        for endpoint in provider["endpointIds"]:
            if endpoint not in services:
                errors.append(f"alternative-store packet is missing endpoint {endpoint}")
            elif provider["lifecycle"] == "LEGACY" and "legacy attribution only" not in services[endpoint].lower():
                errors.append(f"legacy endpoint {endpoint} is not described as legacy attribution only")

    if errors:
        raise ProviderTruthError("; ".join(errors))
    return {
        "status": "ok",
        "providerCount": len(providers),
        "legacyProviderCount": sum(item["lifecycle"] == "LEGACY" for item in providers),
        "publicSurfaceCount": len(surfaces),
        "runtimeSurfaceCount": len(runtime_surfaces),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default="docs/providers/provider-manifest.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = validate_provider_truth(Path(args.repo_root), args.manifest)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
