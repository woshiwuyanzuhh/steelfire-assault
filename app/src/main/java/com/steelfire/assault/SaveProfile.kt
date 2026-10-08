package com.steelfire.assault

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, bounded local progress used by the menu and checkpoint resume flow. */
data class SaveProfile(
    val schemaVersion: Int = SaveProfileStore.CURRENT_VERSION,
    val unlockedLevel: Int = 1,
    val lowPerformance: Boolean = false,
    val continueLevel: Int? = null,
    val checkpointProgress: Float = 0f,
    /** Player screen position at the committed checkpoint; keeps resume aligned with the world snapshot. */
    val checkpointPlayerX: Float = 150f,
    val checkpointScore: Int = 0,
    val checkpointEncounterIndex: Int = 0,
    val checkpointSpawnWave: Int = 0,
    /** Level-01 mechanic state is persisted as bounded offsets for safe resume. */
    val checkpointMagneticOffsets: List<Int> = emptyList(),
    /** Level-02 mechanic state is persisted as bounded immutable segment snapshots. */
    val checkpointBridgeSegments: List<BridgeSegmentState> = emptyList(),
    /** Generic campaign mechanics are scalar snapshots so a process restart resumes the same window. */
    val checkpointGenericTick: Long = 0L,
    val checkpointGenericPhase: Int = 0,
    val checkpointGenericInteractions: Int = 0,
    val checkpointGenericMeter: Int = 0,
    val checkpointGenericHazardActive: Boolean = false
)

/** SharedPreferences wrapper. JSON parsing is deliberately defensive: a bad save never blocks boot. */
object SaveProfileStore {
    const val MAX_LEVEL = 50
    const val CURRENT_VERSION = 1
    private const val TAG = "SaveProfileStore"
    private const val KEY_PROFILE = "save_profile_json"
    private const val LEGACY_UNLOCKED = "unlocked_level"
    private const val LEGACY_LOW_PERFORMANCE = "low_performance"

    fun loadOrDefault(prefs: SharedPreferences): SaveProfile {
        val raw = prefs.getString(KEY_PROFILE, null)
        if (raw.isNullOrBlank()) {
            val migrated = SaveProfile(
                unlockedLevel = prefs.getInt(LEGACY_UNLOCKED, 1).coerceIn(1, MAX_LEVEL),
                lowPerformance = prefs.getBoolean(LEGACY_LOW_PERFORMANCE, false)
            )
            save(prefs, migrated)
            return migrated
        }
        return try {
            val json = JSONObject(raw)
            val version = json.optInt("schemaVersion", -1)
            require(version == CURRENT_VERSION) { "unsupported schema $version" }
            val unlocked = json.optInt("unlockedLevel", 1).coerceIn(1, MAX_LEVEL)
            val continueLevel = if (json.isNull("continueLevel")) null else json.optInt("continueLevel", 0)
                ?.takeIf { it in 1..MAX_LEVEL }
            val profile = SaveProfile(
                schemaVersion = version,
                unlockedLevel = unlocked,
                lowPerformance = json.optBoolean("lowPerformance", false),
                continueLevel = continueLevel,
                checkpointProgress = json.optDouble("checkpointProgress", 0.0).toFloat().coerceIn(0f, 100_000f),
                checkpointPlayerX = json.optDouble("checkpointPlayerX", 150.0).toFloat().coerceIn(55f, 1160f),
                checkpointScore = json.optInt("checkpointScore", 0).coerceIn(0, 2_000_000_000),
                checkpointEncounterIndex = json.optInt("checkpointEncounterIndex", 0).coerceIn(0, 10_000),
                checkpointSpawnWave = json.optInt("checkpointSpawnWave", 0).coerceIn(0, 10_000),
                checkpointMagneticOffsets = json.optJSONArray("checkpointMagneticOffsets")?.let { offsets ->
                    buildList {
                        for (index in 0 until offsets.length().coerceAtMost(16)) {
                            add(offsets.optInt(index, 0).coerceIn(-512, 512))
                        }
                    }
                } ?: emptyList(),
                checkpointBridgeSegments = json.optJSONArray("checkpointBridgeSegments")?.let { segments ->
                    buildList {
                        for (index in 0 until segments.length().coerceAtMost(16)) {
                            val item = segments.optJSONObject(index) ?: continue
                            val id = item.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                            add(BridgeSegmentState(
                                id = id,
                                occupants = item.optInt("occupants", 0).coerceIn(0, 32),
                                warningTicksRemaining = item.optLong("warningTicksRemaining", 0L).coerceIn(0L, 10_000L),
                                collapseTicksRemaining = item.optLong("collapseTicksRemaining", 0L).coerceIn(0L, 10_000L),
                                collapsed = item.optBoolean("collapsed", false),
                                plankDeployed = item.optBoolean("plankDeployed", false),
                                edgeGrabbed = item.optBoolean("edgeGrabbed", false),
                                winchCut = item.optBoolean("winchCut", false)
                            ))
                        }
                    }
                } ?: emptyList(),
                checkpointGenericTick = json.optLong("checkpointGenericTick", 0L).coerceIn(0L, 10_000_000L),
                checkpointGenericPhase = json.optInt("checkpointGenericPhase", 0).coerceIn(0, 3),
                checkpointGenericInteractions = json.optInt("checkpointGenericInteractions", 0).coerceIn(0, 10_000),
                checkpointGenericMeter = json.optInt("checkpointGenericMeter", 0).coerceIn(0, 100),
                checkpointGenericHazardActive = json.optBoolean("checkpointGenericHazardActive", false)
            )
            if (profile.continueLevel != null && profile.continueLevel > profile.unlockedLevel) {
                profile.copy(continueLevel = null)
            } else {
                profile
            }
        } catch (error: Exception) {
            Log.w(TAG, "Corrupt or incompatible save; rebuilding defaults", error)
            val fallback = SaveProfile(
                unlockedLevel = prefs.getInt(LEGACY_UNLOCKED, 1).coerceIn(1, MAX_LEVEL),
                lowPerformance = prefs.getBoolean(LEGACY_LOW_PERFORMANCE, false)
            )
            save(prefs, fallback)
            fallback
        }
    }

