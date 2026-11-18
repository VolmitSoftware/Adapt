import math
from dataclasses import dataclass

from PIL import Image, ImageChops, ImageDraw, ImageFilter

import media_textures as textures
from media_catalog import Adaptation, Catalog

Point = tuple[float, float]
Color = tuple[int, int, int, int]
Cell = tuple[int, int, int, str]
Block = tuple[int, int, int]
Edge = tuple[Block, Block]

SCALE: int = textures.SCALE
TILE: int = textures.ICON
INK: Color = textures.INK
LINE: Color = (64, 64, 64, 255)
WORK_AREA: Color = (43, 108, 176, 255)
WORK_AREA_BACK: Color = (43, 108, 176, 110)
GRID_LINE: Color = (0, 0, 0, 36)
OUTLINE: Color = (0, 0, 0, 80)
OUTLINE_WIDTH: int = 2
GROUND_ALPHA: int = 96
PLATE: int = 5
LABEL_SIZE: int = 28
TITLE_SIZE: int = 30
NOTE_SIZE: int = 20
COMPASS_SIZE: int = 44
LABEL_PAD: int = 48
LABEL_GAP: int = 12
BOX_WIDTH: int = 260
BOX_HEIGHT: int = 76
COLUMN_GAP: int = 150
ROW_GAP: int = 20
TREE_MARGIN: int = 48
BOX_FILL: Color = (112, 112, 112, 255)
BOX_LIGHT: Color = (176, 176, 176, 255)
BOX_DARK: Color = (56, 56, 56, 255)
BOX_NAME_SIZE: int = 28
BOX_NOTE_SIZE: int = 17
NOTE_COLOR: Color = (232, 232, 232, 255)
LEVEL_SIZE: int = 20
ARROW_LENGTH: int = 18
ATTACH_SPACING: int = 18
LANE_OFFSET: int = 44
LANE_SPACING: int = 14
DASH: int = 12
GAP: int = 9
LINE_WIDTH: int = 3
CHEST_FACE: tuple[float, float] = (textures.CHEST_SIZE[0], textures.CHEST_SIZE[1])
DECAL_ACROSS: tuple[float, float] = (0.1, 0.9)
DECAL_UP: tuple[float, float] = (0.04, 0.84)
WORK_RADIUS: int = 2
NODE_FACE: Block = (0, 0, 1)
SKILL_NAMES: dict[str, str] = {'tragoul': 'TragOul'}
CHEST_SCALE: int = 8
PLINTH_CAMERA: textures.FrontCamera = textures.FrontCamera(depth=10, height=6)
GOLEM_CAMERA: textures.FrontCamera = textures.FrontCamera(depth=6, height=16)
COMPASS_GAP: int = 40


@dataclass(frozen=True)
class Placed:
    i: int
    j: int
    k: int
    icon: Image.Image
    outlined: bool


def halo(icon: Image.Image) -> Image.Image:
    padded: Image.Image = Image.new('RGBA', (icon.width + 2 * OUTLINE_WIDTH, icon.height + 2 * OUTLINE_WIDTH), (0, 0, 0, 0))
    padded.alpha_composite(icon, (OUTLINE_WIDTH, OUTLINE_WIDTH))
    alpha: Image.Image = padded.getchannel('A')
    ring: Image.Image = ImageChops.subtract(alpha.filter(ImageFilter.MaxFilter(2 * OUTLINE_WIDTH + 1)), alpha)
    layer: Image.Image = Image.new('RGBA', padded.size, OUTLINE[:3] + (0,))
    layer.putalpha(ring.point(lambda level: OUTLINE[3] if level else 0))
    layer.alpha_composite(padded)
    return layer


