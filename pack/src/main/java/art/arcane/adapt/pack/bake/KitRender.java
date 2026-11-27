package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.Solo;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.theme.ThemedBook;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.raster.RasterDraw;
import art.arcane.adapt.pack.raster.RasterTextures;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * One kit's themed book laid out by the client's composer on a page's canvas, as a book shown on its own whose top right
 * corner pieces keep clear of the header chips (see {@link Solo}): the recorded layer alone, and the moving parts painted
 * over it at any time on the page clock.
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
        book = ThemedBook.alone(spec.geometry(leaf), new PageTheme(kit, null), solo(spec));
        width = spec.canvasWidth();
        height = spec.canvasHeight();
    }

    /** How a pack page on {@code spec}'s canvas is dressed: its top right corner pieces start clear of the header chips. */
    static Solo solo(PageSpec spec) {
        return new Solo(HeaderHold.RIGHT + Solo.APART - spec.canvasX());
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
