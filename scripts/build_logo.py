"""Build the 2:1, 1:1, and in-mod Beyond: Craftlines logos.

Requires Pillow and a supported Minecraft client JAR in the Gradle cache.
Run from any directory with: python3 scripts/build_logo.py
"""

from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter


PROJECT_ROOT = Path(__file__).resolve().parents[1]
BACKGROUND = PROJECT_ROOT / "artwork/source/planner_background.png"
LINKER = PROJECT_ROOT / "src/main/resources/assets/beyond_craftlines/textures/item/network_linker.png"
GENERATOR = PROJECT_ROOT / "artwork/source/dimension_network_generator.png"
OUTPUT = PROJECT_ROOT / "artwork"
MINECRAFT_ASCII_FONT = "assets/minecraft/textures/font/ascii.png"


def find_minecraft_client() -> Path:
    gradle_cache = Path.home() / ".gradle/caches"
    candidates = (
        gradle_cache / "forge_gradle/minecraft_repo/versions/1.20.1/client.jar",
        gradle_cache / "neoformruntime/artifacts/minecraft_1.20.1_client.jar",
        gradle_cache / "neoformruntime/artifacts/minecraft_1.21.1_client.jar",
        gradle_cache / "neoformruntime/artifacts/minecraft_26.1.2_client.jar",
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        "Minecraft client JAR not found in the Gradle cache. "
        "Run a client/build task for one supported version first."
    )


def load_minecraft_ascii() -> Image.Image:
    with ZipFile(find_minecraft_client()) as client_jar:
        return Image.open(BytesIO(client_jar.read(MINECRAFT_ASCII_FONT))).convert("RGBA")


MINECRAFT_ASCII = load_minecraft_ascii()


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_w, target_h = size
    scale = max(target_w / image.width, target_h / image.height)
    resized = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    left = (resized.width - target_w) // 2
    top = (resized.height - target_h) // 2
    return resized.crop((left, top, left + target_w, top + target_h))


