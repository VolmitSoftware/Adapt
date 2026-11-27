package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.text.Align;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads screen geometry. Shape (format 1):
 * <pre>
 * { "format": 1,
 *   "grid": { "width": 176, "height": 222, "x": 8, "pitch": 18, "chestY": 18, "lowerY": [139, 157, 175, 197] },
 *   "screens": {
 *     "skills": {
 *       "page": { "kind": "skills", "x": -27, "y": -19, "width": 230, "height": 260 },
 *       "cards": [ { "origin": "top:0", "columns": 3, "rows": 3, "width": 3, "height": 2, "icon": [0, 0],
 *                    "text": { "name": { "x": -1, "y": 23, "width": 54, "align": "centre" } } } ],
 *       "slots": { "foot.back": ["lower:27"], "foot.tabs": ["lower:29-33"] },
 *       "text": { "heading": { "x": 8, "y": 6, "width": 160, "align": "left", "colour": "404040", "lines": 1 } },
 *       "bars": { "heading.rule": { "x": 8, "y": 27, "width": 162, "height": 1, "colour": "B79C6E" } } } } }
 * </pre>
 * A slot list entry may be a range ({@code top:18-26}). A label of two lines may set {@code "top": true} to keep a
 * one-line text on its first line instead of centring it, and {@code "breaks": true} to split a word too wide for its line
 * with a hyphen. Card labels and bars ({@code "bars"} beside {@code "text"})
 * are relative to the card's top-left slot. Text defaults: left aligned, colour 404040, one line. A bar's colour is
 * used by rules only and defaults to 404040.
 */
public final class PackLayoutReader {
  public static final int FORMAT = 1;
  public static final int DEFAULT_COLOUR = 0x404040;
  private static final String BUILT_IN = "layout.json";

  private PackLayoutReader() {
  }

  /** The layout shipped in the jar: framing A of the design lane's geometry spec. */
  public static PackLayout builtIn() {
    try (InputStream raw = PackLayoutReader.class.getResourceAsStream(BUILT_IN)) {
      if (raw == null) {
        throw new IllegalStateException("missing " + BUILT_IN);
      }
      return read(new InputStreamReader(raw, StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  public static PackLayout read(Reader json) {
    JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
    int format = root.has("format") ? root.get("format").getAsInt() : FORMAT;
    if (format != FORMAT) {
      throw new IllegalArgumentException("unsupported layout format " + format);
    }
    PackGrid grid = grid(root.getAsJsonObject("grid"));
    Map<String, ScreenLayout> screens = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("screens").entrySet()) {
      screens.put(entry.getKey(), screen(entry.getKey(), entry.getValue().getAsJsonObject(), grid));
    }
    return new PackLayout(grid, screens);
  }

  private static PackGrid grid(JsonObject grid) {
    List<Integer> lower = new ArrayList<>();
    for (JsonElement y : grid.getAsJsonArray("lowerY")) {
      lower.add(y.getAsInt());
    }
    return new PackGrid(grid.get("width").getAsInt(), grid.get("height").getAsInt(), grid.get("x").getAsInt(),
        grid.get("pitch").getAsInt(), grid.get("chestY").getAsInt(), lower);
  }

  private static ScreenLayout screen(String id, JsonObject screen, PackGrid grid) {
    JsonObject page = screen.getAsJsonObject("page");
    Rect rect = new Rect(page.get("x").getAsInt(), page.get("y").getAsInt(), page.get("width").getAsInt(),
        page.get("height").getAsInt());
    List<CardGrid> cardGrids = new ArrayList<>();
    JsonArray cards = screen.getAsJsonArray("cards");
    if (cards != null) {
      for (JsonElement element : cards) {
        JsonObject card = element.getAsJsonObject();
        JsonArray icon = card.getAsJsonArray("icon");
        cardGrids.add(new CardGrid(PackSlot.parse(card.get("origin").getAsString()), card.get("columns").getAsInt(),
            card.get("rows").getAsInt(), card.get("width").getAsInt(), card.get("height").getAsInt(),
            icon.get(0).getAsInt(), icon.get(1).getAsInt(), texts(card.getAsJsonObject("text")), bars(card.getAsJsonObject("bars"))));
      }
    }
    Map<String, List<PackSlot>> slots = new LinkedHashMap<>();
    JsonObject slotRoles = screen.getAsJsonObject("slots");
    if (slotRoles != null) {
      for (Map.Entry<String, JsonElement> entry : slotRoles.entrySet()) {
        List<PackSlot> listed = new ArrayList<>();
        for (JsonElement slot : entry.getValue().getAsJsonArray()) {
          listed.addAll(slots(slot.getAsString()));
        }
        slots.put(entry.getKey(), listed);
      }
    }
    CrumbRibbon ribbon = screen.has("ribbon") ? CrumbRibbon.valueOf(screen.get("ribbon").getAsString().toUpperCase(Locale.ROOT)) : CrumbRibbon.ALWAYS;
    return new ScreenLayout(id, page.get("kind").getAsString(), rect, ribbon, ScreenLayout.expand(cardGrids, grid), slots,
        texts(screen.getAsJsonObject("text")), bars(screen.getAsJsonObject("bars")));
  }

  static List<PackSlot> slots(String written) {
    int dash = written.lastIndexOf('-');
    int colon = written.indexOf(':');
    if (dash <= colon) {
      return List.of(PackSlot.parse(written));
    }
    PackSlot first = PackSlot.parse(written.substring(0, dash));
    int last = Integer.parseInt(written.substring(dash + 1).trim());
    List<PackSlot> range = new ArrayList<>();
    for (int index = first.index(); index <= last; index++) {
      range.add(new PackSlot(first.area(), index));
    }
    return range;
  }

  private static Map<String, TextSpec> texts(JsonObject roles) {
    Map<String, TextSpec> text = new LinkedHashMap<>();
    if (roles != null) {
      for (Map.Entry<String, JsonElement> entry : roles.entrySet()) {
        text.put(entry.getKey(), text(entry.getValue().getAsJsonObject()));
      }
    }
    return text;
  }

  private static Map<String, BarSpec> bars(JsonObject roles) {
    Map<String, BarSpec> bars = new LinkedHashMap<>();
    if (roles != null) {
      for (Map.Entry<String, JsonElement> entry : roles.entrySet()) {
        JsonObject bar = entry.getValue().getAsJsonObject();
        int colour = bar.has("colour") ? Integer.parseInt(bar.get("colour").getAsString(), 16) : DEFAULT_COLOUR;
        bars.put(entry.getKey(), new BarSpec(bar.get("x").getAsInt(), bar.get("y").getAsInt(), bar.get("width").getAsInt(),
            bar.get("height").getAsInt(), colour));
      }
    }
    return bars;
  }

  private static TextSpec text(JsonObject text) {
    Align align = text.has("align") ? Align.valueOf(text.get("align").getAsString().toUpperCase(Locale.ROOT)) : Align.LEFT;
    int colour = text.has("colour") ? Integer.parseInt(text.get("colour").getAsString(), 16) : DEFAULT_COLOUR;
    int lines = text.has("lines") ? text.get("lines").getAsInt() : 1;
    int pitch = text.has("pitch") ? text.get("pitch").getAsInt() : 0;
    boolean top = text.has("top") && text.get("top").getAsBoolean();
    boolean breaks = text.has("breaks") && text.get("breaks").getAsBoolean();
    return new TextSpec(text.get("x").getAsInt(), text.get("y").getAsInt(), text.get("width").getAsInt(), align, colour,
        lines, pitch, top, breaks);
  }
}
