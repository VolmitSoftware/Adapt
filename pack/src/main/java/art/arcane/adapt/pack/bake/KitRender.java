package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.client.theme.BookGeometry;
import art.arcane.adapt.client.theme.PageLife;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.Solo;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.theme.ThemeSprite;
import art.arcane.adapt.client.theme.ThemedBook;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.raster.RasterDraw;
import art.arcane.adapt.pack.raster.RasterTextures;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One kit's themed book laid out by the client's composer on a page's canvas, as a book shown on its own (see
 * {@link Solo}): the recorded layer alone, and the moving parts painted over it at any time on the page clock.
 */
final class KitRender {
    private final ThemedBook book;
    private final RasterTextures textures;
    private final UiSpriteCache sprites;
    private final int width;
    private final int height;

    KitRender(ThemeAssets assets, PageSpec spec, int leaf) {
        ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
        textures = RasterTextures.of(Map.of(kit.texture().id(), assets.atlasPng()));
        sprites = new UiSpriteCache(textures);
        book = ThemedBook.alone(spec.geometry(leaf), new PageTheme(kit, null), new Solo());
        footToEnds(book, kit);
        width = spec.canvasWidth();
        height = spec.canvasHeight();
    }


    /**
     * Lays the foot band of tiles level from the paper's left end to its right end, the end tiles cut. The composer centres
     * whole tiles and lifts some a unit, which on one page shown on its own leaves each row short of the corners and a
     * lifted tile reads as a step there. Tiles keep the composer's picks, repeated along each row past its ends.
     */
    private static void footToEnds(ThemedBook book, ThemeKit kit) {
        List<PageLife.FootTile> foot = book.life().foot();
        if (foot.isEmpty()) {
            return;
        }
        List<BookGeometry.Box> paper = book.geometry().paper();
        int p0 = paper.get(0).x0();
        int p1 = paper.get(paper.size() - 1).x1();
        List<List<PageLife.FootTile>> rows = List.of(new ArrayList<>(), new ArrayList<>());
        for (PageLife.FootTile tile : foot) {
            boolean front = tile.rest().equals(kit.sprite("foot/0/0/a")) || tile.rest().equals(kit.sprite("foot/0/1/a"));
            rows.get(front ? 0 : 1).add(tile);
        }
        List<PageLife.FootTile> laid = new ArrayList<>();
        for (List<PageLife.FootTile> run : rows) {
            if (run.isEmpty()) {
                continue;
            }
            PageLife.FootTile first = run.get(0);
            int y = Integer.MIN_VALUE;
            for (PageLife.FootTile tile : run) {
                y = Math.max(y, tile.y());
            }
            int step = first.rest().width();
            for (int x = first.x() - Math.ceilDiv(first.x() - p0, step) * step; x < p1; x += step) {
                PageLife.FootTile like = run.get(Math.floorMod(Math.floorDiv(x - first.x(), step), run.size()));
                int left = Math.max(0, p0 - x);
                int right = Math.max(0, x + step - p1);
                laid.add(new PageLife.FootTile(kit, cut(like.rest(), left, right), cut(like.swayed(), left, right), x + left, y,
                        like.phase() + (x - like.x()) / 45.0));
            }
        }
        foot.clear();
        foot.addAll(laid);
    }

    private static ThemeSprite cut(ThemeSprite sprite, int left, int right) {
        return new ThemeSprite(sprite.u() + left, sprite.v(), sprite.width() - left - right, sprite.height());
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /** The recorded layer: everything that never moves and is laid out before the moving parts. */
    Raster base() {
        Raster raster = new Raster(width, height);
        RasterDraw draw = new RasterDraw(raster, sprites, textures);
        draw.budget(book.layerSize());
        book.paintBack(draw, 0L);
        return raster;
    }

    /** The page behind content at rest: the recorded layer and the moving parts drawn behind it at time 0. */
    Raster back() {
        Raster raster = new Raster(width, height);
        book.paintBack(new RasterDraw(raster, sprites, textures), 0L);
        return raster;
    }

    /** The kit's crumb ribbon, {@code width} wide, as the themed page draws it behind the breadcrumb. */
    Raster ribbon(int width) {
        Raster raster = new Raster(width, KitBake.RIBBON_HEIGHT);
        book.ribbon(new RasterDraw(raster, sprites, textures), 0, 0, width);
        return raster;
    }

    /** Paints the moving parts at {@code millis} over {@code into}, which already holds the base, cut to {@code clip}. */
    void moving(Raster into, long millis, RasterDraw.Clip clip) {
        RasterDraw draw = new RasterDraw(into, sprites, textures);
        if (clip != null) {
            draw.pushScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        }
        draw.skip(book.layerSize());
        book.paintBack(draw, millis);
        book.paintFront(draw, millis);
    }
}
