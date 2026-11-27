package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.raster.RasterDraw;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pages a client draws from a built pack (static glyphs plus host item sprites, read back from the pack's own files)
 * equal the composer's page at every sampled tick: each moving pixel as the composer paints it at the time its region's
 * loop shows at that tick, every other pixel as the composer paints it at any time.
 */
class PackReplayTest {
    private static final PackManifest.Build UNSTAMPED = new PackManifest.Build(PackBake.VERSION, "", "");
    static final List<String> KITS = List.of("herbalism", "stealth", "pickaxe");
    static final long[] TICKS = {0, 37, 113, 260};
    private static final DesignSpec DESIGN = DesignSpec.builtin();
    private static final List<KitBake.Result> BAKED = new ArrayList<>();
    private static InventoryWells wells;

    @BeforeAll
    static void bake() {
        try {
            wells = InventoryWells.of(Files.readAllBytes(Builtin.root().resolve("assets/adapt/textures/gui/kit/tile.png")));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        for (String kit : KITS) {
            BAKED.add(KitBake.bake(Builtin.kit(kit), DESIGN.page("A"), wells));
        }
    }

    static PackReplay replay(List<KitBake.Result> kits) {
        PackWriter writer = new PackWriter("replay", UNSTAMPED);
        kits.forEach(writer::kit);
        PackWriter.Built built = writer.build();
        assertEquals(List.of(), PackValidator.check(built.files()));
        return new PackReplay(built);
    }

    /** The composer's raster for a page set at {@code tick}, on the framing's canvas. */
    static Raster reference(ThemeAssets kit, PageSpec spec, int leaf, List<KitBake.Page> pages, InventoryWells wells, long tick) {
        KitRender render = new KitRender(kit, spec, leaf);
        Raster base = render.base();
        Raster first = base.copy();
        render.moving(first, 0L, null);
        Raster expected = first.copy();
        Raster work = base.copy();
        for (KitBake.Page page : pages) {
            for (LayerBake.Motion motion : page.motions()) {
                for (int y = 0; y < motion.height(); y++) {
                    for (int x = 0; x < motion.width(); x++) {
                        if (motion.mask()[y * motion.width() + x]) {
                            expected.set(motion.x() + x, motion.y() + y, 0);
                        }
                    }
                }
            }
        }
        Raster still = pages.get(0).still();
        for (int y = HeaderHold.Y; y < HeaderHold.Y + HeaderHold.HEIGHT; y++) {
            for (int x = HeaderHold.X; x < HeaderHold.X + HeaderHold.WIDTH; x++) {
                expected.set(x - spec.canvasX(), y - spec.canvasY(), still.get(x - pages.get(0).left(), y - pages.get(0).top()));
            }
        }
        WindowBacking backing = WindowBacking.of(render.back(), spec);
        expected = backing.close(new BakedLayer(expected, pages.get(0).motions())).still();
        if (wells != null) {
            expected = wells.over(expected, spec.canvasX(), spec.canvasY());
        }
        for (KitBake.Page page : pages) {
            List<Integer> hosted = new ArrayList<>();
            page.hosts().forEach((MotionItems.Host host) -> host.areas().forEach((MotionItems.Area area) -> hosted.add(area.index())));
            for (int index = 0; index < page.motions().size(); index++) {
                LayerBake.Motion motion = page.motions().get(index);
                long millis = motion.millisAt(hosted.contains(index) ? tick : 0);
                for (int y = motion.y(); y < motion.y() + motion.height(); y++) {
                    System.arraycopy(base.pixels(), y * base.width() + motion.x(), work.pixels(), y * base.width() + motion.x(), motion.width());
                }
                render.moving(work, millis, new RasterDraw.Clip(motion.x(), motion.y(), motion.x() + motion.width(), motion.y() + motion.height()));
                for (int y = 0; y < motion.height(); y++) {
                    for (int x = 0; x < motion.width(); x++) {
                        if (motion.mask()[y * motion.width() + x]) {
                            expected.blend(motion.x() + x, motion.y() + y, backing.over(motion.x() + x, motion.y() + y,
                                    work.get(motion.x() + x, motion.y() + y)));
                        }
                    }
                }
            }
        }
        return expected;
    }

    private static int differences(Raster expected, Raster actual, StringBuilder first) {
        int count = 0;
        for (int y = 0; y < expected.height(); y++) {
            for (int x = 0; x < expected.width(); x++) {
                int e = expected.get(x, y);
                int a = actual.get(x, y);
                if ((e >>> 24 == 0 ? 0 : e) != (a >>> 24 == 0 ? 0 : a)) {
                    if (count == 0) {
                        first.append(String.format("(%d, %d) expected %08x got %08x", x, y, e, a));
                    }
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void packDrawsTheComposersPages() {
        PackReplay replay = replay(BAKED);
        int compared = 0;
        for (KitBake.Result result : BAKED) {
            String kit = result.kit().skill().toString();
            PageSpec single = BookTop.fit(result.kit(), DESIGN.page("A"));
            List<Case> cases = List.of(new Case(single, 1, List.of(KitBake.SINGLE), null), new Case(single, 1, List.of(KitBake.FALLBACK), wells),
                    new Case(single, 2, List.of(KitBake.SINGLE_PARTNER), null));
            for (Case shown : cases) {
                List<KitBake.Page> baked = new ArrayList<>();
                List<PackManifest.PageFont> written = new ArrayList<>();
                for (KitBake.Page page : result.pages()) {
                    if (shown.layouts().contains(page.layout())) {
                        baked.add(page);
                        written.add(replay.page(kit, page.layout()));
                    }
                }
                assertEquals(shown.layouts().size(), baked.size());
                for (long tick : TICKS) {
                    Raster expected = reference(result.kit(), shown.spec(), shown.leaf(), baked, shown.wells(), tick);
                    Raster actual = replay.draw(written, shown.spec().canvasX(), shown.spec().canvasY(), expected.width(), expected.height(), tick);
                    StringBuilder first = new StringBuilder();
                    assertEquals(0, differences(expected, actual, first), kit + " " + shown.layouts() + " tick " + tick + " " + first);
                    compared++;
                }
            }
        }
        assertEquals(KITS.size() * 3 * TICKS.length, compared);
    }

    private record Case(PageSpec spec, int leaf, List<String> layouts, InventoryWells wells) {
    }

    @Test
    void fallbackPagesReuseTheSinglePagesStrips() {
        PackReplay modern = replay(BAKED);
        for (KitBake.Result result : BAKED) {
            String kit = result.kit().skill().toString();
            List<String> single = textures(modern.page(kit, KitBake.SINGLE));
            List<String> fallback = textures(modern.page(kit, KitBake.FALLBACK));
            assertTrue(!fallback.isEmpty() && single.containsAll(fallback), kit + " " + fallback + " not within " + single);
        }
    }

    private static List<String> textures(PackManifest.PageFont page) {
        List<String> textures = new ArrayList<>();
        page.items().forEach((PackManifest.Item item) -> item.regions().forEach((PackManifest.Region region) -> textures.add(region.texture())));
        return textures;
    }

    @Test
    void everyHostIsInItsModesSlots() {
        for (KitBake.Result result : BAKED) {
            for (KitBake.Page page : result.pages()) {
                for (MotionItems.Host host : page.hosts()) {
                    assertTrue(page.mode().slotList().contains(host.slot()), page.layout() + " slot " + host.slot());
                }
            }
        }
    }
}
