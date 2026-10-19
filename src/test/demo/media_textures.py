import functools
import io
import math
import zipfile
from dataclasses import dataclass
from pathlib import Path
from types import TracebackType

from PIL import Image, ImageDraw, ImageFont

Point = tuple[float, float]

ICON: int = 16
SCALE: int = 4
SUPERSAMPLE: int = 4
TEXTURES: str = 'assets/minecraft/textures/'
SHADES: tuple[float, float, float] = (1.0, 0.8, 0.6)
HALF_WIDTH: float = 8.0 * math.sqrt(3.0) / 2.0
HALF_DEPTH: float = 4.0
HEIGHT: float = 8.0
GLINT: tuple[int, int, int] = (150, 90, 230)
GLINT_BASE: float = 0.14
GLINT_BAND: float = 0.10
INK: tuple[int, int, int, int] = (64, 64, 64, 255)
SHADOW: tuple[int, int, int, int] = (63, 63, 63, 255)
WHITE: tuple[int, int, int, int] = (255, 255, 255, 255)
TAG_MEMBERS: dict[str, str] = {'logs': 'oak_log', 'saplings': 'oak_sapling', 'planks': 'oak_planks'}
SPRITES: dict[str, str] = {'oak_sapling': 'block/oak_sapling'}
CUBES: dict[str, tuple[str, str, str]] = {
    'snow_block': ('snow', 'snow', 'snow'),
    'sticky_piston': ('piston_top_sticky', 'piston_side', 'piston_side'),
    'carved_pumpkin': ('pumpkin_top', 'carved_pumpkin', 'pumpkin_side'),
}
CHESTS: dict[str, str] = {'chest': 'entity/chest/normal', 'copper_chest': 'entity/chest/copper'}
SHIELD: str = 'entity/shield/shield_base_nopattern'
SHIELD_FACE: tuple[int, int, int, int] = (1, 1, 13, 23)
CRAFTING_TABLE: str = 'gui/container/crafting_table'
PANEL_SIZE: tuple[int, int] = (176, 166)
PANEL_EDGE: int = 4
CONTENT_BOX: tuple[int, int, int, int] = (29, 16, 145, 70)
ARROW_BOX: tuple[int, int, int, int] = (90, 35, 112, 50)
RESULT_BOX: tuple[int, int, int, int] = (119, 30, 145, 56)
CHEST_SIZE: tuple[float, float, float] = (14.0 / 16.0, 14.0 / 16.0, 14.0 / 16.0)
CHEST_TOP: tuple[int, int, int, int] = (28, 0, 42, 14)
CHEST_LID_FRONT: tuple[int, int, int, int] = (42, 14, 56, 19)
CHEST_BASE_FRONT: tuple[int, int, int, int] = (42, 33, 56, 43)
CHEST_LID_SIDE: tuple[int, int, int, int] = (28, 14, 42, 19)
CHEST_BASE_SIDE: tuple[int, int, int, int] = (28, 33, 42, 43)
CHEST_LOCK: tuple[int, int, int, int] = (1, 1, 3, 5)
CHEST_LOCK_AT: tuple[int, int] = (6, 3)
CHEST_BASE_AT: int = 4
CHEST_FACE: int = 14


class MissingTexture(RuntimeError):
    pass


@dataclass(frozen=True)
class CustomItem:
    name: str
    base: str
    tint: str | None
    tier: int | None


@functools.cache
def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.load_default(size=size)


