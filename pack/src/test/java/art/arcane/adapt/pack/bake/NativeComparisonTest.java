package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.net.FeedbackSink;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.screen.PreviewSamples;
import art.arcane.adapt.client.screen.ScreenRouter;
import art.arcane.adapt.client.state.ClientActions;
import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.theme.PageThemes;
import art.arcane.adapt.client.ui.PackScreenHost;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.raster.RasterDraw;
import art.arcane.adapt.pack.raster.RasterTextures;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Spike S5: the client's own skill page drawn by {@link RasterDraw}, set against an in-game capture of the same page.
 * Runs only with {@code -Dadapt.pack.evidence=<folder>} and {@code -Dadapt.pack.captures=<folder>}; writes side by side
 * images and the measured difference there. Text and items are masked out, as is anything the raster leaves clear.
 */
class NativeComparisonTest {
    private static final int SCALE = 3;

    @ParameterizedTest
    @CsvSource({"herbalism, grid2-herbalism-gui3-1.png", "hunter, grid3-hunter-gui3.png"})
    void compareWithCapture(String skill, String capture) throws IOException {
        String evidence = System.getProperty("adapt.pack.evidence");
        String captures = System.getProperty("adapt.pack.captures");
        Assumptions.assumeTrue(evidence != null && captures != null, "evidence run only");
        Path assets = Path.of(System.getProperty("adapt.pack.assets"));
        BufferedImage shot = ImageIO.read(Path.of(captures, capture).toFile());
        int width = shot.getWidth() / SCALE;
        int height = shot.getHeight() / SCALE;

        RasterTextures files = new RasterTextures((String id) -> read(assets, id));
        UiSpriteCache sprites = new UiSpriteCache(files);
        PackScreenHost host = new PackScreenHost(width, height);
        ClientState state = new ClientState();
        ClientActions actions = new ClientActions(state, (frame) -> true);
        state.negotiated(ClientProtocol.VERSION, new ServerInfo("pack", "pack", ClientProtocol.SCREENS | ClientProtocol.PANEL));
        state.acceptCatalog(PreviewSamples.catalog());
        state.acceptPlayer(PreviewSamples.player());
        UiMotion still = new UiMotion(() -> 0L, true);
        ScreenRouter router = new ScreenRouter(host, state, actions, UiTheme.DEFAULT, still, PageThemes.defaults(), FeedbackSink.NONE);
        router.open(new Route.Skill(skill));
        Raster ours = new Raster(width, height);
        RasterDraw draw = new RasterDraw(ours, sprites, files);
        router.current().render(draw, -1000, -1000, 0.0F);
        ours.clear();
        draw = new RasterDraw(ours, sprites, files);
        router.current().render(draw, -1000, -1000, 0.0F);

        Raster theirs = new Raster(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                theirs.set(x, y, shot.getRGB(x * SCALE + 1, y * SCALE + 1) | 0xFF000000);
            }
        }
        boolean[] mask = new boolean[width * height];
        for (RasterDraw.TextCall text : draw.texts()) {
            int w = (int) Math.ceil(draw.textWidth(text.text()) * text.scale()) + 1;
            int h = (int) Math.ceil(9 * text.scale()) + 1;
            mark(mask, width, height, text.x() - 1, text.y() - 1, w + 2, h + 2);
        }
        for (RasterDraw.ItemCall item : draw.items()) {
            int size = (int) Math.ceil(16 * item.scale());
            mark(mask, width, height, item.x() - 1, item.y() - 1, size + 2, size + 2);
        }

