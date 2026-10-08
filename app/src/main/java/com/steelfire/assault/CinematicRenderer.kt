package com.steelfire.assault

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A small, deterministic opening sequence built from the bundled art and Canvas primitives.
 * The class owns only timeline state; rendering never advances that timeline.
 */
class CinematicRenderer(private val art: AssetRepository) {
    companion object {
        const val WIDTH = 1280f
        const val HEIGHT = 720f
        const val DURATION = 10.8f

        private const val BACKDROP = "gfx/backgrounds/industrial_port.png"
        private const val OPERATIVE = "gfx/characters/operative_sheet.png"
        private const val VEHICLE_BOSS = "gfx/vehicles/vehicle_boss_sheet.png"
        private const val CG_HANGAR = "cg/cg_01_hangar_3d.png"
        private const val CG_OPERATOR = "cg/cg_02_operative_2d.png"
        private const val CG_CORE = "cg/cg_03_core_title.png"
    }

    enum class Cue { IMPACT }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var time = 0f
    private var impactSent = false

    val finished: Boolean get() = time >= DURATION

    fun reset() {
        time = 0f
        impactSent = false
    }

    /** Advances on the fixed-step game clock and returns one-shot presentation cues. */
    fun update(dt: Float): Cue? {
        val previous = time
        time = min(DURATION, time + dt.coerceAtLeast(0f))
        return if (!impactSent && previous < 5.95f && time >= 5.95f) {
            impactSent = true
            Cue.IMPACT
        } else {
            null
        }
    }

    fun skip() {
        time = DURATION
    }

    fun draw(canvas: Canvas) {
        val t = time.coerceIn(0f, DURATION)
        drawBackdrop(canvas, t)
        if (t < 4.65f) drawHangarPerspective(canvas, t)
        if (t < 6.15f) drawMechReveal(canvas, t)
        if (t < 7.15f) drawOperator(canvas, t)
        drawTitle(canvas, t)
        drawVignette(canvas, t)
    }

    private fun drawBackdrop(canvas: Canvas, t: Float) {
        canvas.drawColor(Color.rgb(5, 11, 18))

        val zoom = 1f + 0.08f * smoothStep(0f, 5.8f, t)
        val overX = (WIDTH * (zoom - 1f)) * 0.5f
        val overY = (HEIGHT * (zoom - 1f)) * 0.5f
        art.draw(
            canvas,
            BACKDROP,
            Rect(0, 0, 1672, 941),
            RectF(-overX, -overY, WIDTH + overX, HEIGHT + overY),
            alpha = (255f * smoothStep(0.1f, 1.0f, t)).toInt()
        )

        // The opening uses a three-shot original CG package: procedural 3D rover,
        // hand-painted 2D operative, then the reactor title plate. If a generated
        // bitmap is unavailable, the older procedural backdrop remains visible.
        val cgPath: String
        val cgSource: Rect
        val cgAlpha: Int
        when {
            t < 4.25f -> {
                cgPath = CG_HANGAR
                cgSource = Rect(0, 0, 1916, 821)
                cgAlpha = (255f * smoothStep(0.25f, 0.9f, t)).toInt()
            }
            t < 7.2f -> {
                cgPath = CG_OPERATOR
                cgSource = Rect(0, 0, 1672, 941)
                cgAlpha = (255f * smoothStep(4.0f, 4.7f, t) * (1f - smoothStep(6.7f, 7.2f, t))).toInt()
            }
            else -> {
                cgPath = CG_CORE
                cgSource = Rect(0, 0, 1672, 941)
                cgAlpha = (255f * smoothStep(6.8f, 7.55f, t)).toInt()
            }
        }
        art.draw(canvas, cgPath, cgSource, RectF(0f, 0f, WIDTH, HEIGHT), cgAlpha)

        // A cool color grade keeps the photo-real backdrop coherent with the game's teal/orange palette.
        fill.shader = LinearGradient(
            0f,
            0f,
            0f,
            HEIGHT,
            Color.argb(130, 2, 12, 24),
            Color.argb(185, 3, 7, 13),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, WIDTH, HEIGHT, fill)
        fill.shader = null

        // Moving haze makes the camera feel alive without any random state or texture dependency.
        val haze = 0.5f + 0.5f * sin(t * 0.65f)
        fill.color = Color.argb((18f + haze * 18f).toInt(), 111, 186, 199)
        canvas.drawOval(RectF(-80f + t * 14f, 285f, 520f + t * 14f, 480f), fill)
        fill.color = Color.argb((14f + (1f - haze) * 14f).toInt(), 255, 174, 86)
        canvas.drawOval(RectF(775f - t * 10f, 190f, 1410f - t * 10f, 420f), fill)
    }

