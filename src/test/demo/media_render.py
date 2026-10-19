import argparse
import sys
import zipfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import media_catalog
import media_diagrams
import media_textures as textures
from media_catalog import Catalog, Infusion, Recipe

DEFAULT_CLIENT_JAR: Path = (Path.home() / 'Library' / 'Application Support' / 'PrismLauncher' / 'libraries' / 'com' / 'mojang' / 'minecraft'
                            / '26.2' / 'minecraft-26.2-client.jar')
DEFAULT_RECIPES: Path = Path('build') / 'demo' / 'golemancy-media' / 'recipes.json'
GRID: int = media_catalog.GRID
SLOT: int = 18
PAD: int = 8
STRIP: int = 24
CAPTION_TOP: int = 6
NAME_SIZE: int = 26
DETAIL_SIZE: int = 20
COUNT_SIZE: int = 24
SLOT_ORIGIN: int = 1
RESULT_OFFSET: tuple[int, int] = (95, 19)
EXTRA: int = 9
CELL_WIDTH: int = 30
CELL_HEIGHT: int = 28
PLUS_WIDTH: int = 2 * CELL_WIDTH + textures.ICON
PLUS_HEIGHT: int = 2 * CELL_HEIGHT + textures.ICON + EXTRA
GAP: int = 10
LODESTONE: str = 'lodestone'
PEDESTAL: str = 'chiseled_copper'
GREEN: tuple[int, int, int, int] = (46, 125, 31, 255)
NUMERALS: tuple[str, ...] = ('', 'I', 'II', 'III')


def plural(count: int, unit: str) -> str:
    return str(count) + ' ' + unit + ('' if count == 1 else 's')


def seconds(ticks: int) -> str:
    return format(ticks / 20, 'g') + ' s'


def caption(image: Image.Image, top: int, lines: list[tuple[str, int, tuple[int, int, int, int]]]) -> None:
    y: int = top
    for line, size, color in lines:
        textures.text(image, (PAD * textures.SCALE, y), line, size, color)
        y += size + 8


def render_recipe(recipe: Recipe, catalog: Catalog, icons: textures.Icons, gui: textures.Gui) -> Image.Image:
    content: Image.Image = gui.content()
    width: int = content.width + 2 * PAD
    height: int = content.height + 2 * PAD + STRIP
    image: Image.Image = gui.panel(width, height)
    image.paste(content, (PAD, PAD))
    scaled: Image.Image = image.resize((width * textures.SCALE, height * textures.SCALE), Image.NEAREST)
    columns: int = max(len(row) for row in recipe.pattern)
    shift_x: int = (GRID - columns) // 2
    shift_y: int = (GRID - len(recipe.pattern)) // 2
    for row, line in enumerate(recipe.pattern):
        for column, symbol in enumerate(line):
            if symbol != ' ':
                x: int = PAD + SLOT_ORIGIN + SLOT * (column + shift_x)
                y: int = PAD + SLOT_ORIGIN + SLOT * (row + shift_y)
                scaled.alpha_composite(icons.large(recipe.key[symbol], textures.SCALE), (x * textures.SCALE, y * textures.SCALE))
    result_x: int = PAD + RESULT_OFFSET[0]
    result_y: int = PAD + RESULT_OFFSET[1]
    scaled.alpha_composite(icons.large(recipe.result, textures.SCALE), (result_x * textures.SCALE, result_y * textures.SCALE))
    if recipe.yield_count > 1:
        textures.text(scaled, ((result_x + textures.ICON) * textures.SCALE, (result_y + textures.ICON) * textures.SCALE), str(recipe.yield_count), COUNT_SIZE,
                      textures.WHITE, 'rb', True)
    lines: list[tuple[str, int, tuple[int, int, int, int]]] = [(catalog.name(recipe.result), NAME_SIZE, textures.INK)]
    if recipe.tithe > 0:
        lines.append(('Tithe: ' + plural(recipe.tithe, 'level'), DETAIL_SIZE, GREEN))
    caption(scaled, (PAD + content.height + CAPTION_TOP) * textures.SCALE, lines)
    return scaled


def result_label(catalog: Catalog, item: str) -> str:
    name: str = catalog.name(item)
    tier: int | None = catalog.items[item].tier if item in catalog.items else None
    return name + (' ' + NUMERALS[tier] if tier is not None and 0 < tier < len(NUMERALS) else '')


