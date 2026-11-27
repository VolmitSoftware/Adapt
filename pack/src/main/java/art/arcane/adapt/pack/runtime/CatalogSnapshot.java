package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.AssetListener;
import art.arcane.adapt.engine.asset.AssetSubscription;
import art.arcane.adapt.engine.asset.InstalledBundle;
import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.platform.value.Id;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A frozen copy of everything a bake reads from a catalog: its bundles, every theme it answers for with the skill's
 * look, emblem and trim, and every sprite the bake draws. A bake of a snapshot cannot see the catalog change halfway, and the bundle and theme hashes are what the
 * pack cache is keyed by.
 */
public final class CatalogSnapshot implements AssetCatalog {
    private final long revision;
    private final List<InstalledBundle> bundles;
    private final Map<Id, ThemeAssets> themes;
    private final Map<Id, byte[]> sprites;
    private final Map<Id, SkillLook> looks;
    private final Map<Id, byte[]> emblems;
    private final Map<Id, byte[]> trims;

    private CatalogSnapshot(long revision, List<InstalledBundle> bundles, Map<Id, ThemeAssets> themes, Map<Id, byte[]> sprites,
                            Map<Id, SkillLook> looks, Map<Id, byte[]> emblems, Map<Id, byte[]> trims) {
        this.revision = revision;
        this.bundles = bundles;
        this.themes = themes;
        this.sprites = sprites;
        this.looks = looks;
        this.emblems = emblems;
        this.trims = trims;
    }

    public static CatalogSnapshot of(AssetCatalog source) {
        long revision = source.revision();
        List<InstalledBundle> bundles = List.copyOf(source.bundles());
        Map<Id, ThemeAssets> themes = new LinkedHashMap<>();
        Map<Id, SkillLook> looks = new HashMap<>();
        Map<Id, byte[]> emblems = new HashMap<>();
        Map<Id, byte[]> trims = new HashMap<>();
        for (Id skill : source.themes()) {
            source.theme(skill).ifPresent((ThemeAssets theme) -> themes.put(skill, theme));
            source.look(skill).ifPresent((SkillLook look) -> looks.put(skill, look));
            source.emblem(skill).ifPresent((byte[] png) -> emblems.put(skill, png));
            source.trim(skill).ifPresent((byte[] png) -> trims.put(skill, png));
        }
        Map<Id, byte[]> sprites = new HashMap<>();
        for (InstalledBundle bundle : bundles) {
            for (AssetEntry entry : bundle.entries()) {
                Id id = entry.id();
                if (PackBake.reads(entry) && !sprites.containsKey(id)) {
                    source.sprite(id).ifPresent((byte[] png) -> sprites.put(id, png));
                }
            }
        }
        return new CatalogSnapshot(revision, bundles, themes, sprites, looks, emblems, trims);
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public List<InstalledBundle> bundles() {
        return bundles;
    }

    @Override
    public Optional<byte[]> sprite(Id sprite) {
        byte[] png = sprites.get(sprite);
        return png == null ? Optional.empty() : Optional.of(png.clone());
    }

    @Override
    public Optional<ThemeAssets> theme(Id skill) {
        return Optional.ofNullable(themes.get(skill));
    }

    @Override
    public Optional<byte[]> emblem(Id skill) {
        byte[] png = emblems.get(skill);
        return png == null ? Optional.empty() : Optional.of(png.clone());
    }

    @Override
    public Optional<byte[]> trim(Id skill) {
        byte[] png = trims.get(skill);
        return png == null ? Optional.empty() : Optional.of(png.clone());
    }

    @Override
    public Optional<SkillLook> look(Id skill) {
        return Optional.ofNullable(looks.get(skill));
    }

    @Override
    public List<Id> themes() {
        return List.copyOf(themes.keySet());
    }

    @Override
    public AssetSubscription subscribe(AssetListener listener) {
        return () -> {
        };
    }
}
