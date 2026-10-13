package com.freevibe.service

import android.content.Context
import android.net.ConnectivityManager
import com.freevibe.BuildConfig
import com.freevibe.data.legal.ProviderChannel
import com.freevibe.data.legal.currentProviderChannel
import com.freevibe.data.local.PreferencesManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The one address the update check reads. It carries no query, account or device identifier. */
internal const val RELEASES_API_URL = "https://api.github.com/repos/SysAdminDoc/Aura/releases/latest"

/** Where the notice sends the user. Aura opens this page and never downloads or installs anything itself. */
internal const val RELEASES_PAGE_URL = "https://github.com/SysAdminDoc/Aura/releases/latest"
private const val RELEASE_PAGE_PREFIX = "https://github.com/SysAdminDoc/Aura/releases/"

internal const val AUTOMATIC_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
internal const val MAX_RELEASE_NOTES_CHARS = 4_000
private const val MAX_RELEASE_RESPONSE_BYTES = 512L * 1024

/** The GitHub release fields the check reads. Everything else in the answer is ignored. */
@JsonClass(generateAdapter = true)
internal data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String = "",
    @Json(name = "body") val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "draft") val draft: Boolean = false,
    @Json(name = "prerelease") val prerelease: Boolean = false,
    @Json(name = "assets") val assets: List<GitHubReleaseAsset> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class GitHubReleaseAsset(
    @Json(name = "name") val name: String = "",
)

/** A published version, trimmed to what the update notice shows. */
@JsonClass(generateAdapter = true)
data class PublishedRelease(
    val tag: String,
    val versionName: String,
    /** Read from the APK file names. Null when a release doesn't follow that naming. */
    val versionCode: Int?,
    val notes: String,
    val pageUrl: String,
)

sealed interface UpdateCheckResult {
    /** Nothing has been published to compare against. */
    data object NoRelease : UpdateCheckResult

    data class UpToDate(val latestVersionName: String) : UpdateCheckResult

    data class Available(val release: PublishedRelease) : UpdateCheckResult

    data class Failed(val reason: UpdateCheckFailure) : UpdateCheckResult

    /** No request was made. */
    data class Skipped(val reason: UpdateCheckSkip) : UpdateCheckResult
}

enum class UpdateCheckFailure { NETWORK, RATE_LIMITED, UNREADABLE }

enum class UpdateCheckSkip { TURNED_OFF, CHECKED_RECENTLY, DATA_SAVER, METERED }

internal data class NetworkPosture(val metered: Boolean, val dataSaver: Boolean)

internal fun interface NetworkPostureReader {
    fun read(): NetworkPosture
}

internal class ReleaseFeedResponse(val code: Int, val body: String)

internal interface ReleaseFeed {
    /** Throws [IOException] when the request can't be completed. */
    suspend fun latest(): ReleaseFeedResponse
}

private val ASSET_VERSION_CODE = Regex("""versionCode-(\d+)""")
private val LEADING_VERSION = Regex("""^v?(\d+(?:\.\d+)*)""")
private val MARKDOWN_HEADING = Regex("""^#{1,6}\s+""")
private val MARKDOWN_BULLET = Regex("""^(\s*)[-*+]\s+""")
private val MARKDOWN_LINK = Regex("""\[([^\]]+)]\([^)\s]+\)""")
private val MARKDOWN_STRONG = Regex("""\*\*(.+?)\*\*""")
private val MARKDOWN_CODE = Regex("""`([^`]+)`""")
private val BLANK_RUN = Regex("""\n{3,}""")

/** The reason an automatic check should not run right now, or null when it should. */
internal fun automaticCheckSkip(
    enabled: Boolean,
    lastCheckedAt: Long,
    now: Long,
    posture: NetworkPosture,
): UpdateCheckSkip? = when {
    !enabled -> UpdateCheckSkip.TURNED_OFF
    // A clock that moved backwards makes the last check look like the future. Check again then.
    lastCheckedAt in 1..now && now - lastCheckedAt < AUTOMATIC_CHECK_INTERVAL_MS -> UpdateCheckSkip.CHECKED_RECENTLY
    posture.dataSaver -> UpdateCheckSkip.DATA_SAVER
    posture.metered -> UpdateCheckSkip.METERED
    else -> null
}

