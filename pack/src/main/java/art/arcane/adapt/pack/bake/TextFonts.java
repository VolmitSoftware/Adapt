package art.arcane.adapt.pack.bake;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes the text row fonts: the vanilla text providers re-declared with every ascent lowered by the row's distance below
 * the title text's top, so text set in a row font lands on that row; and the same rows of the design's condensed font
 * ({@code text/condensed/}: its providers, sheets and advance table) for names too long for the default one. A row a
 * provider cannot reach (its ascent would exceed its height) gets no font.
 */
final class TextFonts {
    private final PackFiles out;
    private static final String CONDENSED = "/art/arcane/adapt/pack/text/condensed/";
    private final List<PackManifest.TextFont> fonts = new ArrayList<>();
    private PackManifest.Condensed condensed;

    TextFonts(PackFiles out) {
        this.out = out;
    }

    List<PackManifest.TextFont> fonts() {
        return fonts;
    }

    /** The condensed font, or null before {@link #rows} wrote it. */
    PackManifest.Condensed condensed() {
        return condensed;
    }

    /** See {@link PackWriter#textRows}. */
    void rows(List<Integer> rows) {
        JsonArray grids = vanillaGrids();
        JsonArray condensedProviders = JsonParser.parseString(new String(read("condensed.json"), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("providers");
        List<PackManifest.CondensedFont> condensedFonts = new ArrayList<>();
        for (int dy : new TreeSet<>(rows)) {
            JsonArray providers = new JsonArray();
            providers.add(PackJson.spaceProvider(new TreeMap<>(Map.of(" ", 4, "‌", 0))));
            if (!shifted(grids, dy, providers)) {
                continue;
            }
            String name = rowName(dy);
            write("ui/text/" + name, providers);
            fonts.add(new PackManifest.TextFont(PackWriter.NAMESPACE + ":ui/text/" + name, dy));
            JsonArray condensedRow = new JsonArray();
            if (shifted(condensedProviders, dy, condensedRow)) {
                write("ui/condensed/" + name, condensedRow);
                condensedFonts.add(new PackManifest.CondensedFont(dy, PackWriter.NAMESPACE + ":ui/condensed/" + name));
            }
        }
        if (!condensedFonts.isEmpty()) {
            for (String sheet : List.of("condensed_ascii", "condensed_accent")) {
                out.put("assets/" + PackWriter.NAMESPACE + "/textures/font/" + sheet + ".png", read(sheet + ".png"));
            }
            Map<Integer, Integer> advances = new TreeMap<>();
            JsonObject table = JsonParser.parseString(new String(read("widths.json"), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("advances");
            for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
                advances.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsInt());
            }
            condensed = new PackManifest.Condensed(advances, condensedFonts);
        }
    }

    /** Copies {@code providers} into {@code into} with every bitmap ascent lowered by {@code dy}; false when one cannot reach the row. */
    private static boolean shifted(JsonArray providers, int dy, JsonArray into) {
        for (JsonElement element : providers) {
            JsonObject provider = element.getAsJsonObject().deepCopy();
            if (provider.has("ascent")) {
                int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
                int ascent = provider.get("ascent").getAsInt() - dy;
                if (ascent > height) {
                    return false;
                }
                provider.addProperty("ascent", ascent);
            }
            into.add(provider);
        }
        return true;
    }

    private static String rowName(int dy) {
        return dy < 0 ? "m" + -dy : Integer.toString(dy);
    }

    private void write(String name, JsonArray providers) {
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        out.put("assets/" + PackWriter.NAMESPACE + "/font/" + name + ".json", PackJson.json(font));
    }

    private static byte[] read(String name) {
        try (InputStream in = TextFonts.class.getResourceAsStream(CONDENSED + name)) {
            if (in == null) {
                throw new IllegalStateException("missing condensed font file " + name);
            }
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static JsonArray vanillaGrids() {
        try (InputStream in = TextFonts.class.getResourceAsStream("/art/arcane/adapt/pack/text/vanilla-text-providers.json")) {
            if (in == null) {
                throw new IllegalStateException("missing vanilla text providers");
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
