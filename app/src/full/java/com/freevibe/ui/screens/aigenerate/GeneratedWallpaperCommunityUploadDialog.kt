package com.freevibe.ui.screens.aigenerate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.data.model.COMMUNITY_UPLOAD_LICENSES
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.ui.components.CommunityPolicyNotice
import com.freevibe.ui.policy.CommunityUploadPolicyKind
import com.freevibe.ui.policy.communityUploadPolicyCopy
import com.freevibe.ui.theme.AuraShapes

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneratedWallpaperCommunityUploadDialog(
    initialTags: List<String>,
    isUploading: Boolean,
    uploadProgress: Float,
    onUpload: (name: String, category: String, tags: List<String>, rights: CommunityUploadRights) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = stringResource(R.string.ai_share_community_default_name)
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var selectedCategory by remember { mutableStateOf("other") }
    var selectedLicense by remember { mutableStateOf(COMMUNITY_UPLOAD_LICENSES.first()) }
    var sourceUrl by remember { mutableStateOf("") }
    var rightsAttested by remember { mutableStateOf(false) }
    var tagsText by remember(initialTags) { mutableStateOf(initialTags.joinToString(", ")) }
    val policyCopy = remember { communityUploadPolicyCopy(CommunityUploadPolicyKind.WALLPAPER) }
    val categories = listOf(
        "other" to stringResource(R.string.wallpapers_upload_category_general),
        "amoled" to stringResource(R.string.wallpapers_upload_category_amoled),
        "minimal" to stringResource(R.string.wallpapers_upload_category_minimal),
        "nature" to stringResource(R.string.wallpapers_upload_category_nature),
        "abstract" to stringResource(R.string.wallpapers_upload_category_abstract),
        "city" to stringResource(R.string.wallpapers_upload_category_city),
        "space" to stringResource(R.string.wallpapers_upload_category_space),
    )
    val parsedTags = remember(tagsText) {
        tagsText.split(',', '#').map { it.trim() }.filter { it.isNotBlank() }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text(stringResource(R.string.ai_share_community_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        stringResource(R.string.ai_share_community_auto_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feed_upload_name_label)) },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.feed_upload_category_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            enabled = !isUploading,
                            label = { Text(label) },
                            shape = AuraShapes.Medium,
                        )
                    }
                }
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.feed_upload_tags_label)) },
                    supportingText = { Text(stringResource(R.string.feed_upload_tags_hint)) },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.feed_upload_license_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COMMUNITY_UPLOAD_LICENSES.forEach { license ->
                        FilterChip(
                            selected = selectedLicense == license,
                            onClick = { selectedLicense = license },
                            enabled = !isUploading,
                            label = { Text(license) },
                            shape = AuraShapes.Medium,
                        )
                    }
                }
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text(stringResource(R.string.feed_upload_source_label)) },
                    supportingText = { Text(stringResource(R.string.feed_upload_source_hint)) },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth(),
                )
                CommunityPolicyNotice(
                    title = policyCopy.publicTitle,
                    body = "${policyCopy.publicBody} ${policyCopy.takedownBody}",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rightsAttested,
                        onCheckedChange = { rightsAttested = it },
                        enabled = !isUploading,
                    )
                    Text(
                        policyCopy.attestation,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isUploading) {
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.wallpapers_upload_progress_percent, (uploadProgress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUpload(
                        name.trim(),
                        selectedCategory,
                        parsedTags,
                        CommunityUploadRights(
                            license = selectedLicense,
                            rightsAttested = rightsAttested,
                            sourceUrl = sourceUrl.trim(),
                        ),
                    )
                },
                enabled = !isUploading && name.isNotBlank() && rightsAttested,
                shape = AuraShapes.Medium,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.ai_share_community))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading,
                shape = AuraShapes.Medium,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
