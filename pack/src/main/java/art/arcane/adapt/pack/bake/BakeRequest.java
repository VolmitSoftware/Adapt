package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.AssetCatalog;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Everything one pack bake reads: the asset catalog (kits, atlases and icons from every bundle), the design's geometry and fixed art, the pack's description, how many kits to bake at once, a cache of kit
 * bakes the caller keeps between bakes (or null), a check that ends the bake early when it turns true, and the build
 * record the manifest carries.
 */
public record BakeRequest(AssetCatalog catalog, DesignSpec design, List<ArtPieces.Piece> art, String description, int threads,
                          KitCache cache, BooleanSupplier cancelled, PackManifest.Build build) {
    public BakeRequest {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(design, "design");
        art = List.copyOf(art);
        Objects.requireNonNull(description, "description");
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be at least 1");
        }
        cancelled = cancelled == null ? () -> false : cancelled;
        Objects.requireNonNull(build, "build");
    }

    /**
     * A bake of {@code catalog} with the design and art shipped in the pack project, one kit at a time,
     * recording no Adapt version and no inputs.
     */
    public static BakeRequest of(AssetCatalog catalog) {
        return new BakeRequest(catalog, DesignSpec.builtin(), ArtPieces.builtin(), "Adapt book pages", 1, null, null,
                new PackManifest.Build(PackBake.VERSION, "", ""));
    }

    public BakeRequest withThreads(int count) {
        return new BakeRequest(catalog, design, art, description, count, cache, cancelled, build);
    }

    public BakeRequest withCache(KitCache kits) {
        return new BakeRequest(catalog, design, art, description, threads, kits, cancelled, build);
    }

    public BakeRequest withCancel(BooleanSupplier check) {
        return new BakeRequest(catalog, design, art, description, threads, cache, check, build);
    }

    /** This request recording Adapt {@code adapt} and the input hash {@code inputs} in its manifest. */
    public BakeRequest withBuild(String adapt, String inputs) {
        return new BakeRequest(catalog, design, art, description, threads, cache, cancelled, new PackManifest.Build(PackBake.VERSION, adapt, inputs));
    }

    /**
     * Kit bakes kept by the caller, by a key of the kit's hash, the design's hash and the bake's own version, so a changed
     * addon re-bakes only its kits. Must be safe to call from several threads when {@code threads} is above 1.
     */
    public interface KitCache {
        KitBake.Result get(String key);

        void put(String key, KitBake.Result result);
    }
}
