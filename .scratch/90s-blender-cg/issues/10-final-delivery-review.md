# 10: Final delivery package and independent review

**What to build:** Package the finished APK and cinematic as a reproducible, reviewable handoff with every ticket's evidence, source/license record, hashes, and known limitations.

**Blocked by:** 09: Full performance, offline, and device acceptance

**Status:** blocked-device-and-listening-review

**Current evidence:** CG engineering file, 2160-frame sequence, title-safe final contact sheet, 90.000s H.264/AAC MP4, APK/SHA-256, and static verifiers are present. Final closure still requires subjective phone/headphone voice review and the iQOO 13 acceptance matrix; `adb devices -l` currently reports no device.

- [ ] Final APK installs from a clean state and matches its SHA-256 manifest.
- [ ] The 90-second H.264/AAC MP4, Blender engineering file, render script, voice master, cue sheet, and preview are present and internally consistent.
- [ ] Asset sources and generated/third-party licenses are complete; no network dependency or secret is packaged.
- [ ] An independent review checks every ticket, records pass/fail evidence, and prevents a 100% status while any criterion remains open.
