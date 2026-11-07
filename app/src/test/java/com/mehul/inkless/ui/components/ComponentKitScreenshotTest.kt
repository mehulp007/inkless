package com.mehul.inkless.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.mehul.inkless.ui.theme.InklessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shared components in both themes; any change to how one looks shows up as a changed image. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h2600dp-xxhdpi")
class ComponentKitScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() {
        compose.setContent { InklessTheme(darkTheme = false) { KitGallery() } }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun dark() {
        compose.setContent { InklessTheme(darkTheme = true) { KitGallery() } }
        compose.onRoot().captureRoboImage()
    }
}
