package com.mehul.inkless.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination

/**
 * How screens move. Opening a page slides it in a little from the right while it fades in;
 * tapping another tab only cross-fades, since tabs sit side by side rather than on top of each
 * other. Going back always slides back: the screen you leave slides right and fades while the one
 * underneath slides in from the left, both at the same time, so there's never a blank moment —
 * from a page, or from another tab back to Home. All of it is short, and Compose shortens or skips
 * it when the phone's animation scale is turned down or off (Developer options, or Accessibility →
 * Remove animations).
 */
object Motion {

    const val SHORT = 120
    const val MEDIUM = 240
    const val BACK = 300
    const val GROW = 600

    /** Going back, each screen travels a fifth of the screen's width. */
    private const val BACK_SHIFT = 5

    private const val NUDGE = 12

    /** The tab a destination belongs to: tab screens sit in a tab's nested graph, pages in the root one. */
    private fun NavDestination.tabGraph(): NavDestination? = parent?.takeIf { it.parent != null }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.switchingTabs(): Boolean {
        val from = initialState.destination.tabGraph()
        val to = targetState.destination.tabGraph()
        return from != null && to != null && from.id != to.id
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (switchingTabs()) {
            fadeIn(tween(MEDIUM, delayMillis = SHORT / 2))
        } else {
            fadeIn(tween(MEDIUM)) + slideInHorizontally(tween(MEDIUM, easing = FastOutSlowInEasing)) { it / NUDGE }
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (switchingTabs()) {
            fadeOut(tween(SHORT))
        } else {
            fadeOut(tween(SHORT)) + slideOutHorizontally(tween(MEDIUM, easing = FastOutSlowInEasing)) { -it / (NUDGE * 2) }
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(BACK, easing = FastOutSlowInEasing)) +
            slideInHorizontally(tween(BACK, easing = FastOutSlowInEasing)) { -it / BACK_SHIFT }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(BACK, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(tween(BACK, easing = FastOutSlowInEasing)) { it / BACK_SHIFT }
    }
}
