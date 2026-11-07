package com.mehul.inkless.ui.theme

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mehul.inkless.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the bundled fonts and every line icon, so a broken font file or a badly converted icon
 * shows up as a changed screenshot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h1400dp-xxhdpi")
class BrandAssetsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fontsAndIcons() {
        compose.setContent { BrandSheet() }
        compose.onRoot().captureRoboImage()
    }
}

private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Lora = FontFamily(variable(R.font.lora, 400))
private val Inter = FontFamily(variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600))

private val Ink = Color(0xFF202B27)
private val Green = Color(0xFF236B53)

private fun lineIcons(): List<Int> = R.drawable::class.java.fields
    .filter { it.name.startsWith("icon_") }
    .sortedBy { it.name }
    .map { it.getInt(null) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrandSheet() {
    Column(
        Modifier
            .background(Color(0xFFF7F6F0))
            .padding(24.dp)
            .width(364.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("The ledger that writes itself.", fontFamily = Lora, fontSize = 38.sp, lineHeight = 44.sp, color = Ink)
        Text("₹84", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 64.sp, color = Ink)
        Text(
            "From canteen chai to late-night orders. Your expenses, quietly organised.",
            fontFamily = Inter,
            fontSize = 15.sp,
            color = Ink,
        )
        Text("Create account", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Green)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lineIcons().forEach { icon ->
                Icon(painterResource(icon), contentDescription = null, tint = Green, modifier = Modifier.size(24.dp))
            }
        }
    }
}
