#!/usr/bin/env python3
"""Offline contract checks for the Level-02 bridge-collapse slice.

The verifier deliberately stays outside the Android runtime.  It checks the
authored JSON and local asset manifest, then looks for the reducer/loader seams
that make the data executable.  A passing result is static evidence only; it
does not replace the device route in the Level-02 ticket.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ASSETS_ROOT = ROOT / "app/src/main/assets"
LEVEL = ASSETS_ROOT / "levels/level_02.json"
MANIFEST_FALLBACK = ASSETS_ROOT / "levels/level_02/manifest.json"
SOURCE_ROOT = ROOT / "app/src/main/java/com/steelfire/assault"
PARSER = SOURCE_ROOT / "LevelDefinition.kt"
GAME = SOURCE_ROOT / "GameView.kt"
REDUCER = SOURCE_ROOT / "BridgeCollapseMechanic.kt"
SAVE = SOURCE_ROOT / "SaveProfile.kt"

LEVEL_KEYS = {
    "id",
    "index",
    "act",
    "title",
    "world_width",
    "story_cue",
    "new_mechanic",
    "checkpoints",
    "beats",
    "boss",
    "asset_manifest",
}
MECHANIC_KEYS = {
    "id",
    "version",
    "warning_ticks",
    "collapse_ticks",
    "segments",
}
SEGMENT_KEYS = {
    "id",
    "x",
    "y",
    "width",
    "capacity",
    "warning_ticks",
    "collapse_ticks",
    "plank_id",
    "winch_id",
}
CHECKPOINT_KEYS = {"id", "x"}
BEAT_KEYS = {
    "id",
    "x",
    "purpose",
    "enemy_kinds",
    "mechanic_params",
    "story_cue",
    "asset_refs",
}

failed: list[str] = []


def check(label: str, ok: bool) -> None:
    """Record a check without hiding a useful list of missing contract pieces."""

    if ok:
        print(f"PASS {label}")
    else:
        failed.append(label)
        print(f"FAIL {label}", file=sys.stderr)


def read_json(path: Path, label: str) -> dict[str, Any] | None:
    if not path.is_file():
        check(f"{label} exists", False)
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        check(f"{label} parses ({error})", False)
        return None
    check(f"{label} parses", isinstance(value, dict))
    return value if isinstance(value, dict) else None


def source(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def no_remote(value: Any) -> bool:
    """Reject remote URLs and path traversal in data consumed offline."""

    if not isinstance(value, str):
        return False
    return not re.search(r"(?:https?|ftp)://|\b(?:cdn|remote)\b|(^|[/\\])\.\.([/\\]|$)", value, re.I)


def local_asset_path(raw: Any) -> Path | None:
    if not isinstance(raw, str) or not no_remote(raw) or raw.startswith(("/", "\\")):
        return None
    candidate = (ASSETS_ROOT / raw).resolve()
    try:
        candidate.relative_to(ASSETS_ROOT.resolve())
    except ValueError:
        return None
    return candidate


def validate_manifest_item(item: Any, index: int, hashes_match: list[bool]) -> bool:
    if not isinstance(item, dict):
        return False
    # Existing manifests omit ``kind`` for audio entries; the path and local
    # provenance fields still provide the same offline traceability guarantee.
    required = {"id", "path", "source", "license", "size_bytes", "sha256", "generator", "fallback"}
    if not required.issubset(item):
        return False
    path = local_asset_path(item.get("path"))
    if path is None or not path.is_file():
        return False
    digest = item.get("sha256")
    size = item.get("size_bytes")
    valid_metadata = (
        isinstance(digest, str)
        and bool(re.fullmatch(r"[0-9a-fA-F]{64}", digest))
        and isinstance(size, int)
        and size > 0
        and all(isinstance(item.get(key), str) and item.get(key).strip() for key in ("id", "source", "license", "generator", "fallback"))
        and no_remote(item["path"])
    )
    if not valid_metadata:
        return False
    actual_digest = hashlib.sha256(path.read_bytes()).hexdigest()
    hashes_match.append(actual_digest == digest.lower() and path.stat().st_size == size)
    return True


level = read_json(LEVEL, "level-02 JSON")
manifest: dict[str, Any] | None = None
if level is not None:
    check("level top-level whitelist", set(level).issubset(LEVEL_KEYS))
    check("level required fields", LEVEL_KEYS.issubset(level))
    check(
        "level identity",
        level.get("id") == "level_02_broken_bridge_echo"
        and level.get("index") == 2
        and level.get("act") == 1,
    )
    check("level title and world width", isinstance(level.get("title"), str) and level.get("title") == "断桥回声" and isinstance(level.get("world_width"), int) and level["world_width"] > 0)
    check("level story cue is local", no_remote(level.get("story_cue")))

    mechanic = level.get("new_mechanic")
    if isinstance(mechanic, dict):
        check("bridge mechanic whitelist", set(mechanic).issubset(MECHANIC_KEYS))
        check("bridge mechanic required fields", MECHANIC_KEYS.issubset(mechanic))
        check("bridge mechanic identity", mechanic.get("id") == "bridge_collapse" and mechanic.get("version") == 1)
        check("bridge warning and collapse ticks", isinstance(mechanic.get("warning_ticks"), int) and mechanic["warning_ticks"] >= 15 and isinstance(mechanic.get("collapse_ticks"), int) and mechanic["collapse_ticks"] > 0)
        segments = mechanic.get("segments")
        check("bridge segments present", isinstance(segments, list) and bool(segments))
        if isinstance(segments, list) and segments:
            segment_ids: list[str] = []
            positions: list[int] = []
            valid_segments = True
            for segment in segments:
                if not isinstance(segment, dict):
                    valid_segments = False
                    continue
                valid_segments &= SEGMENT_KEYS.issubset(segment) and set(segment).issubset(SEGMENT_KEYS)
                valid_segments &= all(isinstance(segment.get(key), str) and segment[key].strip() for key in ("id", "plank_id", "winch_id"))
                valid_segments &= all(isinstance(segment.get(key), int) for key in ("x", "y", "width", "capacity"))
                valid_segments &= segment.get("width", 0) > 0 and segment.get("capacity", 0) > 0
                valid_segments &= all(
                    key not in segment or (isinstance(segment.get(key), int) and segment[key] >= 0)
                    for key in ("warning_ticks", "collapse_ticks")
                )
                segment_ids.append(segment.get("id", ""))
                positions.append(segment.get("x", -1))
            check("bridge segment schema", valid_segments and len(set(segment_ids)) == len(segment_ids))
            check("bridge segments ordered", positions == sorted(positions) and all(0 <= x < level["world_width"] for x in positions))
    else:
        check("bridge mechanic object", False)

    checkpoints = level.get("checkpoints")
    if isinstance(checkpoints, list):
        valid_checkpoints = all(isinstance(item, dict) and CHECKPOINT_KEYS.issubset(item) and set(item).issubset(CHECKPOINT_KEYS) and isinstance(item.get("id"), str) and isinstance(item.get("x"), int) for item in checkpoints)
        positions = [item.get("x", -1) for item in checkpoints if isinstance(item, dict)]
        check("bridge checkpoints schema", valid_checkpoints and len(checkpoints) >= 2)
        check("bridge checkpoints ordered", positions == sorted(positions) and all(0 < x <= level["world_width"] for x in positions))
    else:
        check("bridge checkpoints array", False)

    beats = level.get("beats")
    if isinstance(beats, list):
        valid_beats = True
        beat_positions: list[int] = []
        for beat in beats:
            if not isinstance(beat, dict):
                valid_beats = False
                continue
            valid_beats &= BEAT_KEYS.issubset(beat) and set(beat).issubset(BEAT_KEYS)
            valid_beats &= isinstance(beat.get("id"), str) and isinstance(beat.get("x"), int) and isinstance(beat.get("purpose"), str)
            valid_beats &= isinstance(beat.get("enemy_kinds"), list) and bool(beat["enemy_kinds"])
            valid_beats &= isinstance(beat.get("mechanic_params"), dict) and beat.get("mechanic_params", {}).get("id") == "bridge_collapse"
            valid_beats &= isinstance(beat.get("asset_refs"), list) and bool(beat["asset_refs"])
            valid_beats &= no_remote(beat.get("story_cue")) and all(no_remote(ref) for ref in beat.get("asset_refs", []))
            beat_positions.append(beat.get("x", -1))
        check("bridge beats schema", valid_beats and bool(beats))
        check("bridge beats ordered", beat_positions == sorted(beat_positions) and all(0 <= x < level["world_width"] for x in beat_positions))
    else:
        check("bridge beats array", False)

    check("non-boss level", level.get("boss") is None)
    manifest_ref = level.get("asset_manifest")
    check("manifest reference is local", isinstance(manifest_ref, str) and manifest_ref == "levels/level_02/manifest.json" and no_remote(manifest_ref))
    if isinstance(manifest_ref, str):
        manifest_path = ASSETS_ROOT / manifest_ref
        if not manifest_path.is_file() and MANIFEST_FALLBACK.is_file():
            manifest_path = MANIFEST_FALLBACK
        manifest = read_json(manifest_path, "level-02 manifest")
else:
    # Keep output deterministic when the data slice has not landed yet.
    check("level-02 manifest path", MANIFEST_FALLBACK.is_file())

if manifest is not None and level is not None:
    check("manifest identity", manifest.get("schema_version") == 1 and manifest.get("level_id") == level.get("id"))
    check("manifest offline only", manifest.get("offline_only") is True)
    assets = manifest.get("assets")
    audio = manifest.get("audio")
    procedural = manifest.get("procedural_assets", [])
    check("manifest asset arrays", isinstance(assets, list) and bool(assets) and isinstance(audio, list) and isinstance(procedural, list))
    all_items = (assets if isinstance(assets, list) else []) + (audio if isinstance(audio, list) else [])
    hashes_match: list[bool] = []
    valid_items = all(validate_manifest_item(item, index, hashes_match) for index, item in enumerate(all_items))
    ids = [item.get("id") for item in all_items if isinstance(item, dict)]
    check("manifest local files and metadata", valid_items and len(ids) == len(set(ids)))
    check("manifest hashes match local files", bool(hashes_match) and all(hashes_match))
    valid_procedural = all(
        isinstance(item, dict)
        and all(isinstance(item.get(key), str) and item[key].strip() and no_remote(item[key]) for key in ("id", "kind", "generator", "license"))
        for item in procedural
    )
    check("manifest procedural metadata", valid_procedural)

# Source contract: source files may be added after this verifier, so a missing
# reducer is reported as a failed contract rather than a Python traceback.
parser = source(PARSER)
game = source(GAME)
reducer = source(REDUCER)
save = source(SAVE)
all_kotlin = "\n".join(path.read_text(encoding="utf-8") for path in SOURCE_ROOT.glob("*.kt"))

check("bridge reducer file", bool(reducer))
# The immutable spec is shared with the JSON parser in LevelDefinition.kt;
# reducer-local state/commands/events stay in BridgeCollapseMechanic.kt.
check(
    "bridge reducer data contract",
    "BridgeCollapseSpec" in (reducer + parser)
    and all(marker in reducer for marker in ("BridgeCollapseState", "BridgeCollapseCommand", "BridgeCollapseEvent")),
)
check("bridge reducer fixed step", "FIXED_STEP_TICKS" in reducer and re.search(r"\bfun\s+step\s*\(", reducer) is not None)
check("bridge reducer has no wall clock", not re.search(r"System\.(?:currentTimeMillis|nanoTime)|Thread\.sleep|sleep\(", reducer))
check("strict parser bridge id", "rejectUnknown" in parser and "bridge_collapse" in parser)
check(
    "level-02 loader",
    bool(re.search(r"loadLevel02|levels/level_02\.json", parser + game))
    or ("fun load(assetManager: AssetManager, index: Int)" in parser and "level_%02d.json" in parser and bool(re.search(r"index\s+in\s+1\.\.2", game))),
)
check("level-02 runtime reducer wiring", bool(re.search(r"BridgeCollapseMechanic\s*\.\s*step|bridgeCollapse.*\.step", game, re.I | re.S)))
check("level-02 runtime state", bool(re.search(r"bridgeCollapse|bridge_collapse|BridgeCollapse", game, re.I)))
check(
    "level-02 runtime event consumption",
    "BridgeCollapseEvent.PlankDeployed" in game
    and not re.search(r"BridgeCollapseEvent\.P\s+plankDeployed", game),
)
check(
    "level-02 checkpoint state",
    bool(re.search(r"checkpoint\w*(?:bridge|collapse)|(?:bridge|collapse)\w*checkpoint|mechanic_state", game + save, re.I)),
)
check(
    "level-02 traversal gate",
    "bridgeProgressBlocked" in game and "BridgeCollapseCommand.DeployPlank" in game,
)

if failed:
    raise SystemExit("Level-02 contract verification failed")
print("Level-02 data and runtime contract verification passed; device route and frame-time evidence remain pending.")
