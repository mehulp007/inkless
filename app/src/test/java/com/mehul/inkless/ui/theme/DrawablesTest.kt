package com.mehul.inkless.ui.theme

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every drawable must load through Android's own parser, not just Compose's: the launcher, the
 * notification bar and the widget host read them that way, and the framework is stricter about
 * path data (for example it won't accept arc flags run together like "0022").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class DrawablesTest {

    @Test
    fun `every drawable and the launcher icon load`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val ids = R.drawable::class.java.fields.filter { it.name.startsWith("icon_") || it.name.startsWith("ic_") || it.name.startsWith("widget_") || it.name.startsWith("logo_") }
        assertThat(ids).isNotEmpty()
        val broken = ids.filter { field -> runCatching { context.getDrawable(field.getInt(null)) }.isFailure }.map { it.name }
        assertThat(broken).isEmpty()
        assertThat(context.getDrawable(R.mipmap.ic_launcher)).isNotNull()
    }
}
