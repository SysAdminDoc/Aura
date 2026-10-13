package com.freevibe.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.service.PublishedRelease
import com.freevibe.ui.util.openExternalUrl

/** What changed in a newer Aura, with one way out: the release page. Aura never fetches the APK itself. */
@Composable
fun UpdateNotesDialog(
    release: PublishedRelease,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val openFailed = stringResource(R.string.update_notes_open_failed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title, release.versionName)) },
        text = {
            Text(
                text = release.notes.ifBlank { stringResource(R.string.update_notes_empty) },
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    openExternalUrl(context, release.pageUrl, openFailed)
                },
            ) {
                Text(stringResource(R.string.update_notes_open_page))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}
