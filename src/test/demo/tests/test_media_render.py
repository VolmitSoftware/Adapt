import copy
import dataclasses
import io
import json
import re
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import media_catalog
import media_diagrams
import media_render
import media_textures as textures

Color = tuple[int, int, int]

ITEM_COLORS: dict[str, Color] = {
    'iron_hoe': (170, 170, 170), 'amethyst_shard': (200, 100, 255), 'redstone': (255, 0, 0), 'copper_ingot': (220, 120, 60),
    'ender_pearl': (0, 120, 100), 'firework_star': (90, 90, 90), 'disc_fragment_5': (60, 60, 140), 'sugar': (240, 240, 240),
    'feather': (230, 230, 230), 'rabbit_foot': (160, 110, 70), 'phantom_membrane': (200, 200, 150), 'brick': (170, 60, 50)}
BLOCK_COLORS: dict[str, Color] = {
    'dirt': (134, 96, 67), 'lodestone_top': (120, 120, 130), 'lodestone_side': (100, 100, 110), 'chiseled_copper': (200, 110, 80),
    'iron_block': (216, 216, 216), 'snow': (250, 250, 250), 'carved_pumpkin': (210, 120, 20), 'pumpkin_top': (200, 140, 30),
    'pumpkin_side': (190, 110, 20), 'copper_block': (192, 107, 80), 'oak_log': (110, 85, 50), 'oak_log_top': (160, 130, 80)}
PANEL: Color = (198, 198, 198)

CATALOG: dict = {
    'items': {
        'golemancy:crook': {'name': "Artificer's Crook", 'base': 'IRON_HOE', 'tint': None, 'tier': None},
        'golemancy:blank_core': {'name': 'Blank Animus Core', 'base': 'FIREWORK_STAR', 'tint': '#FF0000', 'tier': None},
        'golemancy:blank_rune': {'name': 'Blank Rune', 'base': 'DISC_FRAGMENT_5', 'tint': None, 'tier': None},
        'golemancy:rune/swiftness/1': {'name': 'Swiftness Rune', 'base': 'DISC_FRAGMENT_5', 'tint': None, 'tier': 1},
    },
    'recipes': [
        {'id': 'golemancy-crook', 'result': 'golemancy:crook', 'yield': 1, 'tithe': 0, 'pattern': [' A ', 'RHR', ' C '],
         'key': {'H': 'IRON_HOE', 'A': 'AMETHYST_SHARD', 'R': 'REDSTONE', 'C': 'COPPER_INGOT'}},
        {'id': 'golemancy-blank-core', 'result': 'golemancy:blank_core', 'yield': 2, 'tithe': 1, 'pattern': ['ARA', 'RPR', 'ARA'],
         'key': {'A': 'AMETHYST_SHARD', 'R': 'REDSTONE', 'P': 'ENDER_PEARL'}},
        {'id': 'golemancy-log-hoe', 'result': 'golemancy:crook', 'yield': 1, 'tithe': 2, 'pattern': ['L', 'H'], 'key': {'L': '#logs', 'H': 'iron_hoe'}},
    ],
    'infusions': [
        {'id': 'swiftness_1', 'center': 'golemancy:blank_rune', 'pedestals': ['SUGAR', 'SUGAR', 'FEATHER', 'FEATHER'],
         'result': 'golemancy:rune/swiftness/1', 'essenceLevels': 2, 'durationTicks': 100},
    ],
    'adaptations': [
        {'id': 'x-root', 'name': 'Root', 'requires': []},
        {'id': 'x-child', 'name': 'Child', 'requires': [{'id': 'x-root', 'level': 1}]},
        {'id': 'x-grandchild', 'name': 'Grandchild', 'requires': [{'id': 'x-child', 'level': 2}, {'id': 'x-root', 'level': 5}]},
        {'id': 'x-bone', 'name': 'Bone', 'requires': [{'id': 'x-root', 'level': 1}, {'id': 'tragoul-skeletal-servant', 'level': 1}]},
    ],
}


def png(image: Image.Image) -> bytes:
    buffer: io.BytesIO = io.BytesIO()
    image.save(buffer, format='PNG')
    return buffer.getvalue()


def solid(color: Color, size: tuple[int, int] = (16, 16)) -> Image.Image:
    return Image.new('RGBA', size, color + (255,))


def crafting_table() -> Image.Image:
    image: Image.Image = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    image.paste(Image.new('RGBA', (176, 166), PANEL + (255,)), (0, 0))
    image.paste(solid((139, 139, 139), (54, 54)), (29, 16))
    image.paste(solid((120, 120, 120), (22, 15)), (90, 35))
    image.paste(solid((139, 139, 139), (26, 26)), (119, 30))
    return image


CHEST_REGIONS: dict[str, tuple[tuple[int, int, int, int], Color]] = {
    'top': ((28, 0, 42, 14), (230, 230, 10)),
    'lid_front': ((42, 14, 56, 19), (200, 30, 30)),
    'base_front': ((42, 33, 56, 43), (30, 30, 200)),
    'lid_side': ((28, 14, 42, 19), (30, 200, 200)),
    'base_side': ((28, 33, 42, 43), (200, 30, 200)),
    'lock': ((1, 1, 3, 5), (90, 90, 90)),
}


def chest_texture() -> Image.Image:
    image: Image.Image = solid((255, 255, 255), (64, 64))
    for box, color in CHEST_REGIONS.values():
        image.paste(solid(color, (box[2] - box[0], box[3] - box[1])), (box[0], box[1]))
    return image


def overlay() -> Image.Image:
    image: Image.Image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    image.paste(solid((255, 255, 255), (8, 16)), (0, 0))
    return image


def write_jar(path: Path) -> None:
    with zipfile.ZipFile(path, 'w') as archive:
        for name, color in ITEM_COLORS.items():
            archive.writestr(textures.TEXTURES + 'item/' + name + '.png', png(solid(color)))
        for name, color in BLOCK_COLORS.items():
            archive.writestr(textures.TEXTURES + 'block/' + name + '.png', png(solid(color)))
        archive.writestr(textures.TEXTURES + 'item/firework_star_overlay.png', png(overlay()))
        archive.writestr(textures.TEXTURES + 'entity/chest/normal.png', png(chest_texture()))
        archive.writestr(textures.TEXTURES + 'entity/chest/copper.png', png(chest_texture()))
        archive.writestr(textures.TEXTURES + 'gui/container/crafting_table.png', png(crafting_table()))


