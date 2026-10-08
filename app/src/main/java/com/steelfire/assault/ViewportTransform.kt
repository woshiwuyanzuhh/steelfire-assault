package com.steelfire.assault

import android.graphics.RectF
import android.view.WindowInsets
import kotlin.math.min

/**
 * Maps the fixed 1280x720 design surface into the current window safe rectangle.
 * Drawing and touch input must use the same instance so letterbox bars cannot
 * receive gameplay input or shift the visible controls away from their hit boxes.
 */
class ViewportTransform(
    val viewWidth: Int,
    val viewHeight: Int,
    insets: Insets = Insets.ZERO,
    private val logicalWidth: Float = 1280f,
    private val logicalHeight: Float = 720f
) {
    data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        companion object { val ZERO = Insets(0, 0, 0, 0) }
    }

    private val safeLeft = insets.left.coerceIn(0, viewWidth)
    private val safeTop = insets.top.coerceIn(0, viewHeight)
    private val safeRight = (viewWidth - insets.right).coerceAtLeast(safeLeft)
    private val safeBottom = (viewHeight - insets.bottom).coerceAtLeast(safeTop)
    val scale: Float = min(
        (safeRight - safeLeft).toFloat() / logicalWidth,
        (safeBottom - safeTop).toFloat() / logicalHeight
    ).coerceAtLeast(0f)
    val offsetX: Float = safeLeft + ((safeRight - safeLeft) - logicalWidth * scale) * 0.5f
    val offsetY: Float = safeTop + ((safeBottom - safeTop) - logicalHeight * scale) * 0.5f
    val drawRect: RectF = RectF(offsetX, offsetY, offsetX + logicalWidth * scale, offsetY + logicalHeight * scale)

    fun toLogical(screenX: Float, screenY: Float): Pair<Float, Float>? {
        if (scale <= 0f || !drawRect.contains(screenX, screenY)) return null
        return ((screenX - offsetX) / scale) to ((screenY - offsetY) / scale)
    }
}
