package com.steelfire.assault

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject

/** Immutable data contract for an offline-authored level. */
data class LevelDefinition(
    val id: String,
    val index: Int,
    val act: Int,
    val title: String,
    val worldWidth: Int,
    val storyCue: String,
    val newMechanic: LevelMechanicSpec,
    val checkpoints: List<LevelCheckpoint>,
    val beats: List<LevelBeat>,
    val boss: JSONObject?,
    val assetManifest: String
)

data class LevelCheckpoint(
    val id: String,
    val x: Int
)

data class LevelBeat(
    val id: String,
    val x: Int,
    val purpose: String,
    val enemyKinds: List<String>,
    val mechanicParams: Map<String, String>,
    val storyCue: String,
    val assetRefs: List<String>
)

/** Whitelisted, data-only mechanic contract shared by authored levels. */
sealed interface LevelMechanicSpec {
    val id: String
    val version: Int
}

/** Configuration for the level-01 magnetic suspended-crate mechanic. */
data class MagneticCoverSpec(
    override val id: String,
    override val version: Int,
    val lockCooldownTicks: Long,
    val boxes: List<MagneticBoxSpec>
) : LevelMechanicSpec

data class MagneticBoxSpec(
    val id: String,
    val lockId: String,
    val x: Int,
    val y: Int,
    val minOffset: Int,
    val maxOffset: Int,
    val step: Int,
    val pressesDoorId: String?
)

/** Strict, local JSON parser. Unknown top-level or mechanic fields are rejected. */
object LevelDefinitionParser {
    private val LEVEL_KEYS = setOf(
        "id", "index", "act", "title", "world_width", "story_cue", "new_mechanic",
        "checkpoints", "beats", "boss", "asset_manifest"
    )
    private val MAGNETIC_MECHANIC_KEYS = setOf("id", "version", "lock_cooldown_ticks", "boxes")
    private val BRIDGE_MECHANIC_KEYS = setOf("id", "version", "warning_ticks", "collapse_ticks", "segments")
    /** Generic authored mechanics intentionally carry only scalar string parameters. */
    private val GENERIC_MECHANIC_KEYS = setOf("id", "version", "params")
    private val GENERIC_MECHANIC_IDS = setOf(
        "tide_valve", "stealth_alert", "rail_switch", "sand_current", "mirage_scan", "frequency_pair",
        "escort_route", "beacon_safe_zone", "tide_cover", "breath_window", "current_flip", "sonar_rhythm",
        "submersible_recoil", "thermal_zone", "lift_selection", "molten_bridge", "weapon_overheat", "forge_overheat",
        "zero_g_thrust", "gravity_flip", "vehicle_jump", "gravity_anchor", "rotating_gravity", "magnetic_freight",
        "thermal_boots", "signal_switch", "bridge_escort", "frostline_cannon", "glass_refraction", "spore_purifier",
        "vine_bridge", "seed_convoy", "spore_core", "counterweight_elevator", "pressure_valves", "rotary_tunnel",
        "deepwell_rescue", "drill_emperor", "reflection_gates", "mimic_counterplay", "memory_sequence", "dual_world",
        "mirror_entity", "coolant_routing", "civilian_convoy", "collapse_rhythm", "overdrive_weapon", "reactor_throne"
    )
    private val BOX_KEYS = setOf(
        "id", "lock_id", "x", "y", "min_offset", "max_offset", "step", "presses_door"
    )
    private val SEGMENT_KEYS = setOf(
        "id", "x", "y", "width", "capacity", "warning_ticks", "collapse_ticks", "winch_id", "plank_id"
    )
    private val CHECKPOINT_KEYS = setOf("id", "x")
    private val BEAT_KEYS = setOf("id", "x", "purpose", "enemy_kinds", "mechanic_params", "story_cue", "asset_refs")

    fun parse(raw: String): LevelDefinition {
        val root = JSONObject(raw)
        rejectUnknown(root, LEVEL_KEYS, "level")
        val mechanicObject = root.getJSONObject("new_mechanic")
        val spec = parseMechanic(mechanicObject)

        val checkpoints = parseCheckpoints(root.getJSONArray("checkpoints"))
        val beats = parseBeats(root.getJSONArray("beats"))
        require(checkpoints.zipWithNext().all { it.first.x < it.second.x }) {
            "checkpoints must be ordered by x"
        }
        require(beats.zipWithNext().all { it.first.x < it.second.x }) {
            "beats must be ordered by x"
        }

        return LevelDefinition(
            id = root.getString("id"),
            index = root.getInt("index").also { require(it in 1..50) { "level index must be in 1..50" } },
            act = root.getInt("act"),
            title = root.getString("title"),
            worldWidth = root.getInt("world_width").also { require(it > 0) },
            storyCue = root.getString("story_cue"),
            newMechanic = spec,
            checkpoints = checkpoints,
            beats = beats,
            boss = root.optJSONObject("boss"),
            assetManifest = root.getString("asset_manifest")
        )
    }

