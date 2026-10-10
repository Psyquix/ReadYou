package me.ash.reader.ui.page.settings.color.inprogress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.InProgressColorPreference
import me.ash.reader.infrastructure.preference.InProgressStylePreference
import me.ash.reader.infrastructure.preference.LocalInProgressColor
import me.ash.reader.infrastructure.preference.LocalInProgressStyle
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight
import androidx.compose.ui.graphics.toArgb

@Composable
fun InProgressStylePage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val style = LocalInProgressStyle.current
    val colorPref = LocalInProgressColor.current
    val themePrimary = MaterialTheme.colorScheme.primary

    var styleDialogVisible by remember { mutableStateOf(false) }
    // Working copy: drags update this, Apply writes it. Null = automatic.
    var pickedArgb by remember(colorPref.value) {
        mutableStateOf(
            if (colorPref.value == COLOR_AUTO) themePrimary.toArgb() else colorPref.value
        )
    }
    var pickedHue by remember { mutableFloatStateOf(214f) }
    var pickedSat by remember { mutableFloatStateOf(0.85f) }
    val themeArgb = themePrimary.toArgb()
    val strengths = highlightStrengths(pickedArgb)
    val shades = accentShades(themeArgb)

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.in_progress), desc = "")
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.in_progress_style),
                    )
                    SettingItem(
                        title =
                            if (style == InProgressStylePreference.Highlight) {
                                stringResource(R.string.in_progress_highlight)
                            } else {
                                stringResource(R.string.in_progress_edge_bar)
                            },
                        onClick = { styleDialogVisible = true },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.in_progress_color),
                    )
                    SettingItem(
                        title = stringResource(R.string.in_progress_automatic_accent),
                        onClick = {
                            InProgressColorPreference.Auto.put(context, scope)
                        },
                    ) {
                        if (colorPref.value == COLOR_AUTO) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    SwatchCaption(text = stringResource(R.string.in_progress_current))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(pickedArgb),
                        ) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "#" + (pickedArgb and 0xFFFFFF)
                                    .toString(16)
                                    .uppercase()
                                    .padStart(6, '0'),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text =
                                    if (colorPref.value == COLOR_AUTO) {
                                        stringResource(R.string.in_progress_automatic_accent)
                                    } else {
                                        stringResource(R.string.in_progress_custom)
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SpectrumPicker(
                        hue = pickedHue,
                        saturation = pickedSat,
                        onPick = { h, s ->
                            pickedHue = h
                            pickedSat = s
                            pickedArgb = hsvToArgb(h, s, 1f)
                        },
                    )
                    SwatchCaption(text = stringResource(R.string.in_progress_strengths))
                    SwatchRow(
                        colors = strengths,
                        selected = selectedSwatch(pickedArgb, strengths),
                        onSelect = { pickedArgb = it },
                    )
                    SwatchCaption(text = stringResource(R.string.in_progress_accent_shades))
                    SwatchRow(
                        colors = shades,
                        selected = selectedSwatch(pickedArgb, shades),
                        onSelect = { pickedArgb = it },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = {
                                InProgressColorPreference.Custom(pickedArgb).put(context, scope)
                            }
                        ) {
                            Text(text = stringResource(R.string.in_progress_apply))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    )

    RadioDialog(
        visible = styleDialogVisible,
        title = stringResource(R.string.in_progress_style),
        options = InProgressStylePreference.values.map {
            RadioDialogOption(
                text =
                    if (it == InProgressStylePreference.Highlight) {
                        stringResource(R.string.in_progress_highlight)
                    } else {
                        stringResource(R.string.in_progress_edge_bar)
                    },
                selected = it == style,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        styleDialogVisible = false
    }
}

@Composable
private fun SwatchCaption(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwatchRow(
    colors: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { argb ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(argb) },
                color = Color(argb),
                border =
                    if (selected == argb) {
                        BorderStroke(2.dp, Color.White)
                    } else {
                        null
                    },
            ) {
                if (selected == argb) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumPicker(
    hue: Float,
    saturation: Float,
    onPick: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        val boxWidth = maxWidth
        val widthPx = with(density) { boxWidth.toPx() }
        val heightPx = with(density) { 170.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red,
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.ui.graphics.Color.White,
                        )
                    )
                )
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures { offset ->
                        onPick(
                            (offset.x / widthPx * 360f).coerceIn(0f, 359.99f),
                            (1f - offset.y / heightPx).coerceIn(0f, 1f),
                        )
                    }
                }
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures { change, _ ->
                        onPick(
                            (change.position.x / widthPx * 360f).coerceIn(0f, 359.99f),
                            (1f - change.position.y / heightPx).coerceIn(0f, 1f),
                        )
                        change.consume()
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        val half = 9.dp.roundToPx()
                        IntOffset(
                            (boxWidth * hue / 360f).roundToPx() - half,
                            (170.dp * (1f - saturation)).roundToPx() - half,
                        )
                    }
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
