package com.mehul.inkless.ui.launch

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import kotlin.math.hypot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays the intro once per app start: coming back from the background goes straight in. */
object LaunchIntroGate {
    private var played = false

    /** True the first time it's asked in this process, false after that. */
    @Synchronized
    fun claim(): Boolean = if (played) false else true.also { played = true }
}

/** The tile's green and the drop's paper white, as on the launcher icon. The same in light and dark. */
private val InkGreen = Color(0xFF236B53)
private val Paper = Color(0xFFFFFEFA)

/**
 * The opening: Android's own launch screen (the ledger drop on a green circle) hands over to this,
 * which starts from that same picture, lets the logo settle, spreads the green out from it until
 * it fills the screen, and fades away onto the app. About 1.3 seconds; behind the green it's the
 * app's own background, so it follows the Light, Dark or System choice. With animations turned off
 * on the phone it just shows the logo for a moment.
 *
 * [circle] and [mark] match where Android draws its launch screen icon, so the hand-over can't be seen.
 */
@Composable
fun LaunchIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Dp = 160.dp,
    mark: Dp = 157.dp,
) {
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val settle = remember { Animatable(1f) }
    val spread = remember { Animatable(0f) }
    val markAlpha = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (animationsOff) {
            delay(STILL_MS)
        } else {
            // The logo takes a small breath, then the ink spreads and the app shows through.
            settle.animateTo(1.06f, tween(SETTLE_MS, easing = FastOutSlowInEasing))
            settle.animateTo(1f, tween(SETTLE_MS, easing = FastOutSlowInEasing))
            coroutineScope {
                launch { markAlpha.animateTo(0f, tween(SPREAD_MS / 2, easing = LinearEasing)) }
                spread.animateTo(1f, tween(SPREAD_MS, easing = FastOutLinearInEasing))
            }
            fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
        }
        onFinished()
    }

    val background = Inkless.colors.background
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            // Nothing underneath can be tapped while the intro is on screen.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .semantics { contentDescription = "Inkless" },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val start = with(density) { circle.toPx() } / 2f
        // Far enough to cover the corners of the screen.
        val cover = hypot(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()) / 2f
        Canvas(Modifier.fillMaxSize()) {
            drawRect(background)
            val radius = start * settle.value + (cover - start) * spread.value
            drawCircle(InkGreen, radius = radius, center = center)
        }
        Image(
            painterResource(R.drawable.logo_mark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Paper),
            modifier = Modifier
                // The drop sits one unit (of the mark's 120) above the middle of its box; Android's
                // icon centres it exactly, so nudge it down to land on the same pixels.
                .offset(y = mark / 120)
                .size(mark)
                .graphicsLayer {
                    scaleX = settle.value
                    scaleY = settle.value
                    alpha = markAlpha.value
                },
        )
    }
}

private const val SETTLE_MS = 160
private const val SPREAD_MS = 480
private const val FADE_MS = 360
private const val STILL_MS = 400L