def catalog() -> media_catalog.Catalog:
    return media_catalog.parse(copy.deepcopy(CATALOG))


class MediaCase(unittest.TestCase):
    def setUp(self) -> None:
        directory: tempfile.TemporaryDirectory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.tmp: Path = Path(directory.name)
        self.jar_path: Path = self.tmp / 'client.jar'
        write_jar(self.jar_path)
        self.jar: textures.Jar = textures.Jar(self.jar_path)
        self.addCleanup(self.jar.archive.close)
        self.icons: textures.Icons = textures.Icons(self.jar, catalog().items)
        self.gui: textures.Gui = textures.Gui(self.jar)


class CatalogTest(unittest.TestCase):
    def test_recipes_json_is_parsed_into_typed_entries(self) -> None:
        parsed: media_catalog.Catalog = catalog()

        self.assertEqual([recipe.id for recipe in parsed.recipes], ['golemancy-crook', 'golemancy-blank-core', 'golemancy-log-hoe'])
        crook: media_catalog.Recipe = parsed.recipes[0]
        self.assertEqual((crook.result, crook.yield_count, crook.tithe, crook.pattern), ('golemancy:crook', 1, 0, (' A ', 'RHR', ' C ')))
        self.assertEqual(crook.key['H'], 'IRON_HOE')
        infusion: media_catalog.Infusion = parsed.infusions[0]
        self.assertEqual((infusion.center, infusion.pedestals, infusion.essence_levels, infusion.duration_ticks),
                         ('golemancy:blank_rune', ('SUGAR', 'SUGAR', 'FEATHER', 'FEATHER'), 2, 100))
        self.assertEqual(parsed.items['golemancy:blank_core'], textures.CustomItem(name='Blank Animus Core', base='FIREWORK_STAR', tint='#FF0000', tier=None))
        self.assertEqual(parsed.adaptations[2].requires, (media_catalog.Requirement('x-child', 2), media_catalog.Requirement('x-root', 5)))

    def test_load_reads_the_file_the_exporter_writes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path: Path = Path(tmp) / 'recipes.json'
            path.write_text(json.dumps(CATALOG, indent=2) + '\n')
            self.assertEqual(media_catalog.load(path), catalog())

    def test_names_fall_back_to_the_item_id(self) -> None:
        parsed: media_catalog.Catalog = catalog()

        self.assertEqual(parsed.name('golemancy:crook'), "Artificer's Crook")
        self.assertEqual(parsed.name('IRON_INGOT'), 'Iron Ingot')
        self.assertEqual(parsed.name('#logs'), 'Logs')

    def test_malformed_catalogs_are_rejected(self) -> None:
        broken: dict[str, dict] = {key: copy.deepcopy(CATALOG) for key in ('missing-section', 'missing-field', 'unkeyed', 'wide', 'tall')}
        del broken['missing-section']['recipes']
        del broken['missing-field']['infusions'][0]['center']
        broken['unkeyed']['recipes'][0]['pattern'] = ['XXX']
        broken['wide']['recipes'][0]['pattern'] = ['ABCD']
        broken['tall']['recipes'][0]['pattern'] = ['A', 'A', 'A', 'A']
        for label, raw in broken.items():
            with self.subTest(label), self.assertRaisesRegex(ValueError, 'Malformed recipe catalog'):
                media_catalog.parse(raw)


