package com.freevibe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freevibe.data.model.CommunityReportReason

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommunityReportDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (CommunityReportReason, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedReason by remember { mutableStateOf(CommunityReportReason.RIGHTS) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CommunityReportReason.entries.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Details") },
                    minLines = 3,
                    maxLines = 5,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(selectedReason, note)
                    onDismiss()
                },
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
