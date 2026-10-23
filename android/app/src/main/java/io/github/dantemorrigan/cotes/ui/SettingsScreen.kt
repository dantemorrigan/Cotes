package io.github.dantemorrigan.cotes.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import io.github.dantemorrigan.cotes.AppSettings
import io.github.dantemorrigan.cotes.EditorFont
import io.github.dantemorrigan.cotes.R
import io.github.dantemorrigan.cotes.TextSize
import io.github.dantemorrigan.cotes.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (сборка ${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("—")
    }

    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text("Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    Section("Тема")
                    Card {
                        Segmented(
                            options = ThemeMode.entries,
                            selected = settings.theme,
                            label = { it.title },
                            onSelect = { mode -> onChange { it.copy(theme = mode) } },
                            modifier = Modifier.padding(12.dp),
                        )
                        Divider()
                        SwitchRow(
                            title = "Чёрный фон",
                            subtitle = "В тёмной теме — для AMOLED-экранов",
                            checked = settings.pureBlack,
                            onChecked = { v -> onChange { it.copy(pureBlack = v) } },
                        )
                    }

                    Section("Цвет")
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
                                title = "Цвета обоев",
                                subtitle = "Material You: акцент подбирается под обои",
                                checked = settings.dynamicColor,
                                onChecked = { v -> onChange { it.copy(dynamicColor = v) } },
                            )
                        }
                    }

                    Section("Редактор")
                    Card {
                        Text(
                            "Размер текста",
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
                        Divider()
                        Text(
                            "Шрифт",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                        )
                        Segmented(
                            options = EditorFont.entries,
                            selected = settings.editorFont,
                            label = { it.title },
                            fontFor = { it.family() },
                            onSelect = { font -> onChange { it.copy(editorFont = font) } },
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
                                "Планы на выходные",
                                fontFamily = settings.editorFont.family(),
                                fontWeight = FontWeight.Bold,
                                fontSize = (settings.textSize.sp + 5).sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Суббота — выставка и прогулка по набережной. Воскресенье — дочитать книгу.",
                                fontFamily = settings.editorFont.family(),
                                fontSize = settings.textSize.sp.sp,
                                lineHeight = settings.textSize.lineHeight.sp,
                            )
                        }
                    }

                    Section("Список")
                    Card {
                        SwitchRow(
                            title = "Компактный список",
                            subtitle = "Больше заметок на экране",
                            checked = settings.compactList,
                            onChecked = { v -> onChange { it.copy(compactList = v) } },
                        )
                        Divider()
                        SwitchRow(
                            title = "Показывать начало текста",
                            subtitle = "Превью под заголовком",
                            checked = settings.showPreview,
                            onChecked = { v -> onChange { it.copy(showPreview = v) } },
                        )
                    }

                    Section("Данные")
                    Card {
                        LinkRow("Резервная копия всех заметок", "Сохранить в файл .json", onExport)
                        Divider()
                        LinkRow("Восстановить из копии", "Подходит и копия из веб-версии", onImport)
                    }

                    Section("О приложении")
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
                                Text("Cotes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Версия $version",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.testTag("version"),
                                )
                            }
                        }
                        Divider()
                        LinkRow("Исходный код", "github.com/dantemorrigan/Cotes") {
                            openLink("https://github.com/dantemorrigan/Cotes")
                        }
                        Divider()
                        LinkRow("Поддержать проект", "Boosty") { openLink("https://boosty.to/svdo/donate") }
                    }

                    Text(
                        "Сбросить настройки",
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
                        "Заметки хранятся только на этом устройстве.",
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
}

private fun Color.lum(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

@Composable
private fun Divider() {
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
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
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
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fontFor: ((T) -> androidx.compose.ui.text.font.FontFamily)? = null,
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
                    fontFamily = fontFor?.invoke(option),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val color = Color(if (dark) accent.dark else accent.light)
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
                contentDescription = "Цвет ${accent.title}"
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
            AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (dark) Color(0xFF16141A) else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            accent.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
