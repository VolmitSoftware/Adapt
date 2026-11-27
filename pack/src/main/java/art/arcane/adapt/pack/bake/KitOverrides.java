package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pack-side replacements for parts of a kit, for kits whose art does not fit the pack page: an optional
 * {@code kits/<namespace>/<path>.json} with a {@code top} and or {@code scene} section in kit form, its {@code atlas} size
 * and its {@code sprites} (name to x, y, w, h in the {@code .png} beside it). The sections replace the kit's, the PNG is
 * stacked under the kit's atlas and its sprites are added or replace the kit's by name. A kit without an override is used
 * as it is.
 */
public final class KitOverrides {
    public static final String ROOT = "/art/arcane/adapt/pack/kits/";
    static final List<String> SECTIONS = List.of("top", "scene");

    private final Function<String, byte[]> files;

    /** Overrides read through {@code files}, which answers a path under {@link #ROOT} with its bytes or null. */
    public KitOverrides(Function<String, byte[]> files) {
        this.files = files;
    }

    /** The overrides shipped with the pack project. */
    public static KitOverrides builtin() {
        return new KitOverrides(KitOverrides::resource);
    }

    /** {@code theme} with its override merged in, or {@code theme} itself when it has none. */
    public ThemeAssets apply(ThemeAssets theme) {
        String base = theme.skill().namespace() + "/" + theme.skill().path();
        byte[] json = files.apply(base + ".json");
        if (json == null) {
            return theme;
        }
        byte[] png = files.apply(base + ".png");
        if (png == null) {
            throw new IllegalStateException("kit override " + base + " has no atlas");
        }
        JsonObject kit = JsonParser.parseString(new String(theme.kitJson(), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject override = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        for (String section : SECTIONS) {
            if (override.has(section)) {
                kit.add(section, override.get(section));
            }
        }
        Raster atlas = Raster.decode(theme.atlasPng());
        Raster extra = Raster.decode(png);
        Raster merged = new Raster(Math.max(atlas.width(), extra.width()), atlas.height() + extra.height());
        merged.paste(atlas, 0, 0);
        merged.paste(extra, 0, atlas.height());
        JsonArray size = new JsonArray();
        size.add(merged.width());
        size.add(merged.height());
        kit.add("atlas", size);
        JsonObject sprites = kit.getAsJsonObject("sprites");
        for (Map.Entry<String, JsonElement> sprite : override.getAsJsonObject("sprites").entrySet()) {
            JsonArray rect = sprite.getValue().getAsJsonArray();
            JsonArray moved = new JsonArray();
            moved.add(rect.get(0).getAsInt());
            moved.add(rect.get(1).getAsInt() + atlas.height());
            moved.add(rect.get(2).getAsInt());
            moved.add(rect.get(3).getAsInt());
            sprites.add(sprite.getKey(), moved);
        }
        byte[] kitBytes = kit.toString().getBytes(StandardCharsets.UTF_8);
        byte[] atlasBytes = merged.png();
        byte[] both = new byte[kitBytes.length + atlasBytes.length];
        System.arraycopy(kitBytes, 0, both, 0, kitBytes.length);
        System.arraycopy(atlasBytes, 0, both, kitBytes.length, atlasBytes.length);
        return new ThemeAssets(theme.skill(), kitBytes, atlasBytes, theme.generated(), PackJson.sha1(both));
    }

    private static byte[] resource(String path) {
        try (InputStream in = KitOverrides.class.getResourceAsStream(ROOT + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
