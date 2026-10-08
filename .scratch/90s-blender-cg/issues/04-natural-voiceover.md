# 04: Natural Chinese voiceover and CG audio master

**What to build:** Produce a 90-second Chinese cinematic audio track that follows the Blender shot timeline. It must combine a restrained narrator, short radio/comms lines, intentional silence, a quiet industrial room bed, and enough impact headroom for mechanical and combat effects. The viewer should understand the stakes and the mission without reading subtitles; speech must remain intelligible on phone speakers and headphones.

**Blocked by:** 01: Product baseline and acceptance protocol; 03: Blender 90-second complete shot and animation

**Status:** implementation complete; listening review pending

- [x] The cue sheet contains exact start times (8/18/28/38/49/59/69/78/86 seconds), speaker/voice, text, delivery rate, and shot purpose; total timeline is exactly 90.0 seconds (`dist/cg/audio_candidates/edge_voice_segments.json`).
- [x] The narration contains distinct narrator and radio/comms layers and intentional silent windows (`intro_voiceover_90s.wav`).
- [x] `tools/audio/synthesize_cg_voiceover.py` and `tools/audio/mix_cg_voiceover.py` reproduce the segments and master locally; runtime reads only the checked-in WAV.
- [x] The master is PCM WAV, 22.05 kHz, 16-bit, mono, exactly 90.0 seconds, with the no-clipping validation recorded by the audio build.
- [x] `dist/cg/steelfire_intro_90s.mp4` is encoded from the 2160 Blender frames and the master; `python tools/blender/verify_cg_render.py` reports `movie_duration=90.000s`, with Blender VSE markers placed on the same 24fps cue frames.
- [ ] A listening pass on phone speaker and headphones records no obvious cut, gap, robotic cadence, or masking by music/SFX; any failed item blocks completion.
