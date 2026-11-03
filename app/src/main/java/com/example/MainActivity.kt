package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppVersionConfig
import com.example.data.model.BottomNavTab
import com.example.ui.components.NativeAdTransitionScreen
import com.example.ui.components.OrderedColorfulDivider
import com.example.ui.components.tvFocusableClickable
import com.example.ui.screens.ContactUsScreen
import com.example.ui.screens.DeveloperControlPanelScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LibraryVideoPlayerScreen
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FoulBlackBg
import com.example.ui.theme.FoulBottomBarBlack
import com.example.ui.theme.FoulCardDark
import com.example.ui.theme.FoulDivider
import com.example.ui.theme.FoulGoldStar
import com.example.ui.theme.FoulPurpleBright
import com.example.ui.theme.FoulPurpleDeep
import com.example.ui.theme.FoulPurplePrimary
import com.example.ui.theme.FoulSurfaceDark
import com.example.ui.theme.FoulTextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FoulTvViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    FoulTvApp()
                }
            }
        }
    }
}

@Composable
fun FoulTvApp(
    viewModel: FoulTvViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nativeTransitionAd by viewModel.nativeAdManager.activeTransitionAd.collectAsStateWithLifecycle()
    val nativeAdDiagnosticState by viewModel.nativeAdManager.diagnosticState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Keep the screen awake across the entire Foul TV app so it never dims or sleeps on Phones, Tablets, or Android TV
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 1. Mandatory App Update Lock Check (Highest priority)
    if (uiState.versionConfig.isForcedUpdateLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FoulBlackBg)
        ) {
            ForcedUpdateLockScreen(
                versionConfig = uiState.versionConfig,
                onUpdateClick = { url ->
                    val target = url.ifBlank { uiState.telegramUrl }
                    if (target.isNotBlank()) {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                }
            )
        }
        return
    }

    // 2. Native Ad Transition Screen before opening any Live Channel, Movie, or Series Episode
    // (Only shown if ads are enabled in the Developer Control Panel)
    val pendingPlayback = uiState.pendingPlaybackAfterAd
    if (uiState.showAdsInApp && uiState.isAdTransitionScreenVisible && pendingPlayback != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FoulBlackBg)
        ) {
            NativeAdTransitionScreen(
                nativeAd = nativeTransitionAd,
                diagnosticState = nativeAdDiagnosticState,
                pendingContentTitle = pendingPlayback.title,
                pendingContentSubtitle = pendingPlayback.subtitle,
                isLiveChannel = pendingPlayback.isLiveChannel,
                onRetryLoadAd = { viewModel.nativeAdManager.retryLoadAdNow() },
                onCloseAdAndPlayContent = viewModel::dismissNativeAdAndStartPlayback
            )
        }
        return
    }

    // 3. Fullscreen Integrated Video Player Overlay (for Live TV Channels, Library Movies & Series Episodes)
    val activePlayback = uiState.activePlayback
    if (activePlayback != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FoulBlackBg)
        ) {
            val activeChannelId = activePlayback.channelId
            val activeChannelStats = if (activeChannelId.isNotBlank()) {
                uiState.channelStatsMap[activeChannelId]
            } else {
                null
            }
            LibraryVideoPlayerScreen(
                playbackRequest = activePlayback,
                showChannelViewsAndLikes = uiState.showChannelViewsAndLikes,
                channelStats = activeChannelStats,
                onToggleChannelLike = {
                    if (activeChannelId.isNotBlank()) {
                        viewModel.toggleChannelLike(activeChannelId)
                    }
                },
                onBack = viewModel::closePlayer
            )
        }
        return
    }

    // 4. Developer Control Panel Fullscreen Overlay (when authenticated, opened & entry enabled)
    if (com.example.ui.screens.SHOW_DEVELOPER_PANEL_ENTRY && uiState.isDeveloperLoggedIn && uiState.isDeveloperPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FoulBlackBg)
        ) {
            DeveloperControlPanelScreen(
                developerEmail = uiState.developerEmail,
                isActionLoading = uiState.isDevActionLoading,
                statusMessage = uiState.devStatusMessage,
                branches = uiState.categories,
                channels = uiState.channels,
                xtreamAccounts = uiState.xtreamAccounts,
                versionConfig = uiState.versionConfig,
                userMessages = uiState.developerContactMessages,
                leagueGroups = uiState.leagueGroups,
                matchPublishedChannels = uiState.matchPublishedChannels,
                publishedEvents = uiState.publishedEvents,
                showChannelViewsAndLikes = uiState.showChannelViewsAndLikes,
                showAdsInApp = uiState.showAdsInApp,
                onlineUsersCount = uiState.onlineUsersCount,
                isFetchingAccountBranchCatalog = uiState.isFetchingAccountBranchCatalog,
                isFetchingAccountCategoryStreams = uiState.isFetchingAccountCategoryStreams,
                accountBranchFetchError = uiState.accountBranchFetchError,
                fetchedAccountLiveCatalog = uiState.fetchedAccountLiveCatalog,
                onDismissStatusMessage = viewModel::clearDevStatusMessage,
                onSaveBranch = viewModel::saveDeveloperBranch,
                onMoveBranchOrder = viewModel::moveBranchInOrder,
                onSaveBranchesOrder = viewModel::saveCurrentBranchesOrder,
                onDeleteBranch = viewModel::deleteDeveloperBranch,
                onFetchAccountLiveCatalog = viewModel::fetchDeveloperAccountLiveCatalog,
                onFetchAccountCategoryStreams = viewModel::fetchDeveloperAccountCategoryStreams,
                onClearAccountLiveCatalog = viewModel::clearDeveloperAccountLiveCatalog,
                onPublishAccountBranch = viewModel::publishDeveloperAccountBranch,
                onSaveChannel = viewModel::saveDeveloperChannel,
                onDeleteChannel = viewModel::deleteDeveloperChannel,
                onSaveXtreamAccount = viewModel::saveDeveloperXtreamAccount,
                onDeleteXtreamAccount = viewModel::deleteDeveloperXtreamAccount,
                onSaveVersionConfig = viewModel::saveDeveloperVersionConfig,
                onSaveMatchPublishedChannel = viewModel::saveDeveloperMatchPublishedChannel,
                onDeleteMatchPublishedChannel = viewModel::deleteDeveloperMatchPublishedChannel,
                onSavePublishedEvent = viewModel::saveDeveloperPublishedEvent,
                onDeletePublishedEvent = viewModel::deleteDeveloperPublishedEvent,
                onSendDeveloperReply = viewModel::sendDeveloperReplyToUser,
                onRefreshUserMessages = viewModel::refreshDeveloperUserMessages,
                onToggleShowChannelViewsAndLikes = viewModel::toggleShowChannelViewsAndLikesOnline,
                onToggleShowAdsInApp = viewModel::toggleShowAdsInAppOnline,
                onRefreshOnlineUsers = viewModel::refreshOnlineUsersCount,
                onRefreshData = { viewModel.loadHomeData(forceRefresh = true) },
                onLogoutDeveloper = viewModel::logoutDeveloper,
                onClosePanel = viewModel::closeDeveloperPanel
            )
        }
        return
    }

    // 5. Independent Fullscreen Contact Us Screen
    if (uiState.isContactUsScreenOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FoulBlackBg)
        ) {
            ContactUsScreen(
                currentUser = uiState.currentSocialUser,
                isAuthLoading = uiState.isSocialAuthLoading,
                authError = uiState.socialAuthError,
                messages = uiState.contactMessages,
                onLogin = viewModel::loginSocialUser,
                onRegister = viewModel::registerSocialUser,
                onLogout = viewModel::logoutSocialUser,
                onSendMessage = viewModel::sendContactUsMessage,
                onRefreshMessages = viewModel::refreshContactUsMessages,
                onClearAuthError = viewModel::clearSocialAuthError,
                onBack = viewModel::closeContactUsScreen
            )
        }
        return
    }

    // Handle Android Back button when not on the Home tab
    BackHandler(enabled = uiState.currentTab != BottomNavTab.HOME) {
        viewModel.selectTab(BottomNavTab.HOME)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(FoulBlackBg),
        containerColor = FoulBlackBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            FoulBottomNavigationBar(
                selectedTab = uiState.currentTab,
                onSelectTab = viewModel::selectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(FoulBlackBg)
        ) {
            when (uiState.currentTab) {
                BottomNavTab.HOME -> {
                    HomeScreen(
                        isLoading = uiState.isLoadingHome,
                        errorMessage = uiState.homeError,
                        currentBranch = uiState.currentHomeBranch,
                        visibleBranches = uiState.visibleHomeBranches,
                        visibleChannels = uiState.visibleBranchChannels,
                        allCategories = uiState.categories,
                        allChannels = uiState.channels,
                        banners = uiState.banners,
                        publishedEvents = uiState.publishedEvents,
                        channelStatsMap = uiState.channelStatsMap,
                        showChannelViewsAndLikes = uiState.showChannelViewsAndLikes,
                        showAdsInApp = uiState.showAdsInApp,
                        telegramUrl = uiState.telegramUrl,
                        onOpenBranch = viewModel::openHomeBranch,
                        onNavigateBackBranch = viewModel::navigateBackHomeBranch,
                        onChannelClick = viewModel::playLiveChannel,
                        onBannerClick = viewModel::onBannerClick,
                        onPlayPublishedEventVideo = viewModel::playPublishedEventVideo,
                        onRefresh = { viewModel.loadHomeData(forceRefresh = true) }
                    )
                }

                BottomNavTab.LIBRARY -> {
                    LibraryScreen(
                        isLoading = uiState.isLoadingLibrary,
                        errorMessage = uiState.libraryError,
                        mainFilter = uiState.libraryMainFilter,
                        displayedItems = uiState.libraryDisplayedItems,
                        hasMore = uiState.libraryHasMore,
                        isSearchOpen = uiState.isLibrarySearchOpen,
                        searchQuery = uiState.searchQuery,
                        searchFilter = uiState.searchFilter,
                        searchResults = uiState.searchResults,
                        isSearching = uiState.isSearching,
                        selectedMediaItem = uiState.selectedMediaItem,
                        isLoadingMediaDetail = uiState.isLoadingMediaDetail,
                        mediaDetailError = uiState.mediaDetailError,
                        mediaDetail = uiState.mediaDetail,
                        selectedSeasonNumber = uiState.selectedSeasonNumber,
                        telegramUrl = uiState.telegramUrl,
                        onSelectMainFilter = viewModel::selectLibraryMainFilter,
                        onLoadMore = viewModel::loadMoreLibraryItems,
                        onOpenSearch = viewModel::openLibrarySearch,
                        onCloseSearch = viewModel::closeLibrarySearch,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchFilterChange = viewModel::updateSearchFilter,
                        onMediaItemClick = viewModel::openMediaDetail,
                        onCloseMediaDetail = viewModel::closeMediaDetail,
                        onSelectSeason = viewModel::selectSeason,
                        onPlayMovie = viewModel::playLibraryMovie,
                        onPlayEpisode = viewModel::playLibraryEpisode,
                        onRefresh = viewModel::loadLibraryInitial
                    )
                }

                BottomNavTab.MATCHES -> {
                    MatchesScreen(
                        dateOptions = uiState.dateOptions,
                        selectedDate = uiState.selectedDateString,
                        isLoading = uiState.isLoadingMatches,
                        error = uiState.matchesError,
                        leagueGroups = uiState.leagueGroups,
                        selectedMatch = uiState.selectedMatch,
                        isLoadingMatchDetail = uiState.isLoadingMatchDetail,
                        matchDetailError = uiState.matchDetailError,
                        matchDetail = uiState.matchDetail,
                        isMatchesSearchOpen = uiState.isMatchesSearchOpen,
                        matchesSearchQuery = uiState.matchesSearchQuery,
                        isSearchingMatches = uiState.isSearchingMatches,
                        matchesSearchResult = uiState.matchesSearchResult,
                        selectedPlayerProfile = uiState.selectedPlayerProfile,
                        isLoadingPlayerProfile = uiState.isLoadingPlayerProfile,
                        playerProfileError = uiState.playerProfileError,
                        selectedTeamProfile = uiState.selectedTeamProfile,
                        isLoadingTeamProfile = uiState.isLoadingTeamProfile,
                        teamProfileError = uiState.teamProfileError,
                        allChannels = uiState.channels,
                        liveEvents = uiState.liveEvents,
                        matchPublishedChannels = uiState.matchPublishedChannels,
                        telegramUrl = uiState.telegramUrl,
                        onSelectDate = viewModel::loadMatchesForDate,
                        onMatchClick = viewModel::openMatchDetail,
                        onWatchChannel = viewModel::playLiveChannel,
                        onCloseMatchDetail = viewModel::closeMatchDetail,
                        onOpenMatchesSearch = viewModel::openMatchesSearch,
                        onCloseMatchesSearch = viewModel::closeMatchesSearch,
                        onMatchesSearchQueryChange = viewModel::updateMatchesSearchQuery,
                        onPlayerClickFromLineup = viewModel::openPlayerProfileFromLineup,
                        onPlayerSelectFromSearch = viewModel::openPlayerProfileFromSearch,
                        onClosePlayerProfile = viewModel::closePlayerProfile,
                        onTeamSelectFromSearch = viewModel::openTeamProfile,
                        onCloseTeamProfile = viewModel::closeTeamProfile,
                        onRefresh = { viewModel.loadMatchesForDate(uiState.selectedDateString) }
                    )
                }

                BottomNavTab.SETTINGS -> {
                    SettingsScreen(
                        telegramUrl = uiState.telegramUrl,
                        currentUser = uiState.currentSocialUser,
                        isDevLoginSheetOpen = uiState.isDevLoginSheetOpen,
                        isLoggingInDeveloper = uiState.isLoggingInDeveloper,
                        devLoginError = uiState.devLoginError,
                        onOpenContactUsScreen = viewModel::openContactUsScreen,
                        onOpenDeveloperSheet = viewModel::openDeveloperLoginSheet,
                        onDismissDeveloperSheet = viewModel::closeDeveloperLoginSheet,
                        onSubmitDeveloperLogin = viewModel::loginDeveloper
                    )
                }
            }
        }
    }
}