    private fun parseMechanic(json: JSONObject): LevelMechanicSpec = when (json.getString("id")) {
        MagneticCoverMechanic.ID -> {
            rejectUnknown(json, MAGNETIC_MECHANIC_KEYS, "new_mechanic")
            MagneticCoverSpec(
                id = json.getString("id"), version = json.getInt("version"),
                lockCooldownTicks = json.getLong("lock_cooldown_ticks").also {
                    require(it > 0) { "lock_cooldown_ticks must be positive" }
                }, boxes = parseBoxes(json.getJSONArray("boxes"))
            ).also { require(it.boxes.isNotEmpty()) { "magnetic_cover requires at least one box" } }
        }
        BridgeCollapseMechanic.ID -> {
            rejectUnknown(json, BRIDGE_MECHANIC_KEYS, "new_mechanic")
            BridgeCollapseSpec(
                id = json.getString("id"), version = json.getInt("version"),
                warningTicks = json.getLong("warning_ticks").also { require(it > 0) },
                collapseTicks = json.getLong("collapse_ticks").also { require(it > 0) },
                segments = parseSegments(json.getJSONArray("segments"))
            ).also { require(it.segments.isNotEmpty()) { "bridge_collapse requires at least one segment" } }
        }
        else -> {
            // Later levels may introduce a new rule before its bespoke reducer lands. Keep the
            // contract data-driven: unknown mechanic ids are accepted only with the generic
            // id/version/params shape, then advanced by GenericMechanic on fixed steps.
            rejectUnknown(json, GENERIC_MECHANIC_KEYS, "new_mechanic")
            val paramsObject = json.optJSONObject("params")
            val params = paramsObject?.keys()?.asSequence()?.associateWith { key ->
                paramsObject.getString(key)
            } ?: emptyMap()
            require(json.getString("id") in GENERIC_MECHANIC_IDS) {
                "unsupported generic mechanic id"
            }
            require(json.getString("id").matches(Regex("[a-z][a-z0-9_]{1,63}"))) {
                "invalid generic mechanic id"
            }
            require(params["cycle_ticks"]?.toLongOrNull()?.let { it in 36L..240L } == true) {
                "generic mechanic cycle_ticks must be between 36 and 240"
            }
            require(params["severity"]?.toIntOrNull()?.let { it in 1..3 } == true) {
                "generic mechanic severity must be between 1 and 3"
            }
            require(params["visual_variant"]?.toIntOrNull()?.let { it in 0..5 } == true) {
                "generic mechanic visual_variant must be between 0 and 5"
            }
            GenericMechanicSpec(
                id = json.getString("id"),
                version = json.getInt("version").also { require(it > 0) },
                params = params
            )
        }
    }

    private fun parseBoxes(array: JSONArray): List<MagneticBoxSpec> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            rejectUnknown(item, BOX_KEYS, "new_mechanic.boxes[$index]")
            val min = item.getInt("min_offset")
            val max = item.getInt("max_offset")
            val step = item.getInt("step")
            require(min < max && step > 0 && (max - min) % step == 0) {
                "invalid magnetic box range at index $index"
            }
            add(MagneticBoxSpec(
                id = item.getString("id"), lockId = item.getString("lock_id"),
                x = item.getInt("x"), y = item.getInt("y"), minOffset = min,
                maxOffset = max, step = step,
                pressesDoorId = item.optString("presses_door", "").takeIf { it.isNotBlank() }
            ))
        }
    }

    private fun parseSegments(array: JSONArray): List<BridgeSegmentSpec> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            rejectUnknown(item, SEGMENT_KEYS, "new_mechanic.segments[$index]")
            val width = item.getInt("width")
            val capacity = item.getInt("capacity")
            require(width > 0 && capacity > 0) { "invalid bridge segment at index $index" }
            add(BridgeSegmentSpec(
                id = item.getString("id"), x = item.getInt("x"), y = item.getInt("y"),
                width = width, capacity = capacity,
                warningTicks = item.optLong("warning_ticks", 0L),
                collapseTicks = item.optLong("collapse_ticks", 0L),
                winchId = item.getString("winch_id"),
                plankId = item.optString("plank_id", "").takeIf { it.isNotBlank() }
            ))
        }
    }

    private fun parseCheckpoints(array: JSONArray): List<LevelCheckpoint> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            rejectUnknown(item, CHECKPOINT_KEYS, "checkpoints[$index]")
            add(LevelCheckpoint(item.getString("id"), item.getInt("x")))
        }
    }

    private fun parseBeats(array: JSONArray): List<LevelBeat> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            rejectUnknown(item, BEAT_KEYS, "beats[$index]")
            val paramsJson = item.getJSONObject("mechanic_params")
            val params = paramsJson.keys().asSequence().associateWith { paramsJson.getString(it) }
            add(LevelBeat(
                id = item.getString("id"), x = item.getInt("x"),
                purpose = item.getString("purpose"),
                enemyKinds = item.getJSONArray("enemy_kinds").strings(),
                mechanicParams = params, storyCue = item.getString("story_cue"),
                assetRefs = item.getJSONArray("asset_refs").strings()
            ))
        }
    }

    private fun rejectUnknown(json: JSONObject, allowed: Set<String>, path: String) {
        json.keys().forEach { key -> require(key in allowed) { "Unknown field $path.$key" } }
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }
}

