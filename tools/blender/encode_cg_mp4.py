"""Encode the authored 2D/3D key art into a reviewable 10.8s MP4.

The APK uses the same three PNGs through CinematicRenderer. This script is a
review export only and keeps the Blender frame sequence untouched.
"""
from pathlib import Path
import shutil
import subprocess

from PIL import Image, ImageDraw, ImageFont
import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT_DIR = ROOT / "dist" / "cg" / "demo_frames"
OUT_DIR.mkdir(parents=True, exist_ok=True)
for old in OUT_DIR.glob("frame_*.png"):
    old.unlink()

WIDTH, HEIGHT, FPS = 960, 540, 24
TOTAL = 260  # 10.833s, matching CinematicRenderer's 10.8s timeline.
shots = [
    (ASSETS / "cg" / "cg_01_hangar_3d.png", 0, 102, "北线坠毁后的第七十二小时"),
    (ASSETS / "cg" / "cg_02_operative_2d.png", 102, 173, "锈港封锁区  //  灰脊小队进入"),
    (ASSETS / "cg" / "cg_03_core_title.png", 173, TOTAL, "钢火突袭  /  RUST HARBOR"),
]
font_path = Path("C:/Windows/Fonts/msyh.ttc")
font = ImageFont.truetype(str(font_path), 24) if font_path.exists() else ImageFont.load_default()
small_font = ImageFont.truetype(str(font_path), 15) if font_path.exists() else ImageFont.load_default()

def cover(image: Image.Image, zoom: float, pan: float) -> Image.Image:
    image = image.convert("RGB")
    scale = max(WIDTH / image.width, HEIGHT / image.height) * zoom
    size = (round(image.width * scale), round(image.height * scale))
    image = image.resize(size, Image.Resampling.LANCZOS)
    max_x = max(0, image.width - WIDTH)
    max_y = max(0, image.height - HEIGHT)
    x = round(max_x * (0.5 + pan * 0.5))
    y = round(max_y * 0.5)
    return image.crop((x, y, x + WIDTH, y + HEIGHT))

loaded = [(start, end, label, Image.open(path).convert("RGB")) for path, start, end, label in shots]
for frame in range(TOTAL):
    for start, end, label, image in loaded:
        if start <= frame < end:
            local = (frame - start) / max(1, end - start - 1)
            zoom = 1.0 + 0.055 * local
            pan = -0.24 + 0.48 * local
            current = cover(image, zoom, pan)
            break
    # Twelve-frame dissolves preserve continuity at the authored shot changes.
    for boundary, left_i, right_i in ((102, 0, 1), (173, 1, 2)):
        if boundary - 8 <= frame < boundary + 8:
            mix = (frame - (boundary - 8)) / 16.0
            left = cover(loaded[left_i][3], 1.055, 0.24)
            right = cover(loaded[right_i][3], 1.0, -0.24)
            current = Image.blend(left, right, mix)
            label = loaded[right_i][2]
            break
    draw = ImageDraw.Draw(current, "RGBA")
    draw.rectangle((0, HEIGHT - 86, WIDTH, HEIGHT), fill=(3, 8, 13, 155))
    draw.text((42, HEIGHT - 69), label, font=font, fill=(245, 238, 218, 245), stroke_width=1, stroke_fill=(0, 0, 0, 160))
    draw.text((42, HEIGHT - 29), "STEELFIRE ASSAULT   //   OFFLINE CINEMATIC PREVIEW", font=small_font, fill=(255, 177, 78, 235))
    current.save(OUT_DIR / f"frame_{frame + 1:04d}.png", optimize=True)

ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
output = ROOT / "dist" / "cg" / "steelfire_intro.mp4"
voice = ASSETS / "audio" / "cg" / "intro_voiceover.wav"
cmd = [
    ffmpeg, "-y", "-framerate", str(FPS), "-i", str(OUT_DIR / "frame_%04d.png"),
    "-i", str(voice), "-t", "10.8", "-c:v", "libx264", "-preset", "medium",
    "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
    "-shortest", "-movflags", "+faststart", str(output),
]
subprocess.run(cmd, check=True)
print(output)
