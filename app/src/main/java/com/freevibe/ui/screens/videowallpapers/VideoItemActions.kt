package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.MEDIA_TYPE_VIDEO
import com.freevibe.data.model.VideoWallpaperAction
import com.freevibe.data.model.normalizeVideoWallpaperLicense
import com.freevibe.data.model.videoWallpaperLicenseCapabilities
import com.freevibe.ui.screens.wallpapers.reportSourceUrl
import com.freevibe.util.tryStartActivity

/** What a video offers besides Apply, read from the same license policy Apply uses. */
internal data class VideoItemActionAvailability(
    val canDownload: Boolean,
    val downloadNeedsConfirmation: Boolean,
    val canShare: Boolean,
    val canReport: Boolean,
)

internal fun VideoWallpaperItem.itemActionAvailability(reportsEnabled: Boolean = true): VideoItemActionAvailability {
    val capabilities = videoWallpaperLicenseCapabilities()
    return VideoItemActionAvailability(
        canDownload = capabilities.canUse(VideoWallpaperAction.DOWNLOAD),
        downloadNeedsConfirmation = capabilities.requiresConfirmation(VideoWallpaperAction.DOWNLOAD),
        // Share sends the source page, never the file, so there has to be one.
        canShare = capabilities.canUse(VideoWallpaperAction.SHARE) && sourcePageUrl.isNotBlank(),
        // A gallery clip is the user's own, so there's nobody to report it to. Reports go to the
        // community queue, so they're off while that provider is.
        canReport = reportsEnabled && contentSource != ContentSource.LOCAL,
    )
}

/** The source page with its title, creator and provider, so credit travels with the link. */
internal fun Resources.videoShareText(item: VideoWallpaperItem): String =
    if (item.uploaderName.isBlank()) {
        getString(R.string.video_wp_share_text_no_creator, item.title, item.source, item.sourcePageUrl)
    } else {
        getString(R.string.video_wp_share_text, item.title, item.uploaderName, item.source, item.sourcePageUrl)
    }

/** Opens the share sheet for [item]'s source page. False when nothing on the device can take it. */
internal fun Context.shareVideoSource(item: VideoWallpaperItem): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, resources.videoShareText(item))
    }
    return tryStartActivity(Intent.createChooser(intent, getString(R.string.video_wp_share_title)), "video share")
}

/** A community report for [this] video, pointing moderators at its source page or, failing that, its stream. */
internal fun VideoWallpaperItem.reportInput(
    reason: CommunityReportReason,
    note: String,
    streamUrl: String?,
): CommunityReportInput = CommunityReportInput(
    contentId = id,
    contentType = MEDIA_TYPE_VIDEO,
    contentSource = contentSource,
    reason = reason,
    note = note,
    sourceUrl = reportSourceUrl(sourcePageUrl, streamUrl.orEmpty()),
    license = normalizeVideoWallpaperLicense(contentSource, license),
    uploaderName = uploaderName,
)

/** Download, Share and Report for the full-screen preview, drawn over video. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImmersiveVideoActions(
    actions: VideoItemActionAvailability,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
) {
    if (!actions.canDownload && !actions.canShare && !actions.canReport) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (actions.canDownload) {
            ImmersiveActionButton(
                label = stringResource(if (isDownloading) R.string.video_wp_downloading else R.string.video_wp_download),
                icon = Icons.Default.Download,
                enabled = !isDownloading,
                busy = isDownloading,
                onClick = onDownload,
            )
        }
        if (actions.canShare) {
            ImmersiveActionButton(stringResource(R.string.video_wp_share_source), Icons.Default.Share, onClick = onShare)
        }
        if (actions.canReport) {
            ImmersiveActionButton(stringResource(R.string.video_wp_report), Icons.Default.Flag, onClick = onReport)
        }
    }
}

@Composable
private fun ImmersiveActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Black.copy(alpha = 0.48f),
            contentColor = Color.White,
            disabledContainerColor = Color.Black.copy(alpha = 0.48f),
            disabledContentColor = Color.White.copy(alpha = 0.72f),
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