    private fun drawHangarPerspective(canvas: Canvas, t: Float) {
        val doorOpen = smoothStep(1.0f, 4.35f, t)
        val vanishingX = 790f - smoothStep(2.0f, 8.0f, t) * 115f
        val vanishingY = 292f

        // Floor panels converge on the same point as the background's hangar, selling a 3D camera move.
        path.reset()
        path.moveTo(0f, 720f)
        path.lineTo(WIDTH, 720f)
        path.lineTo(vanishingX + 90f, vanishingY)
        path.lineTo(vanishingX - 90f, vanishingY)
        path.close()
        fill.color = Color.argb(80, 2, 11, 17)
        canvas.drawPath(path, fill)

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2.2f
        stroke.color = Color.argb(86, 164, 211, 211)
        for (i in 0..10) {
            val x = i / 10f * WIDTH
            canvas.drawLine(vanishingX, vanishingY, x, HEIGHT, stroke)
        }
        for (i in 1..5) {
            val p = i / 5f
            val y = vanishingY + (HEIGHT - vanishingY) * p * p
            canvas.drawLine(0f, y, WIDTH, y, stroke)
        }

        // Sliding blast doors frame the reveal. Their movement is an explicit part of the timeline.
        val panelHeight = 250f * (1f - doorOpen)
        fill.color = Color.argb(220, 7, 17, 24)
        canvas.drawRect(0f, 0f, WIDTH, 92f + panelHeight, fill)
        canvas.drawRect(0f, HEIGHT - 48f - panelHeight, WIDTH, HEIGHT, fill)
        fill.color = Color.argb(210, 10, 24, 31)
        canvas.drawRect(0f, 92f + panelHeight, 230f, HEIGHT - 48f - panelHeight, fill)
        canvas.drawRect(WIDTH - 230f, 92f + panelHeight, WIDTH, HEIGHT - 48f - panelHeight, fill)

        stroke.strokeWidth = 4f
        stroke.color = Color.argb(130, 208, 151, 71)
        for (i in 0..4) {
            val y = 118f + i * 35f + panelHeight * 0.22f
            canvas.drawLine(18f, y, 204f, y, stroke)
            canvas.drawLine(WIDTH - 204f, y, WIDTH - 18f, y, stroke)
        }

        // Recessed door light bars pulse at the exact moment the mech drops into frame.
        val pulse = 0.55f + 0.45f * sin(t * 5.5f)
        fill.color = Color.argb((90f + pulse * 105f).toInt(), 240, 164, 62)
        canvas.drawRoundRect(RectF(262f, 112f + panelHeight * 0.72f, 350f, 122f + panelHeight * 0.72f), 5f, 5f, fill)
        canvas.drawRoundRect(RectF(930f, 112f + panelHeight * 0.72f, 1018f, 122f + panelHeight * 0.72f), 5f, 5f, fill)
        stroke.style = Paint.Style.FILL

        // A restrained scanline layer adds a screen-like presentation without harming readability.
        fill.color = Color.argb(18, 175, 220, 218)
        for (y in 0 until HEIGHT.toInt() step 8) canvas.drawRect(0f, y.toFloat(), WIDTH, y + 1f, fill)
    }

    private fun drawMechReveal(canvas: Canvas, t: Float) {
        val reveal = smoothStep(2.15f, 4.75f, t)
        if (reveal <= 0f) return

        val hover = sin(t * 2.0f) * 8f
        val scale = 0.52f + reveal * 0.26f
        val width = 1080f * scale
        val height = 887f * scale
        val left = 660f + (1f - reveal) * 320f
        val top = 198f + hover - (1f - reveal) * 95f
        val alpha = (255f * reveal).toInt()

        if (t < 4.35f) {
            // The bundled boss vehicle is the hero prop; the glow and cast shadow provide the 3D integration.
            fill.color = Color.argb((100f * reveal).toInt(), 0, 0, 0)
            canvas.drawOval(RectF(left + 15f, top + height * 0.77f, left + width - 10f, top + height * 0.94f), fill)
            art.draw(canvas, VEHICLE_BOSS, Rect(680, 0, 1774, 887), RectF(left, top, left + width, top + height), alpha)

            fill.color = Color.argb((95f * reveal).toInt(), 255, 166, 65)
            canvas.drawCircle(left + width * 0.38f, top + height * 0.79f, 34f + reveal * 12f, fill)
            fill.color = Color.argb((45f * reveal).toInt(), 255, 205, 116)
            canvas.drawCircle(left + width * 0.38f, top + height * 0.79f, 88f + reveal * 18f, fill)
        }

        // A camera flash marks the hard cut from machine reveal to the 2D operator silhouette.
        val flash = smoothStep(5.45f, 5.95f, t) * (1f - smoothStep(6.05f, 6.35f, t))
        if (flash > 0f) {
            fill.color = Color.argb((150f * flash).toInt(), 255, 227, 174)
            canvas.drawRect(0f, 0f, WIDTH, HEIGHT, fill)
        }
    }

