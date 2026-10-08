package com.steelfire.assault

/**
 * Authored campaign pacing data. This file intentionally contains no
 * executable callbacks so a beat can be reviewed and verified offline.
 */
data class EncounterBeat(
    val id: String,
    val triggerX: Float,
    val enemyKinds: List<Int>,
    val cover: Boolean,
    val supply: String?,
    val elite: Boolean = false,
    val vehicle: Boolean = false,
    val bossArena: Boolean = false
)

data class CheckpointSpec(
    val id: String,
    val progress: Float,
    val resumeX: Float,
    val afterBeatId: String
)

data class LevelPacingSpec(
    val id: String,
    val width: Float,
    val bossTriggerX: Float,
    val beats: List<EncounterBeat>,
    val checkpoints: List<CheckpointSpec>,
    /** True for authored five-level chapter finales that use the Boss HUD. */
    val bossLevel: Boolean = bossTriggerX.isFinite(),
    /** Non-Boss levels end at this extraction line. */
    val clearX: Float = (width - 900f).coerceAtLeast(1f)
)

object LevelPacingCatalog {
    /** Authored route for the first playable slice. */
    val LEVEL_01_RUST_TIDE = LevelPacingSpec(
        id = "level_01_rust_tide",
        width = 5200f,
        bossTriggerX = 4300f,
        beats = listOf(
            EncounterBeat("safe_harbor_teach", 360f, listOf(0), cover = false, supply = null),
            EncounterBeat("gantry_crossfire", 860f, listOf(0, 0), cover = true, supply = null),
            EncounterBeat("service_lift_supply", 1450f, listOf(1), cover = true, supply = "ammo"),
            EncounterBeat("six_wheel_breakout", 2050f, listOf(2, 3), cover = true, supply = "armor", vehicle = true),
            EncounterBeat("elite_gate", 2800f, listOf(3, 1), cover = true, supply = null, elite = true),
            EncounterBeat("reactor_bridge", 3550f, listOf(0, 2), cover = false, supply = "ammo"),
            EncounterBeat("boss_square", 4300f, emptyList(), cover = true, supply = "medkit", bossArena = true)
        ),
        checkpoints = listOf(
            CheckpointSpec("lift_exit", 1650f, 1650f, afterBeatId = "service_lift_supply"),
            CheckpointSpec("elite_gate_exit", 3300f, 3300f, afterBeatId = "elite_gate")
        )
    )

    /** Bridge route: traversal ends at the extraction gate and has no boss encounter. */
    val LEVEL_02_BROKEN_BRIDGE = LevelPacingSpec(
        id = "level_02_broken_bridge",
        width = 5200f,
        bossTriggerX = Float.POSITIVE_INFINITY,
        beats = listOf(
            EncounterBeat("bridge_stable", 360f, listOf(0, 1), cover = true, supply = null),
            EncounterBeat("bridge_overload", 1320f, listOf(0, 3), cover = true, supply = "ammo"),
            EncounterBeat("winch_crossfire", 2240f, listOf(1, 0), cover = true, supply = null),
            EncounterBeat("edge_grab_escape", 3180f, listOf(3, 2), cover = false, supply = "medkit"),
            EncounterBeat("bridge_clear", 4100f, listOf(0, 1), cover = false, supply = null)
        ),
        checkpoints = listOf(
            CheckpointSpec("bridge_mid", 1650f, 1650f, afterBeatId = "bridge_overload"),
            CheckpointSpec("bridge_exit", 3300f, 3300f, afterBeatId = "edge_grab_escape")
        )
    )

    fun forLevel(level: Int): LevelPacingSpec? =
        when (level) {
            1 -> LEVEL_01_RUST_TIDE
            2 -> LEVEL_02_BROKEN_BRIDGE
            in 3..50 -> generated(level)
            else -> null
        }

    /** Converts an authored JSON beat table into the fixed-step combat pacing contract. */
    fun fromDefinition(definition: LevelDefinition): LevelPacingSpec {
        val bossLevel = definition.index % 5 == 0 || definition.boss != null
        val width = definition.worldWidth.toFloat().coerceAtLeast(1000f)
        val clearX = (width - 900f).coerceAtLeast(900f)
        // Authored L03-L50 routes use the full world width; Boss arenas open at the
        // authored final arena beat after both checkpoints are reachable.
        val bossTrigger = if (bossLevel) {
            definition.beats.lastOrNull()?.x?.toFloat()?.coerceIn(900f, clearX) ?: clearX
        } else Float.POSITIVE_INFINITY
        val beats = definition.beats.mapIndexed { index, beat ->
            EncounterBeat(
                id = beat.id,
                triggerX = beat.x.toFloat().coerceAtLeast(0f),
                enemyKinds = beat.enemyKinds.map { kind ->
                    when (kind.lowercase()) {
                        "regular", "rifle", "sentinel" -> 0
                        "elite", "armored", "grenadier" -> 1
                        "drone", "charger", "short_hop_drone" -> 2
                        "shield_carrier", "armored_sentry" -> 1
                        else -> 3
                    }
                },
                cover = beat.mechanicParams["cover"]?.toBooleanStrictOrNull() ?: index % 2 == 1,
                supply = beat.mechanicParams["supply"]?.takeIf { it.isNotBlank() },
                elite = beat.enemyKinds.any { it.equals("elite", ignoreCase = true) },
                vehicle = beat.mechanicParams["vehicle"]?.toBooleanStrictOrNull() == true,
                bossArena = bossLevel && beat.x.toFloat() >= bossTrigger
            )
        }
        val checkpoints = definition.checkpoints.mapIndexed { index, checkpoint ->
            CheckpointSpec(
                id = checkpoint.id,
                progress = checkpoint.x.toFloat().coerceIn(0f, clearX),
                resumeX = checkpoint.x.toFloat().coerceIn(0f, clearX),
                afterBeatId = beats.getOrNull(index.coerceAtMost(beats.lastIndex))?.id ?: "start"
            )
        }
        return LevelPacingSpec(
            id = definition.id,
            width = width,
            bossTriggerX = bossTrigger,
            beats = beats,
            checkpoints = checkpoints,
            bossLevel = bossLevel,
            clearX = clearX
        )
    }

    private fun generated(level: Int): LevelPacingSpec {
        val width = 5200f
        val bossLevel = level % 5 == 0
        val objective = 4300f
        val id = "level_${level.toString().padStart(2, '0')}_generated"
        val beats = listOf(
            EncounterBeat("${id}_teach", 420f, listOf(0), cover = false, supply = null),
            EncounterBeat("${id}_pressure", 1500f, listOf(0, 1), cover = true, supply = "ammo"),
            EncounterBeat("${id}_elite", 2700f, listOf(2, 3), cover = true, supply = null, elite = true),
            EncounterBeat("${id}_exit", if (bossLevel) objective else 3800f, if (bossLevel) emptyList() else listOf(0, 2), cover = true, supply = if (bossLevel) "medkit" else null, bossArena = bossLevel)
        )
        return LevelPacingSpec(
            id = id,
            width = width,
            bossTriggerX = if (bossLevel) objective else Float.POSITIVE_INFINITY,
            beats = beats,
            checkpoints = listOf(
                CheckpointSpec("${id}_mid", 1650f, 1650f, beats[1].id),
                CheckpointSpec("${id}_exit", 3300f, 3300f, beats[2].id)
            ),
            bossLevel = bossLevel,
            clearX = objective
        )
    }
}