def render_infusion(infusion: Infusion, catalog: Catalog, icons: textures.Icons, gui: textures.Gui) -> Image.Image:
    arrow: Image.Image = gui.arrow()
    slot: Image.Image = gui.result_slot()
    width: int = PAD + PLUS_WIDTH + GAP + arrow.width + GAP + slot.width + PAD
    height: int = PAD + PLUS_HEIGHT + PAD + STRIP
    image: Image.Image = gui.panel(width, height)
    middle: int = PAD + CELL_HEIGHT + (textures.ICON + EXTRA) // 2
    arrow_x: int = PAD + PLUS_WIDTH + GAP
    slot_x: int = arrow_x + arrow.width + GAP
    image.paste(arrow, (arrow_x, middle - arrow.height // 2))
    image.paste(slot, (slot_x, middle - slot.height // 2))
    scaled: Image.Image = image.resize((width * textures.SCALE, height * textures.SCALE), Image.NEAREST)
    cells: list[tuple[int, int, str, str]] = [
        (1, 0, infusion.pedestals[0], PEDESTAL),
        (0, 1, infusion.pedestals[3], PEDESTAL),
        (1, 1, infusion.center, LODESTONE),
        (2, 1, infusion.pedestals[1], PEDESTAL),
        (1, 2, infusion.pedestals[2], PEDESTAL),
    ]
    for column, row, item, block in cells:
        x: int = (PAD + column * CELL_WIDTH) * textures.SCALE
        y: int = (PAD + row * CELL_HEIGHT) * textures.SCALE
        scaled.alpha_composite(icons.hires(block, textures.SCALE), (x, y + EXTRA * textures.SCALE))
        scaled.alpha_composite(icons.large(item, textures.SCALE), (x, y))
    result: Image.Image = icons.large(infusion.result, textures.SCALE)
    scaled.alpha_composite(result, ((slot_x + (slot.width - textures.ICON) // 2) * textures.SCALE, (middle - textures.ICON // 2) * textures.SCALE))
    lines: list[tuple[str, int, tuple[int, int, int, int]]] = [
        (result_label(catalog, infusion.result), NAME_SIZE, textures.INK),
        ('Essence: ' + plural(infusion.essence_levels, 'level') + '   Duration: ' + seconds(infusion.duration_ticks), DETAIL_SIZE, GREEN)]
    caption(scaled, (PAD + PLUS_HEIGHT + CAPTION_TOP) * textures.SCALE, lines)
    return scaled


def save(image: Image.Image, path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format='PNG', optimize=False, compress_level=6)
    return path


def render_all(catalog: Catalog, jar: textures.Jar, out: Path) -> list[Path]:
    icons: textures.Icons = textures.Icons(jar, catalog.items)
    gui: textures.Gui = textures.Gui(jar)
    written: list[Path] = []
    for recipe in catalog.recipes:
        written.append(save(render_recipe(recipe, catalog, icons, gui), out / 'recipes' / (recipe.id + '.png')))
    for infusion in catalog.infusions:
        written.append(save(render_infusion(infusion, catalog, icons, gui), out / 'infusions' / (infusion.id + '.png')))
    for name, image in media_diagrams.render_all(catalog, icons, gui).items():
        written.append(save(image, out / 'diagrams' / (name + '.png')))
    return written


def arguments(argv: list[str] | None) -> argparse.Namespace:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description='Render Golemancy crafting recipes, infusions, and structure diagrams from a client jar.')
    parser.add_argument('--recipes', default=str(DEFAULT_RECIPES))
    parser.add_argument('--client-jar', default=str(DEFAULT_CLIENT_JAR))
    parser.add_argument('--out', required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args: argparse.Namespace = arguments(argv)
    try:
        catalog: Catalog = media_catalog.load(Path(args.recipes))
        with textures.Jar(Path(args.client_jar)) as jar:
            written: list[Path] = render_all(catalog, jar, Path(args.out))
    except (OSError, ValueError, zipfile.BadZipFile, textures.MissingTexture) as failure:
        print(str(failure), file=sys.stderr)
        return 2
    print('rendered ' + str(len(written)) + ' image(s) into ' + str(Path(args.out)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