class CubeTest(MediaCase):
    def test_block_icon_is_one_icon_with_clear_corners_and_shaded_faces(self) -> None:
        image: Image.Image = self.icons.icon('LODESTONE')

        self.assertEqual(image.size, (16, 16))
        self.assertEqual(image.mode, 'RGBA')
        for corner in ((0, 0), (15, 0), (0, 15), (15, 15)):
            self.assertEqual(image.getpixel(corner)[3], 0, corner)
        self.assertEqual(image.getpixel((8, 3)), BLOCK_COLORS['lodestone_top'] + (255,))
        self.assertEqual(image.getpixel((4, 10)), tuple(round(channel * 0.8) for channel in BLOCK_COLORS['lodestone_side']) + (255,))
        self.assertEqual(image.getpixel((12, 10)), tuple(round(channel * 0.6) for channel in BLOCK_COLORS['lodestone_side']) + (255,))

    def test_block_icon_has_no_holes_inside_its_silhouette(self) -> None:
        image: Image.Image = self.icons.icon('LODESTONE')

        for row in range(2, 14):
            opaque: list[int] = [column for column in range(16) if image.getpixel((column, row))[3] > 0]
            self.assertEqual(opaque, list(range(opaque[0], opaque[-1] + 1)), row)

    def test_slot_icons_are_the_supersampled_full_resolution_render(self) -> None:
        full: Image.Image = self.icons.hires('LODESTONE', textures.SUPERSAMPLE)

        self.assertEqual(full.size, (16 * textures.SUPERSAMPLE, 16 * textures.SUPERSAMPLE))
        self.assertEqual(self.icons.icon('LODESTONE').tobytes(), textures.downsample(full).tobytes())

    def test_chest_is_smaller_than_a_full_cube(self) -> None:
        chest: Image.Image = self.icons.icon('CHEST')
        full: Image.Image = self.icons.icon('IRON_BLOCK')

        self.assertEqual(chest.size, (16, 16))
        self.assertLess(sum(pixel[3] for pixel in chest.getdata()), sum(pixel[3] for pixel in full.getdata()))

    def test_animated_strips_use_their_first_frame(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path: Path = Path(tmp) / 'client.jar'
            strip: Image.Image = Image.new('RGBA', (16, 48), (0, 0, 255, 255))
            strip.paste(solid((255, 0, 0)), (0, 0))
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr(textures.TEXTURES + 'block/lava.png', png(strip))
            with textures.Jar(path) as jar:
                frame: Image.Image = jar.frame('block/lava')
        self.assertEqual(frame.size, (16, 16))
        self.assertEqual(frame.getpixel((8, 8)), (255, 0, 0, 255))


class IconsTest(MediaCase):
    def test_item_textures_are_used_as_they_are(self) -> None:
        self.assertEqual(self.icons.icon('IRON_HOE').getpixel((8, 8)), (170, 170, 170, 255))
        self.assertEqual(self.icons.icon('minecraft:iron_hoe').size, (16, 16))

    def test_block_items_without_an_item_texture_become_cubes(self) -> None:
        icon: Image.Image = self.icons.icon('IRON_BLOCK')

        self.assertEqual(icon.getpixel((8, 3)), (216, 216, 216, 255))
        self.assertEqual(icon.getpixel((0, 0))[3], 0)

    def test_logs_use_their_end_and_bark_textures(self) -> None:
        icon: Image.Image = self.icons.icon('OAK_LOG')

        self.assertEqual(icon.getpixel((8, 3)), (160, 130, 80, 255))
        self.assertEqual(icon.getpixel((4, 10)), (round(110 * 0.8), round(85 * 0.8), round(50 * 0.8), 255))

    def test_override_table_covers_snow_and_pumpkins(self) -> None:
        self.assertEqual(self.icons.icon('SNOW_BLOCK').getpixel((8, 3)), (250, 250, 250, 255))
        pumpkin: Image.Image = self.icons.icon('CARVED_PUMPKIN')
        self.assertEqual(pumpkin.getpixel((8, 3)), (200, 140, 30, 255))
        self.assertEqual(pumpkin.getpixel((4, 10)), (round(210 * 0.8), round(120 * 0.8), round(20 * 0.8), 255))

    def test_chests_come_from_the_entity_texture(self) -> None:
        icon: Image.Image = self.icons.icon('CHEST')

        self.assertEqual(icon.size, (16, 16))
        self.assertGreater(sum(1 for pixel in icon.getdata() if pixel[3] > 0), 100)

    def chest_pixel(self, image: Image.Image, res: int, face: str, across: float, down: float) -> tuple[int, int, int]:
        camera: textures.IsoCamera = textures.ISO
        size: float = textures.CHEST_SIZE[0]
        low: float = (1.0 - size) / 2.0
        high: float = low + size
        a: float = camera.half_width * res
        b: float = camera.half_depth * res
        s: float = camera.height * res
        height: float = size * (1.0 - down)
        if face == 'top':
            x, z, y = low + across * size, low + down * size, size
        elif face == 'front':
            x, z, y = low + across * size, high, height
        else:
            x, z, y = high, high - across * size, height
        position: tuple[int, int] = (round(image.width / 2 + (x - z) * a), round(image.height / 2 + (x + z) * b - y * s))
        return image.getpixel(position)[:3]

    def test_chest_faces_use_the_entity_texture_regions(self) -> None:
        res: int = 8
        image: Image.Image = self.icons.hires('chest', res)

        def shaded(color: Color, factor: float) -> Color:
            return tuple(round(channel * factor) for channel in color)

        self.assertEqual(image.size, (16 * res, 16 * res))
        self.assertEqual(self.chest_pixel(image, res, 'top', 0.5, 0.5), shaded(CHEST_REGIONS['top'][1], 1.0))
        lid_front: Color = shaded(CHEST_REGIONS['lid_front'][1], 0.8)
        base_front: Color = shaded(CHEST_REGIONS['base_front'][1], 0.8)
        self.assertEqual(self.chest_pixel(image, res, 'front', 0.2, 1.5 / 14), lid_front)
        self.assertEqual(self.chest_pixel(image, res, 'front', 0.8, 1.5 / 14), lid_front)
        self.assertEqual(self.chest_pixel(image, res, 'front', 0.2, 11.5 / 14), base_front)
        self.assertEqual(self.chest_pixel(image, res, 'front', 6.5 / 14, 4.5 / 14), shaded(CHEST_REGIONS['lock'][1], 0.8))
        self.assertEqual(self.chest_pixel(image, res, 'side', 0.5, 1.5 / 14), shaded(CHEST_REGIONS['lid_side'][1], 0.6))
        self.assertEqual(self.chest_pixel(image, res, 'side', 0.5, 11.5 / 14), shaded(CHEST_REGIONS['base_side'][1], 0.6))

    def test_the_latch_sits_on_the_front_face_only(self) -> None:
        image: Image.Image = self.icons.hires('copper_chest', 8)
        lock: Color = tuple(round(channel * 0.8) for channel in CHEST_REGIONS['lock'][1])
        lock_side: Color = tuple(round(channel * 0.6) for channel in CHEST_REGIONS['lock'][1])
        colors: set[Color] = {image.getpixel((x, y))[:3] for x in range(image.width) for y in range(image.height)}

        self.assertIn(lock, colors)
        self.assertNotIn(lock_side, colors)
        self.assertNotIn((255, 255, 255), colors)

    def test_chest_slot_icons_are_the_supersampled_chest(self) -> None:
        for name in ('CHEST', 'COPPER_CHEST'):
            icon: Image.Image = self.icons.icon(name)
            self.assertEqual(icon.size, (16, 16), name)
            self.assertEqual(icon.tobytes(), textures.downsample(self.icons.hires(name, textures.SUPERSAMPLE)).tobytes(), name)
            self.assertEqual(icon.getpixel((0, 0))[3], 0, name)

    def test_large_icons_scale_sprites_and_render_blocks_at_full_resolution(self) -> None:
        sprite: Image.Image = self.icons.large('IRON_HOE', 4)
        block: Image.Image = self.icons.large('CHISELED_COPPER', 4)

        self.assertEqual(sprite.size, (64, 64))
        self.assertEqual(sprite.getpixel((10, 10)), (170, 170, 170, 255))
        self.assertEqual(block.size, (64, 64))
        self.assertEqual(block.getpixel((32, 12)), BLOCK_COLORS['chiseled_copper'] + (255,))
        self.assertEqual(self.icons.large('golemancy:crook', 4).size, (64, 64))

    def test_front_cameras_show_the_front_and_top_faces_only(self) -> None:
        camera: textures.FrontCamera = textures.FrontCamera(depth=6, height=16)
        image: Image.Image = camera.box(solid((200, 0, 0)), solid((0, 200, 0)), solid((0, 0, 200)))

        self.assertEqual(image.size, (16, 22))
        self.assertEqual(image.getpixel((8, 2)), (200, 0, 0, 255))
        self.assertEqual(image.getpixel((8, 12)), (0, 160, 0, 255))
        self.assertNotIn((0, 0, 120, 255), set(image.getdata()))

    def test_tags_use_a_representative_member(self) -> None:
        self.assertEqual(self.icons.icon('#logs').tobytes(), self.icons.icon('OAK_LOG').tobytes())
        self.assertEqual(self.icons.icon('#minecraft:logs').tobytes(), self.icons.icon('OAK_LOG').tobytes())
        with self.assertRaisesRegex(textures.MissingTexture, 'No representative item for tag #wool'):
            self.icons.icon('#wool')

    def test_unknown_items_are_reported_by_name(self) -> None:
        with self.assertRaisesRegex(textures.MissingTexture, 'No item or block texture for DIAMOND_HOE|No item or block texture for diamond_hoe'):
            self.icons.icon('DIAMOND_HOE')

    def test_firework_star_cores_tint_the_overlay_with_the_configured_color(self) -> None:
        icon: Image.Image = self.icons.icon('golemancy:blank_core')
        red: tuple[int, int, int, int] = icon.getpixel((2, 8))
        shell: tuple[int, int, int, int] = icon.getpixel((13, 8))

        self.assertGreater(red[0], red[1] + 100)
        self.assertGreater(red[0], red[2] + 100)
        self.assertLess(abs(shell[0] - shell[1]), 30)

    def test_custom_items_carry_a_subtle_glint_and_keep_their_alpha(self) -> None:
        plain: Image.Image = self.icons.icon('IRON_HOE')
        custom: Image.Image = self.icons.icon('golemancy:crook')

        self.assertNotEqual(plain.tobytes(), custom.tobytes())
        self.assertEqual(plain.getchannel('A').tobytes(), custom.getchannel('A').tobytes())
        for pixel in range(16):
            before: tuple[int, int, int, int] = plain.getpixel((pixel, pixel))
            after: tuple[int, int, int, int] = custom.getpixel((pixel, pixel))
            self.assertLess(max(abs(before[channel] - after[channel]) for channel in range(3)), 60)

    def test_glint_leaves_clear_pixels_alone(self) -> None:
        clear: Image.Image = Image.new('RGBA', (4, 4), (10, 20, 30, 0))

        self.assertEqual(textures.glint(clear).tobytes(), clear.tobytes())

    def test_icons_are_cached_but_not_shared(self) -> None:
        first: Image.Image = self.icons.icon('IRON_HOE')
        first.putpixel((0, 0), (1, 2, 3, 255))

        self.assertNotEqual(self.icons.icon('IRON_HOE').getpixel((0, 0)), (1, 2, 3, 255))


class GuiTest(MediaCase):
    def test_panel_keeps_the_texture_background_and_clear_corners(self) -> None:
        panel: Image.Image = self.gui.panel(40, 30)

        self.assertEqual(panel.size, (40, 30))
        self.assertEqual(panel.getpixel((20, 15)), PANEL + (255,))

    def test_vanilla_slot_art_is_cropped_from_the_crafting_table(self) -> None:
        self.assertEqual(self.gui.content().size, (116, 54))
        self.assertEqual(self.gui.arrow().size, (22, 15))
        self.assertEqual(self.gui.result_slot().size, (26, 26))
        self.assertEqual(self.gui.content().getpixel((5, 5)), (139, 139, 139, 255))


class RecipeRenderTest(MediaCase):
    def render(self, index: int) -> Image.Image:
        parsed: media_catalog.Catalog = catalog()
        return media_render.render_recipe(parsed.recipes[index], parsed, self.icons, self.gui)

    def test_recipe_image_is_the_crafting_panel_at_four_times_scale(self) -> None:
        image: Image.Image = self.render(0)

        self.assertEqual(textures.SCALE, 4)
        self.assertEqual(image.size, ((116 + 16) * 4, (54 + 16 + 24) * 4))
        self.assertEqual(image.mode, 'RGBA')

    def test_ingredients_sit_in_their_grid_slots_and_the_result_in_the_result_slot(self) -> None:
        image: Image.Image = self.render(0)

        def center(column: int, row: int) -> tuple[int, int]:
            return ((media_render.PAD + 1 + media_render.SLOT * column) * 4 + 32, (media_render.PAD + 1 + media_render.SLOT * row) * 4 + 32)

        self.assertEqual(image.getpixel(center(1, 1))[:3], (170, 170, 170))
        self.assertEqual(image.getpixel(center(1, 0))[:3], (200, 100, 255))
        self.assertEqual(image.getpixel(center(0, 1))[:3], (255, 0, 0))
        self.assertEqual(image.getpixel(center(1, 2))[:3], (220, 120, 60))
        self.assertEqual(image.getpixel(center(0, 0))[:3], (139, 139, 139))
        result: tuple[int, int] = ((media_render.PAD + media_render.RESULT_OFFSET[0]) * 4 + 32, (media_render.PAD + media_render.RESULT_OFFSET[1]) * 4 + 32)
        self.assertNotEqual(image.getpixel(result)[:3], (139, 139, 139))

    def test_short_patterns_are_centered_in_the_grid(self) -> None:
        image: Image.Image = self.render(2)

        def center(column: int, row: int) -> tuple[int, int]:
            return ((media_render.PAD + 1 + media_render.SLOT * column) * 4 + 32, (media_render.PAD + 1 + media_render.SLOT * row) * 4 + 32)

        self.assertEqual(image.getpixel(center(1, 1))[:3], (170, 170, 170))
        log_top: tuple[int, int] = (center(1, 0)[0], center(1, 0)[1] - 20)
        self.assertEqual(image.getpixel(log_top)[:3], BLOCK_COLORS['oak_log_top'])
        self.assertEqual(image.getpixel(center(0, 0))[:3], (139, 139, 139))

    def test_yield_and_tithe_change_the_caption_but_not_the_size(self) -> None:
        free: Image.Image = self.render(0)
        counted: Image.Image = self.render(1)
        tithed: Image.Image = self.render(2)

        self.assertEqual(free.size, counted.size)
        self.assertEqual(free.size, tithed.size)
        caption_free: Image.Image = free.crop((0, 270, free.width, free.height))
        caption_tithed: Image.Image = tithed.crop((0, 270, tithed.width, tithed.height))
        self.assertNotEqual(caption_free.tobytes(), caption_tithed.tobytes())

    def test_the_result_slot_shows_the_yield_count_only_above_one(self) -> None:
        parsed: media_catalog.Catalog = catalog()
        recipe: media_catalog.Recipe = parsed.recipes[0]
        box: tuple[int, int, int, int] = tuple(value * 4 for value in (media_render.PAD + media_render.RESULT_OFFSET[0] + 6, media_render.PAD + media_render.RESULT_OFFSET[1] + 6,
                                                                       media_render.PAD + media_render.RESULT_OFFSET[0] + 17, media_render.PAD + media_render.RESULT_OFFSET[1] + 17))

        def slot(yield_count: int) -> Image.Image:
            return media_render.render_recipe(dataclasses.replace(recipe, yield_count=yield_count), parsed, self.icons, self.gui).crop(box)

        single: Image.Image = slot(1)
        counted: Image.Image = slot(4)
        self.assertNotIn(textures.WHITE[:3], {pixel[:3] for pixel in single.getdata()})
        self.assertIn(textures.WHITE[:3], {pixel[:3] for pixel in counted.getdata()})
        self.assertIn(textures.SHADOW[:3], {pixel[:3] for pixel in counted.getdata()})
        self.assertNotEqual(single.tobytes(), counted.tobytes())
        self.assertEqual(slot(1).tobytes(), single.tobytes())

    def test_tithe_text_is_singular_for_one_level(self) -> None:
        self.assertEqual(media_render.plural(1, 'level'), '1 level')
        self.assertEqual(media_render.plural(2, 'level'), '2 levels')
        self.assertEqual(media_render.seconds(100), '5 s')
        self.assertEqual(media_render.seconds(110), '5.5 s')


class InfusionRenderTest(MediaCase):
    def test_infusion_image_has_the_plus_arrow_result_layout(self) -> None:
        parsed: media_catalog.Catalog = catalog()
        image: Image.Image = media_render.render_infusion(parsed.infusions[0], parsed, self.icons, self.gui)

        self.assertEqual(image.size, ((8 + 76 + 10 + 22 + 10 + 26 + 8) * 4, (8 + 81 + 8 + 24) * 4))

        def colors(column: int, row: int) -> set[tuple[int, int, int]]:
            x: int = media_render.PAD + column * media_render.CELL_WIDTH
            y: int = media_render.PAD + row * media_render.CELL_HEIGHT + media_render.EXTRA
            return {image.getpixel((px, py))[:3] for px in range(x * 4, (x + 16) * 4) for py in range(y * 4, (y + 16) * 4)}

        lodestone: tuple[int, int, int] = (round(120 * 1.0), round(120 * 1.0), round(130 * 1.0))
        copper: tuple[int, int, int] = (200, 110, 80)
        self.assertIn(lodestone, colors(1, 1))
        plain: tuple[int, int, int] = BLOCK_COLORS['copper_block']
        for column, row in ((1, 0), (2, 1), (1, 2), (0, 1)):
            self.assertIn(copper, colors(column, row))
            self.assertNotIn(plain, colors(column, row))
        self.assertNotIn(copper, colors(1, 1))

    def test_pedestal_items_are_drawn_north_east_south_west_in_listed_order(self) -> None:
        parsed: media_catalog.Catalog = catalog()
        infusion: media_catalog.Infusion = dataclasses.replace(parsed.infusions[0], pedestals=('SUGAR', 'REDSTONE', 'FEATHER', 'COPPER_INGOT'))
        image: Image.Image = media_render.render_infusion(infusion, parsed, self.icons, self.gui)

        def center(column: int, row: int) -> tuple[int, int]:
            return ((media_render.PAD + column * media_render.CELL_WIDTH + 8) * 4, (media_render.PAD + row * media_render.CELL_HEIGHT + 8) * 4)

        self.assertEqual(image.getpixel(center(1, 0))[:3], ITEM_COLORS['sugar'])
        self.assertEqual(image.getpixel(center(2, 1))[:3], ITEM_COLORS['redstone'])
        self.assertEqual(image.getpixel(center(1, 2))[:3], ITEM_COLORS['feather'])
        self.assertEqual(image.getpixel(center(0, 1))[:3], ITEM_COLORS['copper_ingot'])

    def test_result_label_names_the_rune_tier(self) -> None:
        self.assertEqual(media_render.result_label(catalog(), 'golemancy:rune/swiftness/1'), 'Swiftness Rune I')
        self.assertEqual(media_render.result_label(catalog(), 'golemancy:crook'), "Artificer's Crook")


class DiagramTest(MediaCase):
    def test_plinth_top_view_puts_the_pedestals_exactly_two_blocks_out(self) -> None:
        image: Image.Image = media_diagrams.plinth_top(self.icons, self.gui)
        self.assertEqual(image.size, (480, 480))

        def tile(column: int, row: int) -> tuple[int, int, int]:
            return image.getpixel(((20 + 16 * column + 8) * 4, (20 + 16 * row + 8) * 4))[:3]

        self.assertEqual(tile(2, 2), BLOCK_COLORS['lodestone_top'])
        for column, row in ((2, 0), (4, 2), (2, 4), (0, 2)):
            self.assertEqual(tile(column, row), BLOCK_COLORS['chiseled_copper'], (column, row))
        blended: Color = tuple(round((BLOCK_COLORS['dirt'][channel] * media_diagrams.GROUND_ALPHA + PANEL[channel] * (255 - media_diagrams.GROUND_ALPHA)) / 255)
                               for channel in range(3))
        for column, row in ((1, 2), (2, 1), (3, 2), (2, 3), (1, 1), (3, 3), (0, 0), (4, 4)):
            for channel in range(3):
                self.assertAlmostEqual(tile(column, row)[channel], blended[channel], delta=2, msg=(column, row))

    def test_plinth_top_view_labels_the_four_compass_points(self) -> None:
        image: Image.Image = media_diagrams.plinth_top(self.icons, self.gui)

        for position in ((240, 40), (440, 240), (240, 440), (40, 240)):
            window: Image.Image = image.crop((position[0] - 20, position[1] - 24, position[0] + 20, position[1] + 24))
            self.assertTrue(any(pixel[:3] == textures.INK[:3] for pixel in window.getdata()), position)

    def test_plinth_view_keeps_the_pedestals_two_blocks_out_on_the_axes(self) -> None:
        scene: media_diagrams.Scene = media_diagrams.plinth_scene(self.icons, self.gui)

        blocks: set[tuple[int, int]] = {(item.i, item.j) for item in scene.placed if item.k == 0}
        ground: set[tuple[int, int]] = {(item.i, item.j) for item in scene.placed if item.k == -1}
        self.assertEqual(ground, {(i, j) for i in range(5) for j in range(5)})
        self.assertEqual(blocks, {(2, 2), (2, 0), (4, 2), (2, 4), (0, 2)})
        self.assertEqual(len(scene.placed), 30)
        for pedestal in blocks - {(2, 2)}:
            self.assertEqual(sorted((abs(pedestal[0] - 2), abs(pedestal[1] - 2))), [0, 2], pedestal)
        for gap in ((2, 1), (3, 2), (2, 3), (1, 2)):
            self.assertNotIn(gap, blocks)

    def test_plinth_view_shows_ground_between_each_pedestal_and_the_lodestone(self) -> None:
        scene: media_diagrams.Scene = media_diagrams.plinth_scene(self.icons, self.gui)
        image, origin = scene.render(1)
        blended: Color = tuple(round((BLOCK_COLORS['dirt'][channel] * media_diagrams.GROUND_ALPHA + PANEL[channel] * (255 - media_diagrams.GROUND_ALPHA)) / 255)
                               for channel in range(3))

        def pixel(x: float, z: float) -> Color:
            at: tuple[float, float] = scene.project(origin, x, 0, z)
            return image.getpixel((round(at[0]), round(at[1])))[:3]

        for gap in ((2.5, 1.2), (2.5, 3.2), (1.5, 2.5), (3.5, 2.5)):
            for channel in range(3):
                self.assertAlmostEqual(pixel(*gap)[channel], blended[channel], delta=2, msg=gap)
        self.assertNotEqual(pixel(2.5, 2.5), blended)
        self.assertNotEqual(pixel(2.5, 0.5), blended)

    def test_plinth_view_has_compass_markers_and_short_labels(self) -> None:
        image: Image.Image = media_diagrams.plinth_view(self.icons, self.gui)

        self.assertEqual(image.mode, 'RGBA')
        self.assertEqual((image.width % 4, image.height % 4), (0, 0))
        ink: int = sum(1 for pixel in image.getdata() if pixel[:3] == textures.INK[:3])
        self.assertGreater(ink, 800)

    def test_iron_golem_t_runs_left_to_right_with_the_pumpkin_over_the_middle(self) -> None:
        camera: textures.FrontCamera = media_diagrams.GOLEM_CAMERA
        cells: list[media_diagrams.Cell] = [(0, 0, 0, 'IRON_BLOCK'), (-1, 0, 1, 'IRON_BLOCK'), (0, 0, 1, 'IRON_BLOCK'), (1, 0, 1, 'IRON_BLOCK'),
                                            (0, 0, 2, 'CARVED_PUMPKIN')]
        scene: media_diagrams.Scene = media_diagrams.golem_scene(self.icons, cells)

        anchors: list[tuple[float, float]] = [camera.anchor(item.i, item.j, item.k) for item in scene.placed]
        arms: list[tuple[float, float]] = anchors[1:4]
        self.assertEqual(len({anchor[1] for anchor in arms}), 1)
        self.assertEqual([arm[0] for arm in arms], [-16.0, 0.0, 16.0])
        self.assertEqual(anchors[0][0], anchors[2][0])
        self.assertEqual(anchors[4][0], anchors[2][0])
        self.assertLess(anchors[4][1], arms[0][1])
        self.assertGreater(anchors[0][1], arms[0][1])

    def test_golem_builds_image_has_one_labelled_column_per_golem(self) -> None:
        image: Image.Image = media_diagrams.golem_builds(self.icons, self.gui)

        self.assertEqual((image.width % 4, image.height % 4), (0, 0))
        self.assertGreater(image.width, image.height)

    def test_node_decal_lies_flat_on_the_chest_front_face(self) -> None:
        image: Image.Image = media_diagrams.node_on_chest(self.icons, self.gui)
        brick: tuple[int, int, int] = ITEM_COLORS['brick']
        points: list[tuple[int, int]] = [(x, y) for x in range(image.width) for y in range(image.height) if image.getpixel((x, y))[:3] == brick]

        self.assertGreater(len(points), 300)
        left: list[int] = [y for x, y in points if x < min(point[0] for point in points) + 6]
        right: list[int] = [y for x, y in points if x > max(point[0] for point in points) - 6]
        self.assertLess(sum(left) / len(left), sum(right) / len(right))
        self.assertEqual(media_diagrams.CHEST_SCALE % textures.SCALE, 0)

    def test_work_area_is_block_aligned_around_the_block_in_front_of_the_node_face(self) -> None:
        low, high = media_diagrams.work_bounds((0, 0, 0), media_diagrams.NODE_FACE, media_diagrams.WORK_RADIUS)
        center: tuple[float, float, float] = tuple((low[axis] + high[axis]) / 2 for axis in range(3))
        in_front: tuple[int, int, int] = tuple(media_diagrams.NODE_FACE)

        self.assertEqual(center, tuple(in_front[axis] + 0.5 for axis in range(3)))
        self.assertEqual([high[axis] - low[axis] for axis in range(3)], [2 * media_diagrams.WORK_RADIUS + 1] * 3)
        self.assertTrue(all(isinstance(value, int) for value in low + high))
        self.assertGreater(center[2], 0.5 + textures.CHEST_SIZE[2] / 2)
        chest: tuple[float, float] = textures.ISO.world(0.5, 0.5, 0.5)
        area: tuple[float, float] = textures.ISO.world(*center)
        self.assertLess(area[0], chest[0])
        self.assertGreater(area[1], chest[1])

    def test_work_area_covers_the_same_blocks_as_the_node_area(self) -> None:
        low, high = media_diagrams.work_bounds((10, 64, -3), (1, 0, 0), 2)

        self.assertEqual((low, high), ((9, 62, -5), (14, 67, 0)))

    def test_work_radius_is_the_default_radius_of_a_new_node(self) -> None:
        source: str = (ROOT / 'src' / 'main' / 'java' / 'art' / 'arcane' / 'adapt' / 'api' / 'golem' / 'node' / 'NodeRecord.java').read_text()
        found = re.search(r'DEFAULT_RADIUS\s*=\s*(\d+)', source)

        self.assertIsNotNone(found)
        self.assertEqual(media_diagrams.WORK_RADIUS, int(found.group(1)))

    def test_work_area_edges_near_the_viewer_are_drawn_after_the_chest(self) -> None:
        log: list[tuple[str, tuple[float, float] | None, tuple[float, float] | None]] = []
        real_dashed = media_diagrams.dashed
        real_draw = media_diagrams.Scene.draw

        def dashed(draw, start, end, fill) -> None:
            log.append(('edge', start, end))
            real_dashed(draw, start, end, fill)

        def draw(scene, canvas, origin) -> None:
            log.append(('chest', None, None))
            real_draw(scene, canvas, origin)

        with mock.patch.object(media_diagrams, 'dashed', dashed), mock.patch.object(media_diagrams.Scene, 'draw', draw):
            media_diagrams.node_on_chest(self.icons, self.gui)

        self.assertEqual([entry[0] for entry in log], ['edge'] * 9 + ['chest'] + ['edge'] * 3)
        front: list[tuple[str, tuple[float, float] | None, tuple[float, float] | None]] = log[10:]
        shared: set[tuple[float, float]] = set.intersection(*({edge[1], edge[2]} for edge in front))
        self.assertEqual(len(shared), 1)

    def test_only_the_three_edges_meeting_at_the_corner_nearest_the_viewer_are_in_front(self) -> None:
        low, high = media_diagrams.work_bounds((0, 0, 0), media_diagrams.NODE_FACE, media_diagrams.WORK_RADIUS)

        behind, front = media_diagrams.work_edges(low, high)

        self.assertEqual((len(behind), len(front)), (9, 3))
        self.assertTrue(all(high in edge for edge in front))
        self.assertTrue(all(high not in edge for edge in behind))
        self.assertEqual(len({edge for edge in behind + front}), 12)
        for start, end in behind + front:
            self.assertEqual(sum(1 for axis in range(3) if start[axis] != end[axis]), 1)

    def test_plinth_view_lodestone_label_stays_off_the_ground(self) -> None:
        spoken: list[tuple[tuple[int, int], str, int, str]] = []
        real_text = textures.text

        def text(canvas, position, value, size, fill=textures.INK, anchor='la', shadow=False) -> None:
            spoken.append((position, value, size, anchor))
            real_text(canvas, position, value, size, fill, anchor, shadow)

        with mock.patch.object(media_diagrams.textures, 'text', text):
            image: Image.Image = media_diagrams.plinth_view(self.icons, self.gui)

        blended: Color = tuple(round((BLOCK_COLORS['dirt'][channel] * media_diagrams.GROUND_ALPHA + PANEL[channel] * (255 - media_diagrams.GROUND_ALPHA)) / 255)
                               for channel in range(3))
        ground: list[tuple[int, int]] = [(x, y) for x in range(image.width) for y in range(image.height)
                                         if all(abs(image.getpixel((x, y))[channel] - blended[channel]) <= 2 for channel in range(3))]
        plate: tuple[int, int, int, int] = (min(x for x, _ in ground), min(y for _, y in ground), max(x for x, _ in ground), max(y for _, y in ground))
        label = next(entry for entry in spoken if entry[1] == 'Lodestone')
        (x, y), _, size, anchor = label
        span: int = media_diagrams.width_of('Lodestone', size)
        left: float = x if anchor[0] == 'l' else x - span if anchor[0] == 'r' else x - span / 2
        box: tuple[float, float, float, float] = (left, y - size / 2, left + span, y + size / 2)

        clear: bool = box[2] < plate[0] or box[0] > plate[2] or box[3] < plate[1] or box[1] > plate[3]
        self.assertTrue(clear, (box, plate))

    def test_decal_maps_a_sprite_onto_a_parallelogram(self) -> None:
        canvas: Image.Image = Image.new('RGBA', (40, 40), (0, 0, 0, 0))
        sprite: Image.Image = Image.new('RGBA', (2, 2))
        sprite.putdata([(255, 0, 0, 255), (0, 255, 0, 255), (0, 0, 255, 255), (255, 255, 0, 255)])

        media_diagrams.decal(canvas, sprite, ((10, 10), (30, 10), (10, 30)))

        self.assertEqual(canvas.getpixel((15, 15)), (255, 0, 0, 255))
        self.assertEqual(canvas.getpixel((25, 15)), (0, 255, 0, 255))
        self.assertEqual(canvas.getpixel((15, 25)), (0, 0, 255, 255))
        self.assertEqual(canvas.getpixel((25, 25)), (255, 255, 0, 255))
        self.assertEqual(canvas.getpixel((35, 35))[3], 0)
        self.assertEqual(canvas.getpixel((5, 5))[3], 0)

    def test_requirement_depths_and_columns(self) -> None:
        adaptations: tuple[media_catalog.Adaptation, ...] = catalog().adaptations
        depths: dict[str, int] = media_diagrams.depths(adaptations)

        self.assertEqual(depths, {'x-root': 0, 'x-child': 1, 'x-grandchild': 2, 'x-bone': 1})
        layers: list[list[media_catalog.Adaptation]] = media_diagrams.columns(adaptations, depths)
        self.assertEqual([[adaptation.id for adaptation in layer] for layer in layers], [['x-root'], ['x-child', 'x-bone'], ['x-grandchild']])

    def test_requirements_outside_the_catalog_become_a_note(self) -> None:
        adaptations: tuple[media_catalog.Adaptation, ...] = catalog().adaptations
        known: set[str] = {adaptation.id for adaptation in adaptations}

        self.assertEqual(media_diagrams.external_note(adaptations[3], known), '+ Skeletal Servant 1 (TragOul)')
        self.assertEqual(media_diagrams.external_note(adaptations[1], known), '')

    def test_scene_orders_blocks_back_to_front(self) -> None:
        front: Image.Image = solid((255, 0, 0), (64, 64))
        back: Image.Image = solid((0, 0, 255), (64, 64))
        scene: media_diagrams.Scene = media_diagrams.Scene(textures.ISO, 4)
        scene.block(1, 1, 0, front, False)
        scene.block(0, 0, 0, back, False)

        image, origin = scene.render(2)
        center: tuple[float, float] = scene.project(origin, 1.5, 0.5, 1.5)

        self.assertEqual(image.getpixel((round(center[0]), round(center[1]))), (255, 0, 0, 255))

    def test_tree_image_has_no_clipped_box_at_the_edges(self) -> None:
        image: Image.Image = media_diagrams.prerequisite_tree(catalog(), self.gui)

        self.assertEqual(image.width % 4, 0)
        self.assertEqual(image.height % 4, 0)
        self.assertGreater(image.width, media_diagrams.BOX_WIDTH * 3)


class RenderAllTest(MediaCase):
    def render(self, out: Path) -> list[Path]:
        return media_render.render_all(catalog(), self.jar, out)

    def test_every_recipe_infusion_and_diagram_is_written(self) -> None:
        out: Path = self.tmp / 'media'
        written: list[Path] = self.render(out)

        expected: list[str] = ['recipes/golemancy-crook.png', 'recipes/golemancy-blank-core.png', 'recipes/golemancy-log-hoe.png',
                               'infusions/swiftness_1.png', 'diagrams/animus-plinth-top.png', 'diagrams/animus-plinth-view.png', 'diagrams/golem-builds.png',
                               'diagrams/node-on-chest.png', 'diagrams/prerequisite-tree.png']
        self.assertEqual([path.relative_to(out).as_posix() for path in written], expected)
        for name in expected:
            with Image.open(out / name) as image:
                self.assertEqual(image.format, 'PNG', name)
                self.assertEqual(image.mode, 'RGBA', name)
                self.assertGreater(image.width, 100, name)
                self.assertEqual((image.width % 4, image.height % 4), (0, 0), name)

    def test_the_same_input_gives_byte_identical_files(self) -> None:
        first: list[Path] = self.render(self.tmp / 'one')
        second: list[Path] = self.render(self.tmp / 'two')

        self.assertEqual(len(first), len(second))
        for one, two in zip(first, second):
            self.assertEqual(one.read_bytes(), two.read_bytes(), one.name)

    def test_a_second_render_into_the_same_folder_replaces_files_in_place(self) -> None:
        out: Path = self.tmp / 'media'
        self.render(out)
        before: dict[str, bytes] = {path.name: path.read_bytes() for path in out.rglob('*.png')}
        self.render(out)

        self.assertEqual({path.name: path.read_bytes() for path in out.rglob('*.png')}, before)


class CommandLineTest(MediaCase):
    def recipes(self) -> Path:
        path: Path = self.tmp / 'recipes.json'
        path.write_text(json.dumps(CATALOG))
        return path

    def run_main(self, *arguments: str) -> tuple[int, str, str]:
        with mock.patch('sys.stdout', new_callable=io.StringIO) as stdout, mock.patch('sys.stderr', new_callable=io.StringIO) as stderr:
            code: int = media_render.main(list(arguments))
        return code, stdout.getvalue(), stderr.getvalue()

    def test_main_renders_into_the_output_folder(self) -> None:
        out: Path = self.tmp / 'out'
        code, stdout, _ = self.run_main('--recipes', str(self.recipes()), '--client-jar', str(self.jar_path), '--out', str(out))

        self.assertEqual(code, 0)
        self.assertIn('rendered 9 image(s) into ' + str(out), stdout)
        self.assertTrue((out / 'recipes' / 'golemancy-crook.png').is_file())

    def test_default_client_jar_is_the_prism_26_2_client(self) -> None:
        self.assertEqual(media_render.DEFAULT_CLIENT_JAR.parts[-7:], ('PrismLauncher', 'libraries', 'com', 'mojang', 'minecraft', '26.2', 'minecraft-26.2-client.jar'))

    def test_missing_jar_or_recipes_exit_two_with_a_message(self) -> None:
        code, _, stderr = self.run_main('--recipes', str(self.recipes()), '--client-jar', str(self.tmp / 'nope.jar'), '--out', str(self.tmp / 'out'))
        self.assertEqual(code, 2)
        self.assertIn('nope.jar', stderr)
        code, _, stderr = self.run_main('--recipes', str(self.tmp / 'nope.json'), '--client-jar', str(self.jar_path), '--out', str(self.tmp / 'out'))
        self.assertEqual(code, 2)
        self.assertIn('nope.json', stderr)

    def test_a_texture_missing_from_the_jar_exits_two_and_names_it(self) -> None:
        raw: dict = copy.deepcopy(CATALOG)
        raw['recipes'][0]['key']['H'] = 'DIAMOND_HOE'
        path: Path = self.tmp / 'broken.json'
        path.write_text(json.dumps(raw))

        code, _, stderr = self.run_main('--recipes', str(path), '--client-jar', str(self.jar_path), '--out', str(self.tmp / 'out'))

        self.assertEqual(code, 2)
        self.assertIn('diamond_hoe', stderr.lower())

    def test_malformed_recipes_exit_two(self) -> None:
        path: Path = self.tmp / 'broken.json'
        path.write_text('{"items": {}}')

        code, _, stderr = self.run_main('--recipes', str(path), '--client-jar', str(self.jar_path), '--out', str(self.tmp / 'out'))

        self.assertEqual(code, 2)
        self.assertIn('Malformed recipe catalog', stderr)

    def test_out_is_required(self) -> None:
        with mock.patch('sys.stderr', new_callable=io.StringIO), self.assertRaises(SystemExit) as raised:
            media_render.main(['--recipes', str(self.recipes())])
        self.assertEqual(raised.exception.code, 2)


if __name__ == '__main__':
    unittest.main()