        int[] best = {0, 0};
        double bestShare = -1;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                double share = compare(ours, theirs, mask, dx, dy, null).share();
                if (share > bestShare) {
                    bestShare = share;
                    best = new int[]{dx, dy};
                }
            }
        }
        Raster diff = new Raster(width, height);
        Score score = compare(ours, theirs, mask, best[0], best[1], diff);
        // The page content comes from sample data, not the capture's server, so the theme art is also scored alone: the
        // book's 460x260 box centred on screen minus its inner text area (frame 4, padding 4, wide edge 2).
        boolean[] frameOnly = mask.clone();
        int bookX = (width - 460) / 2;
        int bookY = (height - 260) / 2;
        mark(frameOnly, width, height, bookX + 10, bookY + 10, 440, 240);
        Score frame = compare(ours, theirs, frameOnly, best[0], best[1], null);

        Path out = Path.of(evidence);
        Files.createDirectories(out);
        Raster side = new Raster(width * 3, height);
        Raster backdrop = new Raster(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                backdrop.set(x, y, 0xFF202020);
            }
        }
        side.paste(backdrop, 0, 0);
        side.paste(ours, 0, 0);
        side.paste(theirs, width, 0);
        side.paste(backdrop, width * 2, 0);
        side.paste(diff, width * 2, 0);
        Files.write(out.resolve("s5-" + skill + "-side.png"), scaled(side, 2).png());
        String line = String.format(Locale.ROOT, "%s vs %s at %dx%d GUI, offset (%d,%d): %d compared pixels, %d identical (%.2f%%),"
                        + " mean channel error %.2f, masked %d text and %d item calls; theme frame only: %d compared, %d identical (%.2f%%),"
                        + " mean channel error %.2f%n", skill, capture, width, height, best[0], best[1], score.compared(), score.identical(),
                score.share() * 100, score.meanError(), draw.texts().size(), draw.items().size(), frame.compared(), frame.identical(),
                frame.share() * 100, frame.meanError());
        Files.writeString(out.resolve("s5-results.txt"), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private record Score(long compared, long identical, double meanError) {
        double share() {
            return compared == 0 ? 0 : identical / (double) compared;
        }
    }

    private static Score compare(Raster ours, Raster theirs, boolean[] mask, int dx, int dy, Raster diff) {
        long compared = 0;
        long identical = 0;
        long error = 0;
        for (int y = 0; y < ours.height(); y++) {
            for (int x = 0; x < ours.width(); x++) {
                int mine = ours.get(x, y);
                if ((mine >>> 24) != 255 || mask[y * ours.width() + x]) {
                    continue;
                }
                int other = theirs.get(x + dx, y + dy);
                if (other == 0) {
                    continue;
                }
                compared++;
                int channel = Math.abs(((mine >> 16) & 0xFF) - ((other >> 16) & 0xFF)) + Math.abs(((mine >> 8) & 0xFF) - ((other >> 8) & 0xFF))
                        + Math.abs((mine & 0xFF) - (other & 0xFF));
                error += channel;
                if (channel <= 3) {
                    identical++;
                } else if (diff != null) {
                    diff.set(x, y, 0xFFFF2020);
                }
                if (channel <= 3 && diff != null) {
                    diff.set(x, y, 0xFF303030);
                }
            }
        }
        return new Score(compared, identical, compared == 0 ? 0 : error / (3.0 * compared));
    }

    private static void mark(boolean[] mask, int width, int height, int x, int y, int w, int h) {
        for (int row = Math.max(0, y); row < Math.min(height, y + h); row++) {
            for (int column = Math.max(0, x); column < Math.min(width, x + w); column++) {
                mask[row * width + column] = true;
            }
        }
    }

    private static Raster scaled(Raster source, int factor) {
        Raster big = new Raster(source.width() * factor, source.height() * factor);
        for (int y = 0; y < big.height(); y++) {
            for (int x = 0; x < big.width(); x++) {
                big.set(x, y, source.get(x / factor, y / factor));
            }
        }
        return big;
    }

    /** The file at a resource id such as {@code adapt:textures/gui/kit/tome.png} below {@code root}, or null. */
    private static byte[] read(Path root, String id) {
        int colon = id.indexOf(':');
        Path file = root.resolve("assets").resolve(id.substring(0, colon)).resolve(id.substring(colon + 1));
        try {
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
