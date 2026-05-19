"""Phase 4 Stage P4-C3 part А (Convention #60) — генерация иконок приложения.

Исходник: app_icon_source.png (2816×1536, горизонтальный, "100" по центру).

Что делаем:
- Background 432×432 — квадратный кроп центра + затемнение 0.6×RGB.
  Adaptive icon шаблон: foreground масштабируется системой, поэтому
  background должен быть достаточно тёмным чтобы белые лучи "100"
  читались на любой подложке (круглой/квадратной/teardrop).
- Foreground 432×432 — крупный кроп с "100" в безопасной зоне 264×264dp
  (66dp safe area × 4 для xxxhdpi mipmap density). Альфа сохраняется
  для тёмных пикселей вокруг "100" чтобы фон проявлялся.
- Legacy mipmap (mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192)
  — композитный flat PNG (фон + foreground), `ic_launcher.png` +
  `ic_launcher_round.png` (для round-mask Android < 8.0).

Запуск: python parser/scrapers/generate_app_icons.py
Требования: Pillow >= 9.0 (Image.LANCZOS, Image.alpha_composite).
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

from PIL import Image, ImageEnhance

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
SOURCE_PNG = PROJECT_ROOT / "app_icon_source.png"
RES_DIR = PROJECT_ROOT / "android" / "app" / "src" / "main" / "res"

# Adaptive icon: 432×432 = xxxhdpi (4 × 108dp). Безопасная зона 264 (4 × 66dp).
ADAPTIVE_SIZE = 432
SAFE_AREA = 264  # foreground content должен укладываться в эти границы

# Legacy mipmap размеры для Android < 8.0 (без adaptive icon).
LEGACY_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def make_background(src: Image.Image) -> Image.Image:
    """Квадратный кроп центра 1536×1536 + затемнение 0.6×."""
    w, h = src.size
    side = min(w, h)  # 1536
    left = (w - side) // 2
    top = (h - side) // 2
    square = src.crop((left, top, left + side, top + side))
    # Затемнение чтобы золотое "100" сверху ярче читалось.
    darkened = ImageEnhance.Brightness(square).enhance(0.55)
    # Лёгкое размытие/уменьшение контраста — чтобы созвездия не отвлекали.
    softened = ImageEnhance.Contrast(darkened).enhance(0.85)
    return softened.resize((ADAPTIVE_SIZE, ADAPTIVE_SIZE), Image.LANCZOS).convert("RGB")


def make_foreground(src: Image.Image) -> Image.Image:
    """Кроп с '100' в центре + ресайз в safe area.

    Размещение: '100' визуально в центре исходника на высоте ~0.45h.
    Кадрируем квадрат ~900×900 вокруг центра, ресайзим в safe area,
    помещаем в 432×432 canvas с прозрачным фоном.
    """
    w, h = src.size
    cx, cy = w // 2, int(h * 0.52)  # центр "100" слегка ниже геометрического
    crop_side = 900  # под крупное "100" с небольшим воздухом
    left = max(0, cx - crop_side // 2)
    top = max(0, cy - crop_side // 2)
    right = min(w, left + crop_side)
    bottom = min(h, top + crop_side)
    central = src.crop((left, top, right, bottom)).convert("RGBA")
    # Ресайз в SAFE_AREA.
    central = central.resize((SAFE_AREA, SAFE_AREA), Image.LANCZOS)
    # Прозрачный 432×432 canvas, foreground в центре.
    canvas = Image.new("RGBA", (ADAPTIVE_SIZE, ADAPTIVE_SIZE), (0, 0, 0, 0))
    offset = (ADAPTIVE_SIZE - SAFE_AREA) // 2
    canvas.paste(central, (offset, offset), central)
    return canvas


def make_legacy_composite(background: Image.Image, foreground: Image.Image) -> Image.Image:
    """Flat-композит для Android < 8.0 (без adaptive icon)."""
    bg = background.convert("RGBA")
    return Image.alpha_composite(bg, foreground)


def write_round_mask(square: Image.Image) -> Image.Image:
    """Применяет круглую маску к квадратной иконке (для ic_launcher_round.png)."""
    size = square.size[0]
    mask = Image.new("L", (size, size), 0)
    # PIL: рисуем белый круг диаметром = size.
    from PIL import ImageDraw

    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    result = square.convert("RGBA")
    result.putalpha(mask)
    return result


def remove_old_icons() -> None:
    """Удаляет старые ic_launcher.* во всех mipmap-папках."""
    for d in RES_DIR.iterdir():
        if d.is_dir() and d.name.startswith("mipmap-"):
            for f in d.iterdir():
                if f.name.startswith("ic_launcher") and f.suffix in (".png", ".webp"):
                    print(f"  remove {f.relative_to(PROJECT_ROOT)}")
                    f.unlink()


def write_xml(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> int:
    if not SOURCE_PNG.exists():
        print(f"ERROR: {SOURCE_PNG} not found", file=sys.stderr)
        return 1

    src = Image.open(SOURCE_PNG).convert("RGBA")
    print(f"source: {src.size}, mode={src.mode}")

    print("\n[1/4] background + foreground (432x432)")
    background = make_background(src)
    foreground = make_foreground(src)

    drawable = RES_DIR / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    background.save(drawable / "ic_launcher_background.png", optimize=True)
    foreground.save(drawable / "ic_launcher_foreground.png", optimize=True)
    print(f"  -> {drawable / 'ic_launcher_background.png'}")
    print(f"  -> {drawable / 'ic_launcher_foreground.png'}")

    print("\n[2/4] adaptive icon XML")
    adaptive_xml = '<?xml version="1.0" encoding="utf-8"?>\n' \
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n' \
        '    <background android:drawable="@drawable/ic_launcher_background"/>\n' \
        '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n' \
        '    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>\n' \
        '</adaptive-icon>\n'
    write_xml(RES_DIR / "mipmap-anydpi-v26" / "ic_launcher.xml", adaptive_xml)
    write_xml(RES_DIR / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adaptive_xml)
    print(f"  -> mipmap-anydpi-v26/ic_launcher.xml")
    print(f"  -> mipmap-anydpi-v26/ic_launcher_round.xml")

    print("\n[3/4] remove old ic_launcher.* files from mipmap-*")
    remove_old_icons()

    print("\n[4/4] legacy mipmap PNG (5 sizes, square + round)")
    composite = make_legacy_composite(background, foreground)
    for folder_name, size in LEGACY_SIZES.items():
        folder = RES_DIR / folder_name
        folder.mkdir(parents=True, exist_ok=True)
        square = composite.resize((size, size), Image.LANCZOS)
        square.save(folder / "ic_launcher.png", optimize=True)
        round_icon = write_round_mask(square)
        round_icon.save(folder / "ic_launcher_round.png", optimize=True)
        print(f"  -> {folder_name}/ic_launcher.png ({size}x{size}) + _round")

    print("\nDONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
