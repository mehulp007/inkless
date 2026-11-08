package com.mehul.inkless.ui.wrapped

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.mehul.inkless.R
import com.mehul.inkless.insights.WrappedStats
import com.mehul.inkless.share.ShareActivity
import java.io.File

/**
 * The Wrapped card as a picture to post or send: drawn with plain Android Canvas in the app's own
 * green and fonts, saved in the app's cache and shared through the share sheet. It holds only the
 * numbers on the card.
 */
object WrappedImage {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    private const val BACKGROUND = 0xFFF7F6F0.toInt()
    private const val CARD = 0xFFE3EDE4.toInt()
    private const val SURFACE = 0xFFFFFEFA.toInt()
    private const val PRIMARY = 0xFF236B53.toInt()
    private const val INK = 0xFF202B27.toInt()
    private const val MUTED = 0xFF5F6C66.toInt()

    fun share(context: Context, stats: WrappedStats, lines: List<Pair<String, String>>, title: String, headline: String, sub: String) {
        val bitmap = render(context, lines, title, headline, sub)
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

    fun render(context: Context, lines: List<Pair<String, String>>, title: String, headline: String, sub: String): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val lora = font(context, R.font.lora, 400)
        val inter = font(context, R.font.inter, 400)
        val interMedium = font(context, R.font.inter, 500)
        val interBold = font(context, R.font.inter, 600)
        fun paint(face: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }
        val left = 90f

        // The mark: the ledger drop on a green rounded square, and the name.
        canvas.drawRoundRect(RectF(left, 90f, left + 64f, 154f), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRIMARY })
        ResourcesCompat.getDrawable(context.resources, R.drawable.logo_mark, null)?.mutate()?.let { mark ->
            mark.setTint(SURFACE)
            mark.setBounds(left.toInt() + 6, 96, left.toInt() + 58, 148)
            mark.draw(canvas)
        }
        canvas.drawText("inkless", left + 88f, 138f, paint(interBold, 44f, INK))

        canvas.drawText(title, left, 290f, paint(lora, 76f, INK))

        // The total on a green card.
        canvas.drawRoundRect(RectF(left, 340f, WIDTH - left, 600f), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        canvas.drawText("Spent", left + 50f, 410f, paint(interMedium, 34f, PRIMARY))
        canvas.drawText(headline, left + 50f, 520f, paint(interMedium, 112f, INK))
        canvas.drawText(ellipsize(sub, paint(inter, 34f, MUTED), WIDTH - 2 * left - 100f), left + 50f, 572f, paint(inter, 34f, MUTED))

        // The numbers, each on its own raised strip.
        var y = 650f
        for ((label, value) in lines) {
            canvas.drawRoundRect(RectF(left, y, WIDTH - left, y + 100f), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE })
            canvas.drawText(label, left + 40f, y + 42f, paint(inter, 28f, MUTED))
            val valuePaint = paint(interMedium, 38f, INK)
            canvas.drawText(ellipsize(value, valuePaint, WIDTH - 2 * left - 80f), left + 40f, y + 84f, valuePaint)
            y += 118f
        }
        canvas.drawText("Tracked by itself from bank SMS", left, HEIGHT - 70f, paint(inter, 30f, MUTED))
        return bitmap
    }

    /** One of the bundled variable fonts at [weight]; the system sans if it can't be loaded. */
    private fun font(context: Context, id: Int, weight: Int): Typeface {
        val base = try {
            ResourcesCompat.getFont(context, id)
        } catch (e: android.content.res.Resources.NotFoundException) {
            null
        } ?: Typeface.SANS_SERIF
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false) else base
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var cut = text
        while (cut.isNotEmpty() && paint.measureText("$cut…") > width) cut = cut.dropLast(1)
        return "$cut…"
    }
}
