package com.steelfire.assault

import android.graphics.RectF

/** Shared logical geometry for the visible controls and their touch hit regions. */
object ControlLayout {
    const val LOGICAL_WIDTH = 1280f
    const val LOGICAL_HEIGHT = 720f

    // The observation window ends at y=612 in the fixed 70% x 70% contract.
    val moveHit = RectF(16f, 620f, 210f, 720f)
    val moveCenterX = 102f
    val moveCenterY = 668f

    // Visual bounds stay compact; hit bounds preserve a comfortable 72dp-equivalent target.
    // The right rail starts immediately after the observation window (x=1088),
    // so neither its visual nor hit rectangles cover enemies or projectiles.
    val crouchVisual = RectF(1100f, 648f, 1176f, 720f)
    val crouchHit = RectF(1096f, 644f, 1180f, 720f)
    val jumpVisual = RectF(1190f, 648f, 1266f, 720f)
    val jumpHit = RectF(1184f, 644f, 1278f, 720f)
    val shootVisual = RectF(1100f, 562f, 1180f, 634f)
    val shootHit = RectF(1096f, 556f, 1184f, 640f)
    val aimVisual = RectF(1190f, 562f, 1266f, 634f)
    val aimHit = RectF(1184f, 556f, 1278f, 640f)
    val bombVisual = RectF(1100f, 474f, 1176f, 546f)
    val bombHit = RectF(1096f, 468f, 1180f, 552f)
    val weaponVisual = RectF(1190f, 474f, 1266f, 546f)
    val weaponHit = RectF(1184f, 468f, 1278f, 552f)
    val pauseHit = RectF(1175f, 24f, 1250f, 94f)
}
