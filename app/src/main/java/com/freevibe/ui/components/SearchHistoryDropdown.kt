package com.freevibe.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.ui.theme.AuraShapes

/** Space between a search field and the recent searches listed under it. */
private val SearchHistoryGap = 4.dp

/**
 * A search [field] with its recent searches directly below it.
 *
 * The list starts where the measured field ends. A fixed offset put it over the field as soon as
 * the field was taller than that offset, which a wrapped placeholder at large text makes it.
 */
@Composable
fun SearchFieldWithHistory(
    recentQueries: List<String>,
    isHistoryVisible: Boolean,
    onQueryClick: (String) -> Unit,
    onDeleteQuery: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        field()
        SearchHistoryDropdown(
            recentQueries = recentQueries,
            isVisible = isHistoryVisible,
            onQueryClick = onQueryClick,
            onDeleteQuery = onDeleteQuery,
            onClearAll = onClearAll,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SearchHistoryGap),
        )
    }
}

@Composable
fun SearchHistoryDropdown(
    recentQueries: List<String>,
    isVisible: Boolean,
    onQueryClick: (String) -> Unit,
    onDeleteQuery: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible && recentQueries.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Transparent,
            shape = AuraShapes.Medium,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                            ),
                        ),
                    ),
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.search_history_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(
                            stringResource(R.string.search_history_clear_all),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Query items
                recentQueries.take(8).forEach { query ->
                    val openLabel = stringResource(R.string.search_history_action_search_for, query)
                    val recentSearchDescription =
                        stringResource(R.string.search_history_recent_query_cd, query)
                    val removeLabel = stringResource(R.string.search_history_remove_query, query)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = openLabel) { onQueryClick(query) }
                            .semantics {
                                contentDescription = recentSearchDescription
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.History, null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            query,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = lineCapAtDefaultTextSize(1),
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { onDeleteQuery(query) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                removeLabel,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