class Scene:
    def __init__(self, camera: textures.IsoCamera | textures.FrontCamera, scale: int = SCALE) -> None:
        self.camera: textures.IsoCamera | textures.FrontCamera = camera
        self.scale: int = scale
        self.placed: list[Placed] = []

    def block(self, i: int, j: int, k: int, icon: Image.Image, outlined: bool = True) -> None:
        self.placed.append(Placed(i, j, k, icon, outlined))

    def bounds(self) -> tuple[float, float, float, float]:
        xs: list[float] = []
        ys: list[float] = []
        for item in self.placed:
            x, y = self.camera.anchor(item.i, item.j, item.k)
            xs.extend((x, x + self.camera.extent_x))
            ys.extend((y, y + self.camera.extent_y))
        return min(xs), min(ys), max(xs), max(ys)

    def draw(self, canvas: Image.Image, origin: Point) -> None:
        for item in sorted(self.placed, key=lambda placed: self.camera.order(placed.i, placed.j, placed.k)):
            x, y = self.camera.anchor(item.i, item.j, item.k)
            icon: Image.Image = item.icon
            offset: int = 0
            if item.outlined:
                icon = halo(icon)
                offset = OUTLINE_WIDTH
            canvas.alpha_composite(icon, (round((x + origin[0]) * self.scale) - offset, round((y + origin[1]) * self.scale) - offset))

    def render(self, margin: int) -> tuple[Image.Image, Point]:
        min_x, min_y, max_x, max_y = self.bounds()
        origin: Point = (margin - min_x, margin - min_y)
        image: Image.Image = Image.new('RGBA', (math.ceil(max_x - min_x + 2 * margin) * self.scale, math.ceil(max_y - min_y + 2 * margin) * self.scale), (0, 0, 0, 0))
        self.draw(image, origin)
        return image, origin

    def project(self, origin: Point, x: float, y: float, z: float) -> Point:
        sx, sy = self.camera.world(x, y, z)
        return (sx + origin[0]) * self.scale, (sy + origin[1]) * self.scale


def dashed(draw: ImageDraw.ImageDraw, start: Point, end: Point, fill: Color) -> None:
    length: float = math.dist(start, end)
    if length == 0:
        return
    unit: Point = ((end[0] - start[0]) / length, (end[1] - start[1]) / length)
    at: float = 0.0
    while at < length:
        stop: float = min(length, at + DASH)
        draw.line([(start[0] + unit[0] * at, start[1] + unit[1] * at), (start[0] + unit[0] * stop, start[1] + unit[1] * stop)], fill=fill, width=LINE_WIDTH)
        at += DASH + GAP


def width_of(value: str, size: int) -> int:
    return math.ceil(textures.font(size).getlength(value))


def leader(canvas: Image.Image, anchor: Point, text_at: Point, value: str, aligned: str, attach: str, size: int = LABEL_SIZE) -> None:
    draw: ImageDraw.ImageDraw = ImageDraw.Draw(canvas)
    span: int = width_of(value, size)
    left: float = text_at[0] if aligned == 'l' else text_at[0] - span
    end: Point = (left - LABEL_GAP if attach == 'l' else left + span + LABEL_GAP, text_at[1])
    draw.line([anchor, end], fill=LINE, width=LINE_WIDTH)
    draw.ellipse([anchor[0] - 6, anchor[1] - 6, anchor[0] + 6, anchor[1] + 6], fill=LINE)
    textures.text(canvas, (round(text_at[0]), round(text_at[1])), value, size, INK, aligned + 'm')