    private fun drawOperator(canvas: Canvas, t: Float) {
        val arrive = smoothStep(4.75f, 6.35f, t)
        if (arrive <= 0f) return

        // The authored 2D key art already carries the operator silhouette. Keep only
        // a small muzzle accent so the shot still reads as an action beat.
        if (t >= 4.95f) {
            val firing = smoothStep(6.55f, 7.15f, t)
            if (firing > 0.45f) {
                val muzzle = 420f + 18f * sin(t * 20f)
                fill.color = Color.argb((210f * firing).toInt(), 255, 211, 106)
                canvas.drawCircle(muzzle, 380f, 9f + 7f * firing, fill)
                fill.color = Color.argb((80f * firing).toInt(), 255, 130, 53)
                canvas.drawCircle(muzzle, 380f, 30f + 20f * firing, fill)
            }
            return
        }

        val firing = smoothStep(6.55f, 7.15f, t)
        val source = if (firing > 0.55f) Rect(1645, 0, 2172, 724) else Rect(0, 0, 525, 724)
        val x = 158f + 120f * arrive
        val bottom = 632f + sin(t * 7f) * 2f
        val h = 405f
        val w = h * source.width().toFloat() / source.height().toFloat()
        val alpha = (255f * min(1f, arrive * 1.8f)).toInt()

        // Teal backlight turns the photographed sprite into a readable 2D silhouette against the hangar.
        fill.color = Color.argb((95f * arrive).toInt(), 69, 205, 206)
        canvas.drawOval(RectF(x - 45f, bottom - h * 0.22f, x + w * 0.84f, bottom + 20f), fill)
        art.draw(canvas, OPERATIVE, source, RectF(x, bottom - h, x + w, bottom), alpha)

        if (firing > 0.45f) {
            val muzzle = 820f + 18f * sin(t * 20f)
            fill.color = Color.argb((220f * firing).toInt(), 255, 211, 106)
            canvas.drawCircle(muzzle, 470f, 9f + 7f * firing, fill)
            fill.color = Color.argb((95f * firing).toInt(), 255, 130, 53)
            canvas.drawCircle(muzzle, 470f, 30f + 20f * firing, fill)
        }
    }

    private fun drawTitle(canvas: Canvas, t: Float) {
        val reveal = smoothStep(7.0f, 8.35f, t)
        if (reveal <= 0f) return

        val lift = (1f - reveal) * 28f
        title.textAlign = Paint.Align.CENTER
        title.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        title.textSize = 78f
        title.style = Paint.Style.FILL
        title.color = Color.argb((255f * reveal).toInt(), 244, 238, 218)
        title.setShadowLayer(16f, 0f, 4f, Color.argb((170f * reveal).toInt(), 0, 0, 0))
        canvas.drawText("钢火突袭", 640f, 205f + lift, title)
        title.clearShadowLayer()

        title.textSize = 21f
        title.letterSpacing = 0.22f
        title.color = Color.argb((240f * reveal).toInt(), 252, 180, 79)
        canvas.drawText("STEELFIRE ASSAULT  //  RUST HARBOR", 640f, 244f + lift, title)
        title.letterSpacing = 0f

        fill.color = Color.argb((230f * reveal).toInt(), 232, 157, 66)
        canvas.drawRect(480f, 266f + lift, 800f, 270f + lift, fill)

        title.textSize = 16f
        title.color = Color.argb((188f * reveal).toInt(), 205, 219, 219)
        canvas.drawText("离线行动 · 载入战区 01", 640f, 298f + lift, title)

        // A subtle technical slate sells the production-card feel while staying fully original.
        title.textAlign = Paint.Align.LEFT
        title.textSize = 14f
        title.color = Color.argb((160f * reveal).toInt(), 167, 194, 193)
        canvas.drawText("DOCK 07", 54f, 646f, title)
        title.textAlign = Paint.Align.RIGHT
        canvas.drawText("SYSTEMS ONLINE", 1226f, 646f, title)
    }

    private fun drawVignette(canvas: Canvas, t: Float) {
        val fadeIn = 1f - smoothStep(0f, 0.9f, t)
        val fadeOut = smoothStep(9.65f, DURATION, t)
        val alpha = max(fadeIn, fadeOut)
        if (alpha <= 0f) return

        fill.shader = LinearGradient(
            0f,
            0f,
            0f,
            HEIGHT,
            Color.argb((235f * alpha).toInt(), 0, 0, 0),
            Color.argb((40f * alpha).toInt(), 0, 0, 0),
            Shader.TileMode.MIRROR
        )
        canvas.drawRect(0f, 0f, WIDTH, HEIGHT, fill)
        fill.shader = null
    }

    private fun smoothStep(start: Float, end: Float, value: Float): Float {
        if (end <= start) return if (value >= end) 1f else 0f
        val x = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
