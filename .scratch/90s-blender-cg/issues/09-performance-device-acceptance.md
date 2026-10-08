# 09: Full performance, offline, and device acceptance

**What to build:** Execute the complete acceptance matrix against the integrated APK and 90-second CG deliverables, then fix every observed failure before release packaging.

**Blocked by:** 04: Natural Chinese voiceover and CG audio master; 08: UI, progression feedback, and local save vertical slice

**Status:** blocked-until-05-through-08-and-device

**Audit evidence:** `.scratch/90s-blender-cg/audits/product-audit.md` — build smoke passed, but ADB reports no connected device; no device or performance criterion is claimed as passed.

- [ ] iQOO 13 cold-start, landscape safe-area, cinematic playback/skip, complete level, Boss, result, and reinstall pass with crash logs captured.
- [ ] Rapid multitouch, background/return, audio focus, corrupted save, airplane-mode, low-performance, and pressure-scene checks pass.
- [ ] Frame-time and memory evidence meets the agreed target; any visual degradation is explicitly documented and does not change combat rules.
- [ ] Any failure is linked to a corrective change and rerun evidence; a verbal “looks good” is not acceptance.
