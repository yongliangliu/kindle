"""Generate the two original icons kept as PNG assets for Android 5.1.

Requires Pillow. No icon or image is extracted from another application.
"""

from pathlib import Path
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SCALE = 4


def save(image, path, size):
    destination = ROOT / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.resize((size, size), Image.Resampling.LANCZOS).save(destination)


def reader_icon():
    image = Image.new("RGBA", (192 * SCALE, 192 * SCALE), "white")
    draw = ImageDraw.Draw(image)
    s = SCALE
    draw.rounded_rectangle((12*s, 12*s, 180*s, 180*s), radius=27*s,
                           outline="black", width=8*s)
    left = [(95, 70), (78, 61), (49, 63), (49, 129), (77, 127), (95, 136)]
    right = [(95, 70), (112, 61), (141, 63), (141, 129), (113, 127), (95, 136)]
    draw.line([(x*s, y*s) for x, y in left], fill="black", width=6*s, joint="curve")
    draw.line([(x*s, y*s) for x, y in right], fill="black", width=6*s, joint="curve")
    draw.line((95*s, 70*s, 95*s, 136*s), fill="black", width=5*s)
    save(image, "apps/reader/res/drawable-nodpi/books_icon.png", 192)


def floating_bubble():
    image = Image.new("RGBA", (96 * SCALE, 96 * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    s = SCALE
    draw.rounded_rectangle((0, 0, 95*s, 95*s), radius=16*s, fill=(24, 24, 24, 235))
    draw.ellipse((17*s, 17*s, 79*s, 79*s), fill=(126, 126, 126, 255))
    draw.ellipse((22*s, 22*s, 74*s, 74*s), fill="white")
    save(image, "apps/floating-home/res/drawable-xhdpi/ic_launcher.png", 96)


reader_icon()
floating_bubble()
