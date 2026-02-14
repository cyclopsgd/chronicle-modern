package local.oss.chronicle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import local.oss.chronicle.ui.theme.OpusColors
import local.oss.chronicle.ui.theme.OpusTheme

/**
 * Navigation tabs for the main bottom bar.
 */
enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    LIBRARY("Library", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    COLLECTIONS("Collections", Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

/**
 * Combined bottom bar with mini player and navigation.
 *
 * Layout:
 * - MiniPlayer (72dp) - shown when audio is playing
 * - NavigationBar (56dp) - always shown
 */
@Composable
fun MainBottomBar(
    miniPlayerState: MiniPlayerState,
    currentTab: MainTab,
    showCollections: Boolean = true,
    onTabSelect: (MainTab) -> Unit = {},
    onMiniPlayerClick: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Mini Player (above nav bar)
        MiniPlayer(
            state = miniPlayerState,
            onPlayPause = onPlayPause,
            onClick = onMiniPlayerClick,
        )

        // Subtle divider between mini player and nav bar
        if (miniPlayerState.isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OpusColors.TextSecondary.copy(alpha = 0.2f))
            )
        }

        // Navigation Bar (56dp height) with subtle top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OpusColors.SurfaceVariant)
        )
        NavigationBar(
            containerColor = OpusColors.Surface,
            contentColor = OpusColors.TextPrimary,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            MainTab.entries.forEach { tab ->
                // Skip Collections if not available
                if (tab == MainTab.COLLECTIONS && !showCollections) return@forEach

                val isSelected = currentTab == tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelect(tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(text = tab.label)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OpusColors.Primary,
                        selectedTextColor = OpusColors.Primary,
                        unselectedIconColor = OpusColors.TextSecondary,
                        unselectedTextColor = OpusColors.TextSecondary,
                        indicatorColor = Color.Transparent,
                    )
                )
            }
        }
    }
}

/**
 * Standalone navigation bar without mini player.
 */
@Composable
fun MainNavigationBar(
    currentTab: MainTab,
    showCollections: Boolean = true,
    onTabSelect: (MainTab) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Subtle top border for visual separation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OpusColors.SurfaceVariant)
        )
        NavigationBar(
            containerColor = OpusColors.Surface,
            contentColor = OpusColors.TextPrimary,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
        MainTab.entries.forEach { tab ->
            // Skip Collections if not available
            if (tab == MainTab.COLLECTIONS && !showCollections) return@forEach

            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(text = tab.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = OpusColors.Primary,
                    selectedTextColor = OpusColors.Primary,
                    unselectedIconColor = OpusColors.TextSecondary,
                    unselectedTextColor = OpusColors.TextSecondary,
                    indicatorColor = Color.Transparent,
                )
            )
        }
    }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MainBottomBarWithPlayerPreview() {
    OpusTheme(darkTheme = true) {
        MainBottomBar(
            miniPlayerState = MiniPlayerState(
                isVisible = true,
                bookTitle = "Project Hail Mary",
                chapterTitle = "Chapter 12",
                progress = 0.45f,
                isPlaying = true,
            ),
            currentTab = MainTab.HOME,
            showCollections = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MainBottomBarNoPlayerPreview() {
    OpusTheme(darkTheme = true) {
        MainBottomBar(
            miniPlayerState = MiniPlayerState(isVisible = false),
            currentTab = MainTab.LIBRARY,
            showCollections = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MainNavigationBarPreview() {
    OpusTheme(darkTheme = true) {
        MainNavigationBar(
            currentTab = MainTab.SETTINGS,
            showCollections = false,
        )
    }
}