def radial_glow(size: tuple[int, int], center: tuple[int, int], radius: int, color: tuple[int, int, int], opacity: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    cx, cy = center
    draw.ellipse((cx - radius // 3, cy - radius // 3, cx + radius // 3, cy + radius // 3), fill=opacity)
    mask = mask.filter(ImageFilter.GaussianBlur(radius // 2))
    layer = Image.new("RGBA", size, (*color, 0))
    layer.putalpha(mask)
    return layer


def enlarged_icon(icon: Image.Image, scale: int) -> Image.Image:
    return icon.resize((icon.width * scale, icon.height * scale), Image.Resampling.NEAREST)


def paste_icon(canvas: Image.Image, icon: Image.Image, center: tuple[int, int], glow_color: tuple[int, int, int]) -> None:
    x = center[0] - icon.width // 2
    y = center[1] - icon.height // 2

    alpha = icon.getchannel("A")
    glow_mask = alpha.resize((icon.width + 28, icon.height + 28), Image.Resampling.NEAREST)
    glow_mask = glow_mask.filter(ImageFilter.GaussianBlur(18))
    glow = Image.new("RGBA", glow_mask.size, (*glow_color, 0))
    glow.putalpha(glow_mask.point(lambda p: min(165, p)))
    canvas.alpha_composite(glow, (x - 14, y - 14))

    shadow_mask = alpha.filter(ImageFilter.GaussianBlur(8))
    shadow = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    shadow.putalpha(shadow_mask.point(lambda p: round(p * 0.72)))
    canvas.alpha_composite(shadow, (x + 12, y + 16))
    canvas.alpha_composite(icon, (x, y))


def minecraft_text_mask(text: str, pixel_scale: int) -> Image.Image:
    glyphs: list[tuple[Image.Image, int]] = []
    for character in text:
        if character == " ":
            glyphs.append((Image.new("L", (1, 8), 0), 4))
            continue
        codepoint = ord(character)
        if codepoint > 255:
            raise ValueError(f"Unsupported Minecraft ASCII character: {character!r}")
        cell_x = codepoint % 16 * 8
        cell_y = codepoint // 16 * 8
        glyph = MINECRAFT_ASCII.crop((cell_x, cell_y, cell_x + 8, cell_y + 8)).getchannel("A")
        bbox = glyph.getbbox()
        glyph_width = bbox[2] if bbox else 1
        glyphs.append((glyph.crop((0, 0, glyph_width, 8)), glyph_width + 1))

    logical_width = max(1, sum(advance for _, advance in glyphs) - 1)
    logical = Image.new("L", (logical_width, 8), 0)
    cursor = 0
    for glyph, advance in glyphs:
        logical.paste(glyph, (cursor, 0), glyph)
        cursor += advance
    return logical.resize((logical.width * pixel_scale, logical.height * pixel_scale), Image.Resampling.NEAREST)


def add_gold_title(canvas: Image.Image, text: str, font_size: int, baseline_y: int, max_width: int) -> None:
    pixel_scale = max(1, round(font_size / 8))
    title = minecraft_text_mask(text, pixel_scale)
    while title.width > max_width and pixel_scale > 1:
        pixel_scale -= 1
        title = minecraft_text_mask(text, pixel_scale)
    text_w, text_h = title.size
    x = (canvas.width - text_w) // 2
    y = baseline_y

    mask = Image.new("L", canvas.size, 0)
    mask.paste(title, (x, y), title)

    shadow_mask = mask.filter(ImageFilter.GaussianBlur(max(4, pixel_scale // 2)))
    shadow_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shifted_shadow = Image.new("L", canvas.size, 0)
    shifted_shadow.paste(shadow_mask, (5, 8))
    shadow_layer.putalpha(shifted_shadow.point(lambda p: round(p * 0.8)))
    canvas.alpha_composite(shadow_layer)

    stroke_width = max(3, pixel_scale // 2)
    stroke_mask = mask.filter(ImageFilter.MaxFilter(stroke_width * 2 + 1))
    stroke_only = ImageChops.subtract(stroke_mask, mask)
    stroke_layer = Image.new("RGBA", canvas.size, (42, 24, 5, 0))
    stroke_layer.putalpha(stroke_only)
    canvas.alpha_composite(stroke_layer)

    gradient = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    pixels = gradient.load()
    top = baseline_y
    bottom = baseline_y + max(1, text_h)
    for row in range(max(0, top), min(canvas.height, bottom + 1)):
        t = (row - top) / max(1, bottom - top)
        if t < 0.35:
            local = t / 0.35
            color = tuple(round(a + (b - a) * local) for a, b in zip((255, 245, 167), (255, 198, 55)))
        else:
            local = (t - 0.35) / 0.65
            color = tuple(round(a + (b - a) * local) for a, b in zip((255, 198, 55), (166, 91, 8)))
        for col in range(canvas.width):
            pixels[col, row] = (*color, 255)
    gradient.putalpha(mask)
    canvas.alpha_composite(gradient)

    # One-pixel warm highlight keeps the gold legible at thumbnail size.
    highlight_mask = ImageChops.subtract(mask, ImageChops.offset(mask, 0, 2))
    highlight = Image.new("RGBA", canvas.size, (255, 250, 196, 0))
    highlight.putalpha(highlight_mask.point(lambda p: round(p * 0.78)))
    canvas.alpha_composite(highlight)


def make_logo(size: tuple[int, int], out_name: str) -> None:
    background = Image.open(BACKGROUND).convert("RGB")
    base = cover(background, size).filter(ImageFilter.GaussianBlur(max(5, size[0] // 210)))
    base = ImageEnhance.Contrast(base).enhance(0.86)
    canvas = base.convert("RGBA")

    # Cool dark veil keeps the busy recipe tree secondary without hiding it.
    veil = Image.new("RGBA", size, (4, 13, 28, 118))
    canvas = Image.alpha_composite(canvas, veil)

    # Gentle vignette directs attention to the two exact game icons.
    vignette = Image.new("L", size, 0)
    vignette_draw = ImageDraw.Draw(vignette)
    inset_x, inset_y = size[0] // 7, size[1] // 7
    vignette_draw.ellipse((inset_x, inset_y, size[0] - inset_x, size[1] - inset_y), fill=205)
    vignette = ImageChops.invert(vignette.filter(ImageFilter.GaussianBlur(size[0] // 7)))
    dark_edges = Image.new("RGBA", size, (0, 3, 12, 0))
    dark_edges.putalpha(vignette.point(lambda p: round(p * 0.58)))
    canvas = Image.alpha_composite(canvas, dark_edges)

    linker = Image.open(LINKER).convert("RGBA")
    # The supplied reference is the authoritative dimension-network-generator
    # icon, enlarged 8x from its native 16x16 sprite with transparent padding.
    generator = Image.open(GENERATOR).convert("RGBA").resize((16, 16), Image.Resampling.NEAREST)

    if size[0] > size[1]:
        scale = 22
        linker_center = (size[0] * 42 // 100, size[1] * 38 // 100)
        generator_center = (size[0] * 58 // 100, size[1] * 41 // 100)
        glow_radius = size[1] * 2 // 5
    else:
        scale = 25
        linker_center = (size[0] * 40 // 100, size[1] * 34 // 100)
        generator_center = (size[0] * 60 // 100, size[1] * 41 // 100)
        glow_radius = size[0] * 2 // 5

    canvas = Image.alpha_composite(canvas, radial_glow(size, linker_center, glow_radius, (162, 52, 225), 112))
    canvas = Image.alpha_composite(canvas, radial_glow(size, generator_center, glow_radius, (25, 221, 238), 126))

    # Generator sits behind; the linker overlaps it slightly to communicate the integration.
    paste_icon(canvas, enlarged_icon(generator, scale), generator_center, (44, 232, 246))
    paste_icon(canvas, enlarged_icon(linker, scale), linker_center, (203, 83, 255))

    if size[0] > size[1]:
        add_gold_title(canvas, "Beyond Craftlines", 112, size[1] * 66 // 100, size[0] * 76 // 100)
    else:
        add_gold_title(canvas, "Beyond", 116, size[1] * 65 // 100, size[0] * 76 // 100)
        add_gold_title(canvas, "Craftlines", 116, size[1] * 76 // 100, size[0] * 76 // 100)

    OUTPUT.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUTPUT / out_name, quality=96)


make_logo((1600, 800), "beyond_craftlines_logo_2x1.png")
make_logo((1024, 1024), "beyond_craftlines_logo_1x1.png")

mod_logo = Image.open(OUTPUT / "beyond_craftlines_logo_1x1.png").convert("RGBA")
mod_logo = mod_logo.resize((128, 128), Image.Resampling.LANCZOS)
mod_logo = mod_logo.filter(ImageFilter.UnsharpMask(radius=0.8, percent=120, threshold=3))
mod_logo.save(PROJECT_ROOT / "src/main/resources/logo.png")
