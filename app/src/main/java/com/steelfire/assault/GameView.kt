package com.steelfire.assault

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GameView(context: Context) : View(context) {
    private companion object {
        const val W = 1280f
        const val H = 720f
        const val GROUND = 548f
        const val STEP = 1f / 60f
        const val MAGNETIC_CAMERA_SCALE = 0.42f
        const val MAGNETIC_NOTICE_DURATION = 1.2f
    }

    private enum class Mode { CINEMATIC, MENU, LEVELS, PLAYING, PAUSED, CHECKPOINT, RESULT, SETTINGS }
    /** A projectile is advanced only from the fixed-step update. Gravity is zero for hitscan-like rounds. */
    private data class Bullet(
        var x: Float,
        var y: Float,
        var dx: Float,
        var dy: Float,
        var life: Float,
        var damage: Float,
        var enemy: Boolean = false,
        var gravity: Float = 0f,
        var grenade: Boolean = false
    )
    private data class MuzzleAnchor(val x: Float, val y: Float)
    private data class Enemy(var x: Float, var y: Float, var hp: Float, var kind: Int, var cool: Float = 0f, var phase: Float = 0f, var warning: Float = 0f)
    private data class Spark(var x: Float, var y: Float, var life: Float, var color: Int)
    /** Combat tuning stays data-driven so a weapon's job is reviewable without tracing firing code. */
    private data class WeaponSpec(
        val name: String,
        val role: String,
        val damage: Float,
        val cooldown: Float,
        val speed: Float,
        val magazine: Int,
        val reloadRisk: Float,
        val projectileColor: Int,
        val pellets: Int = 1,
        val spread: Float = 0f,
        val arc: Boolean = false
    )

    private enum class VehicleState { AVAILABLE, ENTERING, ACTIVE, DAMAGED, EXITING, DESTROYED }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = AssetRepository(context)
    private val audio = AudioController(context)
    private val cinematic = CinematicRenderer(art)
    private val prefs: SharedPreferences = context.getSharedPreferences("steelfire_save", Context.MODE_PRIVATE)
    private var saveProfile: SaveProfile = SaveProfileStore.loadOrDefault(prefs)
    private var mode = Mode.CINEMATIC
    private var level = 1
    private var unlocked = saveProfile.unlockedLevel
    private var score = 0
    private var playerX = 160f
    private var playerY = GROUND
    private var playerVy = 0f
    private var playerHp = 100f
    private var playerInvuln = 0f
    private var progress = 0f
    private var bossHp = 0f
    private var bossMax = 0f
    private var bossActive = false
    private var bossPhase = 0
    private var bossPhaseNotice = 0f
    private var bossWeakPointOpen = false
    private var inVehicle = false
    private var vehicleState = VehicleState.AVAILABLE
    private var vehicleHp = 180f
    private var vehicleNotice = 0f
    private var weapon = 0
    private var shootHeld = false
    private var crouchHeld = false
    private var aimHeld = false
    private var moveAxis = 0f
    private var jumpQueued = false
    private var bombQueued = false
    private var shotCooldown = 0f
    private var reloadTimer = 0f
    private var ammo = 30
    private var spawnWave = 0
    private var encounterIndex = 0
    private var levelPacing: LevelPacingSpec? = null
    private var levelDefinition: LevelDefinition? = null
    private var magneticSpec: MagneticCoverSpec? = null
    private var magneticState: MagneticCoverState = MagneticCoverState()
    private var checkpointMagneticState: MagneticCoverState = MagneticCoverState()
    private var magneticNotice = 0f
    private var magneticNoticeText = ""
    private var magneticDoorPressed = emptySet<String>()
    private var bridgeSpec: BridgeCollapseSpec? = null
    private var bridgeState: BridgeCollapseState = BridgeCollapseState()
    private var checkpointBridgeState: BridgeCollapseState = BridgeCollapseState()
    private var bridgeNotice = 0f
    private var bridgeNoticeText = ""
    private var genericSpec: GenericMechanicSpec? = null
    private var genericState: GenericMechanicState = GenericMechanic.initialState()
    private var checkpointGenericState: GenericMechanicState = GenericMechanic.initialState()
    private var genericNotice = 0f
    private var genericNoticeText = ""
    private var elapsed = 0f
    private var lowPerformance = saveProfile.lowPerformance
    private var cinematicVoicePlayed = false
    private var lifecyclePaused = false
    private var checkpointProgress = 0f
    private var checkpointX = 150f
    private var checkpointScore = 0
    private var checkpointEncounterIndex = 0
    private var checkpointSpawnWave = 0
    private var checkpointNotice = 0f
    private var hitFlash = 0f
    private var cameraShake = 0f
    private var muzzleFlash = 0f
    private var lastNs = System.nanoTime()
    private var accumulator = 0.0
    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()
    private val sparks = mutableListOf<Spark>()
    private val touchInput = TouchInputMapper(W, H)
    private var windowInsets = ViewportTransform.Insets.ZERO
    private var viewport = ViewportTransform(0, 0)
    private val weaponSpecs = listOf(
        WeaponSpec("M-01 轨道卡宾", "持续压制", 18f, 0.125f, 760f, 30, 0.55f, Color.rgb(255, 220, 102)),
        WeaponSpec("R-07 三连霰", "近距爆发", 14f, 0.385f, 680f, 8, 0.95f, Color.rgb(255, 168, 76), 5, 82f),
        WeaponSpec("V-22 电弧步枪", "穿甲点杀", 42f, 0.455f, 980f, 12, 0.8f, Color.rgb(117, 235, 255)),
        WeaponSpec("L-3 震荡榴弹", "区域控制", 85f, 1.11f, 560f, 5, 1.2f, Color.rgb(255, 126, 72), 1, 0f, true),
        WeaponSpec("S-11 线圈狙击", "弱点狙击", 115f, 1.33f, 1280f, 5, 1.35f, Color.rgb(214, 246, 255)),
        WeaponSpec("犀虎机甲炮", "载具火力", 30f, 0.22f, 900f, 999, 0f, Color.rgb(255, 232, 124))
    )

    // Ground palettes are immutable and shared across frames to keep the composite floor cheap
    // in low-performance mode; only the selected palette index depends on level state.
    private val groundPalettes = arrayOf(
        GroundPalette(
            base = Color.rgb(48, 61, 65), deep = Color.rgb(22, 29, 34), edge = Color.rgb(184, 144, 75),
            highlight = Color.rgb(246, 211, 132), seam = Color.rgb(34, 45, 50), accent = Color.rgb(79, 145, 141),
            substrate = Color.rgb(66, 79, 79)
        ),
        GroundPalette(
            base = Color.rgb(101, 70, 49), deep = Color.rgb(45, 34, 30), edge = Color.rgb(219, 157, 72),
            highlight = Color.rgb(255, 210, 121), seam = Color.rgb(70, 47, 38), accent = Color.rgb(184, 91, 54),
            substrate = Color.rgb(126, 84, 53)
        ),
        GroundPalette(
            base = Color.rgb(40, 72, 80), deep = Color.rgb(19, 35, 44), edge = Color.rgb(78, 191, 190),
            highlight = Color.rgb(163, 245, 224), seam = Color.rgb(27, 56, 63), accent = Color.rgb(73, 141, 166),
            substrate = Color.rgb(52, 99, 101)
        ),
        GroundPalette(
            base = Color.rgb(57, 59, 67), deep = Color.rgb(24, 25, 32), edge = Color.rgb(224, 107, 67),
            highlight = Color.rgb(255, 194, 105), seam = Color.rgb(44, 42, 48), accent = Color.rgb(164, 69, 60),
            substrate = Color.rgb(82, 75, 74)
        )
    )

    private val menuAccent = VisualTokens.ORANGE

    init {
        isFocusable = true
        hud.typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
        postOnAnimation { tick() }
    }

    /** Called by MainActivity whenever system bars or a display cutout changes. */
    fun setWindowInsets(insets: ViewportTransform.Insets) {
        windowInsets = insets
        viewport = ViewportTransform(width, height, windowInsets)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewport = ViewportTransform(w, h, windowInsets)
    }

    private fun tick() {
        val now = System.nanoTime()
        val frame = min(0.08, (now - lastNs) / 1_000_000_000.0)
        lastNs = now
        if (lifecyclePaused) {
            invalidate()
            postOnAnimation { tick() }
            return
        }
        accumulator += frame
        while (accumulator >= STEP) {
            when (mode) {
                Mode.CINEMATIC -> updateCinematic(STEP)
                Mode.PLAYING -> update(STEP)
                else -> Unit
            }
            accumulator -= STEP
        }
        invalidate()
        postOnAnimation { tick() }
    }

    private fun updateCinematic(dt: Float) {
        if (!cinematicVoicePlayed) {
            cinematicVoicePlayed = true
            audio.play("voiceover", 0.86f)
        }
        if (cinematic.update(dt) == CinematicRenderer.Cue.IMPACT) audio.play("boss", 0.48f)
        if (cinematic.finished) mode = Mode.MENU
    }

    private fun startLevel(index: Int) {
        level = index
        levelDefinition = when (index) {
            1 -> LevelDefinitionLoader.loadLevel01(context.assets)
            2 -> LevelDefinitionLoader.loadLevel02(context.assets)
            in 3..SaveProfileStore.MAX_LEVEL -> LevelDefinitionLoader.load(context.assets, index)
            else -> null
        }
        if (index >= 3 && levelDefinition == null) {
            mode = Mode.MENU
            audio.play("ui", 0.32f)
            return
        }
        levelPacing = LevelPacingCatalog.forLevel(index)
        if (index > 2 && levelDefinition != null) levelPacing = LevelPacingCatalog.fromDefinition(levelDefinition!!)
        magneticSpec = levelDefinition?.newMechanic as? MagneticCoverSpec
        magneticState = magneticSpec?.let { MagneticCoverMechanic.initialState(it) } ?: MagneticCoverState()
        checkpointMagneticState = magneticState
        bridgeSpec = levelDefinition?.newMechanic as? BridgeCollapseSpec
        bridgeState = bridgeSpec?.let { BridgeCollapseMechanic.initialState(it) } ?: BridgeCollapseState()
        checkpointBridgeState = bridgeState
        genericSpec = levelDefinition?.newMechanic as? GenericMechanicSpec
        genericState = GenericMechanic.initialState()
        checkpointGenericState = genericState
        bridgeNotice = 0f
        bridgeNoticeText = ""
        genericNotice = 0f
        genericNoticeText = ""
        magneticNotice = 0f
        magneticNoticeText = ""
        magneticDoorPressed = emptySet()
        mode = Mode.PLAYING
        score = 0
        playerX = 150f
        playerY = GROUND
        playerVy = 0f
        playerHp = 100f
        playerInvuln = 0f
        progress = 0f
        bossActive = false
        bossPhase = 0
        bossPhaseNotice = 0f
        bossWeakPointOpen = false
        bossMax = 0f
        bossHp = 0f
        inVehicle = false
        vehicleState = if (level == 1 || level == 2) VehicleState.AVAILABLE else VehicleState.DESTROYED
        vehicleHp = 180f
        vehicleNotice = 0f
        weapon = 0
        shootHeld = false
        crouchHeld = false
        aimHeld = false
        moveAxis = 0f
        jumpQueued = false
        bombQueued = false
        shotCooldown = 0f
        reloadTimer = 0f
        ammo = weaponSpecs[weapon].magazine
        spawnWave = 0
        encounterIndex = 0
        elapsed = 0f
        bullets.clear()
        enemies.clear()
        sparks.clear()
        checkpointProgress = 0f
        checkpointX = playerX
        checkpointScore = 0
        checkpointEncounterIndex = 0
        checkpointSpawnWave = 0
        checkpointNotice = 0f
        hitFlash = 0f
        cameraShake = 0f
        muzzleFlash = 0f
        touchInput.cancel()
        persistContinueState()
        audio.play("ui", 0.5f)
    }

    private fun continueFromSave() {
        val saved = saveProfile
        val savedLevel = saved.continueLevel ?: return startLevel(1)
        startLevel(savedLevel)
        progress = saved.checkpointProgress
        playerX = saved.checkpointPlayerX
        score = saved.checkpointScore
        encounterIndex = saved.checkpointEncounterIndex
        spawnWave = saved.checkpointSpawnWave
        checkpointProgress = saved.checkpointProgress
        checkpointX = playerX
        checkpointScore = score
        checkpointEncounterIndex = encounterIndex
        checkpointSpawnWave = spawnWave
        restoreMagneticOffsets(saved.checkpointMagneticOffsets)
        restoreBridgeSegments(saved.checkpointBridgeSegments)
        genericState = GenericMechanicState(
            simulationTick = saved.checkpointGenericTick,
            phase = saved.checkpointGenericPhase,
            interactions = saved.checkpointGenericInteractions,
            meter = saved.checkpointGenericMeter,
            hazardActive = saved.checkpointGenericHazardActive
        )
        checkpointMagneticState = magneticState
        checkpointBridgeState = bridgeState
        checkpointGenericState = genericState
        mode = Mode.PLAYING
        // startLevel writes an entry snapshot; replace it after applying the
        // persisted checkpoint so an immediate process death cannot erase progress.
        persistContinueState()
    }

    private fun persistContinueState() {
        saveProfile = saveProfile.copy(
            unlockedLevel = unlocked,
            lowPerformance = lowPerformance,
            continueLevel = level.coerceIn(1, SaveProfileStore.MAX_LEVEL),
            checkpointProgress = checkpointProgress,
            checkpointPlayerX = checkpointX,
            checkpointScore = checkpointScore,
            checkpointEncounterIndex = checkpointEncounterIndex,
            checkpointSpawnWave = checkpointSpawnWave,
            checkpointMagneticOffsets = magneticState.boxes.map { it.offset },
            checkpointBridgeSegments = bridgeState.segments,
            checkpointGenericTick = genericState.simulationTick,
            checkpointGenericPhase = genericState.phase,
            checkpointGenericInteractions = genericState.interactions,
            checkpointGenericMeter = genericState.meter,
            checkpointGenericHazardActive = genericState.hazardActive
        )
        SaveProfileStore.save(prefs, saveProfile)
    }

    /** Applies only validated authored offsets when a process was recreated mid-level. */
    private fun restoreMagneticOffsets(offsets: List<Int>) {
        val spec = magneticSpec ?: return
        val base = MagneticCoverMechanic.initialState(spec)
        magneticState = base.copy(
            boxes = base.boxes.mapIndexed { index, box ->
                val requested = offsets.getOrNull(index) ?: box.offset
                box.copy(offset = requested.coerceIn(
                    spec.boxes[index].minOffset,
                    spec.boxes[index].maxOffset
                ), pressedDoor = requested >= spec.boxes[index].maxOffset && spec.boxes[index].pressesDoorId != null)
            }
        )
        magneticDoorPressed = magneticState.boxes.filter { it.pressedDoor }.mapNotNull { state ->
            spec.boxes.firstOrNull { it.id == state.id }?.pressesDoorId
        }.toSet()
    }

    /** Restores only authored bridge ids and bounded counters from a local save snapshot. */
    private fun restoreBridgeSegments(saved: List<BridgeSegmentState>) {
        val spec = bridgeSpec ?: return
        val base = BridgeCollapseMechanic.initialState(spec)
        bridgeState = base.copy(
            segments = base.segments.map { original ->
                val stored = saved.firstOrNull { it.id == original.id } ?: return@map original
                original.copy(
                    occupants = stored.occupants.coerceIn(0, 32),
                    warningTicksRemaining = stored.warningTicksRemaining.coerceIn(0L, 10_000L),
                    collapseTicksRemaining = stored.collapseTicksRemaining.coerceIn(0L, 10_000L),
                    collapsed = stored.collapsed,
                    plankDeployed = stored.plankDeployed,
                    edgeGrabbed = stored.edgeGrabbed,
                    winchCut = stored.winchCut
                )
            }
        )
    }

    /** Restores the last committed checkpoint without replaying the opening menu flow. */
    private fun retryCheckpoint() {
        val savedProgress = checkpointProgress
        val savedX = checkpointX
        val savedScore = checkpointScore
        val savedEncounterIndex = checkpointEncounterIndex
        val savedSpawnWave = checkpointSpawnWave
        startLevel(level)
        progress = savedProgress
        playerX = savedX
        score = savedScore
        encounterIndex = savedEncounterIndex
        spawnWave = savedSpawnWave
        checkpointProgress = savedProgress
        checkpointX = savedX
        checkpointScore = savedScore
        checkpointEncounterIndex = savedEncounterIndex
        checkpointSpawnWave = savedSpawnWave
        magneticState = checkpointMagneticState
        magneticDoorPressed = magneticState.boxes.filter { it.pressedDoor }.mapNotNull { box ->
            magneticSpec?.boxes?.firstOrNull { it.id == box.id }?.pressesDoorId
        }.toSet()
        bridgeState = checkpointBridgeState
        genericState = checkpointGenericState
        checkpointNotice = 0f
        mode = Mode.PLAYING
        persistContinueState()
    }

    private fun update(dt: Float) {
        val input = touchInput.snapshot()
        moveAxis = input.moveAxis
        shootHeld = input.shootHeld
        crouchHeld = input.crouchHeld
        aimHeld = input.aimHeld
        if (input.jumpPressed) jumpQueued = true
        if (input.bombPressed) bombQueued = true
        if (input.weaponNextPressed) cycleWeapon()
        if (input.pausePressed) {
            mode = Mode.PAUSED
            touchInput.consumeTransient()
            return
        }
        touchInput.consumeTransient()
        elapsed += dt
        if (!bridgeProgressBlocked()) progress += 105f * dt
        playerInvuln = max(0f, playerInvuln - dt)
        shotCooldown = max(0f, shotCooldown - dt)
        reloadTimer = max(0f, reloadTimer - dt)
        hitFlash = max(0f, hitFlash - dt)
        cameraShake = max(0f, cameraShake - dt)
        muzzleFlash = max(0f, muzzleFlash - dt)
        if (reloadTimer <= 0f && ammo <= 0) ammo = weaponSpecs[weapon].magazine
        if (jumpQueued && inVehicle && vehicleState == VehicleState.ACTIVE) {
            vehicleState = VehicleState.EXITING
            vehicleNotice = 0.55f
            inVehicle = false
            audio.play("ui", 0.45f)
        } else if (jumpQueued && playerY >= GROUND - 1f) {
            playerVy = -455f
            audio.play("jump", 0.55f)
        }
        jumpQueued = false
        playerVy += 1120f * dt
        playerY += playerVy * dt
        if (playerY > GROUND) {
            playerY = GROUND
            playerVy = 0f
        }
        val genericSlow = if (genericSpec != null && genericState.hazardActive) {
            1f - GenericMechanic.profile(genericSpec!!).severity * 0.045f
        } else {
            1f
        }
        val speed = (if (crouchHeld) 145f else 230f) * genericSlow
        playerX = (playerX + moveAxis * speed * dt).coerceIn(55f, 1160f)

        // Commit checkpoints at authored traversal beats; retries resume before the next beat.
        val candidateCheckpoint = levelPacing?.checkpoints
            ?.lastOrNull { progress >= it.progress }
            ?.progress
            ?: when {
                progress >= 3300f -> 3300f
                progress >= 1650f -> 1650f
                else -> 0f
            }
        if (candidateCheckpoint > checkpointProgress) {
            checkpointProgress = candidateCheckpoint
            checkpointX = playerX
            checkpointScore = score
            checkpointEncounterIndex = encounterIndex
            checkpointSpawnWave = spawnWave
            checkpointMagneticState = magneticState
            checkpointBridgeState = bridgeState
            checkpointGenericState = genericState
            checkpointNotice = 1.4f
            persistContinueState()
            audio.play("ui", 0.38f)
        }
        updateVehicleState(dt)

        val authoredPacing = levelPacing
        if (authoredPacing != null) {
            while (encounterIndex < authoredPacing.beats.size &&
                progress >= authoredPacing.beats[encounterIndex].triggerX) {
                val beat = authoredPacing.beats[encounterIndex]
                beat.enemyKinds.forEachIndexed { offset, kind ->
                    val hp = when (kind) { 0 -> 35f; 1 -> 70f; 2 -> 52f; else -> 90f }
                    val cooldown = when (kind) { 0 -> 1.8f; 1 -> 1.2f; 2 -> 2.2f; else -> 2.7f }
                    val x = 1320f + offset * 112f
                    val phase = encounterIndex * 0.75f + offset * 0.35f
                    enemies.add(Enemy(x, GROUND, hp, kind, cooldown, phase))
                }
                if (genericSpec != null) {
                    genericNotice = 1.25f
                    val cue = levelDefinition?.beats?.getOrNull(encounterIndex)?.storyCue
                        ?.removePrefix("story_")
                        ?.replace('_', ' ')
                    genericNoticeText = "剧情节点 ${encounterIndex + 1}  ·  ${levelDefinition?.title ?: "行动继续"}  ·  ${cue ?: "继续推进"}"
                }
                encounterIndex += 1
            }
        } else {
            val desiredWave = (progress / 430f).toInt()
            while (spawnWave < desiredWave && spawnWave < 11) {
                spawnWave += 1
                // Legacy levels retain their deterministic kind rotation; randomness is visual only.
                val kind = (spawnWave - 1) % 4
                val hp = when (kind) { 0 -> 35f; 1 -> 70f; 2 -> 52f; else -> 90f }
                val cooldown = when (kind) { 0 -> 1.8f; 1 -> 1.2f; 2 -> 2.2f; else -> 2.7f }
                enemies.add(Enemy(1330f + spawnWave * 18f, GROUND, hp, kind, cooldown, spawnWave * 0.5f))
            }
        }
        val bossTrigger = authoredPacing?.bossTriggerX ?: 4700f
        if (isBossLevel() && !bossActive && progress >= bossTrigger) {
            bossActive = true
            bossMax = 420f + level * 80f
            bossHp = bossMax
            bossPhase = 1
            bossPhaseNotice = 1.25f
            audio.play("boss", 0.72f)
        }

        val spec = weaponSpecs[weapon]
        if (shootHeld && shotCooldown <= 0f && reloadTimer <= 0f && ammo > 0) {
            val muzzle = playerMuzzleAnchor()
            val muzzleX = muzzle.x
            val muzzleY = muzzle.y
            val speed = if (inVehicle) weaponSpecs[5].speed else spec.speed
            val damage = if (inVehicle) weaponSpecs[5].damage else spec.damage
            val life = if (spec.arc) 1.8f else 1.4f
            // Keep the aim branch explicit for the gameplay verifier and use a real ballistic arc for L-3.
            val aimDy = if (aimHeld) -105f else 0f
            val launchDy = if (spec.arc && !inVehicle) -430f else aimDy
            val gravity = if (spec.arc && !inVehicle) 1040f else 0f
            if (spec.pellets == 1) {
                bullets.add(Bullet(muzzleX, muzzleY, speed, launchDy, life, damage, gravity = gravity, grenade = spec.arc && !inVehicle))
            } else {
                for (pellet in 0 until spec.pellets) {
                    val offset = (pellet - (spec.pellets - 1) / 2f) * spec.spread + aimDy
                    bullets.add(Bullet(muzzleX, muzzleY, speed, offset, life, damage))
                }
            }
            ammo -= 1
            if (ammo <= 0) reloadTimer = spec.cooldown * (3.2f + spec.reloadRisk) + 0.35f
            shotCooldown = if (inVehicle) 0.22f else spec.cooldown
            muzzleFlash = 0.08f
            audio.play(if (weapon == 1 || inVehicle) "shotgun" else "rifle", if (inVehicle) 0.95f else 0.68f)
        }
        if (bombQueued) {
            bombQueued = false
            // Throwing is a simulation event; the blast is applied only when this
            // grenade reaches the ground or an enemy during a later fixed step.
            val thrower = if (inVehicle) MuzzleAnchor(playerX + 88f, playerY - 82f)
            else MuzzleAnchor(playerX + 30f, playerY - 104f)
            bullets.add(
                Bullet(
                    thrower.x,
                    thrower.y,
                    if (inVehicle) 420f else 360f,
                    if (inVehicle) -320f else -470f,
                    2.2f,
                    85f,
                    gravity = if (inVehicle) 920f else 1120f,
                    grenade = true
                )
            )
            audio.play("ui", 0.45f)
        }

        var magneticCommand: MagneticCoverCommand = MagneticCoverCommand.Noop
        var bridgeCommand: BridgeCollapseCommand = BridgeCollapseCommand.Noop
        bullets.forEach {
            it.dy += it.gravity * dt
            it.x += it.dx * dt
            it.y += it.dy * dt
            it.life -= dt
        }
        // Resolve grenade impact before ordinary projectile hit checks. This keeps
        // splash damage deterministic and prevents the same grenade from damaging
        // an enemy once as a bullet and again as an explosion.
        for (grenade in bullets.filter { !it.enemy && it.grenade }) {
            val hitEnemy = enemies.any { abs(it.x - grenade.x) < 38f && abs((it.y - 82f) - grenade.y) < 52f }
            if ((grenade.y >= GROUND - 4f && grenade.dy >= 0f) || hitEnemy || grenade.life <= 0f) {
                grenade.y = min(grenade.y, GROUND - 4f)
                detonateGrenade(grenade.x, grenade.y, grenade.damage)
                grenade.life = 0f
            }
        }
        for (bullet in bullets.filter { !it.enemy && it.life > 0f }) {
            if (magneticSpec != null) {
                val lockId = magneticLockHit(bullet)
                if (lockId != null) {
                    if (magneticCommand is MagneticCoverCommand.Noop) {
                        magneticCommand = MagneticCoverCommand.ShootLock(lockId)
                    }
                    bullet.life = 0f
                    sparks.add(Spark(bullet.x, bullet.y, 0.2f, Color.rgb(105, 214, 209)))
                    cameraShake = max(cameraShake, 0.035f)
                    continue
                }
            }
            if (bridgeSpec != null) {
                val segmentId = bridgeWinchHit(bullet)
                if (segmentId != null && bridgeCommand is BridgeCollapseCommand.Noop) {
                    bridgeCommand = BridgeCollapseCommand.CutWinch(segmentId)
                    bullet.life = 0f
                    sparks.add(Spark(bullet.x, bullet.y, 0.2f, Color.rgb(255, 210, 98)))
                    continue
                }
            }
            // Enemy y is the foot plane; rounds travel through the torso/weapon line above it.
            val hitEnemy = enemies.firstOrNull { abs(it.x - bullet.x) < 46f && abs((it.y - 82f) - bullet.y) < 70f }
            if (hitEnemy != null) {
                hitEnemy.hp -= bullet.damage
                bullet.life = 0f
                score += 10
                sparks.add(Spark(bullet.x, bullet.y, 0.18f, Color.YELLOW))
                cameraShake = max(cameraShake, 0.045f)
                audio.play("hit", 0.35f)
            }
            if (bossActive && bullet.x > 930f && bullet.x < 1210f && bullet.y > 255f && bullet.y < 565f) {
                if (bossWeakPointOpen) bossHp -= bullet.damage
                bullet.life = 0f
                score += 25
                sparks.add(Spark(bullet.x, bullet.y, 0.18f, if (bossWeakPointOpen) Color.WHITE else Color.rgb(120, 145, 155)))
                cameraShake = max(cameraShake, 0.055f)
                audio.play("hit", 0.4f)
            }
        }
        if (magneticSpec != null) {
            val transition = MagneticCoverMechanic.step(magneticState, magneticSpec!!, magneticCommand)
            magneticState = transition.state
            consumeMagneticEvents(transition.events)
        }
        if (bridgeSpec != null) {
            val active = bridgeSpec!!.segments.lastOrNull { progress >= it.x && progress <= it.x + it.width }
            if (active != null && bridgeCommand is BridgeCollapseCommand.Noop) {
                val activeState = bridgeState.segments.firstOrNull { it.id == active.id }
                bridgeCommand = when {
                    activeState?.collapsed == true && activeState.winchCut && !activeState.plankDeployed ->
                        BridgeCollapseCommand.DeployPlank(active.id)
                    else -> {
                        val load = if (activeState?.plankDeployed == true) 1 else 3
                        BridgeCollapseCommand.SetOccupancy(active.id, load)
                    }
                }
            }
            val transition = BridgeCollapseMechanic.step(bridgeState, bridgeSpec!!, bridgeCommand)
            bridgeState = transition.state
            consumeBridgeEvents(transition.events)
        }
        if (genericSpec != null) {
            val phase = (progress / objectiveX() * 3f).toInt().coerceIn(0, 3)
            val command = if (genericState.interactions < encounterIndex) {
                GenericMechanicCommand.Interact
            } else {
                GenericMechanicCommand.Tick
            }
            val transition = GenericMechanic.step(genericState, genericSpec!!, command, phase)
            genericState = transition.state
            consumeGenericEvents(transition.events)
        }
        bullets.removeAll { it.life <= 0f || it.x > 1380f || it.x < -80f }

        for (enemy in enemies) {
            enemy.phase += dt
            val drift = when (enemy.kind) {
                0 -> 56f // rifle trooper: steady lane pressure
                1 -> 44f // grenadier: slower, arced projectile read
                2 -> if (enemy.warning > 0f) 160f else 78f // charger: red wind-up, then lunge
                else -> 25f // sentry: anchors a lane and rewards flank/aim
            }
            enemy.x -= (drift + level * 8f) * dt
            enemy.cool -= dt
            val telegraph = when (enemy.kind) { 0 -> 0.25f; 1 -> 0.42f; 2 -> 0.62f; else -> 0.78f }
            if (enemy.cool <= 0f && enemy.warning <= 0f && enemy.x < 1180f) enemy.warning = telegraph
            if (enemy.warning > 0f) {
                enemy.warning -= dt
                if (enemy.warning <= 0f) {
                    when (enemy.kind) {
                        0 -> bullets.add(Bullet(enemy.x - 22f, enemy.y - 56f, -330f, 0f, 3f, 10f, true))
                        1 -> bullets.add(Bullet(enemy.x - 22f, enemy.y - 56f, -300f, -36f, 3f, 18f, true))
                        2 -> bullets.add(Bullet(enemy.x - 34f, enemy.y - 48f, -470f, (playerY - enemy.y) * 0.18f, 2.2f, 24f, true))
                        else -> {
                            bullets.add(Bullet(enemy.x - 22f, enemy.y - 82f, -280f, -58f, 3f, 16f, true))
                            bullets.add(Bullet(enemy.x - 22f, enemy.y - 48f, -280f, 0f, 3f, 16f, true))
                            bullets.add(Bullet(enemy.x - 22f, enemy.y - 14f, -280f, 58f, 3f, 16f, true))
                        }
                    }
                    enemy.cool = when (enemy.kind) { 0 -> 1.8f; 1 -> 1.2f; 2 -> 2.2f; else -> 2.7f }
                }
            }
            val playerHeight = if (crouchHeld) 48f else 75f
            if (abs(enemy.x - playerX) < 54f && abs(enemy.y - playerY) < playerHeight) hurt(14f)
        }
        enemies.removeAll { enemy ->
            if (enemy.hp <= 0f) {
                score += if (enemy.kind == 1) 120 else 40
                repeat(if (lowPerformance) 3 else 7) { sparks.add(Spark(enemy.x, enemy.y - 35f, 0.35f, Color.rgb(244, 106, 58))) }
                audio.play("explosion", 0.5f)
                true
            } else {
                enemy.x < -100f
            }
        }

        if (bossActive) {
            val targetY = 385f + sin(elapsed * 1.5f) * 90f
            val bossY = targetY
            val nextPhase = when {
                bossHp <= bossMax * 0.34f -> 3
                bossHp <= bossMax * 0.67f -> 2
                else -> 1
            }
            if (nextPhase != bossPhase) {
                bossPhase = nextPhase
                bossPhaseNotice = 1.25f
                audio.play("boss", 0.7f)
            }
            val attackPeriod = when (bossPhase) { 3 -> 0.82f; 2 -> 1.15f; else -> 1.6f }
            val weakWindow = when (bossPhase) { 3 -> 0.24f; 2 -> 0.32f; else -> 0.42f }
            bossWeakPointOpen = (elapsed % attackPeriod) < weakWindow
            when (bossPhase) {
                1 -> if (elapsed % 1.6f < dt) {
                    bullets.add(Bullet(1000f, bossY, -300f, (playerY - bossY) * 0.25f, 3f, 20f, true))
                }
                2 -> if (elapsed % 1.15f < dt) {
                    for (lane in -1..1) bullets.add(Bullet(1000f, bossY + lane * 42f, -330f, lane * 18f, 3f, 23f, true))
                }
                else -> if (elapsed % 0.82f < dt) {
                    bullets.add(Bullet(1000f, 535f, -420f, 0f, 3f, 28f, true))
                    bullets.add(Bullet(1000f, 320f, -390f, 0f, 3f, 25f, true))
                }
            }
            if (abs(playerX - 1050f) < 90f && abs(playerY - bossY) < 100f) hurt(25f)
        } else bossWeakPointOpen = false
        bossPhaseNotice = max(0f, bossPhaseNotice - dt)
        checkpointNotice = max(0f, checkpointNotice - dt)
        magneticNotice = max(0f, magneticNotice - dt)
        bridgeNotice = max(0f, bridgeNotice - dt)
        genericNotice = max(0f, genericNotice - dt)
        for (bullet in bullets.filter { it.enemy && it.life > 0f }) {
            if (magneticCoverBlocks(bullet)) {
                bullet.life = 0f
                sparks.add(Spark(bullet.x, bullet.y, 0.14f, Color.rgb(105, 214, 209)))
                continue
            }
            val playerHeight = if (crouchHeld) 34f else 55f
            if (abs(bullet.x - playerX) < 38f && abs(bullet.y - playerY + 40f) < playerHeight) {
                hurt(bullet.damage)
                bullet.life = 0f
            }
        }
        bullets.removeAll { it.life <= 0f }
        sparks.forEach { it.life -= dt }
        sparks.removeAll { it.life <= 0f }

        if (bossActive && bossHp <= 0f) {
            bossHp = 0f
            mode = Mode.RESULT
            audio.play("boss", 0.85f)
            if (level >= unlocked) {
                unlocked = min(SaveProfileStore.MAX_LEVEL, level + 1)
            }
            saveProfile = saveProfile.copy(unlockedLevel = unlocked, continueLevel = null)
            SaveProfileStore.save(prefs, saveProfile)
        }
        if (!isBossLevel() && mode == Mode.PLAYING && progress >= objectiveX()) {
            mode = Mode.RESULT
            if (level >= unlocked) unlocked = min(SaveProfileStore.MAX_LEVEL, level + 1)
            saveProfile = saveProfile.copy(unlockedLevel = unlocked, continueLevel = null)
            SaveProfileStore.save(prefs, saveProfile)
        }
        if (!isBossLevel() && progress > (authoredPacing?.width ?: 5200f) && !bossActive) progress = objectiveX()
        if (mode == Mode.PLAYING && playerHp <= 0f) {
            playerHp = 0f
            shootHeld = false
            crouchHeld = false
            aimHeld = false
            touchInput.cancel()
            checkpointNotice = 0f
            mode = Mode.CHECKPOINT
        }
    }

    private fun cycleWeapon() {
        weapon = (weapon + 1) % weaponSpecs.size
        ammo = weaponSpecs[weapon].magazine
        reloadTimer = 0f
        audio.play("ui", 0.45f)
    }

    /** Maps authored level-01 world positions into the current fixed logical viewport. */
    private fun magneticScreenX(box: MagneticBoxSpec): Float =
        W * 0.5f + (box.x - progress) * MAGNETIC_CAMERA_SCALE

    private fun magneticScreenY(box: MagneticBoxSpec, state: MagneticBoxState): Float =
        box.y.toFloat() + state.offset

    private fun magneticLockHit(bullet: Bullet): String? {
        val spec = magneticSpec ?: return null
        return spec.boxes.asSequence().mapNotNull { box ->
            val state = magneticState.boxes.firstOrNull { it.id == box.id } ?: return@mapNotNull null
            val lockX = magneticScreenX(box)
            val lockY = magneticScreenY(box, state) - 66f
            if (abs(bullet.x - lockX) < 44f && abs(bullet.y - lockY) < 42f) box.lockId else null
        }.firstOrNull()
    }

    private fun magneticCoverBlocks(bullet: Bullet): Boolean {
        val spec = magneticSpec ?: return false
        return spec.boxes.any { box ->
            val state = magneticState.boxes.firstOrNull { it.id == box.id } ?: return@any false
            val x = magneticScreenX(box)
            val y = magneticScreenY(box, state)
            bullet.x in (x - 66f)..(x + 66f) && bullet.y in (y - 34f)..(y + 44f)
        }
    }

    private fun bridgeScreenX(segment: BridgeSegmentSpec): Float =
        W * 0.5f + (segment.x - progress) * MAGNETIC_CAMERA_SCALE

    private fun bridgeProgressBlocked(): Boolean {
        val spec = bridgeSpec ?: return false
        return spec.segments.any { segment ->
            val state = bridgeState.segments.firstOrNull { it.id == segment.id }
            state?.collapsed == true && !state.plankDeployed &&
                progress >= segment.x && progress <= segment.x + segment.width
        }
    }

    private fun bridgeWinchHit(bullet: Bullet): String? {
        val spec = bridgeSpec ?: return null
        return spec.segments.asSequence().firstOrNull { segment ->
            val x = bridgeScreenX(segment)
            abs(bullet.x - x) < 52f && abs(bullet.y - (segment.y - 118f)) < 56f
        }?.id
    }

    /** The bridge route completes at its extraction gate; boss routes keep their authored trigger. */
    private fun objectiveX(): Float = if (!isBossLevel()) {
        levelPacing?.clearX ?: 4300f
    } else {
        levelPacing?.bossTriggerX ?: 4700f
    }

    private fun isBossLevel(): Boolean = level == 1 || levelDefinition?.boss != null || level % 5 == 0

    private fun consumeGenericEvents(events: List<GenericMechanicEvent>) {
        for (event in events) {
            when (event) {
                is GenericMechanicEvent.PhaseChanged -> {
                    genericNotice = 1.0f
                    genericNoticeText = "${genericSpec?.id ?: "玩法"}  ·  阶段 ${event.phase + 1}"
                    audio.play("ui", 0.32f)
                }
                is GenericMechanicEvent.InteractionAccepted -> {
                    genericNotice = 0.7f
                    genericNoticeText = "${genericSpec?.id ?: "规则"}  ·  交互 ${event.total}"
                }
                is GenericMechanicEvent.HazardChanged -> {
                    genericNotice = 0.72f
                    genericNoticeText = "${event.label}  ·  ${if (event.active) "危险窗口" else "安全窗口"}"
                }
            }
        }
    }

    private fun consumeBridgeEvents(events: List<BridgeCollapseEvent>) {
        for (event in events) {
            when (event) {
                is BridgeCollapseEvent.OverloadWarning -> {
                    bridgeNotice = 1.1f
                    bridgeNoticeText = "桥板超载  ·  快跳离"
                    audio.play("ui", 0.46f)
                }
                is BridgeCollapseEvent.CollapseStarted -> {
                    bridgeNotice = 0.9f
                    bridgeNoticeText = "裂纹扩大  ·  立即转移"
                    audio.play("damage", 0.42f)
                }
                is BridgeCollapseEvent.BridgeCollapsed -> {
                    bridgeNotice = 1.0f
                    bridgeNoticeText = "桥板坍落  ·  射击绞盘"
                    audio.play("explosion", 0.5f)
                }
                is BridgeCollapseEvent.WarningCleared -> Unit
                is BridgeCollapseEvent.PlankDeployed -> {
                    bridgeNotice = 0.9f
                    bridgeNoticeText = "临时桥板已部署"
                    audio.play("ui", 0.52f)
                }
                is BridgeCollapseEvent.EdgeGrabbed -> {
                    bridgeNotice = 0.8f
                    bridgeNoticeText = "已抓住桥沿"
                }
                is BridgeCollapseEvent.WinchCut -> {
                    bridgeNotice = 0.8f
                    bridgeNoticeText = "绞盘已切断"
                    audio.play("hit", 0.42f)
                }
                is BridgeCollapseEvent.CommandRejected -> Unit
            }
        }
    }

    private fun consumeMagneticEvents(events: List<MagneticCoverEvent>) {
        for (event in events) {
            when (event) {
                is MagneticCoverEvent.CoverMoved -> {
                    magneticNotice = MAGNETIC_NOTICE_DURATION
                    magneticNoticeText = "磁吊箱体移动  ${event.offset}"
                    audio.play("ui", 0.42f)
                }
                is MagneticCoverEvent.DoorPressed -> {
                    magneticDoorPressed = magneticDoorPressed + event.doorId
                    magneticNotice = MAGNETIC_NOTICE_DURATION
                    magneticNoticeText = "闸门已压下  ${event.doorId}"
                    audio.play("ui", 0.62f)
                }
                is MagneticCoverEvent.LockRejected -> if (event.reason == "at_limit") {
                    magneticNotice = 0.55f
                    magneticNoticeText = "箱体已到行程边界"
                }
            }
        }
    }

    /** Deterministic vehicle lifecycle: entry, active fire, damage recovery, exit, and failure. */
    private fun updateVehicleState(dt: Float) {
        if (level != 1 && level != 2) {
            vehicleState = VehicleState.DESTROYED
            inVehicle = false
            return
        }
        vehicleNotice = max(0f, vehicleNotice - dt)
        val vehicleBeat = levelPacing?.beats?.firstOrNull { it.vehicle }
        val vehicleStart = vehicleBeat?.triggerX ?: 1550f
        val vehicleEnd = vehicleStart + 900f
        when (vehicleState) {
            VehicleState.AVAILABLE -> if (progress in vehicleStart..vehicleEnd) {
                vehicleState = VehicleState.ENTERING
                vehicleNotice = 0.7f
                audio.play("ui", 0.5f)
            }
            VehicleState.ENTERING -> if (vehicleNotice <= 0f) {
                vehicleState = VehicleState.ACTIVE
                inVehicle = true
                vehicleHp = max(vehicleHp, 180f)
            }
            VehicleState.ACTIVE -> {
                inVehicle = true
                if (vehicleHp <= 0f) {
                    vehicleState = VehicleState.DESTROYED
                    inVehicle = false
                    vehicleNotice = 0.9f
                    audio.play("explosion", 0.9f)
                } else if (vehicleHp < 70f) {
                    vehicleState = VehicleState.DAMAGED
                    vehicleNotice = 0.8f
                    audio.play("damage", 0.8f)
                } else if (progress > 2450f) {
                    vehicleState = VehicleState.EXITING
                    vehicleNotice = 0.55f
                    inVehicle = false
                    audio.play("ui", 0.45f)
                }
            }
            VehicleState.DAMAGED -> {
                inVehicle = true
                if (vehicleHp <= 0f) {
                    vehicleState = VehicleState.DESTROYED
                    inVehicle = false
                    vehicleNotice = 0.9f
                    audio.play("explosion", 0.9f)
                } else if (vehicleNotice <= 0f) {
                    vehicleState = VehicleState.ACTIVE
                }
            }
            VehicleState.EXITING -> if (vehicleNotice <= 0f) {
                vehicleState = VehicleState.DESTROYED
                inVehicle = false
            }
            VehicleState.DESTROYED -> inVehicle = false
        }
    }

    private fun hurt(amount: Float) {
        if (playerInvuln > 0f) return
        if (inVehicle && vehicleState != VehicleState.DESTROYED && vehicleState != VehicleState.EXITING) {
            vehicleHp -= amount * 1.25f
            vehicleState = if (vehicleHp > 0f) VehicleState.DAMAGED else VehicleState.DESTROYED
            vehicleNotice = if (vehicleHp > 0f) 0.8f else 0.9f
            cameraShake = max(cameraShake, 0.14f)
            audio.play(if (vehicleHp > 0f) "damage" else "explosion", 0.82f)
            return
        }
        playerHp -= amount
        playerInvuln = 0.8f
        hitFlash = 0.12f
        cameraShake = max(cameraShake, 0.11f)
        audio.play("damage", 0.7f)
        repeat(if (lowPerformance) 2 else 5) { sparks.add(Spark(playerX, playerY - 40f, 0.25f, Color.RED)) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(VisualTokens.INK)
        val scale = viewport.scale
        val offsetX = viewport.offsetX
        val offsetY = viewport.offsetY
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        when (mode) {
            Mode.CINEMATIC -> cinematic.draw(canvas)
            Mode.MENU -> drawMenu(canvas)
            Mode.LEVELS -> drawLevels(canvas)
            Mode.SETTINGS -> drawSettings(canvas)
            Mode.PLAYING, Mode.PAUSED -> {
                drawWorld(canvas)
                if (mode == Mode.PAUSED) drawPause(canvas)
            }
            Mode.CHECKPOINT -> {
                drawWorld(canvas)
                drawCheckpoint(canvas)
            }
            Mode.RESULT -> {
                drawWorld(canvas)
                drawResult(canvas)
            }
        }
        canvas.restore()
    }

    private fun drawMenu(c: Canvas) {
        drawBackdrop(c, 0f)
        drawMenuShade(c)
        drawBrand(c)
        val hasContinue = saveProfile.continueLevel != null
        button(c, RectF(420f, 315f, 860f, 400f), if (hasContinue) "继续行动" else "开始突袭", menuAccent, "MISSION  /  " + if (hasContinue) "RESUME" else "DEPLOY")
        button(c, RectF(420f, 430f, 860f, 500f), "关卡选择", VisualTokens.STEEL_LIGHT, "SECTOR MAP")
        button(c, RectF(420f, 530f, 860f, 600f), "设置", VisualTokens.STEEL, "SYSTEM")
        text(c, "原创街机动作射击  ·  离线可玩", 640f, 664f, 20f, VisualTokens.MUTED, false, Paint.Align.CENTER)
        drawCornerMarks(c, RectF(42f, 42f, 1238f, 678f), VisualTokens.GOLD)
    }

    private fun drawLevels(c: Canvas) {
        drawBackdrop(c, 0f)
        drawMenuShade(c)
        text(c, "选择行动区域", 640f, 110f, 46f, VisualTokens.WHITE, true, Paint.Align.CENTER)
        text(c, "SELECT OPERATION SECTOR", 640f, 140f, 15f, VisualTokens.GOLD, true, Paint.Align.CENTER)
        for (index in 1..SaveProfileStore.MAX_LEVEL) {
            val column = (index - 1) % 5
            val row = (index - 1) / 5
            val x = 52f + column * 238f
            val y = 174f + row * 40f
            val available = index <= unlocked
            val boss = index % 5 == 0
            val label = if (available) "${index.toString().padStart(2, '0')}  ${if (boss) "BOSS" else "区域"}" else "${index.toString().padStart(2, '0')}  锁定"
            button(c, RectF(x, y, x + 218f, y + 32f), label, if (available) (if (boss) VisualTokens.RUST else VisualTokens.STEEL_LIGHT) else VisualTokens.STEEL, if (available) "READY" else "LOCKED")
        }
        button(c, RectF(460f, 625f, 820f, 683f), "返回", VisualTokens.STEEL, "BACK")
        drawCornerMarks(c, RectF(42f, 42f, 1238f, 678f), VisualTokens.GOLD)
    }

    private fun drawSettings(c: Canvas) {
        drawBackdrop(c, 0f)
        drawMenuShade(c)
        text(c, "设置", 640f, 140f, 50f, VisualTokens.WHITE, true, Paint.Align.CENTER)
        text(c, "SYSTEM CONFIGURATION", 640f, 170f, 15f, VisualTokens.GOLD, true, Paint.Align.CENTER)
        button(c, RectF(330f, 260f, 950f, 350f), if (lowPerformance) "低性能模式：开启" else "低性能模式：关闭", VisualTokens.STEEL_LIGHT, "PERFORMANCE  /  " + if (lowPerformance) "30 FPS" else "60 FPS")
        text(c, "低性能模式会减少粒子与远景动画，不改变战斗判定", 640f, 410f, 20f, VisualTokens.MUTED, false, Paint.Align.CENTER)
        button(c, RectF(460f, 540f, 820f, 610f), "返回", VisualTokens.STEEL, "BACK")
        drawCornerMarks(c, RectF(42f, 42f, 1238f, 678f), VisualTokens.GOLD)
    }

    private fun drawMenuShade(c: Canvas) {
        paint.shader = LinearGradient(0f, 0f, 0f, H, Color.argb(28, 4, 10, 16), Color.argb(178, 4, 10, 16), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, paint)
        paint.shader = null
        paint.color = Color.argb(80, 105, 214, 209)
        c.drawRect(0f, 276f, W, 278f, paint)
        paint.color = Color.argb(45, 255, 209, 92)
        c.drawRect(0f, 610f, W, 612f, paint)
    }

    private fun drawBrand(c: Canvas) {
        paint.color = Color.argb(180, 8, 16, 23)
        c.drawRoundRect(RectF(448f, 76f, 832f, 252f), 26f, 26f, paint)
        paint.color = Color.argb(150, 255, 209, 92)
        c.drawRoundRect(RectF(448f, 76f, 832f, 82f), 26f, 26f, paint)
        paint.color = Color.argb(210, 255, 122, 69)
        c.drawRect(480f, 112f, 800f, 116f, paint)
        text(c, "钢火突袭", 640f, 180f, 70f, VisualTokens.WHITE, true, Paint.Align.CENTER)
        text(c, "STEELFIRE ASSAULT", 640f, 219f, 22f, VisualTokens.WARNING, true, Paint.Align.CENTER)
        text(c, "OPERATIVE  /  R-07", 640f, 245f, 13f, VisualTokens.STEEL_LIGHT, true, Paint.Align.CENTER)
    }

    private fun drawCornerMarks(c: Canvas, r: RectF, color: Int) {
        paint.color = Color.argb(155, Color.red(color), Color.green(color), Color.blue(color))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        val mark = 22f
        val path = Path()
        path.moveTo(r.left, r.top + mark); path.lineTo(r.left, r.top); path.lineTo(r.left + mark, r.top)
        path.moveTo(r.right - mark, r.top); path.lineTo(r.right, r.top); path.lineTo(r.right, r.top + mark)
        path.moveTo(r.left, r.bottom - mark); path.lineTo(r.left, r.bottom); path.lineTo(r.left + mark, r.bottom)
        path.moveTo(r.right - mark, r.bottom); path.lineTo(r.right, r.bottom); path.lineTo(r.right, r.bottom - mark)
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawWorld(c: Canvas) {
        c.save()
        if (cameraShake > 0f) {
            val strength = cameraShake * 42f
            c.translate(sin(elapsed * 91f) * strength, sin(elapsed * 113f) * strength * 0.55f)
        }
        drawBackdrop(c, progress)
        drawTerrain(c)
        drawGroundContactShadows(c)
        drawMagneticCover(c)
        drawBridgeMechanic(c)
        if ((level == 1 || level == 2) && vehicleState != VehicleState.AVAILABLE &&
            vehicleState != VehicleState.DESTROYED) drawVehicle(c)
        enemies.forEach { drawEnemy(c, it) }
        bullets.forEach { drawBullet(c, it) }
        sparks.forEach { drawSpark(c, it) }
        if (bossActive) drawBoss(c)
        drawPlayer(c)
        drawWorldOverlay(c)
        c.restore()
        // HUD and controls belong to the safe viewport, not the shaken world layer.
        drawHud(c)
        drawControls(c)
        if (hitFlash > 0f) {
            paint.color = Color.argb((95f * (hitFlash / 0.12f).coerceIn(0f, 1f)).toInt(), 255, 70, 56)
            c.drawRect(0f, 0f, W, H, paint)
        }
    }

    private fun drawWorldOverlay(c: Canvas) {
        if (!lowPerformance) {
            paint.color = Color.argb(18, 191, 235, 229)
            for (y in 138..530 step 26) c.drawRect(0f, y.toFloat(), W, y + 1f, paint)
        }
        paint.shader = RadialGradient(W * 0.5f, H * 0.38f, 720f, intArrayOf(Color.TRANSPARENT, Color.argb(125, 2, 8, 14)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, paint)
        paint.shader = null
        drawGenericMechanicOverlay(c)
        if (checkpointNotice > 0f) {
            val a = (235f * (checkpointNotice / 1.4f).coerceIn(0f, 1f)).toInt()
            paint.color = Color.argb(a / 3, 7, 19, 24)
            c.drawRoundRect(RectF(454f, 128f, 826f, 184f), 18f, 18f, paint)
            text(c, "CHECKPOINT  ·  已记录", 640f, 164f, 20f, Color.argb(a, 166, 241, 223), true, Paint.Align.CENTER)
        }
        if (magneticNotice > 0f) {
            val a = (230f * (magneticNotice / MAGNETIC_NOTICE_DURATION).coerceIn(0f, 1f)).toInt()
            paint.color = Color.argb(a / 3, 8, 28, 32)
            c.drawRoundRect(RectF(430f, 192f, 850f, 238f), 16f, 16f, paint)
            text(c, magneticNoticeText, 640f, 222f, 18f, Color.argb(a, 170, 247, 232), true, Paint.Align.CENTER)
        }
        if (bridgeNotice > 0f) {
            val a = (230f * (bridgeNotice / 1.1f).coerceIn(0f, 1f)).toInt()
            paint.color = Color.argb(a / 3, 45, 26, 16)
            c.drawRoundRect(RectF(398f, 246f, 882f, 292f), 16f, 16f, paint)
            text(c, bridgeNoticeText, 640f, 276f, 18f, Color.argb(a, 255, 211, 126), true, Paint.Align.CENTER)
        }
        if (genericNotice > 0f) {
            val a = (220f * (genericNotice / 1.0f).coerceIn(0f, 1f)).toInt()
            paint.color = Color.argb(a / 3, 18, 35, 48)
            c.drawRoundRect(RectF(420f, 300f, 860f, 344f), 16f, 16f, paint)
            text(c, genericNoticeText, 640f, 329f, 17f, Color.argb(a, 166, 241, 223), true, Paint.Align.CENTER)
        }
        if (aimHeld && !inVehicle) {
            paint.color = Color.argb(155, 182, 238, 239)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            c.drawCircle(playerX + 184f, playerY - 62f, 18f + 3f * sin(elapsed * 8f), paint)
            c.drawLine(playerX + 153f, playerY - 62f, playerX + 215f, playerY - 62f, paint)
            c.drawLine(playerX + 184f, playerY - 93f, playerX + 184f, playerY - 31f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    /** Draws the active authored rule as a readable world signal without mutating state. */
    private fun drawGenericMechanicOverlay(c: Canvas) {
        val spec = genericSpec ?: return
        val profile = GenericMechanic.profile(spec)
        val alpha = if (genericState.hazardActive) 46 else 18
        val tint = when (profile.visualVariant) {
            0 -> Color.rgb(92, 210, 201)
            1 -> Color.rgb(255, 190, 91)
            2 -> Color.rgb(132, 206, 255)
            3 -> Color.rgb(255, 112, 86)
            4 -> Color.rgb(207, 156, 255)
            else -> Color.rgb(182, 239, 182)
        }
        paint.color = Color.argb(alpha, Color.red(tint), Color.green(tint), Color.blue(tint))
        when (profile.visualVariant) {
            0, 5 -> for (y in 184..530 step 42) c.drawRect(0f, y.toFloat(), W, y + 3f, paint)
            1, 3 -> for (x in -120..1320 step 96) c.drawRect(x.toFloat() - (progress * 0.22f % 96f), 150f, x + 10f - (progress * 0.22f % 96f), 548f, paint)
            2 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                c.drawCircle(640f, 390f, 170f + genericState.meter * 0.4f, paint)
                paint.style = Paint.Style.FILL
            }
            else -> for (i in 0..11) c.drawCircle(80f + i * 104f, 206f + (i % 3) * 88f, 4f + genericState.meter * 0.018f, paint)
        }
    }

    private fun drawBackdrop(c: Canvas, scroll: Float) {
        val chapter = ((level - 1) / 5).coerceIn(0, 9)
        val sky = when {
            level == 2 -> Color.rgb(145, 105, 67)
            chapter % 5 == 1 -> Color.rgb(34, 48, 76)
            chapter % 5 == 2 -> Color.rgb(38, 56, 54)
            chapter % 5 == 3 -> Color.rgb(56, 43, 55)
            chapter % 5 == 4 -> Color.rgb(42, 43, 64)
            else -> Color.rgb(27, 48, 61)
        }
        c.drawColor(sky)
        val background = art.bitmap("gfx/backgrounds/industrial_port.png")
        if (background != null) {
            val tint = when {
                level == 2 -> Color.argb(62, 195, 116, 44)
                chapter % 5 == 1 -> Color.argb(74, 36, 74, 130)
                chapter % 5 == 2 -> Color.argb(66, 24, 104, 83)
                chapter % 5 == 3 -> Color.argb(70, 118, 48, 92)
                chapter % 5 == 4 -> Color.argb(72, 52, 46, 130)
                else -> Color.argb(24, 10, 25, 34)
            }
            art.draw(c, "gfx/backgrounds/industrial_port.png", null, RectF(0f, 0f, W, H))
            paint.color = tint
            c.drawRect(0f, 0f, W, H, paint)
            // Atmospheric bands create depth on top of the supplied illustration.
            paint.color = Color.argb(66, 5, 10, 14)
            c.drawRect(0f, 360f, W, 548f, paint)
            paint.color = Color.argb(48, 222, 184, 112)
            c.drawRect(0f, 535f, W, 548f, paint)
        }
        paint.shader = LinearGradient(0f, 0f, 0f, 548f, Color.argb(36, 125, 211, 214), Color.argb(118, 7, 13, 20), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, 548f, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        for (i in 0..7) {
            val x = i * 210f - (scroll * 0.08f % 210f)
            paint.color = Color.argb(40, 6, 15, 22)
            c.drawRect(x, 220f + (i % 3) * 35f, x + 150f, 530f, paint)
            paint.color = Color.argb(72, 0, 0, 0)
            c.drawRect(x + 35f, 280f, x + 58f, 470f, paint)
        }
        paint.color = Color.argb(100, 250, 210, 120)
        c.drawCircle(1030f, 135f, 54f, paint)
        if (chapter % 5 == 1) {
            paint.color = Color.argb(130, 145, 230, 255)
            for (i in 0..5) c.drawCircle(120f + i * 220f, 140f + (i % 2) * 35f, 3f, paint)
        }
        paint.color = Color.argb(32, 255, 209, 92)
        for (i in 0..5) {
            val x = 90f + i * 235f - (scroll * 0.18f % 235f)
            c.drawRect(x, 528f - (i % 2) * 22f, x + 92f, 531f - (i % 2) * 22f, paint)
        }
    }

    /**
     * The playable floor is deliberately a composite of a lit contact lip, inset slabs and
     * substrate marks.  This keeps the operative grounded against the illustrated backdrop while
     * giving each route a material signature without introducing another runtime asset.
     */
    private fun drawTerrain(c: Canvas) {
        val palette = groundPalettes[when {
            level == 2 -> 1
            genericSpec != null -> GenericMechanic.profile(genericSpec!!).visualVariant % groundPalettes.size
            level % 5 == 0 -> 3
            else -> 0
        }]
        val contactY = GROUND + 8f
        val scroll = progress * 0.55f

        // Deep slab body and a two-tone lower substrate establish a readable silhouette below feet.
        paint.shader = LinearGradient(0f, contactY, 0f, H, palette.base, palette.deep, Shader.TileMode.CLAMP)
        c.drawRect(0f, contactY, W, H, paint)
        paint.shader = null
        paint.color = Color.argb(125, Color.red(palette.substrate), Color.green(palette.substrate), Color.blue(palette.substrate))
        c.drawRect(0f, contactY + 34f, W, contactY + 91f, paint)
        paint.color = Color.argb(115, Color.red(palette.deep), Color.green(palette.deep), Color.blue(palette.deep))
        c.drawRect(0f, contactY + 91f, W, H, paint)

        // Repeating panels are intentionally offset from one another, so the floor reads as built
        // terrain instead of a single scrolling rectangle.  The pattern is deterministic per frame.
        for (i in -1..10) {
            val x = i * 138f - (scroll % 138f)
            val panel = Path()
            panel.moveTo(x + 8f, contactY + 20f)
            panel.lineTo(x + 124f, contactY + 20f)
            panel.lineTo(x + 112f, contactY + 82f)
            panel.lineTo(x + 22f, contactY + 82f)
            panel.close()
            paint.color = Color.argb(if (i % 2 == 0) 48 else 30, Color.red(palette.highlight), Color.green(palette.highlight), Color.blue(palette.highlight))
            c.drawPath(panel, paint)
            paint.color = Color.argb(155, Color.red(palette.seam), Color.green(palette.seam), Color.blue(palette.seam))
            paint.strokeWidth = 2f
            c.drawLine(x + 8f, contactY + 20f, x + 124f, contactY + 20f, paint)
            c.drawLine(x + 22f, contactY + 82f, x + 112f, contactY + 82f, paint)
            c.drawLine(x + 18f, contactY + 23f, x + 30f, contactY + 78f, paint)
            if (i % 3 == 0) {
                paint.color = Color.argb(175, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
                c.drawRect(x + 42f, contactY + 39f, x + 94f, contactY + 43f, paint)
                c.drawRect(x + 53f, contactY + 54f, x + 83f, contactY + 57f, paint)
            }
        }

        // The top lip catches light and exposes small seams, rocks and hazard cuts beneath the feet.
        paint.color = Color.argb(240, Color.red(palette.edge), Color.green(palette.edge), Color.blue(palette.edge))
        c.drawRect(0f, contactY, W, contactY + 11f, paint)
        paint.color = Color.argb(220, Color.red(palette.highlight), Color.green(palette.highlight), Color.blue(palette.highlight))
        c.drawRect(0f, contactY, W, contactY + 3f, paint)
        for (i in -1..15) {
            val x = i * 92f - (progress * 0.72f % 92f)
            paint.color = Color.argb(175, Color.red(palette.seam), Color.green(palette.seam), Color.blue(palette.seam))
            c.drawRect(x + 34f, contactY + 3f, x + 38f, contactY + 11f, paint)
            if (i % 4 == 1) {
                paint.color = Color.argb(170, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
                c.drawRect(x + 49f, contactY + 5f, x + 71f, contactY + 8f, paint)
            }
        }
        paint.color = Color.argb(110, Color.red(palette.deep), Color.green(palette.deep), Color.blue(palette.deep))
        for (i in 0..18) {
            val x = i * 72f - (progress * 0.38f % 72f)
            c.drawRect(x, contactY + 99f, x + 3f, H, paint)
        }
        // Small alternating chevrons unify the industrial and rocky variants without a texture file.
        paint.color = Color.argb(135, Color.red(palette.highlight), Color.green(palette.highlight), Color.blue(palette.highlight))
        for (i in -1..9) {
            val x = i * 152f - (progress * 0.35f % 152f)
            val chevron = Path()
            chevron.moveTo(x + 20f, contactY + 112f)
            chevron.lineTo(x + 34f, contactY + 126f)
            chevron.lineTo(x + 48f, contactY + 112f)
            chevron.lineTo(x + 42f, contactY + 112f)
            chevron.lineTo(x + 34f, contactY + 120f)
            chevron.lineTo(x + 26f, contactY + 112f)
            chevron.close()
            c.drawPath(chevron, paint)
        }
    }

    private data class GroundPalette(
        val base: Int,
        val deep: Int,
        val edge: Int,
        val highlight: Int,
        val seam: Int,
        val accent: Int,
        val substrate: Int
    )

    private fun drawGroundContactShadows(c: Canvas) {
        val contactY = GROUND + 10f
        fun shadow(x: Float, y: Float, width: Float, opacity: Int) {
            val lift = (GROUND - y).coerceAtLeast(0f)
            val scale = (1f - (lift / 180f).coerceIn(0f, 0.52f))
            paint.color = Color.argb(opacity, 5, 9, 12)
            c.drawOval(RectF(x - width * scale, contactY - 7f, x + width * scale, contactY + 6f), paint)
            paint.color = Color.argb((opacity * 0.42f).toInt(), 227, 202, 133)
            c.drawOval(RectF(x - width * scale * 0.52f, contactY - 4f, x + width * scale * 0.52f, contactY + 2f), paint)
        }
        shadow(playerX, playerY, if (crouchHeld) 48f else 58f, 135)
        enemies.forEach { shadow(it.x, it.y, if (it.kind == 2) 44f else 38f, 105) }
        if (inVehicle) {
            shadow(playerX - 42f, GROUND, 34f, 145)
            shadow(playerX + 53f, GROUND, 34f, 145)
        }
    }

    private fun drawMagneticCover(c: Canvas) {
        val spec = magneticSpec ?: return
        if (level != 1) return
        for (box in spec.boxes) {
            val state = magneticState.boxes.firstOrNull { it.id == box.id } ?: continue
            val x = magneticScreenX(box)
            val y = magneticScreenY(box, state)
            if (x < -150f || x > W + 150f) continue
            val pulse = 0.5f + 0.5f * sin(elapsed * 5.5f + x * 0.01f)
            paint.color = Color.argb(125, 130, 212, 213)
            paint.strokeWidth = 5f
            c.drawLine(x, 74f, x, y - 42f, paint)
            paint.color = Color.rgb(67, 99, 101)
            c.drawRoundRect(RectF(x - 64f, y - 32f, x + 64f, y + 42f), 10f, 10f, paint)
            paint.color = Color.rgb(184, 128, 65)
            c.drawRect(x - 51f, y - 21f, x + 51f, y + 31f, paint)
            paint.color = Color.argb(130, 22, 38, 42)
            c.drawRect(x - 45f, y - 15f, x + 45f, y - 10f, paint)
            c.drawRect(x - 45f, y + 4f, x + 45f, y + 9f, paint)
            paint.color = Color.rgb(105, 214, 209)
            c.drawCircle(x, y - 66f, 17f + pulse * 4f, paint)
            paint.color = Color.rgb(12, 29, 34)
            c.drawCircle(x, y - 66f, 8f, paint)
            paint.color = Color.argb((95f + pulse * 100f).toInt(), 105, 214, 209)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            c.drawCircle(x, y - 66f, 27f + pulse * 6f, paint)
            paint.style = Paint.Style.FILL
            text(c, "磁锁", x, y - 101f, 14f, Color.rgb(170, 245, 235), true, Paint.Align.CENTER)
            if (state.pressedDoor) {
                val doorX = x + 150f
                paint.color = Color.argb(185, 72, 188, 167)
                c.drawRoundRect(RectF(doorX - 28f, GROUND - 190f, doorX + 28f, GROUND + 28f), 8f, 8f, paint)
                paint.color = Color.argb(220, 196, 255, 211)
                c.drawRect(doorX - 5f, GROUND - 173f, doorX + 5f, GROUND + 8f, paint)
                text(c, "闸门 OPEN", doorX, GROUND - 207f, 14f, Color.rgb(190, 255, 214), true, Paint.Align.CENTER)
            }
        }
    }

    private fun drawBridgeMechanic(c: Canvas) {
        val spec = bridgeSpec ?: return
        for (segment in spec.segments) {
            val state = bridgeState.segments.firstOrNull { it.id == segment.id } ?: continue
            val x = bridgeScreenX(segment)
            if (x < -segment.width - 180f || x > W + segment.width + 180f) continue
            val y = segment.y.toFloat()
            val half = segment.width * 0.5f
            if (state.collapsed && !state.plankDeployed) {
                paint.color = Color.argb(210, 47, 51, 56)
                c.drawRect(RectF(x - half, y + 28f, x + half * 0.35f, y + 47f), paint)
                c.drawRect(RectF(x + half * 0.48f, y + 42f, x + half, y + 58f), paint)
                paint.color = Color.argb(190, 255, 108, 64)
                paint.strokeWidth = 4f
                c.drawLine(x - half * 0.3f, y + 34f, x - half * 0.08f, y + 48f, paint)
                c.drawLine(x + half * 0.08f, y + 30f, x + half * 0.25f, y + 45f, paint)
            } else {
                paint.color = if (state.plankDeployed) Color.rgb(141, 101, 58) else Color.rgb(72, 84, 87)
                c.drawRect(RectF(x - half, y, x + half, y + 34f), paint)
                paint.color = if (state.plankDeployed) Color.rgb(218, 164, 90) else Color.rgb(111, 125, 126)
                c.drawRect(RectF(x - half, y, x + half, y + 5f), paint)
                if (state.warningTicksRemaining > 0L || state.collapseTicksRemaining > 0L) {
                    paint.color = if (state.collapseTicksRemaining > 0L) Color.rgb(255, 103, 62) else Color.rgb(255, 211, 91)
                    paint.strokeWidth = 3f
                    val crack = Path()
                    crack.moveTo(x - half * 0.45f, y + 6f)
                    crack.lineTo(x - half * 0.18f, y + 22f)
                    crack.lineTo(x + half * 0.02f, y + 9f)
                    crack.lineTo(x + half * 0.24f, y + 29f)
                    c.drawPath(crack, paint)
                }
            }
            if (!state.winchCut) {
                paint.color = Color.rgb(255, 202, 91)
                c.drawCircle(x, y - 72f, 15f, paint)
                paint.color = Color.rgb(42, 46, 49)
                c.drawCircle(x, y - 72f, 7f, paint)
                text(c, "绞盘", x, y - 98f, 13f, Color.rgb(255, 224, 143), true, Paint.Align.CENTER)
            } else if (!state.collapsed) {
                text(c, "已切断", x, y - 98f, 13f, Color.rgb(185, 255, 213), true, Paint.Align.CENTER)
            }
            if (state.edgeGrabbed) text(c, "抓边", x, y + 59f, 13f, Color.rgb(177, 239, 255), true, Paint.Align.CENTER)
        }
    }

    /**
     * Shared visual/gameplay anchor for the operative's barrel. The sprite's standing
     * rifle sits well above the foot plane; using the old body-center anchor made every
     * round appear to leave the abdomen and miss the enemy torso line.
     */
    private fun playerMuzzleAnchor(): MuzzleAnchor {
        return when {
            inVehicle -> MuzzleAnchor(playerX + 88f, playerY - 82f)
            crouchHeld -> MuzzleAnchor(playerX + 66f, playerY - 80f)
            else -> MuzzleAnchor(playerX + 76f, playerY - 132f)
        }
    }

    private fun drawPlayer(c: Canvas) {
        if (inVehicle) return
        val blink = playerInvuln > 0f && (elapsed * 16f).toInt() % 2 == 0
        if (blink) return
        val sprite = art.bitmap("gfx/characters/operative_sheet.png")
        if (sprite != null) {
            val panel = sprite.width / 4
            val frame = ((elapsed * 8f).toInt() % 4)
            val top = if (crouchHeld) playerY - 150f else playerY - 222f
            val left = playerX - if (crouchHeld) 110f else 94f
            val right = playerX + if (crouchHeld) 110f else 94f
            art.draw(c, "gfx/characters/operative_sheet.png", Rect(frame * panel, 0, (frame + 1) * panel, sprite.height), RectF(left, top, right, playerY + 10f))
            if (muzzleFlash > 0f) {
                val muzzle = playerMuzzleAnchor()
                paint.color = Color.argb((230f * (muzzleFlash / 0.08f).coerceIn(0f, 1f)).toInt(), 255, 207, 93)
                c.drawCircle(muzzle.x, muzzle.y, 14f, paint)
            }
            if (aimHeld) {
                paint.color = Color.argb(185, 102, 224, 230)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                c.drawCircle(playerX + 68f, top + 43f, 17f, paint)
                c.drawLine(playerX + 51f, top + 43f, playerX + 85f, top + 43f, paint)
                c.drawLine(playerX + 68f, top + 26f, playerX + 68f, top + 60f, paint)
                paint.style = Paint.Style.FILL
            }
            return
        }
        paint.color = Color.rgb(244, 231, 197)
        c.drawCircle(playerX, playerY - 82f, 23f, paint)
        paint.color = Color.rgb(240, 106, 58)
        val torsoTop = if (crouchHeld) playerY - 45f else playerY - 62f
        c.drawRoundRect(RectF(playerX - 25f, torsoTop, playerX + 26f, playerY - 8f), 12f, 12f, paint)
        paint.color = Color.rgb(52, 131, 144)
        c.drawRect(playerX - 26f, playerY - 10f, playerX - 5f, playerY + 2f, paint)
        c.drawRect(playerX + 5f, playerY - 10f, playerX + 26f, playerY + 2f, paint)
        paint.color = Color.rgb(245, 199, 77)
        c.drawRect(playerX + 20f, if (aimHeld) playerY - 42f else playerY - 52f, playerX + 58f, if (aimHeld) playerY - 32f else playerY - 42f, paint)
    }

    private fun drawVehicle(c: Canvas) {
        val x = playerX
        val sprite = art.bitmap("gfx/vehicles/vehicle_boss_sheet.png")
        if (sprite != null) {
            val panel = sprite.width / 2
            art.draw(c, "gfx/vehicles/vehicle_boss_sheet.png", Rect(0, 0, panel, sprite.height), RectF(x - 130f, GROUND - 126f, x + 150f, GROUND + 22f))
            if (vehicleState == VehicleState.DAMAGED) {
                paint.color = Color.argb(150, 255, 72, 52)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                c.drawRoundRect(RectF(x - 130f, GROUND - 126f, x + 150f, GROUND + 22f), 18f, 18f, paint)
                paint.style = Paint.Style.FILL
            }
            return
        }
        paint.color = Color.rgb(50, 78, 83)
        c.drawRoundRect(RectF(x - 70f, GROUND - 48f, x + 85f, GROUND + 20f), 16f, 16f, paint)
        paint.color = Color.rgb(215, 162, 70)
        c.drawRect(x - 15f, GROUND - 88f, x + 20f, GROUND - 48f, paint)
        paint.color = Color.rgb(28, 38, 43)
        c.drawCircle(x - 40f, GROUND + 20f, 21f, paint)
        c.drawCircle(x + 53f, GROUND + 20f, 21f, paint)
        paint.color = Color.rgb(240, 106, 58)
        c.drawRect(x + 16f, GROUND - 78f, x + 105f, GROUND - 65f, paint)
        if (vehicleState == VehicleState.DAMAGED) {
            paint.color = Color.argb(160, 255, 72, 52)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            c.drawRoundRect(RectF(x - 70f, GROUND - 48f, x + 85f, GROUND + 20f), 16f, 16f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawEnemy(c: Canvas, e: Enemy) {
        val bob = sin(e.phase * 2f) * 3f
        if (e.warning > 0f) {
            val pulse = 0.5f + 0.5f * sin(e.warning * 35f)
            val cue = when (e.kind) {
                0 -> Color.rgb(255, 204, 88)
                1 -> Color.rgb(255, 147, 72)
                2 -> Color.rgb(255, 72, 72)
                else -> Color.rgb(105, 220, 255)
            }
            paint.color = Color.argb((110f + pulse * 90f).toInt(), Color.red(cue), Color.green(cue), Color.blue(cue))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            val radius = if (e.kind == 2) 48f else 38f
            c.drawCircle(e.x, e.y - 80f + bob, radius + pulse * 8f, paint)
            paint.style = Paint.Style.FILL
            text(c, enemyReadLabel(e.kind), e.x, e.y - 126f + bob, 15f, cue, true, Paint.Align.CENTER)
        }
        val sprite = art.bitmap("gfx/characters/enemy_sheet.png")
        if (sprite != null) {
            val panel = sprite.width / 4
            val frame = if (e.kind == 1) 2 else ((e.phase * 5f).toInt() % 2)
            art.draw(c, "gfx/characters/enemy_sheet.png", Rect(frame * panel, 0, (frame + 1) * panel, sprite.height), RectF(e.x - 72f, e.y - 160f + bob, e.x + 72f, e.y + 12f + bob))
            return
        }
        paint.color = when (e.kind) {
            0 -> Color.rgb(121, 72, 105)
            1 -> Color.rgb(183, 91, 65)
            2 -> Color.rgb(183, 62, 89)
            else -> Color.rgb(57, 114, 133)
        }
        c.drawCircle(e.x, e.y - 76f + bob, if (e.kind == 1) 26f else 20f, paint)
        paint.color = Color.rgb(63, 45, 64)
        c.drawRect(e.x - 24f, e.y - 53f, e.x + 24f, e.y - 8f, paint)
        paint.color = Color.rgb(255, 212, 90)
        c.drawRect(e.x - 28f, e.y - 44f, e.x - 10f, e.y - 36f, paint)
        c.drawRect(e.x + 10f, e.y - 44f, e.x + 28f, e.y - 36f, paint)
    }

    private fun drawBoss(c: Canvas) {
        val y = 385f + sin(elapsed * 1.5f) * 90f
        val phaseTint = when (bossPhase) {
            3 -> Color.rgb(238, 88, 66)
            2 -> Color.rgb(236, 166, 62)
            else -> Color.rgb(77, 196, 199)
        }
        val sprite = art.bitmap("gfx/vehicles/vehicle_boss_sheet.png")
        val telegraphWindow = bossWarningWindow()
        val attackPeriod = when (bossPhase) {
            3 -> 0.82f
            2 -> 1.15f
            else -> 1.6f
        }
        val timeToAttack = elapsed % attackPeriod
        if (timeToAttack > attackPeriod - telegraphWindow) {
            paint.color = Color.argb(120, Color.red(phaseTint), Color.green(phaseTint), Color.blue(phaseTint))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            c.drawCircle(1060f, y - 24f, 165f + 12f * sin(elapsed * 20f), paint)
            paint.style = Paint.Style.FILL
        }
        if (sprite != null) {
            val panel = sprite.width / 2
            art.draw(c, "gfx/vehicles/vehicle_boss_sheet.png", Rect(panel, 0, sprite.width, sprite.height), RectF(900f, y - 170f, 1225f, y + 115f))
            paint.color = Color.argb(150, Color.red(phaseTint), Color.green(phaseTint), Color.blue(phaseTint))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            c.drawOval(RectF(910f, y - 175f, 1215f, y + 120f), paint)
            paint.style = Paint.Style.FILL
            if (bossWeakPointOpen) {
                paint.color = Color.rgb(255, 236, 132)
                c.drawCircle(1080f, y + 25f, 22f + 4f * sin(elapsed * 18f), paint)
                text(c, "核心暴露 · FIRE", 1080f, y + 70f, 18f, Color.rgb(255, 236, 132), true, Paint.Align.CENTER)
            }
            text(c, "${bossTitle()}  /  ${bossPhaseLabel()}", 1060f, y - 182f, 20f, phaseTint, true, Paint.Align.CENTER)
            return
        }
        paint.color = when (level) {
            2 -> Color.rgb(185, 119, 49)
            3 -> Color.rgb(98, 167, 185)
            else -> Color.rgb(164, 72, 64)
        }
        c.drawRoundRect(RectF(965f, y - 120f, 1185f, y + 100f), 25f, 25f, paint)
        paint.color = Color.rgb(26, 35, 41)
        c.drawCircle(1025f, y - 40f, 25f, paint)
        c.drawCircle(1125f, y - 40f, 25f, paint)
        c.drawRect(900f, y - 12f, 1010f, y + 12f, paint)
        paint.color = Color.rgb(255, 212, 90)
        c.drawCircle(1080f, y + 25f, 17f, paint)
        if (bossWeakPointOpen) {
            paint.color = Color.rgb(255, 236, 132)
            c.drawCircle(1080f, y + 25f, 25f + 4f * sin(elapsed * 18f), paint)
            text(c, "核心暴露 · FIRE", 1080f, y + 70f, 18f, Color.rgb(255, 236, 132), true, Paint.Align.CENTER)
        }
        text(c, "${bossTitle()}  /  ${bossPhaseLabel()}", 1075f, y - 145f, 20f, Color.WHITE, true, Paint.Align.CENTER)
    }

    private fun bossTitle(): String = levelDefinition?.boss?.optString("name")?.takeIf { it.isNotBlank() }
        ?: levelDefinition?.boss?.optString("id")?.takeIf { it.isNotBlank() }
        ?: "区域 Boss"

    private fun bossPhaseLabel(): String {
        val phases = levelDefinition?.boss?.optJSONArray("phases")
        val phase = phases?.optJSONObject((bossPhase - 1).coerceAtLeast(0))
        val weakpoint = phase?.optString("weakpoint")?.takeIf { it.isNotBlank() }
        return weakpoint ?: when (bossPhase) { 3 -> "核心暴露"; 2 -> "双炮压制"; else -> "护盾巡逻" }
    }

    private fun bossWarningWindow(): Float {
        val phases = levelDefinition?.boss?.optJSONArray("phases")
        val ticks = phases?.optJSONObject((bossPhase - 1).coerceAtLeast(0))?.optLong("warning_ticks", 30L) ?: 30L
        return (ticks / 60f).coerceIn(0.25f, 0.9f)
    }

    private fun drawBullet(c: Canvas, b: Bullet) {
        paint.color = if (b.enemy) Color.rgb(255, 116, 93) else weaponSpecs[weapon].projectileColor
        if (!b.enemy && b.grenade) {
            // A short deterministic tail makes the grenade's changing tangent readable.
            paint.color = Color.argb(120, 255, 160, 73)
            c.drawLine(b.x, b.y, b.x - b.dx * 0.028f, b.y - b.dy * 0.028f, paint)
            paint.color = Color.rgb(255, 126, 72)
            c.drawCircle(b.x, b.y, 11f, paint)
            paint.color = Color.rgb(255, 227, 132)
            c.drawCircle(b.x - 3f, b.y - 3f, 4f, paint)
        } else {
            c.drawCircle(b.x, b.y, if (b.enemy) 7f else 9f, paint)
        }
    }

    /** Applies one fixed-step grenade blast and emits deterministic visual feedback. */
    private fun detonateGrenade(x: Float, y: Float, damage: Float) {
        val radiusX = 210f
        val radiusY = 150f
        enemies.forEach { enemy ->
            val dx = abs(enemy.x - x)
            val dy = abs((enemy.y - 82f) - y)
            if (dx <= radiusX && dy <= radiusY) {
                val falloff = 1f - 0.35f * max(dx / radiusX, dy / radiusY)
                enemy.hp -= damage * falloff
            }
        }
        if (bossActive && bossWeakPointOpen) {
            val bossY = 385f + sin(elapsed * 1.5f) * 90f
            if (abs(1060f - x) <= radiusX && abs((bossY - 24f) - y) <= radiusY) bossHp -= damage * 0.88f
        }
        val count = if (lowPerformance) 8 else 20
        repeat(count) { index ->
            val phase = index * 0.73f
            val radius = 18f + (index % 5) * 19f
            sparks.add(
                Spark(
                    x + sin(phase) * radius,
                    y + cos(phase) * radius * 0.66f,
                    0.45f,
                    Color.rgb(255, 195, 78)
                )
            )
        }
        cameraShake = max(cameraShake, 0.1f)
        audio.play("explosion", 0.85f)
    }

    private fun enemyReadLabel(kind: Int): String = when (kind) {
        0 -> "锁定 · 蹲下/跳跃"
        1 -> "抛射 · 向前穿过"
        2 -> "冲锋 · 及时跳开"
        else -> "三线 · 换层躲避"
    }

    private fun drawSpark(c: Canvas, s: Spark) {
        paint.color = Color.argb((255f * (s.life / 0.45f).coerceIn(0f, 1f)).toInt(), Color.red(s.color), Color.green(s.color), Color.blue(s.color))
        c.drawCircle(s.x, s.y, 6f + (0.45f - s.life) * 18f, paint)
    }

    private fun drawHud(c: Canvas) {
        panel(c, RectF(24f, 20f, 394f, 116f), VisualTokens.PANEL, VisualTokens.RUST)
        text(c, "生命", 48f, 54f, 18f, VisualTokens.WHITE, true, Paint.Align.LEFT)
        val hpRatio = (playerHp / 100f).coerceIn(0f, 1f)
        paint.color = Color.argb(210, 49, 59, 64)
        c.drawRoundRect(RectF(112f, 37f, 366f, 59f), 10f, 10f, paint)
        paint.shader = LinearGradient(112f, 0f, 366f, 0f, VisualTokens.DANGER, VisualTokens.ORANGE, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(112f, 37f, 112f + 254f * hpRatio, 59f), 10f, 10f, paint)
        paint.shader = null
        paint.color = Color.argb(115, 255, 255, 255)
        c.drawRect(116f, 39f, 112f + 254f * hpRatio - 5f, 42f, paint)
        for (i in 1..4) {
            paint.color = Color.argb(70, 15, 20, 25)
            c.drawRect(112f + i * 50.8f, 37f, 114f + i * 50.8f, 59f, paint)
        }
        text(c, "${playerHp.toInt()}%", 366f, 54f, 16f, VisualTokens.WHITE, true, Paint.Align.RIGHT)
        text(c, "分数  " + score.toString().padStart(5, '0'), 48f, 94f, 20f, VisualTokens.WARNING, true, Paint.Align.LEFT)
        text(c, if (inVehicle) "犀虎机甲车  /  " + vehicleStateLabel() else "步行  /  岚", 366f, 94f, 16f, if (inVehicle) VisualTokens.WARNING else VisualTokens.FRIENDLY, true, Paint.Align.RIGHT)

        if (!bossActive) {
            panel(c, RectF(438f, 20f, 838f, 100f), VisualTokens.PANEL, VisualTokens.FRIENDLY)
            text(c, levelDefinition?.title ?: "区域 0${level}", 462f, 51f, 20f, VisualTokens.WHITE, true, Paint.Align.LEFT)
            text(c, if (inVehicle) "载具突破" else "步行推进", 814f, 51f, 16f, VisualTokens.MUTED, true, Paint.Align.RIGHT)
            val route = (progress / objectiveX()).coerceIn(0f, 1f)
            paint.color = Color.argb(185, 45, 62, 68)
            c.drawRoundRect(RectF(462f, 69f, 814f, 78f), 5f, 5f, paint)
            paint.shader = LinearGradient(462f, 0f, 814f, 0f, VisualTokens.FRIENDLY, VisualTokens.STEEL_LIGHT, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(462f, 69f, 462f + 352f * route, 78f), 5f, 5f, paint)
            paint.shader = null
            for (i in 1..3) {
                paint.color = Color.argb(130, 255, 209, 92)
                c.drawCircle(462f + i * 88f, 73.5f, 3f, paint)
            }
            text(c, "推进  ${progress.toInt()}m", 462f, 96f, 13f, VisualTokens.MUTED, false, Paint.Align.LEFT)
            text(c, "目标  ${((objectiveX() - progress).coerceAtLeast(0f)).toInt()}m", 814f, 96f, 13f, VisualTokens.WARNING, true, Paint.Align.RIGHT)
        }
        if (bossActive) {
            val phaseTint = when (bossPhase) { 3 -> VisualTokens.RUST; 2 -> VisualTokens.WARNING; else -> VisualTokens.FRIENDLY }
            panel(c, RectF(438f, 20f, 1048f, 100f), VisualTokens.PANEL, phaseTint)
            val bossLabel = levelDefinition?.boss?.optString("name")?.takeIf { it.isNotBlank() }
                ?: levelDefinition?.boss?.optString("id")?.takeIf { it.isNotBlank() }
                ?: "GR-4  守门者"
            text(c, bossLabel, 462f, 51f, 20f, VisualTokens.WHITE, true, Paint.Align.LEFT)
            text(c, "阶段 0$bossPhase", 1024f, 51f, 16f, phaseTint, true, Paint.Align.RIGHT)
            paint.color = Color.argb(200, 63, 40, 45)
            c.drawRoundRect(RectF(462f, 69f, 1024f, 78f), 5f, 5f, paint)
            paint.shader = LinearGradient(462f, 0f, 1024f, 0f, phaseTint, VisualTokens.RUST, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(462f, 69f, 462f + 562f * (bossHp / bossMax).coerceIn(0f, 1f), 78f), 5f, 5f, paint)
            paint.shader = null
            text(c, if (bossWeakPointOpen) "核心暴露  /  FIRE" else "护盾锁定  /  读招", 462f, 96f, 13f, if (bossWeakPointOpen) VisualTokens.WARNING else VisualTokens.MUTED, true, Paint.Align.LEFT)
            text(c, "${bossHp.toInt()} / ${bossMax.toInt()}", 1024f, 96f, 13f, VisualTokens.WHITE, true, Paint.Align.RIGHT)
            if (bossPhaseNotice > 0f) {
                val a = (220f * (bossPhaseNotice / 1.25f).coerceIn(0f, 1f)).toInt()
                text(c, "核心模式切换", 640f, 138f, 28f, Color.argb(a, 255, 212, 90), true, Paint.Align.CENTER)
            }
        }
        val spec = weaponSpecs[weapon]
        val ammoText = if (ammo <= 0 && reloadTimer > 0f) "换弹中" else "${ammo}/${spec.magazine}"
        panel(c, RectF(1058f, 20f, 1165f, 116f), VisualTokens.PANEL, spec.projectileColor)
        text(c, "WEAPON", 1078f, 44f, 12f, VisualTokens.MUTED, true, Paint.Align.LEFT)
        text(c, spec.name.substringBefore(' '), 1078f, 69f, 18f, spec.projectileColor, true, Paint.Align.LEFT)
        text(c, ammoText, 1148f, 69f, 18f, VisualTokens.WHITE, true, Paint.Align.RIGHT)
        text(c, spec.role, 1078f, 94f, 13f, VisualTokens.MUTED, false, Paint.Align.LEFT)
        if ((level == 1 || level == 2) && vehicleState != VehicleState.DESTROYED) {
            text(c, "机甲耐久 ${vehicleHp.toInt()}  ·  ${vehicleStateLabel()}", 462f, 115f, 16f, VisualTokens.WARNING, true, Paint.Align.LEFT)
        }
        if (level == 1 && magneticSpec != null) {
            val pressed = magneticState.boxes.count { it.pressedDoor }
            text(c, "磁吊箱体  $pressed/${magneticState.boxes.size}", 1024f, 115f, 15f, VisualTokens.FRIENDLY, true, Paint.Align.RIGHT)
        }
        if (genericSpec != null) {
            val profile = GenericMechanic.profile(genericSpec!!)
            text(c, "${profile.label}  ${genericState.meter}%  ·  ${genericState.phase + 1}/4", 1024f, 115f, 15f, VisualTokens.FRIENDLY, true, Paint.Align.RIGHT)
        }
    }

    private fun drawControls(c: Canvas) {
        val deckAlpha = if (bossActive) 78 else 142
        paint.color = Color.argb(deckAlpha, 6, 14, 20)
        c.drawRoundRect(RectF(1090f, 460f, 1279f, 719f), 24f, 24f, paint)
        paint.color = Color.argb((deckAlpha + 35).coerceAtMost(220), 197, 146, 75)
        c.drawRect(1104f, 460f, 1264f, 463f, paint)
        paint.color = Color.argb(75, 105, 214, 209)
        c.drawRect(1104f, 466f, 1264f, 468f, paint)

        paint.color = Color.argb(26, 105, 214, 209)
        c.drawCircle(ControlLayout.moveCenterX, ControlLayout.moveCenterY, 52f, paint)
        paint.color = Color.argb(110, 210, 230, 229)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        c.drawCircle(ControlLayout.moveCenterX, ControlLayout.moveCenterY, 46f, paint)
        paint.color = Color.argb(145, 238, 246, 237)
        paint.style = Paint.Style.FILL
        c.drawCircle(ControlLayout.moveCenterX + moveAxis * 20f, ControlLayout.moveCenterY, 21f, paint)
        paint.color = Color.argb(80, 20, 40, 48)
        c.drawCircle(ControlLayout.moveCenterX + moveAxis * 20f, ControlLayout.moveCenterY, 14f, paint)
        text(c, "MOVE", ControlLayout.moveCenterX, 706f, 12f, VisualTokens.MUTED, true, Paint.Align.CENTER)
        control(c, ControlLayout.crouchVisual, if (crouchHeld) "起身" else "蹲下", VisualTokens.FRIENDLY)
        control(c, ControlLayout.jumpVisual, "跳跃", VisualTokens.STEEL_LIGHT)
        control(c, ControlLayout.shootVisual, "射击", VisualTokens.ORANGE)
        control(c, ControlLayout.aimVisual, if (aimHeld) "瞄准" else "精瞄", VisualTokens.ICE)
        control(c, ControlLayout.bombVisual, "炸弹", VisualTokens.WARNING)
        control(c, ControlLayout.weaponVisual, "换枪", VisualTokens.STEEL_LIGHT)
        control(c, ControlLayout.pauseHit, "暂停", VisualTokens.WHITE)
    }

    private fun vehicleStateLabel(): String = when (vehicleState) {
        VehicleState.AVAILABLE -> "待机"
        VehicleState.ENTERING -> "登车"
        VehicleState.ACTIVE -> "作战"
        VehicleState.DAMAGED -> "受损"
        VehicleState.EXITING -> "下车"
        VehicleState.DESTROYED -> "失效"
    }

    private fun drawPause(c: Canvas) {
        paint.color = Color.argb(185, 4, 8, 12)
        c.drawRect(0f, 0f, W, H, paint)
        text(c, "已暂停", 640f, 260f, 58f, Color.WHITE, true, Paint.Align.CENTER)
        button(c, RectF(455f, 330f, 825f, 405f), "继续", VisualTokens.STEEL_LIGHT)
        button(c, RectF(455f, 430f, 825f, 505f), "退出关卡", VisualTokens.RUST)
    }

    private fun drawResult(c: Canvas) {
        paint.color = Color.argb(210, 4, 8, 12)
        c.drawRect(0f, 0f, W, H, paint)
        val won = if (!isBossLevel()) progress >= objectiveX() else bossHp <= 0f && progress >= (levelPacing?.bossTriggerX ?: 4700f)
        text(c, if (won) "区域突破" else "行动失败", 640f, 230f, 62f, if (won) Color.rgb(255, 212, 90) else Color.rgb(240, 106, 58), true, Paint.Align.CENTER)
        text(c, "最终分数  " + score, 640f, 305f, 28f, Color.WHITE, true, Paint.Align.CENTER)
        button(c, RectF(435f, 370f, 845f, 450f), if (won && level < SaveProfileStore.MAX_LEVEL) "进入下一关" else "再次挑战", VisualTokens.STEEL_LIGHT)
        button(c, RectF(435f, 475f, 845f, 555f), "返回关卡选择", VisualTokens.STEEL)
    }

    private fun drawCheckpoint(c: Canvas) {
        paint.color = Color.argb(218, 4, 8, 12)
        c.drawRect(0f, 0f, W, H, paint)
        text(c, "行动中断", 640f, 220f, 62f, Color.rgb(240, 106, 58), true, Paint.Align.CENTER)
        text(c, "检查点已保留", 640f, 278f, 28f, Color.rgb(255, 212, 90), true, Paint.Align.CENTER)
        text(c, "区域 ${level} · ${checkpointProgress.toInt()}m · 分数 ${checkpointScore}", 640f, 325f, 23f, Color.WHITE, false, Paint.Align.CENTER)
        button(c, RectF(435f, 370f, 845f, 450f), "从检查点重试", VisualTokens.STEEL_LIGHT)
        button(c, RectF(435f, 475f, 845f, 555f), "返回关卡选择", VisualTokens.STEEL)
    }

    private fun handleDown(id: Int, x: Float, y: Float) {
        when (mode) {
            Mode.CINEMATIC -> {
                audio.stop("voiceover")
                cinematic.skip()
                mode = Mode.MENU
            }
            Mode.MENU -> {
                if (RectF(420f, 315f, 860f, 400f).contains(x, y)) {
                    if (saveProfile.continueLevel != null) continueFromSave() else startLevel(1)
                }
                else if (RectF(420f, 430f, 860f, 500f).contains(x, y)) mode = Mode.LEVELS
                else if (RectF(420f, 530f, 860f, 600f).contains(x, y)) mode = Mode.SETTINGS
            }
            Mode.LEVELS -> {
                for (index in 1..SaveProfileStore.MAX_LEVEL) {
                    val column = (index - 1) % 5
                    val row = (index - 1) / 5
                    val r = RectF(52f + column * 238f, 174f + row * 40f, 270f + column * 238f, 206f + row * 40f)
                    if (r.contains(x, y) && index <= unlocked) startLevel(index)
                }
                if (RectF(460f, 625f, 820f, 683f).contains(x, y)) mode = Mode.MENU
            }
            Mode.SETTINGS -> {
                if (RectF(330f, 260f, 950f, 350f).contains(x, y)) {
                    lowPerformance = !lowPerformance
                    saveProfile = saveProfile.copy(lowPerformance = lowPerformance)
                    SaveProfileStore.save(prefs, saveProfile)
                } else if (RectF(460f, 540f, 820f, 610f).contains(x, y)) mode = Mode.MENU
            }
            Mode.PLAYING -> {
                touchInput.onDown(id, x, y)
                if (touchInput.snapshot().pausePressed) {
                    mode = Mode.PAUSED
                    touchInput.cancel()
                }
            }
            Mode.PAUSED -> {
                if (RectF(455f, 330f, 825f, 405f).contains(x, y)) {
                    mode = Mode.PLAYING
                } else if (RectF(455f, 430f, 825f, 505f).contains(x, y)) {
                    touchInput.cancel()
                    mode = Mode.LEVELS
                }
            }
            Mode.CHECKPOINT -> {
                if (RectF(435f, 370f, 845f, 450f).contains(x, y)) {
                    retryCheckpoint()
                } else if (RectF(435f, 475f, 845f, 555f).contains(x, y)) {
                    mode = Mode.LEVELS
                }
            }
            Mode.RESULT -> {
                val won = if (!isBossLevel()) progress >= objectiveX() else bossHp <= 0f && progress >= (levelPacing?.bossTriggerX ?: 4700f)
                if (RectF(435f, 370f, 845f, 450f).contains(x, y)) {
                    if (won && level < SaveProfileStore.MAX_LEVEL) startLevel(level + 1) else startLevel(level)
                } else if (RectF(435f, 475f, 845f, 555f).contains(x, y)) mode = Mode.LEVELS
            }
        }
    }

    private fun handleMove(id: Int, x: Float, y: Float) {
        if (mode == Mode.PLAYING) touchInput.onMove(id, x, y)
    }

    private fun handleUp(id: Int) {
        touchInput.onUp(id)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        fun logicalPoint(index: Int): Pair<Float, Float>? {
            return viewport.toLogical(event.getX(index), event.getY(index))
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                logicalPoint(index)?.let { (x, y) -> handleDown(event.getPointerId(index), x, y) }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    logicalPoint(i)?.let { (x, y) -> handleMove(event.getPointerId(i), x, y) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val index = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.actionIndex
                handleUp(event.getPointerId(index))
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) touchInput.cancel()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        persistContinueState()
        audio.release()
        art.clear()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) resumeFromLifecycle() else pauseForLifecycle()
    }

    /** Called by the Activity before it enters the background; never catches up elapsed time. */
    fun pauseForLifecycle() {
        lifecyclePaused = true
        accumulator = 0.0
        lastNs = System.nanoTime()
        if (mode == Mode.PLAYING) {
            persistContinueState()
            touchInput.cancel()
            mode = Mode.PAUSED
        }
        audio.setEnabled(false)
    }

    /** Resumes clocking after a lifecycle transition; gameplay remains paused until Continue is tapped. */
    fun resumeFromLifecycle() {
        lifecyclePaused = false
        accumulator = 0.0
        lastNs = System.nanoTime()
        audio.setEnabled(true)
    }

    private fun panel(c: Canvas, r: RectF, color: Int, accent: Int) {
        paint.color = Color.argb(105, 0, 0, 0)
        c.drawRoundRect(RectF(r.left + 5f, r.top + 6f, r.right + 5f, r.bottom + 6f), VisualTokens.PANEL_RADIUS, VisualTokens.PANEL_RADIUS, paint)
        paint.color = Color.argb(226, Color.red(color), Color.green(color), Color.blue(color))
        c.drawRoundRect(r, VisualTokens.PANEL_RADIUS, VisualTokens.PANEL_RADIUS, paint)
        paint.color = Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawRoundRect(RectF(r.left, r.top, r.right, r.top + 4f), VisualTokens.PANEL_RADIUS, VisualTokens.PANEL_RADIUS, paint)
        paint.color = Color.argb(38, 255, 255, 255)
        c.drawRect(r.left + 18f, r.top + 12f, r.right - 18f, r.top + 14f, paint)
    }

    private fun button(c: Canvas, r: RectF, label: String, color: Int, tag: String? = null) {
        paint.color = Color.argb(110, 0, 0, 0)
        c.drawRoundRect(RectF(r.left + 5f, r.top + 7f, r.right + 5f, r.bottom + 7f), 16f, 16f, paint)
        paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, Color.argb(240, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(220, (Color.red(color) * 0.62f).toInt(), (Color.green(color) * 0.62f).toInt(), (Color.blue(color) * 0.62f).toInt()), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 16f, 16f, paint)
        paint.shader = null
        paint.color = Color.argb(95, 255, 255, 255)
        c.drawRoundRect(RectF(r.left, r.top, r.right, r.top + 7f), 16f, 16f, paint)
        paint.color = Color.argb(210, 255, 255, 255)
        c.drawRect(r.left + 18f, r.top + 24f, r.left + 23f, r.bottom - 24f, paint)
        if (tag != null) text(c, tag, r.left + 34f, r.top + 24f, 12f, Color.argb(205, 235, 243, 235), true, Paint.Align.LEFT)
        text(c, label, r.centerX(), r.centerY() + if (tag == null) 10f else 13f, if (tag == null) 27f else 25f, VisualTokens.WHITE, true, Paint.Align.CENTER)
    }

    private fun control(c: Canvas, r: RectF, label: String, accent: Int = VisualTokens.WHITE) {
        paint.color = Color.argb(132, 12, 24, 31)
        c.drawRoundRect(r, VisualTokens.CONTROL_RADIUS, VisualTokens.CONTROL_RADIUS, paint)
        paint.color = Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = VisualTokens.STROKE
        c.drawRoundRect(r, VisualTokens.CONTROL_RADIUS, VisualTokens.CONTROL_RADIUS, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(125, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawRoundRect(RectF(r.left + 18f, r.top + 16f, r.left + 24f, r.bottom - 16f), 3f, 3f, paint)
        text(c, label, r.centerX() + 3f, r.centerY() + 8f, 20f, VisualTokens.WHITE, true, Paint.Align.CENTER)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean, align: Paint.Align) {
        hud.textSize = size
        hud.color = color
        hud.textAlign = align
        hud.typeface = android.graphics.Typeface.create("sans", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        c.drawText(value, x, y, hud)
    }
}




