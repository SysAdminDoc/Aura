package com.freevibe.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.ui.theme.AuraShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import com.freevibe.ui.components.GlassCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val isComplete: Boolean = false,
    val selectedStyles: Set<String> = emptySet(),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesManager,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun toggleStyle(style: String) {
        _state.update { st ->
            val updated = if (style in st.selectedStyles) st.selectedStyles - style else st.selectedStyles + style
            st.copy(selectedStyles = updated)
        }
    }

    fun complete() {
        viewModelScope.launch {
            prefs.setUserStyles(_state.value.selectedStyles.joinToString(","))
            _state.update { it.copy(isComplete = true) }
        }
    }

    fun skip() {
        _state.update { it.copy(isComplete = true) }
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                )
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingTopBar(
                currentPage = pagerState.currentPage,
                totalPages = 4,
                onSkip = viewModel::skip,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> FeaturesPage()
                    2 -> StylePickerPage(state.selectedStyles) { viewModel.toggleStyle(it) }
                    3 -> ReadyPage()
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { i ->
                    val width by animateDpAsState(
                        targetValue = if (pagerState.currentPage == i) 22.dp else 7.dp,
                        animationSpec = tween(durationMillis = 220),
                        label = "indicator_width",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(width)
                            .height(7.dp)
                            .clip(AuraShapes.Hairline)
                            .background(
                                if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            ),
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = AuraShapes.Medium,
                    ) { Text(stringResource(R.string.common_back)) }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.complete()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = AuraShapes.Medium,
                ) {
                    Text(
                        if (pagerState.currentPage == 3) {
                            stringResource(R.string.onboarding_get_started)
                        } else {
                            stringResource(R.string.common_next)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentPage: Int,
    totalPages: Int,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingLabel(
            label = stringResource(R.string.onboarding_step_label, currentPage + 1, totalPages),
            icon = Icons.Default.AutoAwesome,
            tint = MaterialTheme.colorScheme.secondary,
        )
        if (currentPage < totalPages - 1) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip_setup), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnboardingLabel(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = AuraShapes.Compact,
        color = tint.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@Composable
private fun WelcomePage() {
    PageLayout(
        eyebrow = stringResource(R.string.onboarding_welcome_eyebrow),
        icon = Icons.Default.Wallpaper,
        iconColor = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_welcome_body),
        badges = listOf(
            Icons.Default.LockOpen to stringResource(R.string.onboarding_badge_no_account),
            Icons.Default.Block to stringResource(R.string.onboarding_badge_no_ads),
            Icons.Default.Verified to stringResource(R.string.onboarding_badge_open_source),
        ),
    )
}

@Composable
private fun FeaturesPage() {
    val features = listOf(
        Triple(Icons.Default.Wallpaper, stringResource(R.string.onboarding_feature_wallpapers_title), stringResource(R.string.onboarding_feature_wallpapers_body)),
        Triple(Icons.Default.VideoLibrary, stringResource(R.string.onboarding_feature_video_title), stringResource(R.string.onboarding_feature_video_body)),
        Triple(Icons.Default.MusicNote, stringResource(R.string.onboarding_feature_sounds_title), stringResource(R.string.onboarding_feature_sounds_body)),
        Triple(Icons.Default.Schedule, stringResource(R.string.onboarding_feature_rotation_title), stringResource(R.string.onboarding_feature_rotation_body)),
        Triple(Icons.Default.Cloud, stringResource(R.string.onboarding_feature_weather_title), stringResource(R.string.onboarding_feature_weather_body)),
        Triple(Icons.Default.DarkMode, stringResource(R.string.onboarding_feature_amoled_title), stringResource(R.string.onboarding_feature_amoled_body)),
    )

    PageLayout(
        eyebrow = stringResource(R.string.onboarding_features_eyebrow),
        icon = Icons.Default.AutoAwesome,
        iconColor = MaterialTheme.colorScheme.secondary,
        title = stringResource(R.string.onboarding_features_title),
        description = stringResource(R.string.onboarding_features_body),
        badges = listOf(
            Icons.Default.Wallpaper to stringResource(R.string.nav_wallpapers),
            Icons.Default.VideoLibrary to stringResource(R.string.onboarding_badge_video_loops),
            Icons.Default.NotificationsActive to stringResource(R.string.onboarding_badge_sound_tools),
        ),
        content = {
            features.forEachIndexed { index, (icon, title, subtitle) ->
                val anim = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    anim.animateTo(1f, animationSpec = tween(400, delayMillis = index * 80))
                }
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = anim.value
                        translationX = (1f - anim.value) * -60f
                    },
                ) {
                    FeatureRow(icon, title, subtitle)
                }
            }
        },
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = AuraShapes.Medium,
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePickerPage(selectedStyles: Set<String>, onToggle: (String) -> Unit) {
    val styles = listOf(
        OnboardingStyleOption("minimal", stringResource(R.string.onboarding_style_minimal), stringResource(R.string.onboarding_style_minimal_body), Icons.Default.CropSquare, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("amoled", stringResource(R.string.onboarding_style_amoled), stringResource(R.string.onboarding_style_amoled_body), Icons.Default.DarkMode, MaterialTheme.colorScheme.primary),
        OnboardingStyleOption("nature", stringResource(R.string.onboarding_style_nature), stringResource(R.string.onboarding_style_nature_body), Icons.Default.Landscape, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("space", stringResource(R.string.onboarding_style_space), stringResource(R.string.onboarding_style_space_body), Icons.Default.Public, MaterialTheme.colorScheme.tertiary),
        OnboardingStyleOption("anime", stringResource(R.string.onboarding_style_anime), stringResource(R.string.onboarding_style_anime_body), Icons.Default.Movie, MaterialTheme.colorScheme.primary),
        OnboardingStyleOption("abstract", stringResource(R.string.onboarding_style_abstract), stringResource(R.string.onboarding_style_abstract_body), Icons.Default.AutoAwesome, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("neon", stringResource(R.string.onboarding_style_neon), stringResource(R.string.onboarding_style_neon_body), Icons.Default.Bolt, MaterialTheme.colorScheme.tertiary),
        OnboardingStyleOption("city", stringResource(R.string.onboarding_style_city), stringResource(R.string.onboarding_style_city_body), Icons.Default.LocationCity, MaterialTheme.colorScheme.primary),
        OnboardingStyleOption("gradient", stringResource(R.string.onboarding_style_gradient), stringResource(R.string.onboarding_style_gradient_body), Icons.Default.BlurOn, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("dark", stringResource(R.string.onboarding_style_dark), stringResource(R.string.onboarding_style_dark_body), Icons.Default.Brightness3, MaterialTheme.colorScheme.tertiary),
    )

    PageLayout(
        eyebrow = stringResource(R.string.onboarding_style_eyebrow),
        icon = Icons.Default.Tune,
        iconColor = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.onboarding_style_title),
        description = stringResource(R.string.onboarding_style_body),
        badges = emptyList(),
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.onboarding_style_profile),
                    style = MaterialTheme.typography.titleMedium,
                )
                OnboardingLabel(
                    label = if (selectedStyles.isEmpty()) {
                        stringResource(R.string.onboarding_optional)
                    } else {
                        stringResource(R.string.onboarding_selected_count, selectedStyles.size)
                    },
                    icon = Icons.Default.CheckCircle,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(8.dp))

            styles.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { option ->
                        val selected = option.id in selectedStyles
                        Surface(
                            onClick = { onToggle(option.id) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 148.dp)
                                .semantics { this.selected = selected },
                            shape = AuraShapes.Medium,
                            color = if (selected) option.tint.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) option.tint.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = AuraShapes.Medium,
                                        color = option.tint.copy(alpha = 0.14f),
                                    ) {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = null,
                                            tint = option.tint,
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .size(18.dp),
                                        )
                                    }
                                    AnimatedVisibility(visible = selected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = option.tint,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        },
    )
}

@Composable
private fun ReadyPage() {
    PageLayout(
        eyebrow = stringResource(R.string.onboarding_ready_eyebrow),
        icon = Icons.Default.Celebration,
        iconColor = MaterialTheme.colorScheme.secondary,
        title = stringResource(R.string.onboarding_ready_title),
        description = stringResource(R.string.onboarding_ready_body),
        badges = listOf(
            Icons.Default.Wallpaper to stringResource(R.string.onboarding_badge_discover),
            Icons.Default.MusicNote to stringResource(R.string.nav_sounds),
            Icons.Default.Widgets to stringResource(R.string.onboarding_badge_widget),
        ),
        content = {
            FeatureRow(
                icon = Icons.Default.Explore,
                title = stringResource(R.string.onboarding_ready_discover_title),
                subtitle = stringResource(R.string.onboarding_ready_discover_body),
            )
            FeatureRow(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.onboarding_ready_refine_title),
                subtitle = stringResource(R.string.onboarding_ready_refine_body),
            )
            FeatureRow(
                icon = Icons.Default.Widgets,
                title = stringResource(R.string.onboarding_ready_widget_title),
                subtitle = stringResource(R.string.onboarding_ready_widget_body),
            )
        },
    )
}

private data class OnboardingStyleOption(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageLayout(
    eyebrow: String,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    badges: List<Pair<ImageVector, String>> = emptyList(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val hasSupportingContent = content != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 12.dp, vertical = if (hasSupportingContent) 8.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingLabel(label = eyebrow, icon = Icons.Default.AutoAwesome, tint = iconColor)
            Spacer(Modifier.height(if (hasSupportingContent) 10.dp else 16.dp))
            Box(
                modifier = Modifier
                    .size(if (hasSupportingContent) 68.dp else 96.dp)
                    .clip(AuraShapes.Medium)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    tint = iconColor,
                    modifier = Modifier.size(if (hasSupportingContent) 32.dp else 44.dp),
                )
            }
            Spacer(Modifier.height(if (hasSupportingContent) 12.dp else 20.dp))
            Text(
                title,
                style = if (hasSupportingContent) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (hasSupportingContent) 8.dp else 12.dp))
            Text(
                description,
                style = if (hasSupportingContent) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(if (hasSupportingContent) 12.dp else 18.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    badges.forEach { (badgeIcon, label) ->
                        OnboardingLabel(
                            label = label,
                            icon = badgeIcon,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            if (content != null) {
                Spacer(Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}
