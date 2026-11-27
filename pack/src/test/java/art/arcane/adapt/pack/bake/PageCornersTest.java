package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.theme.BookGeometry;
import art.arcane.adapt.client.theme.DressSlot;
import art.arcane.adapt.client.theme.Dressing;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.Solo;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The corners of every built-in kit's page and of generated kits' pages: the band rows above the ribbon plate and the chips are band art from end to
 * end in every frame, no window corner needs the backing behind a clear pixel, a solid band reaches both cover ends, no
 * side piece is tucked under a corner or pile piece, the top right corner pieces keep clear of the chips, the still art
 * has no half-transparent pixel, and the still copy for screens without host items shows every piece whole.
 */
class PageCornersTest {
    /** Window pixels checked at each corner of the chest window. */
    private static final int CORNER_WIDTH = 24;
    private static final int CORNER_HEIGHT = 18;
    private static final Map<String, KitBake.Result> RESULTS = new TreeMap<>();
    private static final Map<String, PageSpec> SPECS = new TreeMap<>();

    @BeforeAll
    static void bake() throws IOException {
        PageSpec framing = DesignSpec.builtin().page("A");
        InventoryWells wells = InventoryWells.of(Files.readAllBytes(Builtin.root().resolve("assets/adapt/textures/gui/kit/tile.png")));
        for (ThemeAssets kit : Builtin.withGenerated()) {
            RESULTS.put(kit.skill().path(), KitBake.bake(kit, framing, wells));
            SPECS.put(kit.skill().path(), BookTop.fit(kit, framing));
        }
    }

