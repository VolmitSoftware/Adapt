package art.arcane.adapt.pack.manifest;

import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.pack.text.RowFont;
import art.arcane.adapt.pack.text.ShiftFont;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the bake's {@code adapt_manifest.json} (schema 2, written by the pack project's {@code PackManifest}):
 * {@code spaceFont} and {@code spaces} (codepoint to advance), {@code textFonts} (a font per row, {@code dy} below the
 * vanilla title line), {@code pages} (per kit and layout: glyphs with codepoint, x and advance, and host items by
 * window slot 0-89, one per host slot), {@code hostSlots} (the slots that carry dressing, per mode), {@code icons}
 * (sprite to item model), {@code art} (named items to item model), {@code paint} (title glyphs by key: font, codepoint,
 * x relative to where they are drawn, advance), {@code kits} (tooltip style and toast icon model per kit;
 * {@code tooltipStyles} names styles that double as kits, the tome among them) and {@code hud.icons} (action bar glyph
 * codepoint per sprite).
 */
public final class PackManifestReader {
  public static final int SCHEMA = 2;
  /** The vanilla title line: text drawn in the default font sits at this container x and y. */
  public static final int TITLE_X = 8;
  public static final int TITLE_Y = 6;
  public static final String TITLE_FONT = "minecraft:default";
  /** Parchment tooltip frames take dark text without the client's shadow. */
  public static final int KIT_SHADOW = 0;
  private static final int CHEST_SLOTS = 54;

  private PackManifestReader() {
  }

  public static PackManifest read(Reader json) {
    JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
    int schema = root.has("schema") ? root.get("schema").getAsInt() : SCHEMA;
    if (schema != SCHEMA) {
      throw new IllegalArgumentException("unsupported pack manifest schema " + schema);
    }
    Map<String, PackManifest.ItemArt> items = new LinkedHashMap<>();
    for (JsonElement element : array(root, "art")) {
      JsonObject art = element.getAsJsonObject();
      items.put(art.get("name").getAsString(), item(art));
    }
    for (JsonElement element : array(root, "icons")) {
      JsonObject icon = element.getAsJsonObject();
      items.put("sprite:" + icon.get("sprite").getAsString(), item(icon));
    }
    Map<String, PackManifest.TooltipArt> tooltips = new LinkedHashMap<>();
    for (JsonElement style : array(root, "tooltipStyles")) {
      tooltips.put(style.getAsString(), new PackManifest.TooltipArt(style.getAsString(), KIT_SHADOW));
    }
    Map<String, Id> toastModels = new LinkedHashMap<>();
    for (JsonElement element : array(root, "kits")) {
      JsonObject kit = element.getAsJsonObject();
      if (kit.has("tooltipStyle")) {
        tooltips.put(kit.get("kit").getAsString(), new PackManifest.TooltipArt(kit.get("tooltipStyle").getAsString(), KIT_SHADOW));
      }
      if (kit.has("toastModel")) {
        toastModels.put(kit.get("toastSprite").getAsString(), Id.parse(kit.get("toastModel").getAsString()));
      }
    }
    return new PackManifest(schema, new RowFont(TITLE_FONT, TITLE_Y, false), TITLE_X, shift(root), rows(root), pages(root),
        hostSlots(root), items, tooltips, paint(root), hudGlyphs(root), toastModels, condensed(root));
  }

  private static PackManifest.ItemArt item(JsonObject item) {
    return new PackManifest.ItemArt(Id.parse(item.get("model").getAsString()));
  }

  private static Map<String, PackManifest.PaintArt> paint(JsonObject root) {
    Map<String, PackManifest.PaintArt> paint = new LinkedHashMap<>();
    for (JsonElement element : array(root, "paint")) {
      JsonObject glyph = element.getAsJsonObject();
      paint.put(glyph.get("key").getAsString(), new PackManifest.PaintArt(glyph.get("font").getAsString(),
          Character.toString(glyph.get("codepoint").getAsInt()), glyph.get("x").getAsInt(), glyph.get("advance").getAsInt()));
    }
    return paint;
  }

  private static Map<String, String> hudGlyphs(JsonObject root) {
    Map<String, String> glyphs = new LinkedHashMap<>();
    JsonObject hud = root.getAsJsonObject("hud");
    JsonObject icons = hud == null ? null : hud.getAsJsonObject("icons");
    if (icons != null) {
      for (Map.Entry<String, JsonElement> entry : icons.entrySet()) {
        glyphs.put(entry.getKey(), Character.toString(entry.getValue().getAsInt()));
      }
    }
    return glyphs;
  }

