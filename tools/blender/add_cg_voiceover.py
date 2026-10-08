"""Attach the 90-second CG voice master and cue markers inside a Blender scene.

Usage:
  blender --background --python tools/blender/add_cg_voiceover.py -- \
      --blend art/blender/steelfire_cg_scene.blend --out art/blender/steelfire_cg_scene_voiced.blend

The helper does not render. It only makes the VSE strip/markers reproducible so the
same WAV is used for previews and the final MP4. Runtime APK playback stays offline.
"""
from __future__ import annotations
import argparse, json, pathlib, sys, bpy

ROOT = pathlib.Path(__file__).resolve().parents[2]
AUDIO = ROOT / 'app/src/main/assets/audio/cg/intro_voiceover_90s.wav'
CUES = ROOT / 'dist/cg/audio_candidates/edge_voice_segments.json'
FPS = 24


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--blend', required=True)
    parser.add_argument('--out', required=True)
    raw = sys.argv
    raw = raw[raw.index('--') + 1:] if '--' in raw else raw[1:]
    args = parser.parse_args(raw)
    bpy.ops.wm.open_mainfile(filepath=str(pathlib.Path(args.blend).resolve()))
    scene = bpy.context.scene
    scene.render.fps = FPS
    scene.frame_start = 1
    scene.frame_end = 90 * FPS
    seq = scene.sequence_editor_create()
    # Avoid duplicate strips when the helper is rerun.
    for strip in list(seq.strips_all):
        if strip.name == 'steelfire_cg_voiceover_90s':
            seq.strips.remove(strip)
    seq.strips.new_sound(
        name='steelfire_cg_voiceover_90s',
        filepath=str(AUDIO.resolve()),
        channel=1,
        frame_start=1,
    )
    for marker in list(scene.timeline_markers):
        if marker.name.startswith('VO_'):
            scene.timeline_markers.remove(marker)
    cues = json.loads(CUES.read_text(encoding='utf-8'))
    for cue in cues:
        frame = 1 + round(float(cue['start']) * FPS)
        scene.timeline_markers.new(f"VO_{cue['id']}_{cue['speaker']}", frame=frame)
    scene.render.ffmpeg.audio_codec = 'AAC'
    scene.render.ffmpeg.audio_bitrate = 192
    bpy.ops.wm.save_as_mainfile(filepath=str(pathlib.Path(args.out).resolve()))
    print(f'attached {AUDIO} and {len(cues)} cue markers')


if __name__ == '__main__':
    main()

