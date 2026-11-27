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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The design's geometry file ({@code geometry-spec.json}, container pixels) read into {@link PageSpec}s, so a new
 * revision of the design is a data change: framing {@code A} is the single page.
 */
public final class DesignSpec {
    public static final String RESOURCE = "/art/arcane/adapt/pack/design/geometry-spec.json";
    private final JsonObject root;
    private final String hash;

    public DesignSpec(String json) {
        root = JsonParser.parseString(json).getAsJsonObject();
        hash = PackJson.sha1(json.getBytes(StandardCharsets.UTF_8));
    }

    /** The SHA-1 of the geometry file, for keying cached bakes. */
    public String hash() {
        return hash;
    }

    /** The geometry shipped with the pack project. */
    public static DesignSpec builtin() {
        try (InputStream in = DesignSpec.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            return new DesignSpec(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public int version() {
        return root.get("version").getAsInt();
    }

    /**
     * The kit page of framing {@code name}: the design's example book (its top is the kit's to choose, see {@link BookTop};
     * its bottom is fixed), its frame, header line and body tops, text column, content boxes and the virtual screen the
     * composer dresses.
     */
    public PageSpec page(String name) {
        JsonObject framing = root.getAsJsonObject("framings").getAsJsonObject(name);
        if (framing == null) {
            throw new IllegalArgumentException("no framing " + name);
        }
        JsonObject kitPage = framing.getAsJsonObject("kitPage");
        JsonObject book = kitPage.getAsJsonObject("book");
        JsonObject column = kitPage.getAsJsonArray("textColumns").get(0).getAsJsonObject();
        List<PageSpec.Box> keep = new ArrayList<>();
        for (JsonElement element : kitPage.getAsJsonArray("contentClear")) {
            JsonObject box = element.getAsJsonObject();
            int x = box.get("x").getAsInt();
            int y = box.get("y").getAsInt();
            keep.add(new PageSpec.Box(x, y, x + box.get("w").getAsInt(), y + box.get("h").getAsInt()));
        }
        JsonObject screen = kitPage.getAsJsonObject("pageSpec").getAsJsonObject("virtualScreen");
        JsonObject container = screen.getAsJsonObject("container");
        List<Integer> rows = new ArrayList<>();
        JsonArray textRows = root.getAsJsonObject("text").getAsJsonObject("textRows").getAsJsonArray(name);
        for (JsonElement row : textRows) {
            rows.add(row.getAsInt() - SlotGrid.TITLE_Y);
        }
        return new PageSpec(name, book.get("x").getAsInt(), book.get("y").getAsInt(), book.get("w").getAsInt(), book.get("h").getAsInt(),
                kitPage.get("frame").getAsInt(), kitPage.get("headerTop").getAsInt(), kitPage.get("bodyTop").getAsInt(),
                new PageSpec.Column(column.get("x").getAsInt(), column.get("right").getAsInt()), keep,
                new PageSpec.Screen(screen.get("w").getAsInt(), screen.get("h").getAsInt(), container.get("x").getAsInt(), container.get("y").getAsInt()),
                hosts(name), artSlots().has(name + "fallback") ? hosts(name + "fallback") : null, rows);
    }

    /** The host slots and rule of dressing mode {@code mode} ({@code A} or {@code Afallback}). */
    public DressingHosts hosts(String mode) {
        JsonObject section = artSlots().getAsJsonObject(mode);
        if (section == null) {
            throw new IllegalArgumentException("no dressing mode " + mode);
        }
        Map<String, Integer> slots = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> slot : section.getAsJsonObject("slots").entrySet()) {
            slots.put(slot.getKey(), slot.getValue().getAsInt());
        }
        return new DressingHosts(mode, slots);
    }

    private JsonObject artSlots() {
        return root.getAsJsonObject("artSlots");
    }
}