object LevelDefinitionLoader {
    private val FALLBACK_MECHANICS = listOf(
        "tide_valve", "stealth_alert", "rail_switch", "sand_current", "mirage_scan", "frequency_pair",
        "escort_route", "beacon_safe_zone", "tide_cover", "breath_window", "current_flip", "sonar_rhythm",
        "submersible_recoil", "thermal_zone", "lift_selection", "molten_bridge", "weapon_overheat", "forge_overheat",
        "zero_g_thrust", "gravity_flip", "vehicle_jump", "gravity_anchor", "rotating_gravity", "magnetic_freight",
        "thermal_boots", "signal_switch", "bridge_escort", "frostline_cannon", "glass_refraction", "spore_purifier",
        "vine_bridge", "seed_convoy", "spore_core", "counterweight_elevator", "pressure_valves", "rotary_tunnel",
        "deepwell_rescue", "drill_emperor", "reflection_gates", "mimic_counterplay", "memory_sequence", "dual_world",
        "mirror_entity", "coolant_routing", "civilian_convoy", "collapse_rhythm", "overdrive_weapon", "reactor_throne"
    )

    fun loadLevel01(assetManager: AssetManager): LevelDefinition? =
        load(assetManager, 1)

    fun loadLevel02(assetManager: AssetManager): LevelDefinition? =
        load(assetManager, 2)

    fun load(assetManager: AssetManager, index: Int): LevelDefinition? =
        try {
            assetManager.open("levels/level_%02d.json".format(index)).bufferedReader(Charsets.UTF_8).use { reader ->
                LevelDefinitionParser.parse(reader.readText())
            }
        } catch (_: java.io.FileNotFoundException) {
            // A missing later file can use the deterministic local staging definition.
            generated(index)
        } catch (_: Exception) {
            // A present but invalid file must not be silently replaced by generic content.
            null
        }

    private fun generated(index: Int): LevelDefinition? {
        if (index !in 3..50) return null
        val chapter = ((index - 1) / 5) + 1
        val mechanicId = FALLBACK_MECHANICS.getOrNull(index - 3) ?: return null
        val mechanicParams = mapOf(
            "cycle_ticks" to (72 + ((index * 13) % 90)).toString(),
            "severity" to ((index % 3) + 1).toString(),
            "visual_variant" to (index % 6).toString()
        )
        val boss = if (index % 5 == 0) JSONObject().put("id", "boss_l${index.toString().padStart(2, '0')}" ) else null
        val beats = listOf(
            LevelBeat("l${index.toString().padStart(2, '0')}_teach", 360, "teach_${index.toString().padStart(2, '0')}", listOf("regular"), mechanicParams, "story_l${index.toString().padStart(2, '0')}_intro", emptyList()),
            LevelBeat("l${index.toString().padStart(2, '0')}_combine", 3000, "combine_${index.toString().padStart(2, '0')}", listOf("regular", "elite"), mechanicParams, "story_l${index.toString().padStart(2, '0')}_mid", emptyList()),
            LevelBeat("l${index.toString().padStart(2, '0')}_pressure", 6000, "pressure_${index.toString().padStart(2, '0')}", listOf("drone", "shield_carrier"), mechanicParams, "story_l${index.toString().padStart(2, '0')}_mid", emptyList()),
            LevelBeat("l${index.toString().padStart(2, '0')}_clear", 8500, "clear_${index.toString().padStart(2, '0')}", listOf("sentry"), mechanicParams, "story_l${index.toString().padStart(2, '0')}_end", emptyList())
        )
        val routedBeats = if (index % 5 == 0) beats + LevelBeat(
            "l${index.toString().padStart(2, '0')}_boss",
            10200,
            "boss_arena_${index.toString().padStart(2, '0')}",
            listOf("sentry"),
            mechanicParams,
            "story_l${index.toString().padStart(2, '0')}_end",
            emptyList()
        ) else beats
        return LevelDefinition(
            id = "level_${index.toString().padStart(2, '0')}_generated",
            index = index,
            act = chapter,
            title = "行动区域 ${index.toString().padStart(2, '0')}",
            worldWidth = 11520,
            storyCue = "story_l${index.toString().padStart(2, '0')}_intro",
            newMechanic = GenericMechanicSpec(mechanicId, 1, mechanicParams + ("chapter" to chapter.toString())),
            checkpoints = listOf(
                LevelCheckpoint("l${index.toString().padStart(2, '0')}_mid", 3300),
                LevelCheckpoint("l${index.toString().padStart(2, '0')}_exit", 7200)
            ),
            beats = routedBeats,
            boss = boss,
            assetManifest = "levels/level_${index.toString().padStart(2, '0')}/manifest.json"
        )
    }
}
