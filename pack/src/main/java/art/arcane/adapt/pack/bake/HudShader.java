package art.arcane.adapt.pack.bake;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * The vanilla text vertex shader with the HUD corner move added ({@code adaptHudCorner}, see {@code HudCorner} in core),
 * once per shader layout the pack's clients use: 26.1.2 draws text with {@code rendertype_text} and its intensity
 * variant, 26.2 and 26.3 with {@code text}, written in two dialects. The base pack holds the 26.1.2 files; an overlay per
 * later pack format range holds that range's {@code text.vsh}.
 */
final class HudShader {
    private static final String SOURCE = "/art/arcane/adapt/pack/shader/";
    private static final String CORE = "assets/minecraft/shaders/core/";
    private static final List<Overlay> OVERLAYS = List.of(new Overlay("adapt_text_88", 88, 96, "text_88.vsh"),
            new Overlay("adapt_text_97", 97, PackWriter.MAX_FORMAT, "text_97.vsh"));

    private HudShader() {
    }

    /** An overlay directory applied to pack formats {@code min} through {@code max}, holding {@code source} as text.vsh. */
    private record Overlay(String directory, int min, int max, String source) {
    }

    static void write(Map<String, byte[]> files) {
        files.put(CORE + "rendertype_text.vsh", read("rendertype_text.vsh"));
        files.put(CORE + "rendertype_text_intensity.vsh", read("rendertype_text_intensity.vsh"));
        for (Overlay overlay : OVERLAYS) {
            files.put(overlay.directory() + "/" + CORE + "text.vsh", read(overlay.source()));
        }
    }

    /** The {@code overlays} section of {@code pack.mcmeta}. */
    static JsonObject overlays() {
        JsonArray entries = new JsonArray();
        for (Overlay overlay : OVERLAYS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("min_format", overlay.min());
            entry.addProperty("max_format", overlay.max());
            entry.addProperty("directory", overlay.directory());
            entries.add(entry);
        }
        JsonObject overlays = new JsonObject();
        overlays.add("entries", entries);
        return overlays;
    }

    private static byte[] read(String name) {
        try (InputStream in = HudShader.class.getResourceAsStream(SOURCE + name)) {
            if (in == null) {
                throw new IllegalStateException("missing shader " + name);
            }
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