    @Test
    void theBandRowsAboveThePlateAreBandArtInEveryFrame() {
        List<String> problems = new ArrayList<>();
        for (ThemeAssets kit : Builtin.withGenerated()) {
            PageSpec spec = SPECS.get(kit.skill().path());
            for (int leaf = 1; leaf <= 2; leaf++) {
                BakedLayer held = HeaderHold.hold(LayerBake.bake(kit, spec, leaf), spec.canvasX(), spec.canvasY());
                int clear = 0;
                for (int y = HeaderHold.Y; y < HeaderHold.PLATE_TOP; y++) {
                    for (int x = HeaderHold.X; x < HeaderHold.RIGHT; x++) {
                        if (held.still().get(x - spec.canvasX(), y - spec.canvasY()) >>> 24 != 0xFF) {
                            clear++;
                        }
                    }
                }
                if (clear > 0) {
                    problems.add(kit.skill().path() + " leaf " + leaf + ": " + clear + " pixels above the plate are not band art");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void noWindowCornerNeedsTheBacking() {
        List<String> problems = new ArrayList<>();
        for (ThemeAssets kit : Builtin.withGenerated()) {
            PageSpec spec = SPECS.get(kit.skill().path());
            for (int leaf = 1; leaf <= 2; leaf++) {
                BakedLayer held = HeaderHold.hold(LayerBake.bake(kit, spec, leaf), spec.canvasX(), spec.canvasY());
                BakedLayer closed = WindowBacking.of(new KitRender(kit, spec, leaf).back(), spec).close(held);
                int backed = 0;
                for (int[] corner : corners()) {
                    for (int y = corner[1]; y < corner[1] + CORNER_HEIGHT; y++) {
                        for (int x = corner[0]; x < corner[0] + CORNER_WIDTH; x++) {
                            backed += backed(held, closed, x - spec.canvasX(), y - spec.canvasY()) ? 1 : 0;
                        }
                    }
                }
                if (backed > 0) {
                    problems.add(kit.skill().path() + " leaf " + leaf + ": " + backed + " corner pixels show the backing");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void aSolidBandReachesBothCoverEnds() {
        List<String> problems = new ArrayList<>();
        for (ThemeAssets assets : Builtin.withGenerated()) {
            ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
            if (!kit.solid() || kit.top() != null) {
                continue;
            }
            PageSpec spec = SPECS.get(assets.skill().path());
            BookGeometry.Box cover = spec.geometry(1).cover();
            Raster base = new KitRender(assets, spec, 1).base();
            for (int x : new int[]{cover.x0(), cover.x1() - 1}) {
                for (int y = cover.y0() - 2; y < cover.y0() + 6; y++) {
                    if (base.get(x, y) >>> 24 != 0xFF) {
                        problems.add(assets.skill().path() + ": band row " + y + " stops short of cover x " + x);
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void noSidePieceIsTuckedUnderACornerOrPile() {
        List<String> problems = new ArrayList<>();
        for (ThemeAssets assets : Builtin.withGenerated()) {
            ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
            PageSpec spec = SPECS.get(assets.skill().path());
            for (int leaf = 1; leaf <= 2; leaf++) {
                List<Dressing.Piece> pieces = Dressing.compose(spec.geometry(leaf), new PageTheme(kit, null), KitRender.solo(spec)).pieces();
                for (Dressing.Piece corner : pieces) {
                    if (corner.slot() != DressSlot.CORNER && corner.slot() != DressSlot.PILE) {
                        continue;
                    }
                    for (Dressing.Piece side : pieces) {
                        if (side.slot() == DressSlot.SIDE && near(corner, side)) {
                            problems.add(assets.skill().path() + " leaf " + leaf + ": side piece at " + side.x() + "," + side.y() + " under the "
                                    + corner.slot() + " at " + corner.x() + "," + corner.y());
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /**
     * The top right corner pieces (a designed top's corner cast and the corner dressing: what the page loses without them)
     * start right of the chips' right edge in the chips' rows.
     */
    @Test
    void theTopRightCornerPiecesKeepClearOfTheChips() {
        List<String> problems = new ArrayList<>();
        for (ThemeAssets kit : Builtin.withGenerated()) {
            PageSpec spec = SPECS.get(kit.skill().path());
            Raster with = new KitRender(kit, spec, 1).back();
            Raster without = new KitRender(cornerless(kit), spec, 1).back();
            int pixels = 0;
            for (int y = PackArt.CHIP_Y; y < PackArt.CHIP_Y + PackArt.CHIP_HEIGHT; y++) {
                for (int x = HeaderHold.RIGHT - CORNER_WIDTH; x < HeaderHold.RIGHT; x++) {
                    int cx = x - spec.canvasX();
                    int cy = y - spec.canvasY();
                    pixels += with.get(cx, cy) != without.get(cx, cy) && corner(with, without, cx, cy, spec) ? 1 : 0;
                }
            }
            if (pixels > 0) {
                problems.add(kit.skill().path() + ": " + pixels + " corner piece pixels under the chips");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void stillArtHasNoHalfTransparentPixel() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, KitBake.Result> result : RESULTS.entrySet()) {
            for (KitBake.Page page : result.getValue().pages()) {
                int partial = 0;
                for (int argb : page.still().pixels()) {
                    int alpha = argb >>> 24;
                    partial += alpha != 0 && alpha != 0xFF ? 1 : 0;
                }
                if (partial > 0) {
                    problems.add(result.getKey() + " " + page.layout() + ": " + partial + " half-transparent pixels");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void theStillCopiesShowEveryPieceWhole() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, KitBake.Result> result : RESULTS.entrySet()) {
            for (KitBake.Page page : result.getValue().pages()) {
                if (!page.layout().equals(KitBake.SINGLE_STILL) && !page.layout().equals(KitBake.SINGLE_PARTNER_STILL)
                        && !page.layout().equals(KitBake.FALLBACK_STILL)) {
                    continue;
                }
                if (!page.hosts().isEmpty() || page.held().size() != page.motions().size()) {
                    problems.add(result.getKey() + " " + page.layout() + ": " + page.hosts().size() + " hosts, " + page.held().size() + " of "
                            + page.motions().size() + " regions held");
                }
                for (LayerBake.Motion motion : page.motions()) {
                    Raster pose = motion.frames().get(PagePreview.frameAt(motion, 0));
                    for (int y = 0; y < motion.height(); y++) {
                        for (int x = 0; x < motion.width(); x++) {
                            if (motion.mask()[y * motion.width() + x] && pose.get(x, y) >>> 24 == 0xFF
                                    && page.still().get(motion.x() + x, motion.y() + y) >>> 24 != 0xFF) {
                                problems.add(result.getKey() + " " + page.layout() + ": a piece has a hole at " + (motion.x() + x) + ","
                                        + (motion.y() + y));
                            }
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    private static boolean near(Dressing.Piece a, Dressing.Piece b) {
        int margin = Solo.APART;
        return b.x() < a.x() + a.sprite().width() + margin && b.x() + b.sprite().width() + margin > a.x()
                && b.y() < a.y() + a.sprite().height() + margin && b.y() + b.sprite().height() + margin > a.y();
    }

    /** {@code kit} without its top's corner cast and its corner dressing. */
    private static ThemeAssets cornerless(ThemeAssets kit) {
        JsonObject json = JsonParser.parseString(new String(kit.kitJson(), StandardCharsets.UTF_8)).getAsJsonObject();
        json.getAsJsonObject("dress").add("corner", new JsonArray());
        if (json.get("top").isJsonObject()) {
            JsonArray cast = new JsonArray();
            for (JsonElement member : json.getAsJsonObject("top").getAsJsonArray("cast")) {
                if (!member.getAsJsonObject().has("corner")) {
                    cast.add(member);
                }
            }
            json.getAsJsonObject("top").add("cast", cast);
        }
        return new ThemeAssets(kit.skill(), json.toString().getBytes(StandardCharsets.UTF_8), kit.atlasPng(), false, "cornerless");
    }

    /**
     * Whether canvas pixel ({@code x}, {@code y}), where the two pages differ, belongs to a corner piece: it joins, through
     * pixels that differ, one beyond the cover's right end, so a cast member that only moved elsewhere does not count.
     */
    private static boolean corner(Raster with, Raster without, int x, int y, PageSpec spec) {
        int beyond = spec.geometry(1).cover().x1();
        boolean[] seen = new boolean[with.width() * with.height()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y});
        seen[y * with.width() + x] = true;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            if (at[0] >= beyond) {
                return true;
            }
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx >= 0 && ny >= 0 && nx < with.width() && ny < with.height() && !seen[ny * with.width() + nx]
                        && with.get(nx, ny) != without.get(nx, ny) && with.get(nx, ny) >>> 24 != 0) {
                    seen[ny * with.width() + nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return false;
    }

    /** The top left of the {@link #CORNER_WIDTH} by {@link #CORNER_HEIGHT} box at each corner of the chest window. */
    private static List<int[]> corners() {
        int right = SlotGrid.CONTAINER_WIDTH - CORNER_WIDTH;
        int bottom = SlotGrid.CONTAINER_HEIGHT - CORNER_HEIGHT;
        return List.of(new int[]{0, 0}, new int[]{right, 0}, new int[]{0, bottom}, new int[]{right, bottom});
    }

    /** Whether the backing changed canvas pixel ({@code x}, {@code y}) of the still layer or of any frame over it. */
    private static boolean backed(BakedLayer held, BakedLayer closed, int x, int y) {
        if (held.still().get(x, y) != closed.still().get(x, y) && held.still().get(x, y) >>> 24 != 0) {
            return true;
        }
        if (held.still().get(x, y) >>> 24 == 0 && closed.still().get(x, y) >>> 24 != 0 && !moving(held, x, y)) {
            return true;
        }
        for (int index = 0; index < held.motions().size(); index++) {
            LayerBake.Motion before = held.motions().get(index);
            LayerBake.Motion after = closed.motions().get(index);
            int mx = x - before.x();
            int my = y - before.y();
            if (mx < 0 || my < 0 || mx >= before.width() || my >= before.height() || !before.mask()[my * before.width() + mx]) {
                continue;
            }
            for (int frame = 0; frame < before.frames().size(); frame++) {
                if (before.frames().get(frame).get(mx, my) != after.frames().get(frame).get(mx, my)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean moving(BakedLayer layer, int x, int y) {
        for (LayerBake.Motion motion : layer.motions()) {
            int mx = x - motion.x();
            int my = y - motion.y();
            if (mx >= 0 && my >= 0 && mx < motion.width() && my < motion.height() && motion.mask()[my * motion.width() + mx]) {
                return true;
            }
        }
        return false;
    }
}
