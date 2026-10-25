package io.github.dantemorrigan.cotes.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import io.github.dantemorrigan.cotes.Accent
import io.github.dantemorrigan.cotes.AppLanguage
import io.github.dantemorrigan.cotes.AppSettings
import io.github.dantemorrigan.cotes.R
import io.github.dantemorrigan.cotes.Sound
import io.github.dantemorrigan.cotes.Sounds
import io.github.dantemorrigan.cotes.TextSize
import io.github.dantemorrigan.cotes.ThemeMode
import io.github.dantemorrigan.cotes.sync.SyncState

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onExportMarkdown: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    syncState: SyncState,
    signIn: GoogleSignIn,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val res = LocalResources.current
    val version = remember(res) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            res.getString(R.string.version, info.versionName, PackageInfoCompat.getLongVersionCode(info).toInt())
        }.getOrDefault("")
    }
    // Язык меняется через AppCompat; Activity пересоздаётся, и экран читает новое значение.
    val language = remember { AppLanguage.current() }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var importHelpOpen by rememberSaveable { mutableStateOf(false) }

    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("settings"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    AccountSection(syncState, signIn, onSyncNow, onSignOut)

                    Section(stringResource(R.string.section_theme))
                    Card {
                        Segmented(
                            options = ThemeMode.entries,
                            selected = settings.theme,
                            label = { stringResource(it.title) },
                            onSelect = { mode -> onChange { it.copy(theme = mode) } },
                            modifier = Modifier.padding(12.dp),
                        )
                        Divider()
                        SwitchRow(
                            title = stringResource(R.string.pure_black),
                            subtitle = stringResource(R.string.pure_black_sub),
                            checked = settings.pureBlack,
                            onChecked = { v -> onChange { it.copy(pureBlack = v) } },
                        )
                    }

                    Section(stringResource(R.string.section_color))
                    Card {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Accent.entries.forEach { accent ->
                                AccentSwatch(
                                    accent = accent,
                                    selected = settings.accent == accent && !(settings.dynamicColor && dynamicColorAvailable),
                                    dark = colors.background.lum() < 0.5f,
                                    onClick = { onChange { it.copy(accent = accent, dynamicColor = false) } },
                                )
                            }
                        }
                        if (dynamicColorAvailable) {
                            Divider()
                            SwitchRow(
                                title = stringResource(R.string.dynamic_color),
                                subtitle = stringResource(R.string.dynamic_color_sub),
                                checked = settings.dynamicColor,
                                onChecked = { v -> onChange { it.copy(dynamicColor = v) } },
                            )
                        }
                    }

                    Section(stringResource(R.string.section_language))
                    Card {
                        Segmented(
                            options = AppLanguage.entries,
                            selected = language,
                            label = {
                                when (it) {
                                    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                    AppLanguage.EN -> stringResource(R.string.language_en)
                                    AppLanguage.RU -> stringResource(R.string.language_ru)
                                }
                            },
                            onSelect = { if (it != language) AppLanguage.set(it) },
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("language"),
                        )
                    }

                    Section(stringResource(R.string.section_editor))
                    Card {
                        Text(
                            stringResource(R.string.text_size),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                        )
                        Segmented(
                            options = TextSize.entries,
                            selected = settings.textSize,
                            label = { it.title },
                            onSelect = { size -> onChange { it.copy(textSize = size) } },
                            modifier = Modifier.padding(12.dp),
                        )
                        // Живой пример: как будет выглядеть заметка.
                        Column(
                            Modifier
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.background)
                                .padding(16.dp)
                        ) {
                            Text(
                                stringResource(R.string.sample_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = (settings.textSize.sp + 5).sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.sample_text),
                                fontSize = settings.textSize.sp.sp,
                                lineHeight = settings.textSize.lineHeight.sp,
                            )
                        }
                    }

                    Section(stringResource(R.string.section_list))
                    Card {
                        SwitchRow(
                            title = stringResource(R.string.compact_list),
                            subtitle = stringResource(R.string.compact_list_sub),
                            checked = settings.compactList,
                            onChecked = { v -> onChange { it.copy(compactList = v) } },
                        )
                        Divider()
                        SwitchRow(
                            title = stringResource(R.string.show_preview),
                            subtitle = stringResource(R.string.show_preview_sub),
                            checked = settings.showPreview,
                            onChecked = { v -> onChange { it.copy(showPreview = v) } },
                        )
                    }

                    Section(stringResource(R.string.section_sounds))
                    Card {
                        SwitchRow(
                            title = stringResource(R.string.sounds),
                            subtitle = stringResource(R.string.sounds_sub),
                            checked = settings.sounds,
                            onChecked = { v ->
                                onChange { it.copy(sounds = v) }
                                // Включили — сразу слышно, как это звучит.
                                if (v) Sounds.get(context).play(Sound.Pin)
                            },
                        )
                    }

                    Section(stringResource(R.string.section_data))
                    Card {
                        LinkRow(stringResource(R.string.restore), stringResource(R.string.restore_sub), onImport)
                        Divider()
                        LinkRow(stringResource(R.string.import_help), stringResource(R.string.import_help_sub)) { importHelpOpen = true }
                        Divider()
                        LinkRow(stringResource(R.string.backup), stringResource(R.string.backup_sub), onExport)
                        Divider()
                        LinkRow(stringResource(R.string.export_md), stringResource(R.string.export_md_sub), onExportMarkdown)
                    }

                    Section(stringResource(R.string.section_about))
                    Card {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(painterResource(R.drawable.ic_cotes_cat), contentDescription = null, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    version,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.testTag("version"),
                                )
                            }
                        }
                        Divider()
                        LinkRow(stringResource(R.string.privacy), stringResource(R.string.privacy_sub)) { privacyOpen = true }
                    }

                    Text(
                        stringResource(R.string.reset_settings),
                        color = colors.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onReset)
                            .padding(14.dp),
                    )
                    Text(
                        stringResource(if (syncState.account != null) R.string.settings_footer_sync else R.string.settings_footer),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 32.dp),
                    )
                }
            }
        }
    }

    if (importHelpOpen) {
        AlertDialog(
            onDismissRequest = { importHelpOpen = false },
            title = { Text(stringResource(R.string.import_help)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    importHelpApps.forEach { (icon, name, how) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(36.dp))
                            Column {
                                Text(stringResource(name), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(how), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text(stringResource(R.string.import_help_footer), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { importHelpOpen = false; onImport() }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { importHelpOpen = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (privacyOpen) {
        AlertDialog(
            onDismissRequest = { privacyOpen = false },
            title = { Text(stringResource(R.string.privacy)) },
            text = { Text(stringResource(R.string.privacy_text)) },
            confirmButton = {
                TextButton(onClick = { privacyOpen = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

private fun Color.lum(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
internal fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

@Composable
internal fun Divider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Сегментированный переключатель с плавной подсветкой выбранного варианта. */
@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) colors.primary else Color.Transparent, label = "seg-bg")
            val fg by animateColorAsState(if (isSelected) colors.onPrimary else colors.onSurface, label = "seg-fg")
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val color = Color(if (dark) accent.dark else accent.light)
    val title = stringResource(accent.title)
    val name = stringResource(R.string.accent_cd, title)
    val checkScale by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check",
    )
    val ring by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ring",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = name
            }
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .border(BorderStroke(ring, MaterialTheme.colorScheme.onSurface), CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (dark) Color(0xFF16141A) else Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .scale(checkScale),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Приложения в окне «Переезд»: значок, название и короткая инструкция по экспорту. */
private val importHelpApps = listOf(
        Triple(R.drawable.ic_import_simplenote, R.string.import_help_simplenote_name, R.string.import_help_simplenote),
        Triple(R.drawable.ic_import_keep, R.string.import_help_keep_name, R.string.import_help_keep),
        Triple(R.drawable.ic_import_evernote, R.string.import_help_evernote_name, R.string.import_help_evernote),
        Triple(R.drawable.ic_import_standardnotes, R.string.import_help_sn_name, R.string.import_help_sn),
        Triple(R.drawable.ic_import_joplin, R.string.import_help_joplin_name, R.string.import_help_joplin),
        Triple(R.drawable.ic_import_bear, R.string.import_help_bear_name, R.string.import_help_bear),
        Triple(R.drawable.ic_import_notion, R.string.import_help_notion_name, R.string.import_help_notion),
        Triple(R.drawable.ic_import_obsidian, R.string.import_help_obsidian_name, R.string.import_help_obsidian),
        Triple(R.drawable.ic_import_other, R.string.import_help_other_name, R.string.import_help_other),
)