internal fun interpretReleaseResponse(
    code: Int,
    body: String,
    adapter: JsonAdapter<GitHubRelease>,
    installedVersionCode: Int,
    installedVersionName: String,
): UpdateCheckResult {
    if (code == 404) return UpdateCheckResult.NoRelease
    if (code == 403 || code == 429) return UpdateCheckResult.Failed(UpdateCheckFailure.RATE_LIMITED)
    if (code !in 200..299) return UpdateCheckResult.Failed(UpdateCheckFailure.UNREADABLE)
    val parsed = adapter.fromJsonOrNull(body) ?: return UpdateCheckResult.Failed(UpdateCheckFailure.UNREADABLE)
    if (parsed.draft || parsed.prerelease) return UpdateCheckResult.NoRelease
    val release = parsed.toPublishedRelease() ?: return UpdateCheckResult.Failed(UpdateCheckFailure.UNREADABLE)
    return if (isNewerRelease(release, installedVersionCode, installedVersionName)) {
        UpdateCheckResult.Available(release)
    } else {
        UpdateCheckResult.UpToDate(release.versionName)
    }
}

private fun <T> JsonAdapter<T>.fromJsonOrNull(json: String): T? =
    try {
        fromJson(json)
    } catch (_: IOException) {
        null
    } catch (_: JsonDataException) {
        null
    }

