package io.github.dantemorrigan.cotes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.dantemorrigan.cotes.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Элементы, на которые указывают подсказки первого запуска. */
enum class CoachTarget { NewNote, FirstNote, SaveStatus }

/** Три шага знакомства: создать заметку, узнать про автосохранение, узнать про свайпы. */
enum class Tip(val step: Int, val target: CoachTarget, val above: Boolean) {
    Create(1, CoachTarget.NewNote, above = true),
    Write(2, CoachTarget.SaveStatus, above = true),
    Swipe(3, CoachTarget.FirstNote, above = false),
}

/** Положение элементов в координатах окна; подсказка рисуется поверх всего экрана. */
class CoachAnchors {
    val bounds = mutableStateMapOf<CoachTarget, Rect>()
}

/** null, когда подсказки уже пройдены: тогда положение элементов не отслеживается вовсе. */
val LocalCoachAnchors = staticCompositionLocalOf<CoachAnchors?> { null }

fun Modifier.coachAnchor(anchors: CoachAnchors?, target: CoachTarget): Modifier =
    if (anchors == null) this
    else onGloballyPositioned {
        val bounds = it.boundsInRoot()
        if (anchors.bounds[target] != bounds) anchors.bounds[target] = bounds
    }

/**
 * Подсказка-облачко со стрелкой к элементу. [onSkip] — кнопка «Пропустить» рядом с основной. Появляется с небольшой задержкой,
 * когда экран уже доехал на место, и не мешает нажимать на всё остальное.
 */
@Composable
fun CoachMark(
    tip: Tip,
    anchors: CoachAnchors,
    onPrimary: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val anchor = anchors.bounds[tip.target]
    val colors = MaterialTheme.colorScheme
    val visible = remember { MutableTransitionState(false) }
    val caretX = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(tip) {
        delay(450)
        visible.targetState = true
    }
    val (title, text, action) = when (tip) {
        Tip.Create -> Triple(R.string.onb_create_title, R.string.onb_create_text, R.string.onb_skip)
        Tip.Write -> Triple(R.string.onb_write_title, R.string.onb_write_text, R.string.onb_got_it)
        Tip.Swipe -> Triple(R.string.onb_swipe_title, R.string.onb_swipe_text, R.string.onb_done)
    }
    val shape = RoundedCornerShape(18.dp)
    val bubble = colors.inverseSurface

    Layout(
        content = {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(220)) + scaleIn(
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.85f,
                    transformOrigin = TransformOrigin(0.5f, if (tip.above) 1f else 0f),
                ),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f),
            ) {
                Column(
                    Modifier
                        .testTag("tip")
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .drawBehind {
                            // Стрелка к элементу: треугольник у верхнего или нижнего края облачка.
                            val x = caretX.floatValue.coerceIn(24.dp.toPx(), size.width - 24.dp.toPx())
                            val h = 8.dp.toPx()
                            val w = 9.dp.toPx()
                            val path = Path().apply {
                                if (tip.above) {
                                    moveTo(x - w, size.height - 1f)
                                    lineTo(x, size.height + h)
                                    lineTo(x + w, size.height - 1f)
                                } else {
                                    moveTo(x - w, 1f)
                                    lineTo(x, -h)
                                    lineTo(x + w, 1f)
                                }
                                close()
                            }
                            drawPath(path, bubble)
                        }
                        .shadow(6.dp, shape)
                        .background(bubble, shape)
                        .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                        Text(
                            stringResource(title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.inverseOnSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.onb_step, tip.step, Tip.entries.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inverseOnSurface.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        stringResource(text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inverseOnSurface,
                        modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val buttonColors = ButtonDefaults.textButtonColors(contentColor = colors.inversePrimary)
                        if (onSkip != null) {
                            TextButton(onClick = onSkip, colors = buttonColors) { Text(stringResource(R.string.onb_skip)) }
                        }
                        TextButton(onClick = onPrimary, colors = buttonColors) {
                            Text(stringResource(action), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val maxWidth = minOf(constraints.maxWidth - 2 * margin, 340.dp.roundToPx()).coerceAtLeast(0)
        val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = maxWidth))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable == null || anchor == null) return@layout
            val x = (anchor.center.x - placeable.width / 2f).roundToInt()
                .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val y = if (tip.above) anchor.top.roundToInt() - placeable.height - gap else anchor.bottom.roundToInt() + gap
            caretX.floatValue = anchor.center.x - x
            placeable.place(x, y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0)))
        }
    }
}
