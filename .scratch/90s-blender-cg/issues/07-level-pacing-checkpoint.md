# 07: Level pacing and checkpoint vertical slice

**What to build:** Build one authored first level that teaches the controls, escalates encounters, uses cover and supplies, introduces the vehicle, pays off with the boss, and supports a meaningful checkpoint retry.

**Blocked by:** 06: Weapon, enemy, vehicle, and boss combat vertical slice

**Status:** implementation-ready-static-verified; device-acceptance-pending

**Audit evidence:** `.scratch/90s-blender-cg/audits/level-slice.md`, `tools/verify_level_pacing.py`. The first level now has a fixed authored beat table, deterministic encounter consumption, a data-backed vehicle/Boss beat, and checkpoint restoration of the consumed beat index. Device playthrough and frame-time evidence remain open.

- [ ] A first-time player can reach the first checkpoint while learning movement, firing, jump/crouch, and weapon choice without an unexplained damage spike.
- [ ] The level has authored encounter beats, camera bounds, cover, destructible or readable set dressing, supplies, an elite beat, vehicle beat, boss arena, and a result transition.
- [x] Static data and runtime wiring define authored encounter beats, vehicle/Boss beats, and documented checkpoints.
- [x] Static retry path restores the checkpoint progress, score, consumed beat index, and legacy wave index without replaying the 90-second CG or re-consuming completed beats.
- [ ] A recorded playthrough demonstrates a complete start-to-result loop at the target frame-rate budget.

## Verification boundary

- Static source verifier: `python tools/verify_level_pacing.py` (pass).
- Build: `:app:compileDebugKotlin` (pass).
- Not claimed: first-time player damage curve, touch/audio behavior, complete start-to-result recording, or iQOO 13 frame-rate acceptance.
