package com.steelfire.assault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.IOException

/** Lazy, density-independent asset access. Generated art is kept in assets so it can be streamed and replaced. */
class AssetRepository(private val context: Context) {
    private val cache = HashMap<String, Bitmap>()
    private val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isDither = true }

    fun bitmap(path: String): Bitmap? {
        cache[path]?.let { return it }
        return try {
            context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                ?.also { cache[path] = it }
        } catch (_: IOException) { null }
    }

    fun draw(canvas: android.graphics.Canvas, path: String, src: Rect?, dst: RectF, alpha: Int = 255) {
        val b = bitmap(path) ?: return
        val previous = paint.alpha
        paint.alpha = alpha
        canvas.drawBitmap(b, src, dst, paint)
        paint.alpha = previous
    }

    fun clear() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}
