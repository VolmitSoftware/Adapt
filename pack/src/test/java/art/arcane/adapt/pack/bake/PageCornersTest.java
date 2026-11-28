package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.theme.BandStage;
import art.arcane.adapt.client.theme.BookGeometry;
import art.arcane.adapt.client.theme.DressSlot;
import art.arcane.adapt.client.theme.Dressing;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.Solo;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.theme.ThemeSprite;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corners of every built-in kit's page and of generated kits' pages: the band rows above the ribbon plate and the chips are band art from end to
 * end in every frame, no window corner needs the backing behind a clear pixel, a solid band reaches both cover ends, the
 * foot (a scene's ground or a level band of tiles) reaches both bottom corners, no side piece is tucked under a corner
 * or pile piece, the still art has no half-transparent pixel, and the still copy for screens without host items shows every piece whole.
 */
class PageCornersTest {
    /** Window pixels checked at each corner of the chest window. */
    private static final int CORNER_WIDTH = 24;
    private static final int CORNER_HEIGHT = 18;
    /** Rows of the frame beside the paper, at each end, that must meet the corner. */
    private static final int CORNER_SIDE = 8;
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
                List<Dressing.Piece> pieces = Dressing.compose(spec.geometry(leaf), new PageTheme(kit, null), new Solo()).pieces();
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

    /**
     * A foot band of tiles runs level from the paper's left end to its right end on every baked page kind, at rest and
     * swayed: each line the kit's tiles draw solid is drawn solid across the whole paper, and nothing else is.
     */
    @Test
    void theFootBandRunsLevelToBothPaperEnds() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (ThemeAssets assets : Builtin.withGenerated()) {
            ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
            if (!kit.footBand() || kit.foot() != null) {
                continue;
            }
            checked++;
            PageSpec spec = SPECS.get(assets.skill().path());
            int want = solidLines(kit, 0) + solidLines(kit, 1);
            for (KitBake.Page page : RESULTS.get(assets.skill().path()).pages()) {
                int leaf = page.layout().startsWith(KitBake.SINGLE_PARTNER) ? 2 : 1;
                List<BookGeometry.Box> paper = spec.geometry(leaf).paper();
                int p0 = paper.get(0).x0();
                int p1 = paper.get(paper.size() - 1).x1();
                Raster bare = new KitRender(footless(assets), spec, leaf).back();
                for (long tick : new long[]{0L, 14L}) {
                    Raster shown = PagePreview.compose(List.of(page), page.left(), page.top(), spec.canvasWidth(), spec.canvasHeight(), tick);
                    List<Integer> solid = new ArrayList<>();
                    for (int y = paper.get(0).y1() - 16; y < paper.get(0).y1(); y++) {
                        boolean across = true;
                        for (int x = p0; x < p1 && across; x++) {
                            across = shown.get(x, y) >>> 24 == 0xFF && shown.get(x, y) != bare.get(x, y);
                        }
                        if (across) {
                            solid.add(y);
                        }
                    }
                    if (solid.size() != want) {
                        problems.add(assets.skill().path() + " " + page.layout() + " tick " + tick + ": solid band lines " + solid + " from x " + p0
                                + " to " + p1 + ", want " + want);
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
        assertTrue(checked > 0, "no kit draws a foot band of tiles");
    }

    /** A foot scene's ground runs from the cover's left end to its right end on both leaves of the page. */
    @Test
    void aFootSceneReachesBothCoverEnds() {
        List<String> problems = new ArrayList<>();
        for (ThemeAssets assets : Builtin.withGenerated()) {
            ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
            if (kit.foot() == null) {
                continue;
            }
            PageSpec spec = SPECS.get(assets.skill().path());
            for (int leaf = 1; leaf <= 2; leaf++) {
                BookGeometry geometry = spec.geometry(leaf);
                int feet = 0;
                for (BandStage stage : BandStage.of(geometry, new PageTheme(kit, null))) {
                    if (stage.top()) {
                        continue;
                    }
                    feet++;
                    if (stage.groundFrom() != geometry.cover().x0() || stage.groundTo() != geometry.cover().x1()) {
                        problems.add(assets.skill().path() + " leaf " + leaf + ": foot ground " + stage.groundFrom() + ".." + stage.groundTo()
                                + ", cover " + geometry.cover().x0() + ".." + geometry.cover().x1());
                    }
                }
                if (feet != 1) {
                    problems.add(assets.skill().path() + " leaf " + leaf + ": " + feet + " foot stages");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /** The top corner pieces of every page sit as mirror images: the right one as far in from the cover's right end as the left one from its left end. */
    @Test
    void theTopCornerPiecesMirrorEachOther() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (ThemeAssets assets : Builtin.withGenerated()) {
            ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
            PageSpec spec = SPECS.get(assets.skill().path());
            for (int leaf = 1; leaf <= 2; leaf++) {
                BookGeometry.Box cover = spec.geometry(leaf).cover();
                Dressing.Piece left = null;
                Dressing.Piece right = null;
                for (Dressing.Piece piece : Dressing.compose(spec.geometry(leaf), new PageTheme(kit, null), new Solo()).pieces()) {
                    if (piece.slot() != DressSlot.CORNER) {
                        continue;
                    }
                    if (piece.flip()) {
                        right = piece;
                    } else {
                        left = piece;
                    }
                }
                if (left == null || right == null) {
                    continue;
                }
                checked++;
                int leftIn = left.x() - cover.x0();
                int rightIn = cover.x1() - right.x() - right.sprite().width();
                if (leftIn != rightIn || left.y() != right.y()) {
                    problems.add(assets.skill().path() + " leaf " + leaf + ": left corner " + leftIn + " in at y " + left.y() + ", right corner "
                            + rightIn + " in at y " + right.y());
                }
            }
        }
        assertEquals(List.of(), problems);
        assertTrue(checked > 0, "no kit has corner pieces");
    }

    /**
     * On every baked page kind (single, partner and fallback pages and their still copies) the top band covers the same rows
     * at the cover's left and right ends, so neither end stops short or steps, and the frame beside the paper meets all
     * four corners: it is drawn {@link #CORNER_SIDE} rows down from the paper's top and up from its bottom on both sides.
     */
    @Test
    void theBandsMeetEveryCornerAlike() {
        List<String> problems = new ArrayList<>();
        for (ThemeAssets assets : Builtin.withGenerated()) {
            PageSpec spec = SPECS.get(assets.skill().path());
            for (KitBake.Page page : RESULTS.get(assets.skill().path()).pages()) {
                int leaf = page.layout().startsWith(KitBake.SINGLE_PARTNER) ? 2 : 1;
                BookGeometry geometry = spec.geometry(leaf);
                BookGeometry.Box cover = geometry.cover();
                BookGeometry.Box paper = geometry.paper().get(0);
                Raster shown = PagePreview.compose(List.of(page), page.left(), page.top(), spec.canvasWidth(), spec.canvasHeight(), 0L);
                String where = assets.skill().path() + " " + page.layout();
                for (int y = cover.y0(); y < paper.y0(); y++) {
                    boolean leftEnd = shown.get(cover.x0(), y) >>> 24 == 0xFF;
                    boolean rightEnd = shown.get(cover.x1() - 1, y) >>> 24 == 0xFF;
                    if (leftEnd != rightEnd) {
                        problems.add(where + ": top band row " + y + (leftEnd ? " reaches the left end only" : " reaches the right end only"));
                    }
                }
                for (int y = paper.y0(); y < paper.y1(); y++) {
                    if (y >= paper.y0() + CORNER_SIDE && y < paper.y1() - CORNER_SIDE) {
                        continue;
                    }
                    for (int x : new int[]{paper.x0() - 1, paper.x1()}) {
                        if (shown.get(x, y) >>> 24 != 0xFF) {
                            problems.add(where + ": side gap at " + x + "," + y);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /** How many lines every one of the kit's foot tiles in {@code row} draws opaque across its whole width. */
    private static int solidLines(ThemeKit kit, int row) {
        Raster atlas = Raster.decode(kitAtlas(kit));
        int lines = 0;
        ThemeSprite first = kit.sprite("foot/" + row + "/0/a");
        for (int y = 0; y < first.height(); y++) {
            boolean across = true;
            for (String name : new String[]{"0/a", "0/b", "1/a", "1/b"}) {
                ThemeSprite sprite = kit.sprite("foot/" + row + "/" + name);
                for (int x = 0; x < sprite.width() && across; x++) {
                    across = atlas.get(sprite.u() + x, sprite.v() + y) >>> 24 == 0xFF;
                }
            }
            lines += across ? 1 : 0;
        }
        return lines;
    }

    private static byte[] kitAtlas(ThemeKit kit) {
        for (ThemeAssets assets : Builtin.withGenerated()) {
            if (assets.skill().path().equals(kit.skill())) {
                return assets.atlasPng();
            }
        }
        throw new IllegalStateException(kit.skill());
    }

    /** {@code kit} without its foot band. */
    private static ThemeAssets footless(ThemeAssets kit) {
        JsonObject json = JsonParser.parseString(new String(kit.kitJson(), StandardCharsets.UTF_8)).getAsJsonObject();
        json.addProperty("footBand", false);
        return new ThemeAssets(kit.skill(), json.toString().getBytes(StandardCharsets.UTF_8), kit.atlasPng(), false, "footless");
    }

    private static boolean near(Dressing.Piece a, Dressing.Piece b) {
        int margin = Solo.APART;
        return b.x() < a.x() + a.sprite().width() + margin && b.x() + b.sprite().width() + margin > a.x()
                && b.y() < a.y() + a.sprite().height() + margin && b.y() + b.sprite().height() + margin > a.y();
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
