package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.AdaptAssetCatalog;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.platform.value.Id;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bakes the built-in kits and icons from a resource folder into the pack through {@link PackBake}, for inspection:
 * {@code <out>/pack.zip} the pack, {@code <out>/report.txt} the numbers per kit, and {@code <out>/contact-<layout>.png}
 * every kit's page at tick 0 side by side.
 * <p>Usage: {@code BuiltinBake <resources folder> <out folder> [threads]}.
 */
public final class BuiltinBake {
    private BuiltinBake() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("usage: BuiltinBake <resources folder> <out folder> [threads]");
        }
        Path root = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int threads = args.length == 3 ? Integer.parseInt(args[2]) : 1;
        AdaptAssetCatalog catalog = TestCatalogs.empty(List.of());
        catalog.install(TestCatalogs.folder(root.resolve("assets").resolve(Id.ADAPT)), Id.ADAPT);
        Map<String, KitBake.Result> kept = new ConcurrentHashMap<>();
        BakeRequest.KitCache cache = new BakeRequest.KitCache() {
            @Override
            public KitBake.Result get(String key) {
                return kept.get(key);
            }

            @Override
            public void put(String key, KitBake.Result result) {
                kept.put(key, result);
            }
        };
        Files.createDirectories(out);
        long start = System.nanoTime();
        PackBake.Result result = PackBake.build(BakeRequest.of(catalog).withThreads(threads).withCache(cache));
        Files.write(out.resolve("pack.zip"), result.zip());
        String summary = String.format(Locale.ROOT, "pack: zip %d bytes, sha1 %s, content %s, %.1f s%n", result.zip().length, result.sha1(),
                result.manifest().contentHash(), (System.nanoTime() - start) / 1e9);
        List<KitBake.Result> kits = new ArrayList<>(kept.values());
        kits.sort((KitBake.Result a, KitBake.Result b) -> a.kit().skill().compareTo(b.kit().skill()));
        DesignSpec design = DesignSpec.builtin();
        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "design geometry version %d, %d kits, %d threads%n", design.version(), kits.size(), threads));
        report.append("kit layout: glyphs, still px, regions (exact/nearest), host items, regions per host, still for reach,"
                + " strip px, longest loop ticks\n");
        for (KitBake.Result kit : kits) {
            for (KitBake.Page page : kit.pages()) {
                report.append(line(kit, page));
            }
        }
        report.append(summary);
        Files.write(out.resolve("contact-single.png"), contact(kits, List.of(KitBake.SINGLE), design.page("A")).png());
        Files.write(out.resolve("contact-fallback.png"), contact(kits, List.of(KitBake.FALLBACK), design.page("A")).png());
        Files.writeString(out.resolve("report.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.print(report);
    }

    private static String line(KitBake.Result kit, KitBake.Page page) {
        int exact = 0;
        int nearest = 0;
        int longest = 0;
        for (LayerBake.Motion motion : page.motions()) {
            switch (motion.closure()) {
                case EXACT -> exact++;
                case NEAREST -> nearest++;
            }
            longest = Math.max(longest, motion.ticks());
        }
        long still = 0;
        for (StillTiles.Tile tile : page.tiles()) {
            still += (long) tile.width() * tile.height();
        }
        int fewest = Integer.MAX_VALUE;
        int most = 0;
        for (MotionItems.Host host : page.hosts()) {
            fewest = Math.min(fewest, host.areas().size());
            most = Math.max(most, host.areas().size());
        }
        return String.format(Locale.ROOT, "%s %s: %d, %d, %d (%d/%d), %d, %s, %d, %d, %d%n", kit.kit().skill().path(), page.layout(),
                page.tiles().size(), still, page.motions().size(), exact, nearest, page.hosts().size(),
                page.hosts().isEmpty() ? "-" : fewest + "-" + most, page.held().size(), page.motionArea(), longest);
    }

    private static Raster contact(List<KitBake.Result> kits, List<String> layouts, PageSpec spec) {
        int columns = 6;
        int cellWidth = spec.canvasWidth();
        int cellHeight = spec.canvasHeight();
        int rows = (kits.size() + columns - 1) / columns;
        Raster sheet = new Raster(columns * cellWidth, rows * cellHeight);
        for (int index = 0; index < kits.size(); index++) {
            int cx = index % columns * cellWidth;
            int cy = index / columns * cellHeight;
            Raster backdrop = new Raster(cellWidth - 2, cellHeight - 2);
            for (int y = 0; y < backdrop.height(); y++) {
                for (int x = 0; x < backdrop.width(); x++) {
                    backdrop.set(x, y, 0xFF2B2F36);
                }
            }
            sheet.paste(backdrop, cx, cy);
            List<KitBake.Page> shown = kits.get(index).pages().stream().filter((KitBake.Page page) -> layouts.contains(page.layout())).toList();
            sheet.paste(PagePreview.compose(shown, spec.canvasX(), spec.canvasY(), cellWidth, cellHeight, 0), cx, cy);
        }
        return sheet;
    }
}