private data class NavTabItemSpec(
    val tab: BottomNavTab,
    val label: String,
    val testTag: String
)

@Composable
private fun FoulBottomNavigationBar(
    selectedTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit
) {
    val navItems = listOf(
        NavTabItemSpec(
            tab = BottomNavTab.HOME,
            label = "الرئيسية",
            testTag = "nav_home"
        ),
        NavTabItemSpec(
            tab = BottomNavTab.LIBRARY,
            label = "المكتبة",
            testTag = "nav_library"
        ),
        NavTabItemSpec(
            tab = BottomNavTab.MATCHES,
            label = "جدول المباريات",
            testTag = "nav_matches"
        ),
        NavTabItemSpec(
            tab = BottomNavTab.SETTINGS,
            label = "الإعدادات",
            testTag = "nav_settings"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FoulBottomBarBlack)
            .navigationBarsPadding()
            .testTag("bottom_nav_bar")
    ) {
        OrderedColorfulDivider(thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = selectedTab == item.tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        FoulPurplePrimary,
                                        FoulPurpleDeep
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(FoulCardDark.copy(alpha = 0.45f), FoulBlackBg)
                                )
                            }
                        )
                        .border(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) FoulPurpleBright else FoulDivider,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(14.dp),
                            scaleOnFocus = 1.05f,
                            onClick = { onSelectTab(item.tab) }
                        )
                        .padding(vertical = 6.dp)
                        .testTag(item.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        com.example.ui.components.FoulCustomNavIcon(
                            tab = item.tab,
                            isSelected = isSelected
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isSelected) Color.White else FoulTextSecondary,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ForcedUpdateLockScreen(
    versionConfig: AppVersionConfig,
    onUpdateClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FoulBlackBg)
            .padding(24.dp)
            .testTag("forced_update_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = FoulCardDark),
            border = BorderStroke(1.5.dp, FoulPurpleBright),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(FoulPurpleDeep.copy(alpha = 0.7f), FoulCardDark)
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(FoulPurpleBright)
                        .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "تحديث إجباري مطلوب (${versionConfig.requiredVersion})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = FoulSurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FoulDivider)
                ) {
                    Text(
                        text = "الإصدار الحالي: ${AppVersionConfig.APP_CURRENT_VERSION} • الإصدار المطلوب: ${versionConfig.requiredVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        color = FoulGoldStar,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = versionConfig.updateText.ifBlank {
                        "يرجى تحديث التطبيق إلى الإصدار الجديد للاستمرار في المشاهدة."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoulTextSecondary,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = { onUpdateClick(versionConfig.updateUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = FoulPurpleBright),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("forced_update_action_button")
                ) {
                    Text(
                        text = "تحديث التطبيق الآن",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
