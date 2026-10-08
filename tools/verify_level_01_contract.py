#!/usr/bin/env python3
"""Validate the level-01 JSON/manifest and magnetic-cover Kotlin contract."""

from __future__ import annotations

import json
import hashlib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEVEL = ROOT / "app/src/main/assets/levels/level_01.json"
MANIFEST = ROOT / "app/src/main/assets/levels/level_01/manifest.json"
KOTLIN = ROOT / "app/src/main/java/com/steelfire/assault/LevelDefinition.kt"
MECHANIC = ROOT / "app/src/main/java/com/steelfire/assault/MagneticCoverMechanic.kt"
GAME = ROOT / "app/src/main/java/com/steelfire/assault/GameView.kt"
failed: list[str] = []


def check(label: str, ok: bool) -> None:
    if ok:
        print(f"PASS {label}")
    else:
        failed.append(label)
        print(f"FAIL {label}")


level = json.loads(LEVEL.read_text(encoding="utf-8"))
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
kotlin = KOTLIN.read_text(encoding="utf-8")
mechanic = MECHANIC.read_text(encoding="utf-8")
game = GAME.read_text(encoding="utf-8")

check("level identity", level["id"] == "level_01_rust_harbor_departure" and level["index"] == 1)
check("magnetic-cover mechanic", level["new_mechanic"]["id"] == "magnetic_cover")
check("magnetic boxes", len(level["new_mechanic"]["boxes"]) >= 2)
check("ordered beats", all(a["x"] < b["x"] for a, b in zip(level["beats"], level["beats"][1:])))
check("ordered checkpoints", all(a["x"] < b["x"] for a, b in zip(level["checkpoints"], level["checkpoints"][1:])))
check("manifest link", level["asset_manifest"] == "levels/level_01/manifest.json")
check("manifest local assets", manifest["offline_only"] is True and all(
    not re.search(r"https?://|cdn|remote", item.get("path", ""), re.I)
    for item in manifest["assets"] + manifest["audio"]
))
check("manifest files exist", all((ROOT / "app/src/main/assets" / item["path"]).is_file()
                                   for item in manifest["assets"] + manifest["audio"]))
check("manifest hashes and generators", all(
    isinstance(item.get("sha256"), str) and len(item["sha256"]) == 64
    and isinstance(item.get("size_bytes"), int) and item["size_bytes"] > 0
    and isinstance(item.get("generator"), str) and isinstance(item.get("fallback"), str)
    for item in manifest["assets"] + manifest["audio"]
))
hashes_match = all(
    hashlib.sha256((ROOT / "app/src/main/assets" / item["path"]).read_bytes()).hexdigest() == item["sha256"]
    and (ROOT / "app/src/main/assets" / item["path"]).stat().st_size == item["size_bytes"]
    for item in manifest["assets"] + manifest["audio"]
)
check("manifest hashes match local files", hashes_match)
check("strict parser", "rejectUnknown" in kotlin and "LevelDefinitionParser" in kotlin)
check("fixed-step reducer", "FIXED_STEP_TICKS" in mechanic and "MagneticCoverMechanic" in mechanic)
check("no wall-clock mechanic", not re.search(r"System\.currentTimeMillis|System\.nanoTime|sleep\(", mechanic))
check("runtime level loader fallback", "LevelDefinitionLoader.loadLevel01(context.assets)" in game and
      "levelDefinition?.title" in game)
check("runtime mechanic state", "magneticState" in game and "MagneticCoverMechanic.initialState" in game)
check("runtime mechanic fixed-step command", "MagneticCoverMechanic.step" in game and
      "MagneticCoverCommand.ShootLock" in game)
check("runtime lock collision", "magneticLockHit" in game and "consumeMagneticEvents" in game)
check("runtime cover collision", "magneticCoverBlocks" in game and "continue" in game[game.find("for (bullet in bullets.filter { it.enemy") : game.find("for (bullet in bullets.filter { it.enemy") + 900])
check("runtime mechanic rendering", "drawMagneticCover" in game and "磁锁" in game)
check("mechanic checkpoint persistence", "checkpointMagneticOffsets" in game or
      "checkpointMagneticOffsets" in (ROOT / "app/src/main/java/com/steelfire/assault/SaveProfile.kt").read_text(encoding="utf-8"))

if failed:
    raise SystemExit("level-01 contract verification failed")
print("Level-01 data contract verification passed")