def text(canvas: Image.Image, position: tuple[int, int], value: str, size: int, fill: tuple[int, int, int, int] = INK, anchor: str = 'la',
         shadow: bool = False) -> None:
    draw: ImageDraw.ImageDraw = ImageDraw.Draw(canvas)
    if shadow:
        draw.text((position[0] + max(1, size // 8), position[1] + max(1, size // 8)), value, font=font(size), fill=SHADOW, anchor=anchor)
    draw.text(position, value, font=font(size), fill=fill, anchor=anchor)


def hex_color(value: str) -> tuple[int, int, int]:
    text: str = value.lstrip('#')
    return int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16)


class Jar:
    def __init__(self, path: Path) -> None:
        self.path: Path = path
        self.archive: zipfile.ZipFile = zipfile.ZipFile(path)
        self.names: frozenset[str] = frozenset(self.archive.namelist())
        self.cache: dict[str, Image.Image] = {}

    def __enter__(self) -> 'Jar':
        return self

    def __exit__(self, kind: type[BaseException] | None, value: BaseException | None, trace: TracebackType | None) -> None:
        self.archive.close()

    def has(self, texture: str) -> bool:
        return TEXTURES + texture + '.png' in self.names

    def image(self, texture: str) -> Image.Image:
        cached: Image.Image | None = self.cache.get(texture)
        if cached is None:
            entry: str = TEXTURES + texture + '.png'
            if entry not in self.names:
                raise MissingTexture(texture + ' is not in ' + str(self.path))
            cached = Image.open(io.BytesIO(self.archive.read(entry))).convert('RGBA')
            self.cache[texture] = cached
        return cached.copy()

    def frame(self, texture: str) -> Image.Image:
        image: Image.Image = self.image(texture)
        width, height = image.size
        return image.crop((0, 0, width, width)) if height > width and height % width == 0 else image


def shade(image: Image.Image, factor: float) -> Image.Image:
    red, green, blue, alpha = image.split()
    scale: list[int] = [round(level * factor) for level in range(256)]
    return Image.merge('RGBA', (red.point(scale), green.point(scale), blue.point(scale), alpha))


def sample(face: Image.Image, u: float, v: float) -> tuple[int, int, int, int]:
    width, height = face.size
    return face.getpixel((min(width - 1, max(0, int(u * width))), min(height - 1, max(0, int(v * height)))))


def iso_box(top: Image.Image, left: Image.Image, right: Image.Image, size: tuple[float, float, float] = (1.0, 1.0, 1.0), res: int = 1,
            camera: 'IsoCamera | None' = None) -> Image.Image:
    view: IsoCamera = camera if camera is not None else ISO
    extent_x, extent_y, extent_z = size
    x0: float = (1.0 - extent_x) / 2.0
    x1: float = x0 + extent_x
    z0: float = (1.0 - extent_z) / 2.0
    z1: float = z0 + extent_z
    faces: tuple[Image.Image, Image.Image, Image.Image] = (shade(top, SHADES[0]), shade(left, SHADES[1]), shade(right, SHADES[2]))
    pixels: int = ICON * res
    canvas: Image.Image = Image.new('RGBA', (pixels, pixels), (0, 0, 0, 0))
    a: float = view.half_width * res
    b: float = view.half_depth * res
    s: float = view.height * res
    origin_x: float = pixels / 2
    origin_y: float = (ICON / 2 + (view.height - 2 * view.half_depth) / 2) * res
    for row in range(pixels):
        for column in range(pixels):
            screen_x: float = column + 0.5 - origin_x
            screen_y: float = row + 0.5 - origin_y
            x: float = ((screen_y + extent_y * s) / b + screen_x / a) / 2.0
            z: float = ((screen_y + extent_y * s) / b - screen_x / a) / 2.0
            if x0 <= x <= x1 and z0 <= z <= z1:
                canvas.putpixel((column, row), sample(faces[0], (x - x0) / extent_x, (z - z0) / extent_z))
                continue
            x = (screen_x + a * z1) / a
            y: float = (b * x - (screen_y - b * z1)) / s
            if x0 <= x <= x1 and 0.0 <= y <= extent_y:
                canvas.putpixel((column, row), sample(faces[1], (x - x0) / extent_x, 1.0 - y / extent_y))
                continue
            z = (a * x1 - screen_x) / a
            y = (b * z - (screen_y - b * x1)) / s
            if z0 <= z <= z1 and 0.0 <= y <= extent_y:
                canvas.putpixel((column, row), sample(faces[2], (z1 - z) / extent_z, 1.0 - y / extent_y))
    return canvas


def front_box(top: Image.Image, front: Image.Image, res: int = 1, depth: int = 10, height: int = 6) -> Image.Image:
    faces: tuple[Image.Image, Image.Image] = (shade(top, SHADES[0]), shade(front, SHADES[1]))
    across: int = ICON * res
    back: int = depth * res
    down: int = height * res
    canvas: Image.Image = Image.new('RGBA', (across, back + down), (0, 0, 0, 0))
    for row in range(back + down):
        for column in range(across):
            if row < back:
                canvas.putpixel((column, row), sample(faces[0], (column + 0.5) / across, (row + 0.5) / back))
            else:
                canvas.putpixel((column, row), sample(faces[1], (column + 0.5) / across, (row - back + 0.5) / down))
    return canvas


@dataclass(frozen=True)
class IsoCamera:
    half_width: float
    half_depth: float
    height: float
    step_x: int
    step_y: int
    step_z: int
    extent_x: int = ICON
    extent_y: int = ICON

    def world(self, x: float, y: float, z: float) -> Point:
        return (x - z) * self.step_x, (x + z) * self.step_y - y * self.step_z

    def anchor(self, i: int, j: int, k: int) -> Point:
        corner_x, corner_y = self.world(i, k, j)
        return corner_x - ICON / 2, corner_y - (ICON / 2 + (self.height - 2 * self.half_depth) / 2)

    def order(self, i: int, j: int, k: int) -> tuple[int, int, int]:
        return i + j + k, k, i

    def box(self, top: Image.Image, left: Image.Image, right: Image.Image, size: tuple[float, float, float] = (1.0, 1.0, 1.0), res: int = 1) -> Image.Image:
        return iso_box(top, left, right, size, res, self)


@dataclass(frozen=True)
class FrontCamera:
    depth: int
    height: int

    @property
    def extent_x(self) -> int:
        return ICON

    @property
    def extent_y(self) -> int:
        return self.depth + self.height

    def world(self, x: float, y: float, z: float) -> Point:
        return x * ICON, z * self.depth - y * self.height

    def anchor(self, i: int, j: int, k: int) -> Point:
        return self.world(i, k + 1, j)

    def order(self, i: int, j: int, k: int) -> tuple[int, int, int]:
        return j, k, i

    def box(self, top: Image.Image, left: Image.Image, right: Image.Image, size: tuple[float, float, float] = (1.0, 1.0, 1.0), res: int = 1) -> Image.Image:
        return front_box(top, left, res, self.depth, self.height)


ISO: IsoCamera = IsoCamera(HALF_WIDTH, HALF_DEPTH, HEIGHT, 7, 4, 8)


def downsample(image: Image.Image) -> Image.Image:
    return image.resize((ICON, ICON), Image.BOX)


def cube(top: Image.Image, left: Image.Image, right: Image.Image) -> Image.Image:
    return downsample(iso_box(top, left, right, res=SUPERSAMPLE))


def top_face(top: Image.Image, res: int = 1, camera: IsoCamera | None = None) -> Image.Image:
    hidden: Image.Image = Image.new('RGBA', (1, 1), (0, 0, 0, 0))
    return iso_box(top, hidden, hidden, res=res, camera=camera)


def glint(icon: Image.Image) -> Image.Image:
    result: Image.Image = icon.copy()
    for row in range(icon.height):
        for column in range(icon.width):
            red, green, blue, alpha = icon.getpixel((column, row))
            if alpha == 0:
                continue
            weight: float = GLINT_BASE + (GLINT_BAND if (column + row) % 8 < 3 else 0.0)
            result.putpixel((column, row), (round(red + (GLINT[0] - red) * weight), round(green + (GLINT[1] - green) * weight),
                                            round(blue + (GLINT[2] - blue) * weight), alpha))
    return result


def tinted(overlay: Image.Image, color: tuple[int, int, int]) -> Image.Image:
    red, green, blue, alpha = overlay.split()
    return Image.merge('RGBA', (red.point([round(level * color[0] / 255) for level in range(256)]),
                                green.point([round(level * color[1] / 255) for level in range(256)]),
                                blue.point([round(level * color[2] / 255) for level in range(256)]), alpha))


class Icons:
    def __init__(self, jar: Jar, custom: dict[str, CustomItem]) -> None:
        self.jar: Jar = jar
        self.custom: dict[str, CustomItem] = custom
        self.cache: dict[str, Image.Image] = {}

    def icon(self, item: str) -> Image.Image:
        cached: Image.Image | None = self.cache.get(item)
        if cached is None:
            custom: CustomItem | None = self.custom.get(item)
            cached = self.made(custom) if custom is not None else self.vanilla(item)
            self.cache[item] = cached
        return cached.copy()

    def large(self, item: str, res: int) -> Image.Image:
        if item in self.custom:
            return self.icon(item).resize((ICON * res, ICON * res), Image.NEAREST)
        name: str = self.member(item)
        if name == 'shield' or name in SPRITES or (name not in CHESTS and self.jar.has('item/' + name)):
            return self.icon(item).resize((ICON * res, ICON * res), Image.NEAREST)
        return self.box(name, res)

    def made(self, custom: CustomItem) -> Image.Image:
        name: str = custom.base.lower()
        if name == 'firework_star' and custom.tint is not None:
            base: Image.Image = self.jar.frame('item/firework_star')
            base.alpha_composite(tinted(self.jar.frame('item/firework_star_overlay'), hex_color(custom.tint)))
            return glint(base)
        return glint(self.vanilla(name))

    def vanilla(self, item: str) -> Image.Image:
        name: str = self.member(item)
        if name == 'shield':
            return self.shield()
        if name in SPRITES:
            return self.sprite(self.jar.frame(SPRITES[name]))
        if name not in CHESTS and self.jar.has('item/' + name):
            return self.sprite(self.jar.frame('item/' + name))
        return downsample(self.box(name, SUPERSAMPLE))

    def hires(self, item: str, res: int, camera: 'IsoCamera | FrontCamera | None' = None) -> Image.Image:
        return self.box(self.member(item), res, camera)

    def member(self, item: str) -> str:
        raw: str = item.lower()
        if not raw.startswith('#'):
            return raw.removeprefix('minecraft:')
        tag: str = raw.removeprefix('#').removeprefix('minecraft:')
        if tag not in TAG_MEMBERS:
            raise MissingTexture('No representative item for tag ' + item)
        return TAG_MEMBERS[tag]

    def sprite(self, texture: Image.Image) -> Image.Image:
        if texture.size == (ICON, ICON):
            return texture
        return texture.resize((ICON, ICON), Image.NEAREST)

    def box(self, name: str, res: int, camera: 'IsoCamera | FrontCamera | None' = None) -> Image.Image:
        if name in CHESTS:
            return self.chest(name, res, camera)
        top, left, right = self.faces(name)
        return (camera if camera is not None else ISO).box(top, left, right, res=res)

    def faces(self, name: str) -> tuple[Image.Image, Image.Image, Image.Image]:
        top, left, right = CUBES[name] if name in CUBES else (self.pick(name, ('_top', '')), self.pick(name, ('_side', '')), self.pick(name, ('_side', '')))
        return self.jar.frame('block/' + top), self.jar.frame('block/' + left), self.jar.frame('block/' + right)

    def pick(self, name: str, suffixes: tuple[str, ...]) -> str:
        for suffix in suffixes:
            if self.jar.has('block/' + name + suffix):
                return name + suffix
        raise MissingTexture('No item or block texture for ' + name)

    def chest_face(self, texture: Image.Image, lid: tuple[int, int, int, int], base: tuple[int, int, int, int], latch: bool) -> Image.Image:
        face: Image.Image = Image.new('RGBA', (CHEST_FACE, CHEST_FACE))
        face.paste(texture.crop(base).transpose(Image.FLIP_TOP_BOTTOM), (0, CHEST_BASE_AT))
        face.paste(texture.crop(lid).transpose(Image.FLIP_TOP_BOTTOM), (0, 0))
        if latch:
            face.alpha_composite(texture.crop(CHEST_LOCK), CHEST_LOCK_AT)
        return face

    def chest(self, name: str, res: int, camera: 'IsoCamera | FrontCamera | None' = None) -> Image.Image:
        texture: Image.Image = self.jar.image(CHESTS[name])
        front: Image.Image = self.chest_face(texture, CHEST_LID_FRONT, CHEST_BASE_FRONT, True)
        side: Image.Image = self.chest_face(texture, CHEST_LID_SIDE, CHEST_BASE_SIDE, False)
        return (camera if camera is not None else ISO).box(texture.crop(CHEST_TOP), front, side, CHEST_SIZE, res)

    def shield(self) -> Image.Image:
        face: Image.Image = self.jar.image(SHIELD).crop(SHIELD_FACE)
        width: int = round(face.width * ICON / face.height)
        canvas: Image.Image = Image.new('RGBA', (ICON, ICON), (0, 0, 0, 0))
        canvas.paste(face.resize((width, ICON), Image.NEAREST), ((ICON - width) // 2, 0))
        return canvas


class Gui:
    def __init__(self, jar: Jar) -> None:
        self.texture: Image.Image = jar.image(CRAFTING_TABLE)
        self.background: tuple[int, int, int, int] = self.texture.getpixel((PANEL_SIZE[0] // 2, PANEL_EDGE + 1))

    def panel(self, width: int, height: int) -> Image.Image:
        edge: int = PANEL_EDGE
        panel_width, panel_height = PANEL_SIZE
        middle_x: int = panel_width // 2
        middle_y: int = panel_height // 2
        image: Image.Image = Image.new('RGBA', (width, height), self.background)
        image.paste(self.texture.crop((middle_x, 0, middle_x + 1, edge)).resize((width - 2 * edge, edge), Image.NEAREST), (edge, 0))
        image.paste(self.texture.crop((middle_x, panel_height - edge, middle_x + 1, panel_height)).resize((width - 2 * edge, edge), Image.NEAREST),
                    (edge, height - edge))
        image.paste(self.texture.crop((0, middle_y, edge, middle_y + 1)).resize((edge, height - 2 * edge), Image.NEAREST), (0, edge))
        image.paste(self.texture.crop((panel_width - edge, middle_y, panel_width, middle_y + 1)).resize((edge, height - 2 * edge), Image.NEAREST),
                    (width - edge, edge))
        image.paste(self.texture.crop((0, 0, edge, edge)), (0, 0))
        image.paste(self.texture.crop((panel_width - edge, 0, panel_width, edge)), (width - edge, 0))
        image.paste(self.texture.crop((0, panel_height - edge, edge, panel_height)), (0, height - edge))
        image.paste(self.texture.crop((panel_width - edge, panel_height - edge, panel_width, panel_height)), (width - edge, height - edge))
        return image

    def content(self) -> Image.Image:
        return self.texture.crop(CONTENT_BOX)

    def arrow(self) -> Image.Image:
        return self.texture.crop(ARROW_BOX)

    def result_slot(self) -> Image.Image:
        return self.texture.crop(RESULT_BOX)
