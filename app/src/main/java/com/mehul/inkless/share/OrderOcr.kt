package com.mehul.inkless.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the text off a shared screenshot with Tesseract, entirely on the phone: the image and the
 * text never leave it, and Tesseract makes no network calls. The English model ships in the app's
 * assets and is copied out once, because Tesseract wants a real file.
 */
@Singleton
class OrderOcr @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Null when the image can't be opened. */
    suspend fun read(image: Uri): String? = withContext(Dispatchers.Default) {
        val bitmap = decode(image) ?: return@withContext null
        val tess = TessBaseAPI()
        try {
            if (!tess.init(modelDir().absolutePath, LANGUAGE)) return@withContext null
            tess.setImage(bitmap)
            tess.utF8Text
        } finally {
            tess.recycle()
            bitmap.recycle()
        }
    }

    // The sharing app may not have passed along permission to read the image.
    private fun decode(image: Uri): Bitmap? = try {
        decodeOrThrow(image)
    } catch (e: SecurityException) {
        Log.w(TAG, "No access to the shared image")
        null
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't read the shared image: ${e.javaClass.simpleName}")
        null
    }

    private fun decodeOrThrow(image: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Measuring only: decodeStream returns null here by design and fills in [bounds].
        val stream = context.contentResolver.openInputStream(image) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "The shared file isn't an image")
            return null
        }
        // A long scrolling screenshot can be huge; halve it until it's a sensible size to hold in memory.
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample * sample) > MAX_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(image)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    @Synchronized
    private fun modelDir(): File {
        val dir = File(context.noBackupFilesDir, "ocr")
        val model = File(dir, "tessdata/$LANGUAGE.traineddata")
        if (!model.exists()) {
            model.parentFile?.mkdirs()
            val partial = File(model.path + ".part")
            context.assets.open("tessdata/$LANGUAGE.traineddata").use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            partial.renameTo(model)
        }
        return dir
    }

    private companion object {
        const val TAG = "OrderOcr"
        const val LANGUAGE = "eng"
        const val MAX_PIXELS = 12_000_000L
    }
}