internal fun GitHubRelease.toPublishedRelease(): PublishedRelease? {
    val tag = tagName.trim()
    val versionName = LEADING_VERSION.find(tag)?.groupValues?.get(1) ?: return null
    return PublishedRelease(
        tag = tag,
        versionName = versionName,
        versionCode = assets.mapNotNull { asset ->
            ASSET_VERSION_CODE.find(asset.name)?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull(),
        notes = plainReleaseNotes(body.orEmpty()),
        pageUrl = releasePageUrl(htmlUrl),
    )
}

/**
 * The installed versionCode against the one in the published APK names. A release whose files
 * don't carry a versionCode is compared by version name instead.
 */
internal fun isNewerRelease(release: PublishedRelease, installedVersionCode: Int, installedVersionName: String): Boolean =
    release.versionCode?.let { it > installedVersionCode }
        ?: (compareVersionNames(release.versionName, installedVersionName) > 0)

internal fun compareVersionNames(first: String, second: String): Int {
    val left = versionParts(first)
    val right = versionParts(second)
    for (index in 0 until maxOf(left.size, right.size)) {
        val difference = (left.getOrNull(index) ?: 0L).compareTo(right.getOrNull(index) ?: 0L)
        if (difference != 0) return difference
    }
    return 0
}

private fun versionParts(name: String): List<Long> =
    LEADING_VERSION.find(name.trim())?.groupValues?.get(1)
        ?.split('.')
        ?.map { it.toLongOrNull() ?: Long.MAX_VALUE }
        .orEmpty()

/** Only a page under Aura's own releases is ever opened. Anything else falls back to the latest-release page. */
internal fun releasePageUrl(raw: String?): String {
    val url = raw?.trim().orEmpty()
    val trusted = url.startsWith(RELEASE_PAGE_PREFIX) && url.none { it.isWhitespace() || it.isISOControl() }
    return if (trusted) url else RELEASES_PAGE_URL
}

/** Release notes are written in Markdown. This keeps the words and drops the markup. */
internal fun plainReleaseNotes(body: String): String {
    val text = body.replace("\r\n", "\n")
        .lineSequence()
        .map { line -> MARKDOWN_BULLET.replace(MARKDOWN_HEADING.replace(line.trimEnd(), "")) { "${it.groupValues[1]}• " } }
        .joinToString("\n")
        .replace(MARKDOWN_LINK, "$1")
        .replace(MARKDOWN_STRONG, "$1")
        .replace(MARKDOWN_CODE, "$1")
        .replace(BLANK_RUN, "\n\n")
        .trim()
    return if (text.length <= MAX_RELEASE_NOTES_CHARS) text else text.take(MAX_RELEASE_NOTES_CHARS).trimEnd() + "…"
}

/**
 * Looks for a newer published Aura, and only when the user has turned the check on. It reads one
 * public page, stores what it found, and leaves downloading and installing to the user.
 */
@Singleton
class UpdateChecker internal constructor(
    private val prefs: PreferencesManager,
    private val feed: ReleaseFeed,
    private val posture: NetworkPostureReader,
    moshi: Moshi,
    private val installedVersionCode: Int,
    private val installedVersionName: String,
    /** False in a Play build. Play keeps those up to date, so they never ask GitHub. */
    val offered: Boolean,
    private val now: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefs: PreferencesManager,
        client: OkHttpClient,
        moshi: Moshi,
    ) : this(
        prefs = prefs,
        feed = OkHttpReleaseFeed(client),
        posture = AndroidNetworkPosture(context),
        moshi = moshi,
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedVersionName = BuildConfig.VERSION_NAME,
        offered = currentProviderChannel == ProviderChannel.GITHUB,
        now = System::currentTimeMillis,
    )

    private val releaseAdapter = moshi.adapter(GitHubRelease::class.java)
    private val storedAdapter = moshi.adapter(PublishedRelease::class.java)
    private val checkLock = Mutex()
    @Volatile
    private var automaticCheckTried = false

    private val _lastResult = MutableStateFlow<UpdateCheckResult?>(null)

    /** What the most recent check in this app run came back with. */
    val lastResult: StateFlow<UpdateCheckResult?> = _lastResult.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** The newer version the last check found. It clears once that version is installed or the check is turned off. */
    val available: Flow<PublishedRelease?> =
        combine(prefs.updateCheckEnabled, prefs.updateCheckRelease) { enabled, stored ->
            if (offered && enabled) decodeStored(stored)?.takeIf(::isNewer) else null
        }.distinctUntilChanged()

    /** That version until the user closes its notice. Settings keeps listing it either way. */
    val notice: Flow<PublishedRelease?> =
        combine(available, prefs.updateCheckDismissedTag) { release, dismissedTag ->
            release?.takeIf { it.tag != dismissedTag }
        }.distinctUntilChanged()

    suspend fun dismissNotice(release: PublishedRelease) = prefs.setUpdateCheckDismissedTag(release.tag)

    /**
     * The check Aura starts by itself: at most one try per app run and one answer a day, and never
     * on a metered network or with Data Saver on.
     */
    suspend fun checkIfDue(): UpdateCheckResult {
        val skip = automaticCheckSkip(
            enabled = turnedOn(),
            lastCheckedAt = prefs.updateCheckLastAt.first(),
            now = now(),
            posture = posture.read(),
        ) ?: UpdateCheckSkip.CHECKED_RECENTLY.takeIf { automaticCheckTried }
        if (skip != null) {
            val skipped = UpdateCheckResult.Skipped(skip)
            if (skip == UpdateCheckSkip.DATA_SAVER || skip == UpdateCheckSkip.METERED) _lastResult.value = skipped
            return skipped
        }
        automaticCheckTried = true
        return runCheck()
    }

    /** The check the user asked for. It runs on any network, and still never while the check is turned off. */
    suspend fun checkNow(): UpdateCheckResult =
        if (turnedOn()) runCheck() else UpdateCheckResult.Skipped(UpdateCheckSkip.TURNED_OFF)

    private suspend fun turnedOn(): Boolean = offered && prefs.updateCheckEnabled.first()

    /** Turning the check off also forgets what it last found. */
    suspend fun setEnabled(enabled: Boolean) {
        prefs.setUpdateCheckEnabled(enabled)
        if (!enabled) {
            prefs.setUpdateCheckRelease("")
            prefs.setUpdateCheckLastAt(0L)
            prefs.setUpdateCheckDismissedTag("")
            _lastResult.value = null
        }
    }

    private suspend fun runCheck(): UpdateCheckResult = checkLock.withLock {
        _checking.value = true
        try {
            val result = try {
                val response = feed.latest()
                interpretReleaseResponse(
                    code = response.code,
                    body = response.body,
                    adapter = releaseAdapter,
                    installedVersionCode = installedVersionCode,
                    installedVersionName = installedVersionName,
                )
            } catch (_: IOException) {
                UpdateCheckResult.Failed(UpdateCheckFailure.NETWORK)
            }
            when (result) {
                is UpdateCheckResult.Available -> {
                    prefs.setUpdateCheckRelease(storedAdapter.toJson(result.release))
                    prefs.setUpdateCheckLastAt(now())
                }
                is UpdateCheckResult.UpToDate, UpdateCheckResult.NoRelease -> {
                    prefs.setUpdateCheckRelease("")
                    prefs.setUpdateCheckLastAt(now())
                }
                // A failed check leaves the last answer alone and doesn't count as today's check.
                is UpdateCheckResult.Failed, is UpdateCheckResult.Skipped -> Unit
            }
            _lastResult.value = result
            result
        } finally {
            _checking.value = false
        }
    }

    private fun isNewer(release: PublishedRelease): Boolean =
        isNewerRelease(release, installedVersionCode, installedVersionName)

    private fun decodeStored(stored: String): PublishedRelease? =
        if (stored.isBlank()) null else storedAdapter.fromJsonOrNull(stored)
}

private class OkHttpReleaseFeed(private val client: OkHttpClient) : ReleaseFeed {
    override suspend fun latest(): ReleaseFeedResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            ReleaseFeedResponse(
                code = response.code,
                body = if (response.isSuccessful) response.peekBody(MAX_RELEASE_RESPONSE_BYTES).string() else "",
            )
        }
    }
}

internal class AndroidNetworkPosture(private val context: Context) : NetworkPostureReader {
    override fun read(): NetworkPosture {
        // No connectivity service means nothing is known about the network, so treat it as metered.
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkPosture(metered = true, dataSaver = false)
        return NetworkPosture(
            metered = connectivity.isActiveNetworkMetered,
            dataSaver = connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
        )
    }
}
