package art.arcane.adapt.pack.bake;

import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.api.asset.AssetKind;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.InstalledBundle;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.manifest.PackSprites;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Builds one pack from an asset catalog: every kit's pages, the icons, the design's art, the paint the built-in screen
 * layout draws, the HUD font and the text fonts, validated and zipped. The same catalog and design give the same bytes. Holds no state between calls,
 * draws without a display (only {@code javax.imageio} and plain rasters), and may run off the server thread.
 */
public final class PackBake {
    /** Changes whenever a bake's output for the same input changes, so cached kit bakes from older code are not reused. */
    public static final String VERSION = "28";
    static final Id TOOLTIP = Id.adapt("gui/kit/tooltip");
    static final Id TILE = Id.adapt("gui/kit/tile");
    static final Id TOKEN = Id.adapt("gui/kit/token_learned");
    private static final Set<AssetKind> SPRITES = EnumSet.of(AssetKind.SPRITE, AssetKind.PREFERENCE_ICON, AssetKind.EMBLEM,
            AssetKind.TRIM, AssetKind.GUI_TEXTURE);

    private PackBake() {
    }

    /** A built pack: its zip, its manifest, and the zip's SHA-1 as the client checks it. */
    public record Result(byte[] zip, PackManifest manifest, String sha1) {
    }

    public static Result build(BakeRequest request) {
        AssetCatalog catalog = request.catalog();
        DesignSpec design = request.design();
        PageSpec single = design.page("A");
        InventoryWells wells = catalog.sprite(TILE).map(InventoryWells::of).orElse(null);
        List<ThemeAssets> themes = themes(catalog);
        List<KitBake.Result> kits = kits(request, themes, single, wells);
        List<IconFiles.Icon> icons = icons(catalog);
        TreeSet<Integer> rows = new TreeSet<>(single.textRows());
        for (int y : PackArt.textRows(PackLayoutReader.builtIn())) {
            rows.add(y - SlotGrid.TITLE_Y);
        }
        check(request);
        PackWriter writer = new PackWriter(request.description(), request.build()).icons(icons);
        catalog.sprite(TOOLTIP).map(Raster::decode).ifPresent(writer::tooltipShape);
        for (KitBake.Result kit : kits) {
            writer.kit(kit);
        }
        Raster token = catalog.sprite(TOKEN).map(Raster::decode).orElse(new Raster(16, 16));
        PackWriter.Built built = writer.art(request.art(), wells)
                .paint(PackArt.paint(PackLayoutReader.builtIn()), (String id) -> catalog.sprite(Id.parse(id)).orElse(null), request.art())
                .hud(icons, request.art(), token).textRows(new ArrayList<>(rows)).build();
        List<String> problems = PackValidator.check(built.files());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("pack failed validation: " + problems);
        }
        byte[] zip = built.zip();
        return new Result(zip, built.manifest(), PackJson.sha1(zip));
    }

    private static List<KitBake.Result> kits(BakeRequest request, List<ThemeAssets> themes, PageSpec single, InventoryWells wells) {
        List<KitBake.Result> results = new ArrayList<>();
        if (request.threads() == 1) {
            for (ThemeAssets theme : themes) {
                check(request);
                results.add(kit(request, theme, single, wells));
            }
            return results;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(request.threads(), Math.max(1, themes.size())));
        try {
            List<Future<KitBake.Result>> pending = new ArrayList<>();
            for (ThemeAssets theme : themes) {
                pending.add(pool.submit(() -> {
                    check(request);
                    return kit(request, theme, single, wells);
                }));
            }
            for (Future<KitBake.Result> future : pending) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("pack bake interrupted");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(failure.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    private static KitBake.Result kit(BakeRequest request, ThemeAssets theme, PageSpec single, InventoryWells wells) {
        String key = theme.skill() + "|" + theme.hash() + "|" + request.design().hash() + "|" + VERSION;
        BakeRequest.KitCache cache = request.cache();
        if (cache != null) {
            KitBake.Result known = cache.get(key);
            if (known != null) {
                return known;
            }
        }
        KitBake.Result result = KitBake.bake(theme, single, wells);
        if (cache != null) {
            cache.put(key, result);
        }
        return result;
    }

    private static void check(BakeRequest request) {
        if (request.cancelled().getAsBoolean()) {
            throw new CancellationException("pack bake cancelled");
        }
    }

    /** Whether {@code catalog} holds the built-in kit art every page draws on. */
    public static boolean hasBuiltinArt(AssetCatalog catalog) {
        return catalog.sprite(TILE).isPresent() && catalog.sprite(TOOLTIP).isPresent();
    }

    /** Every theme the catalog answers for, generated ones included, in the catalog's order, with the pack's kit overrides merged in. */
    static List<ThemeAssets> themes(AssetCatalog catalog) {
        KitOverrides overrides = KitOverrides.builtin();
        List<ThemeAssets> themes = new ArrayList<>();
        for (Id skill : catalog.themes()) {
            catalog.theme(skill).map(overrides::apply).ifPresent(themes::add);
        }
        return themes;
    }

    /** Whether the bake draws {@code entry}: a PNG sprite under {@code gui/} that is not a theme atlas. */
    public static boolean reads(AssetEntry entry) {
        return SPRITES.contains(entry.kind()) && entry.id().path().startsWith("gui/") && entry.path().endsWith(".png");
    }

    /**
     * Every 16x16 sprite under {@code gui/} in the catalog's bundles, then the emblem and trim the catalog generates for
     * each skill whose bundle has none, by sprite id.
     */
    static List<IconFiles.Icon> icons(AssetCatalog catalog) {
        TreeMap<Id, byte[]> sprites = new TreeMap<>();
        for (InstalledBundle bundle : catalog.bundles()) {
            for (AssetEntry entry : bundle.entries()) {
                if (reads(entry)) {
                    catalog.sprite(entry.id()).ifPresent((byte[] png) -> sprites.putIfAbsent(entry.id(), png));
                }
            }
        }
        for (Id skill : catalog.themes()) {
            catalog.emblem(skill).ifPresent((byte[] png) -> sprites.putIfAbsent(Id.parse(PackSprites.packId(IconSprites.emblem(skill))), png));
            catalog.trim(skill).ifPresent((byte[] png) -> sprites.putIfAbsent(Id.parse(PackSprites.packId(IconSprites.trim(skill))), png));
        }
        List<IconFiles.Icon> icons = new ArrayList<>();
        for (Map.Entry<Id, byte[]> sprite : sprites.entrySet()) {
            byte[] png = sprite.getValue();
            if (IconFiles.width(png) == IconFiles.SIZE && IconFiles.height(png) == IconFiles.SIZE) {
                icons.add(new IconFiles.Icon(sprite.getKey(), png));
            }
        }
        return icons;
    }
}
