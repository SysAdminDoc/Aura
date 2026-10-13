package com.freevibe.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.ui.theme.AuraShapes
import kotlinx.coroutines.launch
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperAction
import com.freevibe.data.model.wallpaperActionMessage
import com.freevibe.data.remote.toSound
import com.freevibe.data.remote.toWallpaper
import com.freevibe.ui.navigation.LocalAuraNavigationLayout
import com.freevibe.ui.navigation.PrimaryNavigationBarHeight
import com.freevibe.ui.navigation.PrimaryNavigationBarSidePadding
import com.freevibe.ui.navigation.PrimaryNavigationRailTopSpace
import com.freevibe.ui.navigation.PrimaryNavigationRailWidth
import com.freevibe.ui.navigation.Screen
import com.freevibe.ui.navigation.auraNavigationLayoutForWidth
import com.freevibe.ui.navigation.bottomBarLabelsFit
import com.freevibe.ui.navigation.isExpanded
import com.freevibe.ui.navigation.railLabelsFit
import com.freevibe.ui.navigation.rememberWidestNavigationLabel
import com.freevibe.ui.screens.community.CommunityReportsScreen
import com.freevibe.ui.screens.categories.CategoriesScreen
import com.freevibe.ui.screens.collections.CollectionsScreen
import com.freevibe.ui.screens.community.CreatorProfileScreen
import com.freevibe.ui.screens.videowallpapers.VideoWallpapersScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.freevibe.ui.screens.downloads.DownloadsScreen
import com.freevibe.ui.screens.editor.SoundEditorScreen
import com.freevibe.ui.screens.editor.WallpaperCropScreen
import com.freevibe.ui.screens.editor.WallpaperEditorScreen
import com.freevibe.ui.screens.favorites.FavoritesScreen
import com.freevibe.ui.screens.licenses.LicensesScreen
import com.freevibe.ui.screens.library.LibraryScreen
import com.freevibe.ui.screens.onboarding.OnboardingScreen
import com.freevibe.ui.screens.settings.SettingsScreen
import com.freevibe.ui.screens.settings.RotationHealthScreen
import com.freevibe.ui.screens.settings.SettingsNavigation
import com.freevibe.ui.screens.settings.WallpaperHistoryScreen
import com.freevibe.ui.screens.search.UniversalSearchScreen
import com.freevibe.ui.screens.sounds.ContactPickerScreen
import com.freevibe.ui.screens.sounds.DeviceSoundsScreen
import com.freevibe.ui.screens.sounds.SoundDetailScreen
import com.freevibe.ui.screens.sounds.SoundsScreen
import com.freevibe.ui.screens.wallpapers.WallpaperDetailScreen
import com.freevibe.ui.screens.wallpapers.WallpapersScreen
import com.freevibe.ui.screens.wallpapers.canApplyFromWallpaperPreview
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.CountBadge
import com.freevibe.data.repository.HiddenItemKind
import com.freevibe.ui.screens.hidden.HiddenItemsScreen

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FreeVibeRootEntryPoint {
    fun preferencesManager(): PreferencesManager
    fun favoritesRepository(): com.freevibe.data.repository.FavoritesRepository
    fun applyFeedbackBus(): com.freevibe.service.ApplyFeedbackBus
    fun wallpaperApplier(): com.freevibe.service.WallpaperApplier
    fun wallpaperHistoryManager(): com.freevibe.service.WallpaperHistoryManager
    fun databaseDowngradeReceiptStore(): com.freevibe.data.local.DatabaseDowngradeReceiptStore
    fun updateChecker(): com.freevibe.service.UpdateChecker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeVibeRoot(
    initialNavigateTo: String? = null,
    initialWallpaper: Wallpaper? = null,
    navigationToken: Long = 0L,
) {
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context, FreeVibeRootEntryPoint::class.java)
    }
    val favoritesCount by remember { entryPoint.favoritesRepository().count() }.collectAsStateWithLifecycle(initialValue = 0)
    val preferencesManager = remember { entryPoint.preferencesManager() }
    var onboardingDone by remember { mutableStateOf(preferencesManager.isOnboardingComplete()) }
    val navigationRootRoute = if (onboardingDone) Screen.Wallpapers.route else Screen.Onboarding.route

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Mobile-data warning: the browse tabs stream and download wallpapers, video wallpapers, and
    // sounds. Warn once per app run while on a metered connection; dismissal sticks for the session.
    val onMeteredConnection = com.freevibe.ui.components.rememberOnMeteredConnection()
    var dataWarningDismissed by rememberSaveable { mutableStateOf(false) }

    // A downgrade reset the library before any screen existed. The user's next
    // move is to wonder where their favorites went, so this is raised at the root
    // rather than buried in Settings, and it survives process death until it has
    // actually been read.
    val downgradeReceiptStore = remember { entryPoint.databaseDowngradeReceiptStore() }
    var downgradeReceipt by remember { mutableStateOf(downgradeReceiptStore.read()) }

    // The update check is off until the user turns it on in Settings. When it is on, this is the
    // one automatic try per app run, and the notice stays up until it is closed or the update lands.
    val updateChecker = remember { entryPoint.updateChecker() }
    val updateNotice by updateChecker.notice.collectAsStateWithLifecycle(initialValue = null)
    var showUpdateNotes by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(updateChecker) { updateChecker.checkIfDue() }

    // Handle deep-link navigation from widget or notification
    LaunchedEffect(navigationToken, initialNavigateTo, initialWallpaper?.id) {
        val route = when {
            initialWallpaper != null -> Screen.WallpaperDetail.createRoute(initialWallpaper)
            initialNavigateTo == "favorites" -> Screen.Favorites.route
            else -> initialNavigateTo
        }

        if (route != null) {
            // navigate_to arrives via an exported-activity intent extra, so the route can be
            // arbitrary (hostile sender, stale shortcut after a rename). An unknown route must
            // not crash the launcher activity — stay on the start destination instead.
            runCatching {
                navController.navigate(route) {
                    popUpTo(navigationRootRoute) {
                        saveState = route == Screen.Favorites.route
                    }
                    launchSingleTop = true
                    restoreState = route == Screen.Favorites.route
                }
            }
        }
    }

    val showBottomBar = Screen.bottomNavItems.any {
        isBottomNavDestination(screen = it, destination = currentDestination)
    }

    val startRoute = navigationRootRoute

    // Global "Applied — Undo" snackbar host. Any ViewModel that applies a wallpaper posts
    // to ApplyFeedbackBus; we observe it here at the root so the snackbar persists across
    // navigation (e.g. tap Apply on detail, back to list, snackbar still visible).
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        entryPoint.applyFeedbackBus().events.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = if (event.undoTarget != null) resources.getString(R.string.apply_feedback_undo) else null,
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed && event.undoTarget != null) {
                val entry = event.undoTarget
                scope.launch {
                    val target = runCatching {
                        com.freevibe.data.model.WallpaperTarget.valueOf(entry.target)
                    }.getOrDefault(com.freevibe.data.model.WallpaperTarget.BOTH)
                    entryPoint.wallpaperApplier().applyFromUrl(entry.fullUrl, target)
                        .onSuccess {
                            // Re-record the restored wallpaper so that the next apply's
                            // previousSnapshot() correctly reflects what is now on-screen.
                            entryPoint.wallpaperHistoryManager().recordRestore(entry)
                            entryPoint.applyFeedbackBus().post(
                                com.freevibe.service.ApplyFeedbackEvent(
                                    message = resources.getString(R.string.apply_feedback_reverted),
                                    undoTarget = null,
                                )
                            )
                        }
                        .onFailure { e ->
                            entryPoint.applyFeedbackBus().post(
                                com.freevibe.service.ApplyFeedbackEvent(
                                    message = resources.getString(
                                        R.string.apply_feedback_undo_failed,
                                        e.message ?: resources.getString(R.string.apply_feedback_unknown_error),
                                    ),
                                    undoTarget = null,
                                )
                            )
                        }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        val navigationLayout = auraNavigationLayoutForWidth(maxWidth)
        val useNavigationRail = showBottomBar && navigationLayout.isExpanded
        val selectedPrimaryScreen = Screen.bottomNavItems.firstOrNull { screen ->
            isBottomNavDestination(screen = screen, destination = currentDestination)
        }
        val navigateToPrimary: (Screen) -> Unit = { screen ->
            navController.navigate(screen.route) {
                popUpTo(navigationRootRoute) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        CompositionLocalProvider(LocalAuraNavigationLayout provides navigationLayout) {
        downgradeReceipt?.let { receipt ->
            com.freevibe.ui.components.DatabaseDowngradeWarningDialog(
                receipt = receipt,
                onAcknowledge = {
                    downgradeReceiptStore.acknowledge()
                    downgradeReceipt = null
                },
                onOpenBackup = {
                    downgradeReceiptStore.acknowledge()
                    downgradeReceipt = null
                    runCatching {
                        navController.navigate(
                            Screen.Settings.createRoute(Screen.Settings.BACKUP_SECTION),
                        )
                    }
                },
            )
        }
        updateNotice?.takeIf { showUpdateNotes }?.let { release ->
            com.freevibe.ui.components.UpdateNotesDialog(
                release = release,
                onDismiss = { showUpdateNotes = false },
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                // Lift the snackbar above bottom navigation when visible.
                AuraSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (showBottomBar && !useNavigationRail) 72.dp else 0.dp),
                )
            },
            bottomBar = {
                if (showBottomBar && !useNavigationRail) {
                    PrimaryNavigationBar(
                        selected = selectedPrimaryScreen,
                        favoritesCount = favoritesCount,
                        onNavigate = navigateToPrimary,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                if (showBottomBar && onMeteredConnection && !dataWarningDismissed) {
                    com.freevibe.ui.components.MobileDataWarningBanner(
                        onDismiss = { dataWarningDismissed = true },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                val shownUpdateNotice = updateNotice
                if (showBottomBar && shownUpdateNotice != null) {
                    com.freevibe.ui.components.AuraStatusBanner(
                        icon = Icons.Default.NewReleases,
                        title = stringResource(R.string.update_available_title, shownUpdateNotice.versionName),
                        message = stringResource(R.string.update_notice_message),
                        primaryAction = com.freevibe.ui.components.AuraStatusAction(
                            label = stringResource(R.string.update_notice_whats_new),
                            icon = Icons.Default.Description,
                            onClick = { showUpdateNotes = true },
                        ),
                        secondaryAction = com.freevibe.ui.components.AuraStatusAction(
                            label = stringResource(R.string.update_notice_dismiss),
                            icon = Icons.Default.Close,
                            onClick = { scope.launch { updateChecker.dismissNotice(shownUpdateNotice) } },
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (useNavigationRail) {
                    PrimaryNavigationRail(
                        selected = selectedPrimaryScreen,
                        favoritesCount = favoritesCount,
                        onNavigate = navigateToPrimary,
                    )
                }
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = (if (useNavigationRail) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxSize())
                    .padding(
                        bottom = padding.calculateBottomPadding(),
                    ),
                enterTransition = { fadeIn(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + slideInHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 5 } },
                exitTransition = { fadeOut(tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) },
                popEnterTransition = { fadeIn(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + slideInHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 5 } },
                popExitTransition = { fadeOut(tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) + slideOutHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 5 } },
            ) {
            // ── Onboarding ────────────────────────────────────────
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        preferencesManager.setOnboardingComplete()
                        onboardingDone = true
                        navController.navigate(Screen.Wallpapers.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }

            // ── Main tabs ─────────────────────────────────────────
            composable(
                route = Screen.Wallpapers.destinationPattern,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("color") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("similarId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("similarSource") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("similarFullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                WallpapersScreen(
                    initialQuery = backStackEntry.arguments?.getString("query")?.ifBlank { null },
                    initialColor = backStackEntry.arguments?.getString("color")?.ifBlank { null },
                    initialSimilarId = backStackEntry.arguments?.getString("similarId")?.ifBlank { null },
                    initialSimilarSource = backStackEntry.arguments?.getString("similarSource")?.ifBlank { null },
                    initialSimilarFullUrl = backStackEntry.arguments?.getString("similarFullUrl")?.ifBlank { null },
                    isExpandedLayout = navigationLayout.isExpanded,
                    onWallpaperClick = { wallpaper ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(wallpaper)) { launchSingleTop = true }
                    },
                    onGenerateClick = {
                        navController.navigate(Screen.AiWallpaper.route) { launchSingleTop = true }
                    },
                    onCategoriesClick = {
                        navController.navigate(Screen.Categories.route) { launchSingleTop = true }
                    },
                    onCollectionsClick = {
                        navController.navigate(Screen.Collections.route) { launchSingleTop = true }
                    },
                )
            }
            composable(
                route = Screen.VideoWallpapers.destinationPattern,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                VideoWallpapersScreen(
                    initialQuery = backStackEntry.arguments?.getString("query")?.ifBlank { null },
                    onManageHiddenClick = {
                        navController.navigate(Screen.HiddenItems.createRoute(HiddenItemKind.VIDEO)) { launchSingleTop = true }
                    },
                )
            }
            composable(
                route = Screen.Sounds.destinationPattern,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                SoundsScreen(
                    onSoundClick = { sound ->
                        navController.navigate(Screen.SoundDetail.createRoute(sound)) { launchSingleTop = true }
                    },
                    onCreateRingtone = { uri ->
                        navController.navigate(Screen.SoundEditor.createLocalRoute(uri)) { launchSingleTop = true }
                    },
                    onOpenDeviceSounds = {
                        navController.navigate(Screen.DeviceSounds.route) { launchSingleTop = true }
                    },
                    initialQuery = backStackEntry.arguments?.getString("query")?.ifBlank { null },
                    isExpandedLayout = navigationLayout.isExpanded,
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onSearchClick = { navController.navigate(Screen.UniversalSearch.route) { launchSingleTop = true } },
                    onFavoritesClick = { navController.navigate(Screen.Favorites.route) { launchSingleTop = true } },
                    onDownloadsClick = { navController.navigate(Screen.Downloads.route) { launchSingleTop = true } },
                    onCollectionsClick = { navController.navigate(Screen.Collections.route) { launchSingleTop = true } },
                    onLocalImportsClick = { navController.navigate(Screen.Collections.route) { launchSingleTop = true } },
                    onRecentActivityClick = { navController.navigate(Screen.WallpaperHistory.route) { launchSingleTop = true } },
                    onHiddenItemsClick = { navController.navigate(Screen.HiddenItems.createRoute()) { launchSingleTop = true } },
                    onBackupRestoreClick = {
                        navController.navigate(Screen.Settings.createRoute(Screen.Settings.BACKUP_SECTION)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Screen.UniversalSearch.destinationPattern,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                UniversalSearchScreen(
                    initialQuery = backStackEntry.arguments?.getString("query")?.ifBlank { null },
                    onBack = { navController.navigateUp() },
                    onWallpaperClick = { favorite ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(favorite.toWallpaper())) { launchSingleTop = true }
                    },
                    onSoundClick = { favorite ->
                        navController.navigate(Screen.SoundDetail.createRoute(favorite.toSound())) { launchSingleTop = true }
                    },
                    onDownloadClick = { download ->
                        navController.navigate(Screen.Downloads.createRoute(download.id)) { launchSingleTop = true }
                    },
                    onCollectionsClick = { navController.navigate(Screen.Collections.route) { launchSingleTop = true } },
                    onFavoritesClick = { navController.navigate(Screen.Favorites.route) { launchSingleTop = true } },
                    onSearchWallpapers = { query ->
                        navController.navigate(Screen.Wallpapers.createRoute(query = query)) {
                            popUpTo(navigationRootRoute) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onSearchVideos = { query ->
                        navController.navigate(Screen.VideoWallpapers.createRoute(query = query)) {
                            popUpTo(navigationRootRoute) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onSearchSounds = { query ->
                        navController.navigate(Screen.Sounds.createRoute(query = query)) {
                            popUpTo(navigationRootRoute) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onWallpaperClick = { fav ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(fav.toWallpaper())) { launchSingleTop = true }
                    },
                    onSoundClick = { fav ->
                        navController.navigate(Screen.SoundDetail.createRoute(fav.toSound())) { launchSingleTop = true }
                    },
                )
            }
            composable(
                route = Screen.Settings.destinationPattern,
                arguments = listOf(
                    navArgument("section") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                SettingsScreen(
                    initialSection = backStackEntry.arguments?.getString("section")?.ifBlank { null },
                    navigation = SettingsNavigation(
                        onDownloadsClick = { navController.navigate(Screen.Downloads.route) { launchSingleTop = true } },
                        onLicensesClick = { navController.navigate(Screen.Licenses.route) { launchSingleTop = true } },
                        onCategoriesClick = { navController.navigate(Screen.Categories.route) { launchSingleTop = true } },
                        onHistoryClick = { navController.navigate(Screen.WallpaperHistory.route) { launchSingleTop = true } },
                        onCollectionsClick = { navController.navigate(Screen.Collections.route) { launchSingleTop = true } },
                        onCreatorProfileClick = { navController.navigate(Screen.CreatorProfile.route) { launchSingleTop = true } },
                        onCommunityReportsClick = { navController.navigate(Screen.CommunityReports.route) { launchSingleTop = true } },
                        onGeneratedWallpapersClick = { navController.navigate(Screen.AiWallpaper.route) { launchSingleTop = true } },
                        onRotationHealthClick = { navController.navigate(Screen.RotationHealth.route) { launchSingleTop = true } },
                    ),
                )
            }

            generatedWallpaperRoute(navController)

            // ── Detail screens ────────────────────────────────────
            composable(
                Screen.WallpaperDetail.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val fallbackWallpaper = fullUrl.takeIf { it.isNotBlank() }?.let {
                    Wallpaper(
                        id = wallpaperId,
                        source = backStackEntry.arguments?.getString("source")
                            ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                            ?: ContentSource.WALLHAVEN,
                        thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                        fullUrl = fullUrl,
                        width = backStackEntry.arguments?.getInt("width") ?: 0,
                        height = backStackEntry.arguments?.getInt("height") ?: 0,
                    )
                }
                WallpaperDetailScreen(
                    wallpaperId = wallpaperId,
                    fallbackWallpaper = fallbackWallpaper,
                    onBack = { navController.popBackStack() },
                    onEdit = { wallpaper -> navController.navigate(Screen.WallpaperEditor.createRoute(wallpaper)) { launchSingleTop = true } },
                    onCrop = { wallpaper -> navController.navigate(Screen.WallpaperCrop.createRoute(wallpaper)) { launchSingleTop = true } },
                    onPreview = { wallpaper -> navController.navigate(Screen.WallpaperPreview.createRoute(wallpaper)) { launchSingleTop = true } },
                    onSearchTag = { tag ->
                        navController.navigate(Screen.Wallpapers.createRoute(query = tag)) {
                            popUpTo(navigationRootRoute) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onSearchColor = { colorHex ->
                        navController.navigate(Screen.Wallpapers.createRoute(color = colorHex)) {
                            popUpTo(navigationRootRoute) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onFindSimilar = { wallpaper ->
                        navController.navigate(Screen.Wallpapers.createSimilarRoute(wallpaper)) {
                            popUpTo(navigationRootRoute) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }
            composable(
                Screen.SoundDetail.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("previewUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("downloadUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                )
            ) { backStackEntry ->
                val soundId = backStackEntry.arguments?.getString("id").orEmpty()
                val fallbackSound = backStackEntry.arguments?.getString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { name ->
                        Sound(
                            id = soundId,
                            source = backStackEntry.arguments?.getString("source")
                                ?.let { sourceName ->
                                    runCatching { ContentSource.valueOf(sourceName) }
                                        .getOrDefault(ContentSource.LOCAL)
                                }
                                ?: ContentSource.LOCAL,
                            name = name,
                            previewUrl = backStackEntry.arguments?.getString("previewUrl").orEmpty(),
                            downloadUrl = backStackEntry.arguments?.getString("downloadUrl").orEmpty(),
                        )
                    }
                SoundDetailScreen(
                    soundId = soundId,
                    fallbackSound = fallbackSound,
                    onBack = { navController.popBackStack() },
                    onEdit = { sound -> navController.navigate(Screen.SoundEditor.createRoute(sound, editConfirmed = true)) { launchSingleTop = true } },
                    onContactPicker = { sound ->
                        navController.navigate(Screen.ContactPicker.createRoute(sound)) { launchSingleTop = true }
                    },
                    onOpenSound = { sound ->
                        navController.navigate(Screen.SoundDetail.createRoute(sound)) { launchSingleTop = true }
                    },
                    onSearchTag = { tag ->
                        navController.navigate(Screen.Sounds.createRoute(query = tag)) {
                            popUpTo(navigationRootRoute) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }

            // ── Editors ───────────────────────────────────────────
            composable(
                Screen.WallpaperEditor.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val fallbackWallpaper = fullUrl.takeIf { it.isNotBlank() }?.let {
                    Wallpaper(
                        id = wallpaperId,
                        source = backStackEntry.arguments?.getString("source")
                            ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                            ?: ContentSource.WALLHAVEN,
                        thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                        fullUrl = fullUrl,
                        width = backStackEntry.arguments?.getInt("width") ?: 0,
                        height = backStackEntry.arguments?.getInt("height") ?: 0,
                    )
                }
                WallpaperEditorScreen(
                    wallpaperId = wallpaperId,
                    fallbackWallpaper = fallbackWallpaper,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.WallpaperPreview.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val wallpaper = Wallpaper(
                    id = wallpaperId,
                    source = backStackEntry.arguments?.getString("source")
                        ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                        ?: ContentSource.WALLHAVEN,
                    thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                    fullUrl = fullUrl,
                    width = backStackEntry.arguments?.getInt("width") ?: 0,
                    height = backStackEntry.arguments?.getInt("height") ?: 0,
                )
                // previewVm is used only for color extraction — the actual apply runs in
                // the root `scope` so it cannot be cancelled by popBackStack().
                val previewVm: com.freevibe.ui.screens.wallpapers.WallpapersViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                com.freevibe.ui.screens.wallpapers.WallpaperPreviewScreen(
                    wallpaper = wallpaper,
                    onBack = { navController.popBackStack() },
                    onApply = previewApply@{ target, fitCanvasStyle ->
                        if (!canApplyFromWallpaperPreview(wallpaper)) {
                            entryPoint.applyFeedbackBus().post(
                                com.freevibe.service.ApplyFeedbackEvent(
                                    message = wallpaper.wallpaperActionMessage(WallpaperAction.APPLY),
                                    undoTarget = null,
                                ),
                            )
                            return@previewApply
                        }
                        // Kick off the apply in the root composition scope (survives pop)
                        // so the bitmap download + WallpaperManager call complete even if
                        // the preview destination is removed from the back stack first.
                        scope.launch {
                            entryPoint.wallpaperApplier().applyFromUrl(
                                url = wallpaper.fullUrl,
                                target = target,
                                fitCanvasStyle = fitCanvasStyle,
                            )
                                .onSuccess {
                                    entryPoint.wallpaperHistoryManager().record(wallpaper, target)
                                    val undoTarget = entryPoint.wallpaperHistoryManager().previousSnapshot()
                                    val label = when (target) {
                                        com.freevibe.data.model.WallpaperTarget.HOME -> resources.getString(R.string.apply_target_home)
                                        com.freevibe.data.model.WallpaperTarget.LOCK -> resources.getString(R.string.apply_target_lock)
                                        com.freevibe.data.model.WallpaperTarget.BOTH -> resources.getString(R.string.apply_target_both)
                                    }
                                    entryPoint.applyFeedbackBus().post(
                                        com.freevibe.service.ApplyFeedbackEvent(
                                            message = resources.getString(R.string.apply_feedback_applied_to, label),
                                            undoTarget = undoTarget,
                                        )
                                    )
                                }
                                .onFailure { e ->
                                    // The preview screen pops immediately, so this snackbar is the
                                    // only signal the apply didn't happen.
                                    entryPoint.applyFeedbackBus().post(
                                        com.freevibe.service.ApplyFeedbackEvent(
                                            message = resources.getString(
                                                R.string.apply_feedback_apply_failed,
                                                e.message ?: resources.getString(R.string.apply_feedback_unknown_error),
                                            ),
                                            undoTarget = null,
                                        )
                                    )
                                }
                        }
                        navController.popBackStack()
                    },
                    viewModel = previewVm,
                )
            }
            composable(
                Screen.WallpaperCrop.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val fallbackWallpaper = fullUrl.takeIf { it.isNotBlank() }?.let {
                    Wallpaper(
                        id = wallpaperId,
                        source = backStackEntry.arguments?.getString("source")
                            ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                            ?: ContentSource.WALLHAVEN,
                        thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                        fullUrl = fullUrl,
                        width = backStackEntry.arguments?.getInt("width") ?: 0,
                        height = backStackEntry.arguments?.getInt("height") ?: 0,
                    )
                }
                WallpaperCropScreen(
                    wallpaperId = wallpaperId,
                    fallbackWallpaper = fallbackWallpaper,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.SoundEditor.destinationPattern,
                arguments = listOf(
                    navArgument("soundId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("previewUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("downloadUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("localUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("editConfirmed") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val soundId = backStackEntry.arguments?.getString("soundId").orEmpty().ifBlank { null }
                val localUri = backStackEntry.arguments?.getString("localUri").orEmpty().ifBlank { null }
                val editConfirmed = backStackEntry.arguments?.getBoolean("editConfirmed") ?: false
                val fallbackSound = soundId?.let { id ->
                    backStackEntry.arguments?.getString("name")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { name ->
                            Sound(
                                id = id,
                                source = backStackEntry.arguments?.getString("source")
                                    ?.let { sourceName ->
                                        runCatching { ContentSource.valueOf(sourceName) }
                                            .getOrDefault(ContentSource.LOCAL)
                                    }
                                    ?: ContentSource.LOCAL,
                                name = name,
                                previewUrl = backStackEntry.arguments?.getString("previewUrl").orEmpty(),
                                downloadUrl = backStackEntry.arguments?.getString("downloadUrl").orEmpty(),
                            )
                        }
                }
                SoundEditorScreen(
                    soundId = soundId,
                    fallbackSound = fallbackSound,
                    initialLocalUri = localUri?.let(Uri::parse),
                    editConfirmed = editConfirmed,
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Contact Picker ────────────────────────────────────
            composable(
                Screen.ContactPicker.destinationPattern,
                arguments = listOf(
                    navArgument("soundId") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("previewUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("downloadUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                )
            ) { backStackEntry ->
                val soundId = backStackEntry.arguments?.getString("soundId").orEmpty()
                val fallbackSound = backStackEntry.arguments?.getString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { name ->
                        com.freevibe.data.model.Sound(
                            id = soundId,
                            source = backStackEntry.arguments?.getString("source")
                                ?.let { sourceName ->
                                    runCatching { com.freevibe.data.model.ContentSource.valueOf(sourceName) }
                                        .getOrDefault(com.freevibe.data.model.ContentSource.LOCAL)
                                }
                                ?: com.freevibe.data.model.ContentSource.LOCAL,
                            name = name,
                            previewUrl = backStackEntry.arguments?.getString("previewUrl").orEmpty(),
                            downloadUrl = backStackEntry.arguments?.getString("downloadUrl").orEmpty(),
                        )
                    }
                ContactPickerScreen(
                    soundId = soundId,
                    fallbackSound = fallbackSound,
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Downloads ─────────────────────────────────────────
            composable(
                route = Screen.Downloads.destinationPattern,
                arguments = listOf(
                    navArgument("focus") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    focusDownloadId = backStackEntry.arguments?.getString("focus")?.ifBlank { null },
                )
            }

            // ── Categories ────────────────────────────────────────
            composable(Screen.Categories.route) {
                CategoriesScreen(
                    onBack = { navController.popBackStack() },
                    onCategoryClick = { query ->
                        navController.navigate(Screen.Wallpapers.createRoute(query = query)) {
                            popUpTo(navigationRootRoute) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }

            // ── Licenses ──────────────────────────────────────────
            composable(Screen.Licenses.route) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }

            // ── Collections ────────────────────────────────────
            composable(
                route = Screen.Collections.destinationPattern,
                arguments = listOf(
                    navArgument("importToken") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("importUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                CollectionsScreen(
                    onBack = { navController.popBackStack() },
                    onWallpaperClick = { wallpaper ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(wallpaper)) { launchSingleTop = true }
                    },
                    initialImportToken = backStackEntry.arguments?.getString("importToken")?.ifBlank { null },
                    initialImportUri = backStackEntry.arguments?.getString("importUri")?.ifBlank { null },
                )
            }

            // ── Creator Profile ─────────────────────────────────
            composable(Screen.CreatorProfile.route) {
                CreatorProfileScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Community Reports ───────────────────────────────
            composable(Screen.CommunityReports.route) {
                CommunityReportsScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Sounds already on the phone ──────────────────────
            composable(Screen.DeviceSounds.route) {
                DeviceSoundsScreen(onBack = { navController.popBackStack() })
            }

            // ── Rotation Health ──────────────────────────────────
            composable(Screen.RotationHealth.route) {
                RotationHealthScreen(onBack = { navController.popBackStack() })
            }

            // ── Hidden items ─────────────────────────────────────
            composable(
                route = Screen.HiddenItems.destinationPattern,
                arguments = listOf(
                    navArgument("kind") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                HiddenItemsScreen(
                    initialKind = backStackEntry.arguments?.getString("kind")
                        ?.let { name -> HiddenItemKind.entries.firstOrNull { it.name == name } },
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Wallpaper History ────────────────────────────────
            composable(Screen.WallpaperHistory.route) {
                WallpaperHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onWallpaperClick = { entry ->
                        val wallpaper = com.freevibe.data.model.Wallpaper(
                            id = entry.wallpaperId,
                            source = try { com.freevibe.data.model.ContentSource.valueOf(entry.source) }
                                catch (_: Exception) { com.freevibe.data.model.ContentSource.WALLHAVEN },
                            thumbnailUrl = entry.thumbnailUrl,
                            fullUrl = entry.fullUrl,
                            width = entry.width,
                            height = entry.height,
                        )
                        navController.navigate(Screen.WallpaperDetail.createRoute(wallpaper)) { launchSingleTop = true }
                    },
                )
            }
            }
            }
            }
        }
        }
    }
}

private fun isBottomNavDestination(
    screen: Screen,
    destination: androidx.navigation.NavDestination?,
): Boolean {
    if (destination == null) return false
    if (screen == Screen.Library) {
        val libraryRoutes = setOf(
            Screen.Library.route,
            Screen.Favorites.route,
            Screen.Downloads.route,
            Screen.Downloads.destinationPattern,
            Screen.Collections.route,
            Screen.Collections.destinationPattern,
            Screen.WallpaperHistory.route,
            Screen.HiddenItems.route,
            Screen.HiddenItems.destinationPattern,
        )
        return destination.hierarchy.any { navDestination ->
            navDestination.route in libraryRoutes
        }
    }
    return destination.hierarchy.any { navDestination ->
        screen.matchesDestination(navDestination.route)
    }
}

/**
 * The bottom bar for compact windows. Labels show while every one of them fits whole. At large
 * text they don't, so the bar keeps all five destinations as icons and each icon carries the
 * destination's full name for screen readers.
 */
@Composable
internal fun PrimaryNavigationBar(
    selected: Screen?,
    favoritesCount: Int,
    onNavigate: (Screen) -> Unit,
) {
    val titles = Screen.bottomNavItems.map { stringResource(it.titleRes) }
    val labelStyle = MaterialTheme.typography.labelSmall
    val widestLabel = rememberWidestNavigationLabel(titles, labelStyle)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        val showLabels = bottomBarLabelsFit(
            barWidth = maxWidth - PrimaryNavigationBarSidePadding * 2,
            barHeight = PrimaryNavigationBarHeight,
            itemCount = titles.size,
            label = widestLabel,
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .height(PrimaryNavigationBarHeight)
                .padding(horizontal = PrimaryNavigationBarSidePadding),
        ) {
            Screen.bottomNavItems.forEachIndexed { index, screen ->
                val isSelected = screen == selected
                val screenTitle = titles[index]

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(screen) },
                    icon = {
                        BottomNavIcon(
                            screen = screen,
                            selected = isSelected,
                            screenTitle = screenTitle,
                            favoritesCount = favoritesCount,
                        )
                    },
                    label = if (showLabels) {
                        { Text(screenTitle, style = labelStyle, maxLines = 1) }
                    } else {
                        null
                    },
                    alwaysShowLabel = showLabels,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

/**
 * The side rail for expanded windows. It drops its labels on the same rule as the bar, and it
 * scrolls, so a short landscape window can never leave a destination out of reach.
 */
@Composable
internal fun PrimaryNavigationRail(
    selected: Screen?,
    favoritesCount: Int,
    onNavigate: (Screen) -> Unit,
) {
    val titles = Screen.bottomNavItems.map { stringResource(it.titleRes) }
    val labelStyle = MaterialTheme.typography.labelSmall
    val widestLabel = rememberWidestNavigationLabel(titles, labelStyle)
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Start + WindowInsetsSides.Vertical,
                ),
            )
            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
        shape = AuraShapes.Medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
            val showLabels = railLabelsFit(
                railWidth = PrimaryNavigationRailWidth,
                railHeight = maxHeight,
                itemCount = titles.size,
                label = widestLabel,
            )
            NavigationRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .width(PrimaryNavigationRailWidth),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Spacer(Modifier.height(PrimaryNavigationRailTopSpace))
                Screen.bottomNavItems.forEachIndexed { index, screen ->
                    val isSelected = screen == selected
                    val screenTitle = titles[index]

                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onNavigate(screen) },
                        icon = {
                            BottomNavIcon(
                                screen = screen,
                                selected = isSelected,
                                screenTitle = screenTitle,
                                favoritesCount = favoritesCount,
                            )
                        },
                        label = if (showLabels) {
                            { Text(screenTitle, style = labelStyle, maxLines = 1) }
                        } else {
                            null
                        },
                        alwaysShowLabel = showLabels,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavIcon(
    screen: Screen,
    selected: Boolean,
    screenTitle: String,
    favoritesCount: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box {
            Icon(
                imageVector = if (selected) screen.selectedIcon else screen.icon,
                contentDescription = screenTitle,
                modifier = Modifier.size(22.dp),
            )
            if (screen == Screen.Library && favoritesCount > 0) {
                CountBadge(
                    count = favoritesCount,
                    // Pinned by its top-start corner and left out of the icon's size, so a badge
                    // made bigger by large text grows away from the icon instead of covering it.
                    modifier = Modifier.layout { measurable, constraints ->
                        val badge = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(0, 0) {
                            badge.placeRelative(x = 16.dp.roundToPx(), y = (-7).dp.roundToPx())
                        }
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .width(if (selected) 18.dp else 10.dp)
                .height(2.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = AuraShapes.Hairline,
                ),
        )
    }
}
