package com.freevibe.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ── TalkBack announcements and reading order ──────────────────────
//
// A polite live region is read when its own text changes and stays quiet while it doesn't. So
// everything here hangs the announcement on text that only moves on a real transition: a load
// landing, a download finishing, a preview starting. Nothing that ticks (a percent, a playback
// position) belongs in one.

/** How long a state card has to stay on screen before it reads itself out. */
internal const val STATE_MESSAGE_SETTLE_MS = 300L

// The modifiers that never change are built once, here and on AuraFeedSection. A screen that
// recomposes on every playback tick then hands Compose an equal modifier instead of a new one.
private val PoliteLiveRegion = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
private val FeedTraversalRoot = Modifier.semantics { isTraversalGroup = true }

/**
 * Polite live region. With no [announcement] the node's own text is what gets read, which suits a
 * status line that's already on screen. Pass one for a container or a node with no text of its own.
 */
fun Modifier.politeAnnouncement(announcement: String? = null): Modifier {
    // A blank description would still be a TalkBack stop, with nothing to say.
    val spoken = announcement?.takeIf { it.isNotBlank() } ?: return then(PoliteLiveRegion)
    return semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = spoken
    }
}

/**
 * For a row that ticks until it's done, like a download. It only becomes a live region once
 * [settled], so the percent on the way there is never read out and the outcome is read once.
 */
fun SemanticsPropertyReceiver.announceOnceSettled(settled: Boolean) {
    if (settled) liveRegion = LiveRegionMode.Polite
}

/**
 * A state card's title and description as one TalkBack stop that reads itself politely.
 *
 * It waits a beat before becoming a live region. Screens often show their empty card for a frame or
 * two before the first database or network result lands, and that flash shouldn't be spoken as
 * "nothing here". After that the text only changes when the state does, so recomposing says nothing.
 */
@Composable
fun AuraStateMessage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STATE_MESSAGE_SETTLE_MS)
        settled = true
    }
    val announces = settled
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            heading()
            if (announces) liveRegion = LiveRegionMode.Polite
        },
        content = content,
    )
}

/**
 * A status line nobody sees. TalkBack reads [status] when it changes, and a blank one is skipped.
 *
 * Put it last in the Box it shares with the content. Compose leaves a node out of the accessibility
 * tree when something drawn later covers it, so a list declared after this would hide it. Two of
 * them in one Box need different corners for the same reason.
 */
@Composable
fun AuraLiveStatus(status: String, modifier: Modifier = Modifier) {
    Box(modifier.size(1.dp).politeAnnouncement(status))
}

/**
 * The unseen status line for a feed's list. It says [loading] when a refresh runs over results that
 * are already showing, and [ready] once a load this screen watched has landed.
 *
 * It's blank with nothing to show, because the loading row and the empty and error cards read
 * themselves. It's also blank for a list that was simply there when the screen opened.
 */
@Composable
fun AuraFeedStatus(
    isLoading: Boolean,
    hasContent: Boolean,
    loading: String,
    ready: String,
    modifier: Modifier = Modifier,
) {
    var sawLoad by remember { mutableStateOf(false) }
    SideEffect { if (isLoading) sawLoad = true }
    AuraLiveStatus(
        status = feedStatus(
            isLoading = isLoading,
            hasContent = hasContent,
            sawLoad = sawLoad,
            loading = loading,
            ready = ready,
        ),
        modifier = modifier,
    )
}

internal fun feedStatus(
    isLoading: Boolean,
    hasContent: Boolean,
    sawLoad: Boolean,
    loading: String,
    ready: String,
): String = when {
    !hasContent -> ""
    isLoading -> loading
    sawLoad -> ready
    else -> ""
}

/** The parts of a feed screen, in the order TalkBack reads them. */
enum class AuraFeedSection(internal val traversalOrder: Float) {
    /** Title, search and filters. */
    Header(-2f),

    /** Download progress and source banners. */
    Status(-1f),

    /** The grid or list, or the card standing in for it. Anything unmarked is read alongside it. */
    Content(0f),
    ;

    internal val traversalGroup: Modifier = Modifier.semantics {
        isTraversalGroup = true
        traversalIndex = traversalOrder
    }
}

/**
 * Keeps [feedSection] ordering inside one screen. Traversal indexes are compared between everything
 * in the nearest traversal group, so without this a feed's sections would be sorted against the
 * navigation bar.
 */
fun Modifier.feedTraversalRoot(): Modifier = then(FeedTraversalRoot)

/** Reads this part of a feed as one block, in [AuraFeedSection] order instead of by position. */
fun Modifier.feedSection(section: AuraFeedSection): Modifier = then(section.traversalGroup)
