# 06: Weapon, enemy, vehicle, and boss combat vertical slice

**What to build:** Deliver a demonstrable combat encounter containing three weapons with different jobs, four readable enemy behaviors, an enterable/drivable/damageable vehicle, and a learnable multi-phase gatekeeper boss.

**Blocked by:** 05: Character interaction vertical slice

**Status:** implementation-ready-static-verified; device-acceptance-pending

**Audit evidence:** `.scratch/90s-blender-cg/audits/combat-slice.md` and `tools/verify_combat_slice.py`. The Kotlin slice now contains data-driven weapon roles/feedback, four authored enemy reads, a deterministic vehicle lifecycle, and phase-gated Boss weak points. Static verifier and debug compilation pass; touch/audio/device behavior remains unchecked until the full iQOO 13 acceptance run.

- [ ] Each weapon has distinct cadence, damage, ammunition, reload risk, projectile read, and feedback; switching is reliable under multitouch.
- [ ] Each enemy exposes a telegraph before damage and has a counterplay that can be learned from one encounter.
- [ ] Vehicle enter, move, attack, damage, exit, and failure/recovery are all demonstrable without a stuck state.
- [ ] Boss phases change attack patterns, arena read, weak-point window, and audio/visual cue; defeating it reaches a result state.

## Verification boundary

- Static source verifier: `python tools/verify_combat_slice.py` (pass).
- Build: `:app:assembleDebug` (pass).
- Not claimed: true multitouch, audio timing, vehicle feel, or iQOO 13 runtime acceptance.
