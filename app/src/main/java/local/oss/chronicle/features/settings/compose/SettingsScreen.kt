package local.oss.chronicle.features.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.oss.chronicle.ui.theme.OpusColors
import local.oss.chronicle.ui.theme.OpusTheme

/**
 * UI State for the Settings screen.
 */
data class SettingsUiState(
    val preferences: List<SettingsItem> = emptyList(),
    val showOptionsSheet: Boolean = false,
    val optionsTitle: String = "",
    val options: List<String> = emptyList(),
    val selectedOptionIndex: Int = -1,
)

/**
 * Sealed class representing different types of settings items.
 */
sealed class SettingsItem {
    data class Category(val title: String) : SettingsItem()
    data class Clickable(
        val id: String,
        val title: String,
        val explanation: String? = null,
        val currentValue: String? = null,
        val onClick: () -> Unit = {},
    ) : SettingsItem()
    data class Switch(
        val id: String,
        val title: String,
        val explanation: String? = null,
        val isChecked: Boolean = false,
        val onCheckedChange: (Boolean) -> Unit = {},
    ) : SettingsItem()
}

/**
 * Main Settings screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onDismissOptionsSheet: () -> Unit = {},
    onOptionSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OpusColors.Background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OpusColors.TextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OpusColors.Background,
                ),
            )

            // Settings List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                items(
                    items = state.preferences,
                    key = { item ->
                        when (item) {
                            is SettingsItem.Category -> "category_${item.title}"
                            is SettingsItem.Clickable -> "clickable_${item.id}"
                            is SettingsItem.Switch -> "switch_${item.id}"
                        }
                    }
                ) { item ->
                    when (item) {
                        is SettingsItem.Category -> {
                            SettingsCategory(title = item.title)
                        }
                        is SettingsItem.Clickable -> {
                            ClickablePreference(
                                title = item.title,
                                explanation = item.explanation,
                                currentValue = item.currentValue,
                                onClick = item.onClick,
                            )
                        }
                        is SettingsItem.Switch -> {
                            SwitchPreference(
                                title = item.title,
                                explanation = item.explanation,
                                isChecked = item.isChecked,
                                onCheckedChange = item.onCheckedChange,
                            )
                        }
                    }
                }

                // Bottom spacing for mini player
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // Options Bottom Sheet
        if (state.showOptionsSheet) {
            OptionsBottomSheet(
                title = state.optionsTitle,
                options = state.options,
                selectedIndex = state.selectedOptionIndex,
                onDismiss = onDismissOptionsSheet,
                onOptionSelected = onOptionSelected,
            )
        }
    }
}

/**
 * Category header for grouping settings.
 */
@Composable
fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OpusColors.TextSecondary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 24.dp,
                bottom = 8.dp,
            )
    )
}

/**
 * Clickable preference item for navigation/selection.
 */
@Composable
fun ClickablePreference(
    title: String,
    explanation: String? = null,
    currentValue: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = OpusColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (explanation != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpusColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (currentValue != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpusColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = OpusColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Switch preference item for toggle settings.
 */
@Composable
fun SwitchPreference(
    title: String,
    explanation: String? = null,
    isChecked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = OpusColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (explanation != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpusColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OpusColors.Primary,
                checkedTrackColor = OpusColors.Primary.copy(alpha = 0.5f),
                uncheckedThumbColor = OpusColors.TextSecondary,
                uncheckedTrackColor = OpusColors.SurfaceVariant,
            )
        )
    }
}

/**
 * Modal bottom sheet for selecting options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OpusColors.Surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Options list
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOptionSelected(index) }
                        .background(
                            if (isSelected) OpusColors.Primary.copy(alpha = 0.1f)
                            else OpusColors.Surface
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onOptionSelected(index) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = OpusColors.Primary,
                            unselectedColor = OpusColors.TextSecondary,
                        )
                    )
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) OpusColors.Primary else OpusColors.TextPrimary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SettingsScreenPreview() {
    OpusTheme(darkTheme = true) {
        SettingsScreen(
            state = SettingsUiState(
                preferences = listOf(
                    SettingsItem.Category(title = "Appearance"),
                    SettingsItem.Clickable(
                        id = "cover_style",
                        title = "Book Cover Style",
                        explanation = "Choose how book covers are displayed",
                        currentValue = "Square",
                    ),
                    SettingsItem.Category(title = "Playback"),
                    SettingsItem.Switch(
                        id = "skip_silence",
                        title = "Skip Silence",
                        explanation = "Skip silent parts of audiobooks",
                        isChecked = true,
                    ),
                    SettingsItem.Switch(
                        id = "auto_rewind",
                        title = "Smart Rewind",
                        explanation = "Rewind a bit when resuming after a break",
                        isChecked = true,
                    ),
                    SettingsItem.Clickable(
                        id = "jump_forward",
                        title = "Jump Forward Duration",
                        explanation = "Time to skip when tapping forward",
                        currentValue = "30 seconds",
                    ),
                    SettingsItem.Category(title = "Account"),
                    SettingsItem.Clickable(
                        id = "server",
                        title = "Change Server",
                        explanation = "Currently connected to: My Plex Server",
                    ),
                    SettingsItem.Clickable(
                        id = "logout",
                        title = "Log Out",
                    ),
                )
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun OptionsBottomSheetPreview() {
    OpusTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OpusColors.Surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Jump Forward Duration",
                    style = MaterialTheme.typography.titleMedium,
                    color = OpusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                listOf("10 seconds", "15 seconds", "20 seconds", "30 seconds", "60 seconds").forEachIndexed { index, option ->
                    val isSelected = index == 3
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) OpusColors.Primary.copy(alpha = 0.1f)
                                else OpusColors.Surface
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = OpusColors.Primary,
                                unselectedColor = OpusColors.TextSecondary,
                            )
                        )
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) OpusColors.Primary else OpusColors.TextPrimary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
