package art.arcane.adapt.pack.screen;

import art.arcane.adapt.pack.bake.SlotGrid;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Draws a pack screen as a 26.x client would from the built pack's own files: the container, the title runs (page
 * glyphs, paint, and text in the vanilla sheet when one is given, else in boxes of the vanilla widths), then each slot's
 * item model in window slot order, every model under its GUI transform with its animation frame at the tick and its
 * tints from the look's colours, and optionally one slot's tooltip in its frame. Every font, glyph, item definition,
 * model, texture and tooltip sprite the page names must resolve in the pack; what does not is listed in
 * {@link #problems()}. A look with no item model on the screens' plain paper item is a hole and draws magenta; other
 * items without a pack model are the menu's own and draw as grey stand-ins, or as their vanilla item texture when
 * {@code adapt.pack.vanilla} names a client jar.
 */
final class ScreenCompositor {
  static final int LEFT = -100;
  static final int TOP = -60;
  static final int WIDTH = 376;
  static final int HEIGHT = 352;
  private static final int BACKDROP = 0xFF202326;
  private static final int CONTAINER = 0xFFC6C6C6;
  private static final int HOLE = 0xFFFF00FF;
  private static final int STAND_IN = 0xFF8B8B8B;
  /** The item the screens name a slot with when it shows only pack art; with no pack model it is a hole. */
  private static final Id PLAIN = Id.minecraft("paper");
  private static final String VANILLA = System.getProperty("adapt.pack.vanilla");
  private static final Map<Id, Raster> VANILLA_ITEMS = new TreeMap<>((Id a, Id b) -> a.toString().compareTo(b.toString()));

  private final Map<String, byte[]> files;
  private final Raster sheet;
  private final PackFontMetrics metrics = PackFontMetrics.vanilla();
  private final List<String> problems = new ArrayList<>();
  private final Map<String, JsonObject> fonts = new TreeMap<>();

  /** {@code sheet} is the vanilla ASCII sheet for text, or null for boxes. */
  ScreenCompositor(Map<String, byte[]> files, Raster sheet) {
    this.files = files;
    this.sheet = sheet;
  }

  List<String> problems() {
    return problems;
  }

  /** The screen of {@code menu} and {@code surface} at {@code tick}; {@code hover} names a slot whose tooltip to draw, or null. */
  Raster draw(ChestMenu menu, PackSurface surface, long tick, PackSlot hover) {
    Raster canvas = new Raster(WIDTH, HEIGHT);
    fill(canvas, 0, 0, WIDTH, HEIGHT, BACKDROP);
    fill(canvas, -LEFT, -TOP, SlotGrid.CONTAINER_WIDTH, height(menu), CONTAINER);
    Raster layer = layer(menu, surface, tick, hover);
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        canvas.blend(x, y, layer.get(x, y));
      }
    }
    return canvas;
  }

  /** The window's height in GUI pixels for {@code menu}'s rows: the chest rows, the player inventory and the frame. */
  static int height(ChestMenu menu) {
    return 114 + 18 * menu.rows();
  }

  /**
   * What the pack draws for {@code menu} and {@code surface} at {@code tick}, without the vanilla chest under it: clear
   * wherever the pack draws nothing. Its top left is container pixel ({@link #LEFT}, {@link #TOP}).
   */
  Raster layer(ChestMenu menu, PackSurface surface, long tick, PackSlot hover) {
    Raster canvas = new Raster(WIDTH, HEIGHT);
    title(canvas, surface.title());
    Map<Integer, SlotLook> looks = new TreeMap<>();
    Map<Integer, MenuButton> buttons = new TreeMap<>();
    for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
      buttons.put(entry.getKey(), entry.getValue());
      looks.put(entry.getKey(), surface.look(PackSlot.top(entry.getKey())));
    }
    for (Map.Entry<Integer, MenuButton> entry : surface.lower().entrySet()) {
      buttons.put(54 + entry.getKey(), entry.getValue());
      looks.put(54 + entry.getKey(), surface.look(PackSlot.lower(entry.getKey())));
    }
    for (Map.Entry<Integer, MenuButton> entry : buttons.entrySet()) {
      item(canvas, entry.getKey(), entry.getValue(), looks.get(entry.getKey()), tick);
    }
    if (hover != null) {
      int slot = hover.area() == PackSlot.Area.TOP ? hover.index() : 54 + hover.index();
      MenuButton button = buttons.get(slot);
      if (button != null) {
        tooltip(canvas, slot, button, looks.get(slot));
      }
    }
    return canvas;
  }

  /**
   * {@code layer} over the vanilla chest on a GUI screen {@code width} by {@code height}, the window centred as the
   * client centres it (its top left at the floor of half the free width and height).
   */
  static Raster screen(Raster layer, ChestMenu menu, int width, int height) {
    int windowHeight = height(menu);
    int left = (width - SlotGrid.CONTAINER_WIDTH) / 2;
    int top = (height - windowHeight) / 2;
    Raster out = new Raster(width, height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int cx = x - left;
        int cy = y - top;
        out.set(x, y, cx >= 0 && cy >= 0 && cx < SlotGrid.CONTAINER_WIDTH && cy < windowHeight ? CONTAINER : BACKDROP);
        out.blend(x, y, layer.get(cx - LEFT, cy - TOP));
      }
    }
    return out;
  }

  private void title(Raster canvas, List<TitleRun> runs) {
    int pen = SlotGrid.TITLE_X;
    for (TitleRun run : runs) {
      JsonObject font = run.font().equals("minecraft:default") ? null : font(run.font());
      if (font == null && !run.font().equals("minecraft:default")) {
        continue;
      }
      for (int at = 0; at < run.text().length(); ) {
        int codepoint = run.text().codePointAt(at);
        at += Character.charCount(codepoint);
        pen += glyph(canvas, run, font, codepoint, pen);
      }
    }
  }

  /** Draws one character of a run at {@code pen} and returns its advance. */
  private int glyph(Raster canvas, TitleRun run, JsonObject font, int codepoint, int pen) {
    String text = Character.toString(codepoint);
    if (font == null) {
      return vanilla(canvas, codepoint, pen, SlotGrid.TITLE_Y, run.colour());
    }
    for (JsonElement element : font.getAsJsonArray("providers")) {
      JsonObject provider = element.getAsJsonObject();
      String type = provider.get("type").getAsString();
      if (type.equals("space")) {
        JsonObject advances = provider.getAsJsonObject("advances");
        if (advances.has(text)) {
          return advances.get(text).getAsInt();
        }
      } else if (type.equals("bitmap") && provider.getAsJsonArray("chars").size() == 1 && provider.getAsJsonArray("chars").get(0).getAsString().equals(text)) {
        String texture = asset(provider.get("file").getAsString(), "textures", "");
        byte[] png = files.get(texture);
        if (png == null) {
          problems.add(run.font() + " U+" + Integer.toHexString(codepoint) + ": missing " + texture);
          return 0;
        }
        Raster art = Raster.decode(png);
        paste(canvas, tint(art, run.colour()), pen, SlotGrid.TITLE_Y + 7 - provider.get("ascent").getAsInt());
        return advance(art);
      } else if (type.equals("bitmap") && !provider.get("file").getAsString().equals("minecraft:font/ascii.png")) {
        JsonArray rows = provider.getAsJsonArray("chars");
        for (int row = 0; row < rows.size(); row++) {
          int column = rows.get(row).getAsString().indexOf(text);
          if (column < 0) {
            continue;
          }
          byte[] png = files.get(asset(provider.get("file").getAsString(), "textures", ""));
          if (png == null) {
            problems.add(run.font() + ": missing " + provider.get("file").getAsString());
            return 0;
          }
          Raster sheet = Raster.decode(png);
          int cellWidth = sheet.width() / rows.get(row).getAsString().length();
          int cellHeight = sheet.height() / rows.size();
          Raster cell = sheet.crop(column * cellWidth, row * cellHeight, cellWidth, cellHeight);
          paste(canvas, tint(cell, run.colour()), pen, SlotGrid.TITLE_Y + 7 - provider.get("ascent").getAsInt());
          return advance(cell);
        }
      } else if (type.equals("bitmap") && provider.get("file").getAsString().equals("minecraft:font/ascii.png")) {
        for (JsonElement row : provider.getAsJsonArray("chars")) {
          if (row.getAsString().contains(text)) {
            return vanilla(canvas, codepoint, pen, SlotGrid.TITLE_Y + 7 - provider.get("ascent").getAsInt(), run.colour());
          }
        }
      } else if (type.equals("bitmap") && !provider.get("file").getAsString().startsWith("minecraft:")) {
        Raster cell = cell(provider, codepoint);
        if (cell != null) {
          if (codepoint != ' ') {
            paste(canvas, tint(cell, run.colour()), pen, SlotGrid.TITLE_Y + 7 - provider.get("ascent").getAsInt());
          }
          return advance(cell);
        }
      }
    }
    if (codepoint < 0x80) {
      return metrics.width(text);
    }
    problems.add(run.font() + ": no glyph U+" + Integer.toHexString(codepoint));
    return 0;
  }

  /** The cell of a grid bitmap provider (a pack font sheet) holding {@code codepoint}, or null when the sheet has none. */
  private Raster cell(JsonObject provider, int codepoint) {
    JsonArray rows = provider.getAsJsonArray("chars");
    for (int row = 0; row < rows.size(); row++) {
      int[] points = rows.get(row).getAsString().codePoints().toArray();
      for (int column = 0; column < points.length; column++) {
        if (points[column] != codepoint) {
          continue;
        }
        byte[] png = files.get(asset(provider.get("file").getAsString(), "textures", ""));
        if (png == null) {
          problems.add("missing font sheet " + provider.get("file").getAsString());
          return null;
        }
        Raster sheet = Raster.decode(png);
        int width = sheet.width() / points.length;
        int height = sheet.height() / rows.size();
        return sheet.crop(column * width, row * height, width, height);
      }
    }
    return null;
  }

  private int vanilla(Raster canvas, int codepoint, int pen, int top, int colour) {
    int advance = metrics.width(Character.toString(codepoint));
    if (codepoint == ' ') {
      return advance;
    }
    if (sheet != null && codepoint < 0x80) {
      Raster cell = sheet.crop((codepoint % 16) * 8, (codepoint / 16) * 8, 8, 8);
      paste(canvas, tint(cell, colour), pen, top);
    } else {
      fill(canvas, pen - LEFT, top + 1 - TOP, Math.max(1, advance - 1), 6, 0xFF000000 | colour);
    }
    return advance;
  }

  private void item(Raster canvas, int slot, MenuButton button, SlotLook look, long tick) {
    int x = SlotGrid.x(slot);
    int y = SlotGrid.y(slot);
    if (look == null) {
      standIn(canvas, x, y, button.icon().type());
      return;
    }
    if (look.itemModel() == null) {
      if (button.icon().type().equals(PLAIN) && button.icon().itemModel() == null) {
        fill(canvas, x - LEFT, y - TOP, 16, 16, HOLE);
        problems.add("slot " + slot + " (" + LegacyText.plain(String.valueOf(button.icon().name())) + "): no item model");
      } else {
        standIn(canvas, x, y, button.icon().type());
      }
      return;
    }
    String definition = asset(look.itemModel().toString(), "items", ".json");
    byte[] json = files.get(definition);
    if (json == null) {
      fill(canvas, x - LEFT, y - TOP, 16, 16, HOLE);
      problems.add("slot " + slot + ": no item definition " + look.itemModel());
      return;
    }
    tree(canvas, JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("model"), slot, look, tick);
    if (look.tooltipStyle() != null && !files.containsKey(tooltipSprite(look.tooltipStyle()))) {
      problems.add("slot " + slot + ": no tooltip style " + look.tooltipStyle());
    }
  }

  /** A menu's own item: its vanilla item or block texture when the client jar is given and has one, else a grey square. */
  private void standIn(Raster canvas, int x, int y, Id type) {
    Raster art = vanilla(type);
    if (art == null) {
      fill(canvas, x + 2 - LEFT, y + 2 - TOP, 12, 12, STAND_IN);
    } else {
      paste(canvas, art.width() > 16 ? art.crop(0, 0, 16, 16) : art, x, y);
    }
  }

  private static synchronized Raster vanilla(Id type) {
    if (VANILLA == null || type == null) {
      return null;
    }
    if (VANILLA_ITEMS.containsKey(type)) {
      return VANILLA_ITEMS.get(type);
    }
    Raster found = null;
    try (ZipFile jar = new ZipFile(VANILLA)) {
      for (String folder : List.of("item", "block")) {
        ZipEntry entry = jar.getEntry("assets/" + type.namespace() + "/textures/" + folder + "/" + type.path() + ".png");
        if (entry != null) {
          found = Raster.decode(jar.getInputStream(entry).readAllBytes());
          break;
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    VANILLA_ITEMS.put(type, found);
    return found;
  }

  private void tree(Raster canvas, JsonObject node, int slot, SlotLook look, long tick) {
    String type = node.get("type").getAsString();
    switch (type) {
      case "minecraft:empty" -> {
      }
      case "minecraft:composite" -> node.getAsJsonArray("models").forEach((JsonElement part) -> tree(canvas, part.getAsJsonObject(), slot, look, tick));
      case "minecraft:select" -> {
        int index = node.get("index").getAsInt();
        String chosen = index < look.strings().size() ? look.strings().get(index) : null;
        JsonObject picked = node.has("fallback") ? node.getAsJsonObject("fallback") : null;
        for (JsonElement option : node.getAsJsonArray("cases")) {
          if (option.getAsJsonObject().get("when").getAsString().equals(chosen)) {
            picked = option.getAsJsonObject().getAsJsonObject("model");
          }
        }
        if (picked != null) {
          tree(canvas, picked, slot, look, tick);
        }
      }
      case "minecraft:model" -> {
        int tint = -1;
        JsonArray tints = node.getAsJsonArray("tints");
        if (tints != null && !tints.isEmpty()) {
          JsonObject first = tints.get(0).getAsJsonObject();
          int index = first.get("index").getAsInt();
          tint = index < look.colours().size() ? look.colours().get(index) : first.get("default").getAsInt();
        }
        model(canvas, node.get("model").getAsString(), slot, tint, tick);
      }
      default -> problems.add("slot " + slot + ": item model type " + type);
    }
  }

  private void model(Raster canvas, String id, int slot, int tint, long tick) {
    byte[] json = files.get(asset(id, "models", ".json"));
    if (json == null) {
      problems.add("slot " + slot + ": no model " + id);
      return;
    }
    JsonObject model = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
    JsonObject textures = model.getAsJsonObject("textures");
    int sx = SlotGrid.x(slot);
    int sy = SlotGrid.y(slot);
    if (model.has("parent") && model.get("parent").getAsString().equals("minecraft:item/generated")) {
      for (int layer = 0; textures.has("layer" + layer); layer++) {
        Raster art = texture(textures.get("layer" + layer).getAsString(), tick, slot);
        if (art != null) {
          paste(canvas, layer == 0 ? tint(art, tint) : art, sx, sy);
        }
      }
      return;
    }
    JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
    double scale = gui.getAsJsonArray("scale").get(0).getAsDouble();
    double shiftX = gui.getAsJsonArray("translation").get(0).getAsDouble();
    double shiftY = gui.getAsJsonArray("translation").get(1).getAsDouble();
    for (JsonElement element : model.getAsJsonArray("elements")) {
      JsonObject box = element.getAsJsonObject();
      JsonArray from = box.getAsJsonArray("from");
      JsonArray to = box.getAsJsonArray("to");
      int x0 = (int) Math.round(sx + 8 + (from.get(0).getAsDouble() - 8) * scale + shiftX);
      int y0 = (int) Math.round(sy + 8 - ((to.get(1).getAsDouble() - 8) * scale + shiftY));
      JsonObject south = box.getAsJsonObject("faces").getAsJsonObject("south");
      Raster art = texture(textures.get(south.get("texture").getAsString().substring(1)).getAsString(), tick, slot);
      if (art != null) {
        paste(canvas, south.has("tintindex") ? tint(art, tint) : art, x0, y0);
      }
    }
  }

  /** The texture {@code id} as shown at {@code tick}: one frame of an animated strip. */
  private Raster texture(String id, long tick, int slot) {
    String path = asset(id, "textures", ".png");
    byte[] png = files.get(path);
    if (png == null) {
      problems.add("slot " + slot + ": no texture " + id);
      return null;
    }
    Raster strip = Raster.decode(png);
    byte[] meta = files.get(path + ".mcmeta");
    if (meta == null) {
      return strip;
    }
    JsonObject animation = JsonParser.parseString(new String(meta, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animation");
    int width = animation.get("width").getAsInt();
    int height = animation.get("height").getAsInt();
    long total = 0;
    for (JsonElement frame : animation.getAsJsonArray("frames")) {
      total += frame.getAsJsonObject().get("time").getAsInt();
    }
    long at = Math.floorMod(tick, total);
    for (JsonElement element : animation.getAsJsonArray("frames")) {
      JsonObject frame = element.getAsJsonObject();
      if (at < frame.get("time").getAsInt()) {
        return strip.crop(0, frame.get("index").getAsInt() * height, width, height);
      }
      at -= frame.get("time").getAsInt();
    }
    return strip.crop(0, 0, width, height);
  }

  private void tooltip(Raster canvas, int slot, MenuButton button, SlotLook look) {
    List<List<LegacyText.Segment>> lines = new ArrayList<>();
    lines.add(LegacyText.segments(String.valueOf(button.icon().name()), 0x2E2216));
    for (String line : button.icon().lore()) {
      lines.add(LegacyText.segments(line, 0x5A4430));
    }
    int width = 0;
    for (List<LegacyText.Segment> line : lines) {
      int lineWidth = 0;
      for (LegacyText.Segment segment : line) {
        lineWidth += metrics.width(segment.text());
      }
      width = Math.max(width, lineWidth);
    }
    int height = 8 + (lines.size() - 1) * 10 + (lines.size() > 1 ? 2 : 0);
    int x = SlotGrid.x(slot) + 20;
    int y = SlotGrid.y(slot) - 4;
    if (look != null && look.tooltipStyle() != null && files.containsKey(tooltipSprite(look.tooltipStyle()))) {
      paste(canvas, nine(Raster.decode(files.get(tooltipSprite(look.tooltipStyle()))), width + 10, height + 10, 5), x - 5, y - 5);
    } else {
      fill(canvas, x - 3 - LEFT, y - 3 - TOP, width + 6, height + 6, 0xF0100010);
    }
    for (int index = 0; index < lines.size(); index++) {
      int pen = x;
      for (LegacyText.Segment segment : lines.get(index)) {
        for (int at = 0; at < segment.text().length(); at++) {
          pen += vanilla(canvas, segment.text().charAt(at), pen, y + index * 10 + (index > 0 ? 2 : 0), segment.colour());
        }
      }
    }
  }

  private JsonObject font(String id) {
    return fonts.computeIfAbsent(id, (String key) -> {
      byte[] json = files.get(asset(key, "font", ".json"));
      if (json == null) {
        problems.add("no font " + key);
        return null;
      }
      return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
    });
  }

  private static String tooltipSprite(String style) {
    Id id = Id.parse(style);
    return "assets/" + id.namespace() + "/textures/gui/sprites/tooltip/" + id.path() + "_background.png";
  }

  private static String asset(String id, String folder, String suffix) {
    int colon = id.indexOf(':');
    String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
    return "assets/" + ns + "/" + folder + "/" + id.substring(colon + 1) + suffix;
  }

  private static Raster nine(Raster piece, int width, int height, int border) {
    Raster out = new Raster(width, height);
    for (int y = 0; y < height; y++) {
      int sy = y < border ? y : y >= height - border ? piece.height() - (height - y) : border + (y - border) % (piece.height() - 2 * border);
      for (int x = 0; x < width; x++) {
        int sx = x < border ? x : x >= width - border ? piece.width() - (width - x) : border + (x - border) % (piece.width() - 2 * border);
        out.set(x, y, piece.get(sx, sy));
      }
    }
    return out;
  }

  private static Raster tint(Raster art, int colour) {
    if ((colour & 0xFFFFFF) == 0xFFFFFF) {
      return art;
    }
    Raster out = new Raster(art.width(), art.height());
    for (int y = 0; y < art.height(); y++) {
      for (int x = 0; x < art.width(); x++) {
        int argb = art.get(x, y);
        int r = ((argb >> 16) & 0xFF) * ((colour >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((colour >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (colour & 0xFF) / 255;
        out.set(x, y, (argb & 0xFF000000) | r << 16 | g << 8 | b);
      }
    }
    return out;
  }

  private static int advance(Raster glyph) {
    for (int x = glyph.width() - 1; x >= 0; x--) {
      for (int y = 0; y < glyph.height(); y++) {
        if ((glyph.get(x, y) >>> 24) != 0) {
          return x + 2;
        }
      }
    }
    return 1;
  }

  private static void paste(Raster canvas, Raster art, int x, int y) {
    for (int row = 0; row < art.height(); row++) {
      for (int column = 0; column < art.width(); column++) {
        int px = x - LEFT + column;
        int py = y - TOP + row;
        if (px >= 0 && py >= 0 && px < canvas.width() && py < canvas.height()) {
          canvas.blend(px, py, art.get(column, row));
        }
      }
    }
  }

  private static void fill(Raster canvas, int x, int y, int width, int height, int argb) {
    for (int row = Math.max(0, y); row < Math.min(canvas.height(), y + height); row++) {
      for (int column = Math.max(0, x); column < Math.min(canvas.width(), x + width); column++) {
        canvas.blend(column, row, argb);
      }
    }
  }
}