  private static Map<String, List<PackSlot>> hostSlots(JsonObject root) {
    Map<String, List<PackSlot>> modes = new LinkedHashMap<>();
    JsonObject listed = root.getAsJsonObject("hostSlots");
    if (listed != null) {
      for (Map.Entry<String, JsonElement> entry : listed.entrySet()) {
        List<PackSlot> slots = new ArrayList<>();
        for (JsonElement slot : entry.getValue().getAsJsonArray()) {
          slots.add(windowSlot(slot.getAsInt()));
        }
        modes.put(entry.getKey(), slots);
      }
    }
    return modes;
  }

  private static ShiftFont shift(JsonObject root) {
    Map<Integer, String> glyphs = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("spaces").entrySet()) {
      glyphs.put(entry.getValue().getAsInt(), Character.toString(Integer.parseInt(entry.getKey())));
    }
    return new ShiftFont(root.get("spaceFont").getAsString(), glyphs);
  }

  /**
   * The condensed font: {@code "condensed": {"advances": {"<code point>": advance, ...}, "fonts": [{"dy": 0, "font":
   * "<title font>"}, {"dy": n, "font": "<row font>"}, ...]}}, row fonts declared like {@code textFonts}; null when absent.
   */
  private static PackManifest.Condensed condensed(JsonObject root) {
    JsonObject condensed = root.getAsJsonObject("condensed");
    if (condensed == null) {
      return null;
    }
    Map<Integer, Integer> advances = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : condensed.getAsJsonObject("advances").entrySet()) {
      advances.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsInt());
    }
    RowFont title = null;
    List<RowFont> rows = new ArrayList<>();
    for (JsonElement element : condensed.getAsJsonArray("fonts")) {
      JsonObject font = element.getAsJsonObject();
      int dy = font.get("dy").getAsInt();
      RowFont row = new RowFont(font.get("font").getAsString(), TITLE_Y + dy, dy != 0);
      if (dy == 0) {
        title = row;
      } else {
        rows.add(row);
      }
    }
    return title == null ? null : new PackManifest.Condensed(PackFontMetrics.of("condensed", advances), title, rows);
  }

  private static List<RowFont> rows(JsonObject root) {
    List<RowFont> rows = new ArrayList<>();
    for (JsonElement element : array(root, "textFonts")) {
      JsonObject font = element.getAsJsonObject();
      int dy = font.get("dy").getAsInt();
      if (dy != 0) {
        rows.add(new RowFont(font.get("font").getAsString(), TITLE_Y + dy, true));
      }
    }
    return rows;
  }

  private static Map<String, PackManifest.PageArt> pages(JsonObject root) {
    Map<String, PackManifest.PageArt> pages = new LinkedHashMap<>();
    for (JsonElement element : array(root, "pages")) {
      JsonObject page = element.getAsJsonObject();
      String font = page.get("font").getAsString();
      List<PackManifest.Tile> tiles = new ArrayList<>();
      for (JsonElement glyph : page.getAsJsonArray("glyphs")) {
        JsonObject tile = glyph.getAsJsonObject();
        tiles.add(new PackManifest.Tile(font, Character.toString(tile.get("codepoint").getAsInt()), tile.get("x").getAsInt(),
            tile.get("advance").getAsInt()));
      }
      List<PackManifest.Host> hosts = new ArrayList<>();
      for (JsonElement item : array(page, "items")) {
        JsonObject host = item.getAsJsonObject();
        hosts.add(new PackManifest.Host(windowSlot(host.get("slot").getAsInt()), item(host)));
      }
      pages.put(page.get("kit").getAsString() + "/" + page.get("layout").getAsString(), new PackManifest.PageArt(tiles, hosts));
    }
    return pages;
  }

  /** Window slot 0-53 is a chest slot, 54-89 a player slot. */
  static PackSlot windowSlot(int slot) {
    return slot < CHEST_SLOTS ? PackSlot.top(slot) : PackSlot.lower(slot - CHEST_SLOTS);
  }

  private static JsonArray array(JsonObject root, String name) {
    JsonArray array = root.getAsJsonArray(name);
    return array == null ? new JsonArray() : array;
  }
}
