"""Offline verification for the 90-second Blender frame sequence and muxed movie."""

from pathlib import Path
import json
import re
import shutil
import subprocess
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover - the workspace image stack normally has Pillow
    Image = None

ROOT = Path(__file__).resolve().parents[2]
FRAME_DIR = ROOT / "dist" / "cg" / "blender_frames"
MANIFEST = ROOT / "dist" / "cg" / "steelfire_cg_shots.json"
CUES = ROOT / "dist" / "cg" / "audio_candidates" / "edge_voice_segments.json"
MOVIE = ROOT / "dist" / "cg" / "steelfire_intro_90s.mp4"
EXPECTED = 2160


def main() -> int:
    frames = sorted(FRAME_DIR.glob("frame_*.png"))
    numbers = [int(p.stem.split("_")[-1]) for p in frames]
    missing = [n for n in range(1, EXPECTED + 1) if n not in set(numbers)]
    if len(frames) != EXPECTED or missing:
        print(f"FAIL frames={len(frames)} missing={missing[:12]}")
        return 1
    if Image is not None:
        with Image.open(frames[0]) as im:
            if im.size != (960, 540):
                print(f"FAIL first frame size={im.size}")
                return 1
    if not MANIFEST.exists():
        print("FAIL shot manifest missing")
        return 1
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if data.get("fps") != 24 or data.get("duration_seconds") != 90 or data.get("frames") != EXPECTED:
        print("FAIL manifest timing")
        return 1
    if not CUES.exists():
        print("FAIL voice cue sheet missing")
        return 1
    cue_starts = [round(float(item["start"]), 3) for item in json.loads(CUES.read_text(encoding="utf-8"))]
    shot_starts = [round(float(item["start_seconds"]), 3) for item in data["shots"][1:]]
    if cue_starts != shot_starts:
        print(f"FAIL cue alignment cues={cue_starts} shots={shot_starts}")
        return 1
    if not MOVIE.exists():
        print("PASS frames and manifest; movie not encoded yet")
        return 0
    ffprobe = shutil.which("ffprobe")
    if ffprobe:
        probe = subprocess.run(
            [ffprobe, "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(MOVIE)],
            text=True, capture_output=True, check=False,
        )
        if probe.returncode != 0:
            print("FAIL ffprobe could not read movie")
            return 1
        duration = float(probe.stdout.strip())
    else:
        # Portable workspace installs often bundle ffmpeg through imageio but
        # omit ffprobe. Ask the bundled binary for its header and parse the
        # duration line; this is enough for the fixed 90-second acceptance.
        try:
            import imageio_ffmpeg
            ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
        except Exception:
            print("FAIL ffprobe unavailable and bundled ffmpeg could not be located")
            return 1
        probe = subprocess.run([ffmpeg, "-i", str(MOVIE)], text=True, capture_output=True, check=False)
        match = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", probe.stderr)
        if not match:
            print("FAIL bundled ffmpeg could not read movie duration")
            return 1
        hours, minutes, seconds = match.groups()
        duration = int(hours) * 3600 + int(minutes) * 60 + float(seconds)
    if abs(duration - 90.0) > 0.08:
        print(f"FAIL movie duration={duration:.3f}")
        return 1
    print(f"PASS frames={len(frames)} movie_duration={duration:.3f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
