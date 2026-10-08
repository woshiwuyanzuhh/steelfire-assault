package com.steelfire.assault

import android.graphics.RectF

/**
 * Maps logical 1280x720 touch coordinates to actions while preserving pointer ownership.
 * A pointer keeps its action until it is released; a second finger cannot steal a held shot.
 */
class TouchInputMapper(
    private val width: Float = 1280f,
    private val height: Float = 720f
) {
    private enum class Control { MOVE, JUMP, SHOOT, BOMB, WEAPON, CROUCH, AIM, PAUSE }

    private val owners = LinkedHashMap<Int, Control>()
    private val movePositions = HashMap<Int, Float>()
    private var jumpPressed = false
    private var bombPressed = false
    private var weaponNextPressed = false
    private var pausePressed = false

    private val moveBounds = ControlLayout.moveHit
    private val crouchBounds = ControlLayout.crouchHit
    private val jumpBounds = ControlLayout.jumpHit
    private val shootBounds = ControlLayout.shootHit
    private val aimBounds = ControlLayout.aimHit
    private val bombBounds = ControlLayout.bombHit
    private val weaponBounds = ControlLayout.weaponHit
    private val pauseBounds = ControlLayout.pauseHit

    fun onDown(pointerId: Int, x: Float, y: Float): Boolean {
        if (owners.containsKey(pointerId)) return true
        val control = controlAt(x, y) ?: return false
        owners[pointerId] = control
        when (control) {
            Control.MOVE -> movePositions[pointerId] = x
            Control.JUMP -> jumpPressed = true
            Control.BOMB -> bombPressed = true
            Control.WEAPON -> weaponNextPressed = true
            Control.PAUSE -> pausePressed = true
            else -> Unit
        }
        return true
    }

    fun onMove(pointerId: Int, x: Float, y: Float) {
        if (owners[pointerId] == Control.MOVE) movePositions[pointerId] = x
    }

    fun onUp(pointerId: Int) {
        owners.remove(pointerId)
        movePositions.remove(pointerId)
    }

    fun cancel() {
        owners.clear()
        movePositions.clear()
        jumpPressed = false
        bombPressed = false
        weaponNextPressed = false
        pausePressed = false
    }

    fun snapshot(): InputSnapshot {
        val moveOwner = owners.entries.firstOrNull { it.value == Control.MOVE }?.key
        val moveX = moveOwner?.let { movePositions[it] } ?: ControlLayout.moveCenterX
        return InputSnapshot(
            moveAxis = ((moveX - ControlLayout.moveCenterX) / 80f).coerceIn(-1f, 1f),
            jumpPressed = jumpPressed,
            shootHeld = owners.values.any { it == Control.SHOOT },
            bombPressed = bombPressed,
            weaponNextPressed = weaponNextPressed,
            crouchHeld = owners.values.any { it == Control.CROUCH },
            aimHeld = owners.values.any { it == Control.AIM },
            pausePressed = pausePressed,
            ownedPointerIds = owners.keys.toSet()
        )
    }

    fun consumeTransient() {
        jumpPressed = false
        bombPressed = false
        weaponNextPressed = false
        pausePressed = false
    }

    private fun controlAt(x: Float, y: Float): Control? = when {
        pauseBounds.contains(x, y) -> Control.PAUSE
        moveBounds.contains(x, y) -> Control.MOVE
        crouchBounds.contains(x, y) -> Control.CROUCH
        jumpBounds.contains(x, y) -> Control.JUMP
        shootBounds.contains(x, y) -> Control.SHOOT
        aimBounds.contains(x, y) -> Control.AIM
        bombBounds.contains(x, y) -> Control.BOMB
        weaponBounds.contains(x, y) -> Control.WEAPON
        else -> null
    }
}