    fun save(prefs: SharedPreferences, profile: SaveProfile) {
        val bounded = profile.copy(
            schemaVersion = CURRENT_VERSION,
            unlockedLevel = profile.unlockedLevel.coerceIn(1, MAX_LEVEL),
            continueLevel = profile.continueLevel?.takeIf { it in 1..MAX_LEVEL },
            checkpointProgress = profile.checkpointProgress.coerceIn(0f, 100_000f),
            checkpointPlayerX = profile.checkpointPlayerX.coerceIn(55f, 1160f),
            checkpointScore = profile.checkpointScore.coerceIn(0, 2_000_000_000),
            checkpointEncounterIndex = profile.checkpointEncounterIndex.coerceIn(0, 10_000),
            checkpointSpawnWave = profile.checkpointSpawnWave.coerceIn(0, 10_000),
            checkpointMagneticOffsets = profile.checkpointMagneticOffsets.take(16).map { it.coerceIn(-512, 512) },
            checkpointBridgeSegments = profile.checkpointBridgeSegments.take(16).map { segment ->
                segment.copy(
                    id = segment.id.take(64),
                    occupants = segment.occupants.coerceIn(0, 32),
                    warningTicksRemaining = segment.warningTicksRemaining.coerceIn(0L, 10_000L),
                    collapseTicksRemaining = segment.collapseTicksRemaining.coerceIn(0L, 10_000L)
                )
            },
            checkpointGenericTick = profile.checkpointGenericTick.coerceIn(0L, 10_000_000L),
            checkpointGenericPhase = profile.checkpointGenericPhase.coerceIn(0, 3),
            checkpointGenericInteractions = profile.checkpointGenericInteractions.coerceIn(0, 10_000),
            checkpointGenericMeter = profile.checkpointGenericMeter.coerceIn(0, 100),
            checkpointGenericHazardActive = profile.checkpointGenericHazardActive
        )
        val bridgeSegments = JSONArray()
        bounded.checkpointBridgeSegments.forEach { segment ->
            bridgeSegments.put(JSONObject()
                .put("id", segment.id)
                .put("occupants", segment.occupants)
                .put("warningTicksRemaining", segment.warningTicksRemaining)
                .put("collapseTicksRemaining", segment.collapseTicksRemaining)
                .put("collapsed", segment.collapsed)
                .put("plankDeployed", segment.plankDeployed)
                .put("edgeGrabbed", segment.edgeGrabbed)
                .put("winchCut", segment.winchCut))
        }
        val json = JSONObject()
            .put("schemaVersion", CURRENT_VERSION)
            .put("unlockedLevel", bounded.unlockedLevel)
            .put("lowPerformance", bounded.lowPerformance)
            .put("continueLevel", bounded.continueLevel)
            .put("checkpointProgress", bounded.checkpointProgress.toDouble())
            .put("checkpointPlayerX", bounded.checkpointPlayerX.toDouble())
            .put("checkpointScore", bounded.checkpointScore)
            .put("checkpointEncounterIndex", bounded.checkpointEncounterIndex)
            .put("checkpointSpawnWave", bounded.checkpointSpawnWave)
            .put("checkpointMagneticOffsets", JSONArray(bounded.checkpointMagneticOffsets))
            .put("checkpointBridgeSegments", bridgeSegments)
            .put("checkpointGenericTick", bounded.checkpointGenericTick)
            .put("checkpointGenericPhase", bounded.checkpointGenericPhase)
            .put("checkpointGenericInteractions", bounded.checkpointGenericInteractions)
            .put("checkpointGenericMeter", bounded.checkpointGenericMeter)
            .put("checkpointGenericHazardActive", bounded.checkpointGenericHazardActive)
        prefs.edit()
            .putString(KEY_PROFILE, json.toString())
            .putInt(LEGACY_UNLOCKED, bounded.unlockedLevel)
            .putBoolean(LEGACY_LOW_PERFORMANCE, bounded.lowPerformance)
            .apply()
    }
}
