package com.mehul.inkless.ui.wrapped

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.mehul.inkless.insights.WrappedStats
import com.mehul.inkless.share.ShareActivity
import java.io.File

/**
 * The Wrapped card as a picture to post or send: drawn with plain Android Canvas, saved in the
 * app's cache and shared through the share sheet. It holds only the numbers on the card.
 */
object WrappedImage {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    fun share(context: Context, stats: WrappedStats, lines: List<Pair<String, String>>, title: String, headline: String, sub: String) {
        val bitmap = render(lines, title, headline, sub)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "inkless-${stats.month}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Share your month")
            // Inkless's own "break it down" share target makes no sense for its own picture.
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ShareActivity::class.java)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun render(lines: List<Pair<String, String>>, title: String, headline: String, sub: String): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        canvas.drawRect(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            Paint().apply { shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), 0xFF2B2D6E.toInt(), 0xFF6A3D9A.toInt(), Shader.TileMode.CLAMP) },
        )
        fun paint(size: Float, bold: Boolean = false, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            this.alpha = alpha
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val left = 90f
        canvas.drawText("Inkless", left, 140f, paint(40f, bold = true, alpha = 200))
        canvas.drawText(title, left, 250f, paint(72f, bold = true))
        canvas.drawText(headline, left, 400f, paint(130f, bold = true))
        canvas.drawText(sub, left, 470f, paint(40f, alpha = 210))

        var y = 610f
        for ((label, value) in lines) {
            canvas.drawText(label, left, y, paint(34f, alpha = 190))
            canvas.drawText(ellipsize(value, paint(50f, bold = true), WIDTH - 2 * left), left, y + 62f, paint(50f, bold = true))
            y += 130f
        }
        canvas.drawText("Tracked by itself from bank SMS", left, HEIGHT - 80f, paint(32f, alpha = 170))
        return bitmap
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var cut = text
        while (cut.isNotEmpty() && paint.measureText("$cut…") > width) cut = cut.dropLast(1)
        return "$cut…"
    }
}
