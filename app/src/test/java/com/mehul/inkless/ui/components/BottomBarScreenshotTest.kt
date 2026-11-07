package com.mehul.inkless.ui.components

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.InklessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bottom bar with each kind of tab: selected, with a count, and plain. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h300dp-xxhdpi")
class BottomBarScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() {
        compose.setContent { InklessTheme(darkTheme = false) { Bars() } }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun dark() {
        compose.setContent { InklessTheme(darkTheme = true) { Bars() } }
        compose.onRoot().captureRoboImage()
    }
}

private val tabs = listOf(
    BottomTab("Home", R.drawable.icon_house),
    BottomTab("To sort", R.drawable.icon_inbox, count = 2),
    BottomTab("Friends", R.drawable.icon_users),
    BottomTab("Money", R.drawable.icon_chart_trend),
)

@Composable
private fun Bars() {
    Column(Modifier.background(Inkless.colors.background)) {
        InklessBottomBar(tabs, selectedIndex = 0, onSelect = {}, modifier = Modifier.padding(top = 16.dp))
        InklessBottomBar(tabs, selectedIndex = 1, onSelect = {}, modifier = Modifier.padding(top = 16.dp))
    }
}
