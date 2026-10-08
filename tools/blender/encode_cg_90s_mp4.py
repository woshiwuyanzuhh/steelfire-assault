"""Encode the rendered 90-second Blender frame sequence with the local voice master.

Blender stays responsible for geometry, materials, camera and frame rendering;
this deterministic offline step only muxes PNG frames and the checked-in WAV.
It never fetches media over the network.
"""

from pathlib import Path
import subprocess
import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[2]
FRAMES = ROOT / "dist" / "cg" / "blender_frames" / "frame_%04d.png"
AUDIO = ROOT / "app" / "src" / "main" / "assets" / "audio" / "cg" / "intro_voiceover_90s.wav"
OUTPUT = ROOT / "dist" / "cg" / "steelfire_intro_90s.mp4"
FPS = 24
DURATION = 90


def main() -> None:
    if not AUDIO.exists():
        raise SystemExit(f"Missing audio master: {AUDIO}")
    first = FRAMES.parent / "frame_0001.png"
    if not first.exists():
        raise SystemExit(f"Missing rendered frames: {first}")
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [
        ffmpeg,
        "-y",
        "-framerate", str(FPS),
        "-start_number", "1",
        "-i", str(FRAMES),
        "-i", str(AUDIO),
        "-t", str(DURATION),
        "-map", "0:v:0",
        "-map", "1:a:0",
        "-c:v", "libx264",
        "-preset", "medium",
        "-crf", "18",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "192k",
        "-ar", "22050",
        "-ac", "1",
        "-shortest",
        "-movflags", "+faststart",
        str(OUTPUT),
    ]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(cmd, check=True)
    print(f"Encoded {OUTPUT}")


if __name__ == "__main__":
    main()