def finish(gui: textures.Gui, width: int, height: int) -> Image.Image:
    return gui.panel(width // SCALE, height // SCALE).resize((width, height), Image.NEAREST)


def multiple(value: float) -> int:
    whole: int = math.ceil(value)
    return whole + (-whole) % SCALE


def plinth_top(icons: textures.Icons, gui: textures.Gui) -> Image.Image:
    grid: int = 5
    margin: int = 20
    size: int = grid * TILE + 2 * margin
    image: Image.Image = gui.panel(size, size)
    ground: Image.Image = icons.jar.frame('block/dirt')
    ground.putalpha(GROUND_ALPHA)
    lodestone: Image.Image = icons.jar.frame('block/lodestone_top')
    pedestal: Image.Image = icons.jar.frame('block/chiseled_copper')
    middle: int = grid // 2
    structure: dict[tuple[int, int], Image.Image] = {(middle, middle): lodestone, (middle, 0): pedestal, (grid - 1, middle): pedestal,
                                                    (middle, grid - 1): pedestal, (0, middle): pedestal}
    for column in range(grid):
        for row in range(grid):
            image.alpha_composite(structure.get((column, row), ground), (margin + column * TILE, margin + row * TILE))
    scaled: Image.Image = image.resize((size * SCALE, size * SCALE), Image.NEAREST)
    overlay: Image.Image = Image.new('RGBA', scaled.size, (0, 0, 0, 0))
    draw: ImageDraw.ImageDraw = ImageDraw.Draw(overlay)
    for index in range(grid + 1):
        offset: int = (margin + index * TILE) * SCALE
        draw.line([(offset, margin * SCALE), (offset, (margin + grid * TILE) * SCALE)], fill=GRID_LINE, width=2)
        draw.line([(margin * SCALE, offset), ((margin + grid * TILE) * SCALE, offset)], fill=GRID_LINE, width=2)
    scaled.alpha_composite(overlay)
    center: int = size * SCALE // 2
    edge: int = margin * SCALE // 2
    far: int = size * SCALE - edge
    for value, position in (('N', (center, edge)), ('E', (far, center)), ('S', (center, far)), ('W', (edge, center))):
        textures.text(scaled, position, value, COMPASS_SIZE, INK, 'mm')
    return scaled


def faint(texture: Image.Image, background: tuple[int, int, int, int]) -> Image.Image:
    layer: Image.Image = texture.copy()
    layer.putalpha(GROUND_ALPHA)
    base: Image.Image = Image.new('RGBA', texture.size, background)
    base.alpha_composite(layer)
    return base


def plinth_scene(icons: textures.Icons, gui: textures.Gui) -> Scene:
    scene: Scene = Scene(PLINTH_CAMERA)
    ground: Image.Image = faint(icons.jar.frame('block/dirt'), gui.background)
    tile: Image.Image = PLINTH_CAMERA.box(ground, ground, ground, res=SCALE)
    for i in range(PLATE):
        for j in range(PLATE):
            scene.block(i, j, -1, tile)
    middle: int = PLATE // 2
    scene.block(middle, middle, 0, icons.hires('lodestone', SCALE, PLINTH_CAMERA))
    for i, j in ((middle, 0), (PLATE - 1, middle), (middle, PLATE - 1), (0, middle)):
        scene.block(i, j, 0, icons.hires('chiseled_copper', SCALE, PLINTH_CAMERA))
    return scene


def plinth_view(icons: textures.Icons, gui: textures.Gui) -> Image.Image:
    scene: Scene = plinth_scene(icons, gui)
    layer: Image.Image
    origin: Point
    layer, origin = scene.render(1)
    pedestal_label: str = 'Pedestal (any chiseled copper)'
    left: int = 2 * COMPASS_GAP
    top: int = 2 * COMPASS_GAP + COMPASS_SIZE // 2
    bottom: int = COMPASS_GAP + COMPASS_SIZE + LABEL_SIZE
    label_left: int = left + layer.width // 2 + 2 * COMPASS_GAP
    lodestone_label: str = 'Lodestone'
    lodestone_size: int = NOTE_SIZE + 4
    width: int = multiple(max(layer.width + 2 * left, label_left + width_of(pedestal_label, LABEL_SIZE) + LABEL_PAD,
                              label_left + width_of(lodestone_label, lodestone_size) + LABEL_PAD))
    height: int = multiple(layer.height + top + bottom)
    canvas: Image.Image = finish(gui, width, height)
    shift: tuple[int, int] = (left, top)
    canvas.alpha_composite(layer, shift)
    moved: Point = (origin[0] + shift[0] / SCALE, origin[1] + shift[1] / SCALE)

    def at(x: float, y: float, z: float) -> Point:
        return scene.project(moved, x, y, z)

    middle: float = PLATE / 2
    center: Point = at(middle, 0, middle)
    north: tuple[int, int] = (round(center[0]), round(at(middle, 1, 0)[1]) - COMPASS_GAP)
    textures.text(canvas, north, 'N', COMPASS_SIZE, INK, 'mm')
    textures.text(canvas, (round(at(PLATE, 0, middle)[0]) + COMPASS_GAP, round(center[1])), 'E', COMPASS_SIZE, INK, 'mm')
    south: tuple[int, int] = (round(center[0]), round(at(middle, -1, PLATE)[1]) + COMPASS_GAP)
    textures.text(canvas, south, 'S', COMPASS_SIZE, INK, 'mm')
    textures.text(canvas, (round(at(0, 0, middle)[0]) - COMPASS_GAP, round(center[1])), 'W', COMPASS_SIZE, INK, 'mm')
    leader(canvas, at(middle + 0.4, 1, middle), (north[0] + 2 * COMPASS_GAP, north[1]), lodestone_label, 'l', 'l', lodestone_size)
    leader(canvas, at(middle, 0.5, PLATE), (south[0] + 2 * COMPASS_GAP, south[1]), pedestal_label, 'l', 'l')
    return canvas


def golem_builds(icons: textures.Icons, gui: textures.Gui) -> Image.Image:
    builds: list[tuple[str, str, list[Cell]]] = [
        ('Snow golem', '2 snow blocks + carved pumpkin', [(0, 0, 0, 'snow_block'), (0, 0, 1, 'snow_block'), (0, 0, 2, 'carved_pumpkin')]),
        ('Iron golem', '4 iron blocks in a T + carved pumpkin',
         [(0, 0, 0, 'iron_block'), (-1, 0, 1, 'iron_block'), (0, 0, 1, 'iron_block'), (1, 0, 1, 'iron_block'), (0, 0, 2, 'carved_pumpkin')]),
        ('Copper golem', '1 copper block + carved pumpkin', [(0, 0, 0, 'copper_block'), (0, 0, 1, 'carved_pumpkin')]),
    ]
    scenes: list[Image.Image] = [golem_scene(icons, cells).render(1)[0] for _, _, cells in builds]
    widths: list[int] = [max(scene.width, width_of(title, TITLE_SIZE), width_of(note, NOTE_SIZE)) + 2 * LABEL_PAD
                         for scene, (title, note, _) in zip(scenes, builds)]
    top: int = max(scene.height for scene in scenes)
    caption: int = TITLE_SIZE + NOTE_SIZE + 3 * LABEL_GAP
    width: int = multiple(sum(widths) + 2 * LABEL_PAD)
    height: int = multiple(LABEL_PAD + top + caption + LABEL_PAD)
    canvas: Image.Image = finish(gui, width, height)
    x: int = LABEL_PAD + (width - 2 * LABEL_PAD - sum(widths)) // 2
    for (title, note, _), scene, column in zip(builds, scenes, widths):
        middle: int = x + column // 2
        canvas.alpha_composite(scene, (middle - scene.width // 2, LABEL_PAD + top - scene.height))
        textures.text(canvas, (middle, LABEL_PAD + top + LABEL_GAP), title, TITLE_SIZE, INK, 'ma')
        textures.text(canvas, (middle, LABEL_PAD + top + 2 * LABEL_GAP + TITLE_SIZE), note, NOTE_SIZE, INK, 'ma')
        x += column
    return canvas


def golem_scene(icons: textures.Icons, cells: list[Cell]) -> Scene:
    scene: Scene = Scene(GOLEM_CAMERA)
    for i, j, k, item in cells:
        scene.block(i, j, k, icons.hires(item, SCALE, GOLEM_CAMERA))
    return scene


def decal(canvas: Image.Image, sprite: Image.Image, corners: tuple[Point, Point, Point]) -> None:
    origin, across, down = corners
    ux: float = across[0] - origin[0]
    uy: float = across[1] - origin[1]
    vx: float = down[0] - origin[0]
    vy: float = down[1] - origin[1]
    determinant: float = ux * vy - uy * vx
    xs: list[float] = [origin[0], across[0], down[0], across[0] + vx]
    ys: list[float] = [origin[1], across[1], down[1], across[1] + vy]
    for y in range(math.floor(min(ys)), math.ceil(max(ys))):
        for x in range(math.floor(min(xs)), math.ceil(max(xs))):
            dx: float = x + 0.5 - origin[0]
            dy: float = y + 0.5 - origin[1]
            u: float = (dx * vy - dy * vx) / determinant
            v: float = (ux * dy - uy * dx) / determinant
            if 0 <= u < 1 and 0 <= v < 1:
                pixel: tuple[int, int, int, int] = sprite.getpixel((int(u * sprite.width), int(v * sprite.height)))
                if pixel[3] > 0:
                    canvas.alpha_composite(Image.new('RGBA', (1, 1), pixel), (x, y))


def work_bounds(block: Block, face: Block, radius: int) -> tuple[Block, Block]:
    center: Block = (block[0] + face[0], block[1] + face[1], block[2] + face[2])
    return (center[0] - radius, center[1] - radius, center[2] - radius), (center[0] + radius + 1, center[1] + radius + 1, center[2] + radius + 1)


def work_edges(low: Block, high: Block) -> tuple[list[Edge], list[Edge]]:
    behind: list[Edge] = []
    front: list[Edge] = []
    for axis in range(3):
        for a in (low, high):
            for b in (low, high):
                others: list[int] = [index for index in range(3) if index != axis]
                start: list[int] = [0, 0, 0]
                end: list[int] = [0, 0, 0]
                start[axis], end[axis] = low[axis], high[axis]
                for index, corner in zip(others, (a, b)):
                    start[index] = end[index] = corner[index]
                edge: Edge = (tuple(start), tuple(end))
                (front if high in edge else behind).append(edge)
    return behind, front


def draw_edges(layer: Image.Image, edges: list[Edge], low: Block, scene: Scene, origin: Point) -> None:
    draw: ImageDraw.ImageDraw = ImageDraw.Draw(layer)
    for start, end in edges:
        dashed(draw, scene.project(origin, *start), scene.project(origin, *end), WORK_AREA_BACK if low in (start, end) else WORK_AREA)


def node_on_chest(icons: textures.Icons, gui: textures.Gui) -> Image.Image:
    camera: textures.IsoCamera = textures.ISO
    scale: int = CHEST_SCALE
    scene: Scene = Scene(camera, scale)
    scene.block(0, 0, 0, icons.hires('chest', scale, camera))
    low, high = work_bounds((0, 0, 0), NODE_FACE, WORK_RADIUS)
    behind, front = work_edges(low, high)
    corners: list[Point] = [camera.world(x, y, z) for x in (low[0], high[0]) for y in (low[1], high[1]) for z in (low[2], high[2])]
    box_left: float = min(corner[0] for corner in corners) * scale
    box_right: float = max(corner[0] for corner in corners) * scale
    box_top: float = min(corner[1] for corner in corners) * scale
    box_bottom: float = max(corner[1] for corner in corners) * scale
    side: int = width_of('Work area', LABEL_SIZE) + 3 * LABEL_PAD
    width: int = multiple(box_right - box_left + 2 * side)
    height: int = multiple(box_bottom - box_top + 2 * LABEL_PAD)
    origin: Point = ((side - box_left) / scale, (LABEL_PAD - box_top) / scale)
    canvas: Image.Image = finish(gui, width, height)
    layer: Image.Image = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    draw_edges(layer, behind, low, scene, origin)
    canvas.alpha_composite(layer)
    scene.draw(canvas, origin)
    size_x, size_y, size_z = textures.CHEST_SIZE
    face_z: float = 0.5 + size_z / 2
    face_left: float = 0.5 - size_x / 2
    u0, u1 = DECAL_ACROSS
    v0, v1 = DECAL_UP
    decal(canvas, icons.icon('brick'), (scene.project(origin, face_left + u0 * size_x, v1 * size_y, face_z),
                                       scene.project(origin, face_left + u1 * size_x, v1 * size_y, face_z),
                                       scene.project(origin, face_left + u0 * size_x, v0 * size_y, face_z)))
    front_layer: Image.Image = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    draw_edges(front_layer, front, low, scene, origin)
    canvas.alpha_composite(front_layer)
    node: Point = scene.project(origin, face_left + u0 * size_x, (v0 + v1) / 2 * size_y, face_z)
    chest: Point = scene.project(origin, 0.5 + size_x / 2, 0.3 * size_y, 0.5)
    area: Point = scene.project(origin, high[0], high[1], low[2])
    leader(canvas, node, (side - LABEL_PAD, node[1] + 90), 'Node', 'r', 'r')
    leader(canvas, chest, (width - side + LABEL_PAD, chest[1] + 90), 'Chest', 'l', 'l')
    leader(canvas, area, (width - side + LABEL_PAD, area[1] - 30), 'Work area', 'l', 'l')
    return canvas


def depths(adaptations: tuple[Adaptation, ...]) -> dict[str, int]:
    known: dict[str, Adaptation] = {adaptation.id: adaptation for adaptation in adaptations}
    result: dict[str, int] = {}

    def depth(identifier: str) -> int:
        if identifier not in result:
            parents: list[str] = [requirement.id for requirement in known[identifier].requires if requirement.id in known]
            result[identifier] = 0 if not parents else 1 + max(depth(parent) for parent in parents)
        return result[identifier]

    for adaptation in adaptations:
        depth(adaptation.id)
    return result


def external_note(adaptation: Adaptation, known: set[str]) -> str:
    notes: list[str] = []
    for requirement in adaptation.requires:
        if requirement.id not in known:
            skill, _, rest = requirement.id.partition('-')
            notes.append('+ ' + rest.replace('-', ' ').title() + ' ' + str(requirement.level) + ' (' + SKILL_NAMES.get(skill, skill.title()) + ')')
    return ', '.join(notes)


def columns(adaptations: tuple[Adaptation, ...], levels: dict[str, int]) -> list[list[Adaptation]]:
    layers: list[list[Adaptation]] = [[] for _ in range(max(levels.values()) + 1)]
    for adaptation in adaptations:
        layers[levels[adaptation.id]].append(adaptation)
    rows: dict[str, int] = {adaptation.id: row for row, adaptation in enumerate(layers[0])}
    for depth in range(1, len(layers)):
        def barycenter(adaptation: Adaptation) -> float:
            sources: list[int] = [rows[requirement.id] for requirement in adaptation.requires if requirement.id in rows]
            return sum(sources) / len(sources) if sources else 0.0

        layers[depth].sort(key=barycenter)
        for row, adaptation in enumerate(layers[depth]):
            rows[adaptation.id] = row
    return layers


def curve(start: Point, end: Point) -> list[Point]:
    reach: float = (end[0] - start[0]) / 2
    p1: Point = (start[0] + reach, start[1])
    p2: Point = (end[0] - reach, end[1])
    points: list[Point] = []
    for step in range(33):
        t: float = step / 32
        u: float = 1 - t
        points.append((u ** 3 * start[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * end[0],
                       u ** 3 * start[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * end[1]))
    return points


def arrowhead(draw: ImageDraw.ImageDraw, tip: Point, before: Point) -> None:
    angle: float = math.atan2(tip[1] - before[1], tip[0] - before[0])
    spread: float = math.radians(26)
    draw.polygon([tip, (tip[0] - ARROW_LENGTH * math.cos(angle - spread), tip[1] - ARROW_LENGTH * math.sin(angle - spread)),
                  (tip[0] - ARROW_LENGTH * math.cos(angle + spread), tip[1] - ARROW_LENGTH * math.sin(angle + spread))], fill=LINE)


def box(canvas: Image.Image, x: int, y: int, name: str, note: str) -> None:
    draw: ImageDraw.ImageDraw = ImageDraw.Draw(canvas)
    draw.rectangle([x, y, x + BOX_WIDTH - 1, y + BOX_HEIGHT - 1], fill=BOX_DARK)
    draw.rectangle([x + 3, y + 3, x + BOX_WIDTH - 1, y + BOX_HEIGHT - 1], fill=BOX_LIGHT)
    draw.rectangle([x + 3, y + 3, x + BOX_WIDTH - 4, y + BOX_HEIGHT - 4], fill=BOX_FILL)
    middle: int = x + BOX_WIDTH // 2
    if note:
        textures.text(canvas, (middle, y + 26), name, BOX_NAME_SIZE, textures.WHITE, 'mm', True)
        textures.text(canvas, (middle, y + 55), note, BOX_NOTE_SIZE, NOTE_COLOR, 'mm')
    else:
        textures.text(canvas, (middle, y + BOX_HEIGHT // 2), name, BOX_NAME_SIZE, textures.WHITE, 'mm', True)


def level_tag(canvas: Image.Image, background: Color, at: Point, level: int) -> None:
    value: str = 'Lv ' + str(level)
    half: int = width_of(value, LEVEL_SIZE) // 2 + 5
    draw: ImageDraw.ImageDraw = ImageDraw.Draw(canvas)
    draw.rectangle([at[0] - half, at[1] - LEVEL_SIZE // 2 - 3, at[0] + half, at[1] + LEVEL_SIZE // 2 + 3], fill=background)
    textures.text(canvas, (round(at[0]), round(at[1])), value, LEVEL_SIZE, INK, 'mm')


def prerequisite_tree(catalog: Catalog, gui: textures.Gui) -> Image.Image:
    levels: dict[str, int] = depths(catalog.adaptations)
    layers: list[list[Adaptation]] = columns(catalog.adaptations, levels)
    known: set[str] = {adaptation.id for adaptation in catalog.adaptations}
    pitch: int = BOX_HEIGHT + ROW_GAP
    tallest: int = max(len(layer) for layer in layers)
    stack: int = tallest * pitch - ROW_GAP
    skips: list[tuple[str, str]] = [(adaptation.id, requirement.id) for adaptation in catalog.adaptations for requirement in adaptation.requires
                                    if requirement.id in known and levels[adaptation.id] - levels[requirement.id] > 1]
    width: int = multiple(2 * TREE_MARGIN + len(layers) * BOX_WIDTH + (len(layers) - 1) * COLUMN_GAP)
    height: int = multiple(2 * TREE_MARGIN + stack + (LANE_OFFSET + LANE_SPACING * len(skips) if skips else 0))
    canvas: Image.Image = finish(gui, width, height)
    positions: dict[str, Point] = {}
    for column, layer in enumerate(layers):
        top: float = TREE_MARGIN + (tallest - len(layer)) * pitch / 2
        for row, adaptation in enumerate(layer):
            positions[adaptation.id] = (TREE_MARGIN + column * (BOX_WIDTH + COLUMN_GAP), top + row * pitch)
    draw: ImageDraw.ImageDraw = ImageDraw.Draw(canvas)
    tags: list[tuple[Point, int]] = []
    for adaptation in catalog.adaptations:
        target: Point = positions[adaptation.id]
        incoming: list[int] = [index for index, requirement in enumerate(adaptation.requires) if requirement.id in known]
        for slot, index in enumerate(incoming):
            requirement = adaptation.requires[index]
            source: Point = positions[requirement.id]
            start: Point = (source[0] + BOX_WIDTH, source[1] + BOX_HEIGHT / 2)
            end: Point = (target[0], target[1] + BOX_HEIGHT / 2 + (slot - (len(incoming) - 1) / 2) * ATTACH_SPACING)
            if levels[adaptation.id] - levels[requirement.id] > 1:
                lane: float = TREE_MARGIN + stack + LANE_OFFSET + skips.index((adaptation.id, requirement.id)) * LANE_SPACING
                path: list[Point] = [start, (start[0] + LANE_OFFSET, start[1]), (start[0] + LANE_OFFSET, lane), (end[0] - LANE_OFFSET, lane),
                                     (end[0] - LANE_OFFSET, end[1]), end]
                tag_at: Point = ((start[0] + end[0]) / 2, lane)
            else:
                path = curve(start, end)
                tag_at = (end[0] - 60, end[1])
            draw.line(path, fill=LINE, width=LINE_WIDTH, joint='curve')
            arrowhead(draw, end, path[-2])
            if requirement.level > 1:
                tags.append((tag_at, requirement.level))
    for adaptation in catalog.adaptations:
        x, y = positions[adaptation.id]
        box(canvas, round(x), round(y), adaptation.name, external_note(adaptation, known))
    for at, level in tags:
        level_tag(canvas, gui.background, at, level)
    return canvas


def render_all(catalog: Catalog, icons: textures.Icons, gui: textures.Gui) -> dict[str, Image.Image]:
    return {
        'animus-plinth-top': plinth_top(icons, gui),
        'animus-plinth-view': plinth_view(icons, gui),
        'golem-builds': golem_builds(icons, gui),
        'node-on-chest': node_on_chest(icons, gui),
        'prerequisite-tree': prerequisite_tree(catalog, gui),
    }
