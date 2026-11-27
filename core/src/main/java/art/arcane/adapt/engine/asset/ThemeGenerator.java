package art.arcane.adapt.engine.asset;

import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A plain page theme for a skill that has none, in the authored kits' format: a palette derived from the skill's
 * colour, a small set of dressing pieces, a designed top and foot band with lamps and an orb, all drawn here into one
 * atlas. The skill's icon item picks between an organic and a crystal set. The same look always gives the same bytes.
 */
public final class ThemeGenerator {
  private static final int ATLAS_WIDTH = 128;
  private static final int GAP = 1;
  private static final List<String> ORGANIC = List.of("sapling", "seed", "flower", "leaves", "wheat", "log", "wood", "vine",
      "mushroom", "bone", "egg", "fish", "carrot", "potato", "berry", "apple", "lead", "saddle", "bucket", "kelp", "moss",
      "bamboo", "cactus", "sugar", "hay", "tulip", "poppy", "dandelion", "orchid", "lily", "fern", "grass");

  private ThemeGenerator() {
  }

  public static ThemeAssets generate(SkillLook look) {
    Map<Character, Integer> palette = palette(look.rgb());
    boolean organic = organic(look.icon());
    Random random = new Random(seed(look));
    Map<String, String[]> art = new LinkedHashMap<>();
    JsonObject dress = dress(art, organic);
    put(art, "mat", MAT);
    put(art, "matT", MAT_SIDE);
    put(art, "div", DIVIDER);
    JsonObject scene = foot(art, random, organic);
    JsonObject top = top(art);

    Map<String, int[]> rects = new LinkedHashMap<>();
    int height = pack(art, rects);
    int[] pixels = new int[ATLAS_WIDTH * height];
    for (Map.Entry<String, String[]> entry : art.entrySet()) {
      int[] rect = rects.get(entry.getKey());
      paint(pixels, rect[0], rect[1], entry.getValue(), palette);
    }
    byte[] atlas = PngImages.encode(ATLAS_WIDTH, height, pixels);

    JsonObject kit = new JsonObject();
    String skill = Id.ADAPT.equals(look.skill().namespace()) ? look.skill().path() : look.skill().toString();
    kit.addProperty("skill", skill);
    kit.addProperty("seed", look.skill().path());
    kit.addProperty("concept", "generated " + (organic ? "grove" : "crystal"));
    kit.add("atlas", ints(ATLAS_WIDTH, height));
    JsonObject roles = new JsonObject();
    for (Map.Entry<Character, Integer> role : palette.entrySet()) {
      roles.addProperty(String.valueOf(role.getKey()), hex(role.getValue()));
    }
    kit.add("palette", roles);
    kit.addProperty("solid", true);
    kit.addProperty("footBand", false);
    kit.add("hem", null);
    kit.addProperty("cloth", false);
    kit.add("band", null);
    JsonObject move = new JsonObject();
    move.addProperty("hang", "sway");
    move.addProperty("side", organic ? "sway" : "still");
    kit.add("move", move);
    JsonArray emit = new JsonArray();
    emit.add(organic ? "drift" : "rise");
    emit.add(1);
    kit.add("emit", emit);
    kit.addProperty("ring", false);
    kit.addProperty("solo", false);
    kit.addProperty("noflip", false);
    kit.addProperty("dens", 1.0);
    JsonObject life = new JsonObject();
    life.add("flyer", null);
    life.addProperty("role", "");
    life.addProperty("flyers", 0);
    life.add("hopper", null);
    life.addProperty("glints", 2);
    kit.add("life", life);
    JsonObject sprites = new JsonObject();
    for (Map.Entry<String, int[]> rect : rects.entrySet()) {
      sprites.add(rect.getKey(), ints(rect.getValue()));
    }
    kit.add("sprites", sprites);
    kit.add("dress", dress);
    kit.add("scene", scene);
    kit.add("top", top);
    byte[] json = kit.toString().getBytes(StandardCharsets.UTF_8);
    return new ThemeAssets(look.skill(), json, atlas, true, AssetHashes.sha1(json, atlas));
  }

  static boolean organic(Id icon) {
    for (String word : ORGANIC) {
      if (icon.path().contains(word)) {
        return true;
      }
    }
    return false;
  }

  private static long seed(SkillLook look) {
    long hash = 0xcbf29ce484222325L;
    for (byte value : (look.skill() + "#" + Integer.toHexString(look.rgb()) + "#" + look.icon()).getBytes(StandardCharsets.UTF_8)) {
      hash ^= value & 0xFF;
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  // Palette

  static Map<Character, Integer> palette(int rgb) {
    double[] hsl = hsl(rgb);
    double h = hsl[0];
    double s = hsl[1];
    Map<Character, Integer> roles = new LinkedHashMap<>();
    roles.put('1', rgb(h, s * 0.65, 0.22));
    roles.put('2', rgb(h, s * 0.65, 0.36));
    roles.put('3', rgb(h, s * 0.7, 0.52));
    roles.put('4', rgb(h, s * 0.35, 0.82));
    roles.put('k', rgb(h, Math.min(s, 0.3), 0.08));
    roles.put('w', rgb(h, Math.min(s, 0.35), 0.15));
    roles.put('a', rgb(h, s * 0.22, 0.2));
    roles.put('b', rgb(h, s * 0.22, 0.36));
    roles.put('c', rgb(h, s * 0.22, 0.56));
    roles.put('p', rgb);
    roles.put('q', rgb(h, s * 0.45, 0.86));
    roles.put('A', rgb);
    roles.put('B', roles.get('q'));
    roles.put('S', mix(rgb, roles.get('k'), 0.2));
    roles.put('T', mix(roles.get('q'), roles.get('k'), 0.2));
    roles.put('F', roles.get('c'));
    return roles;
  }

  private static double[] hsl(int rgb) {
    double r = (rgb >> 16 & 0xFF) / 255.0;
    double g = (rgb >> 8 & 0xFF) / 255.0;
    double b = (rgb & 0xFF) / 255.0;
    double max = Math.max(r, Math.max(g, b));
    double min = Math.min(r, Math.min(g, b));
    double l = (max + min) / 2;
    if (max == min) {
      return new double[]{0, 0, l};
    }
    double d = max - min;
    double s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    double h;
    if (max == r) {
      h = (g - b) / d + (g < b ? 6 : 0);
    } else if (max == g) {
      h = (b - r) / d + 2;
    } else {
      h = (r - g) / d + 4;
    }
    return new double[]{h / 6, s, l};
  }

  private static int rgb(double h, double s, double l) {
    s = Math.max(0, Math.min(1, s));
    if (s == 0) {
      int grey = (int) Math.round(l * 255);
      return grey << 16 | grey << 8 | grey;
    }
    double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    double p = 2 * l - q;
    int r = (int) Math.round(channel(p, q, h + 1.0 / 3) * 255);
    int g = (int) Math.round(channel(p, q, h) * 255);
    int b = (int) Math.round(channel(p, q, h - 1.0 / 3) * 255);
    return r << 16 | g << 8 | b;
  }

  private static double channel(double p, double q, double t) {
    if (t < 0) {
      t += 1;
    }
    if (t > 1) {
      t -= 1;
    }
    if (t < 1.0 / 6) {
      return p + (q - p) * 6 * t;
    }
    if (t < 0.5) {
      return q;
    }
    if (t < 2.0 / 3) {
      return p + (q - p) * (2.0 / 3 - t) * 6;
    }
    return p;
  }

  private static int mix(int from, int to, double t) {
    int r = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
    int g = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
    int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    return r << 16 | g << 8 | b;
  }

  private static String hex(int rgb) {
    return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
  }

  // Art, one role character per pixel, '.' clear

  private static final String[] MAT = {
      "kkkkkkkk",
      "bcbbbcbb",
      "bbbbbbbb",
      "aaaaaaaa"};
  private static final String[] MAT_SIDE = {
      "kbba",
      "kcba",
      "kbba",
      "kbba",
      "kbba",
      "kbca",
      "kbba",
      "kbba"};
  private static final String[] DIVIDER = {
      "aaaaaaaa",
      "ccbcccbc",
      "aaaaaaaa"};
  private static final String[] PENNANT = {
      "k....",
      "kpp..",
      "kpqp.",
      "kpppk",
      "kpp..",
      "kp...",
      "k....",
      "k....",
      "k....",
      "a...."};
  private static final String[] CHAIN = {
      ".b.",
      "b.b",
      ".b.",
      ".c.",
      "c.c",
      ".c.",
      "kpk",
      ".k."};
  private static final String[] TASSEL = {
      "..a..",
      "..a..",
      ".kpk.",
      "kpqpk",
      ".kpk.",
      ".333.",
      "3.3.3"};
  private static final String[] CRYSTAL = {
      "..k...",
      ".kqk..",
      ".kpk..",
      "kpqpk.",
      "kpppk.",
      "kpqpkk",
      "kpppkq",
      ".kpkkp",
      ".kpkkp",
      "kbpkk.",
      "kbbbk.",
      "kbcbk.",
      ".kbk..",
      "..k..."};
  private static final String[] VINE = {
      "..3...",
      ".323..",
      "..2...",
      "..2.3.",
      "..223.",
      ".32...",
      "322...",
      "..2...",
      "..23..",
      "..2233",
      ".12...",
      "312...",
      ".11...",
      "..1..."};
  private static final String[] GEMS = {
      "...k...",
      "..kqk..",
      ".kpqpk.",
      "kpppppk",
      ".kpppk.",
      "..kpk..",
      "...k..."};
  private static final String[] LEAVES = {
      "...3...",
      "..333..",
      ".32323.",
      "3322233",
      ".32223.",
      "..121..",
      "...1..."};
  private static final String[] SPARK = {".q.", "qpq", ".q."};
  private static final String[] DOT = {"pq", "qp"};
  private static final String[] SPECK = {"q"};
  private static final String[] RAIL = {
      "kkkkkkkkkkkkkkkkkkkkkkkk",
      "bcbbcbbcbbcbbcbbcbbcbbcb",
      "bbpbbbbbpbbbbbpbbbbbpbbb",
      "aaaaaaaaaaaaaaaaaaaaaaaa",
      "kkkkkkkkkkkkkkkkkkkkkkkk"};
  private static final String[] HOOK = {
      "..........kk.",
      "kkkkkkkkkkpqk",
      "bcbbcbbcbbkpk",
      "aaaaaaaaaakkk",
      "kkkkkkkkkk..."};
  private static final String[][] LAMP = {{
      "..kkk..",
      ".kpqpk.",
      ".kqqqk.",
      ".kpqpk.",
      "..kkk..",
      "...a...",
      "...a...",
      "...b...",
      "...a...",
      "...a...",
      "...b...",
      "...a...",
      "..aaa..",
      ".kbbbk.",
      ".kbcbk.",
      "kkkkkkk"}, {
      "..kkk..",
      ".kqpqk.",
      ".kpppk.",
      ".kqpqk.",
      "..kkk..",
      "...a...",
      "...a...",
      "...b...",
      "...a...",
      "...a...",
      "...b...",
      "...a...",
      "..aaa..",
      ".kbbbk.",
      ".kbcbk.",
      "kkkkkkk"}};
  private static final String[][] ORB = {{
      ".kkk.",
      "kpqpk",
      "kqqqk",
      "kpqpk",
      ".kkk."}, {
      ".kkk.",
      "kqpqk",
      "kpppk",
      "kqpqk",
      ".kkk."}};
  private static final String[] FINIAL = {
      "..k..",
      ".kqk.",
      ".kpk.",
      "kpqpk",
      ".kpk.",
      "..b..",
      "..b..",
      ".kbk.",
      "kbcbk"};

  private static JsonObject dress(Map<String, String[]> art, boolean organic) {
    JsonObject slots = new JsonObject();
    for (String slot : List.of("top", "hang", "side", "foot", "corner", "pile", "bit", "reach", "hook", "trim")) {
      List<String[]> pieces = switch (slot) {
        case "hang" -> List.of(PENNANT, CHAIN, TASSEL);
        case "side" -> List.of(organic ? VINE : CRYSTAL, organic ? LEAVES : GEMS);
        case "bit" -> List.of(SPARK, DOT, SPECK);
        case "reach" -> List.<String[]>of(RAIL);
        case "hook" -> List.<String[]>of(HOOK);
        default -> List.of();
      };
      JsonArray list = new JsonArray();
      for (int index = 0; index < pieces.size(); index++) {
        String[] rows = pieces.get(index);
        put(art, "d/" + slot + index + "/0", rows);
        JsonObject entry = new JsonObject();
        entry.addProperty("w", rows[0].length());
        entry.addProperty("h", rows.length);
        entry.addProperty("frames", 1);
        entry.add("mask", mask(List.<String[]>of(rows)));
        list.add(entry);
      }
      slots.add(slot, list);
    }
    return slots;
  }

  private static JsonObject foot(Map<String, String[]> art, Random random, boolean organic) {
    for (int variant = 0; variant < 3; variant++) {
      put(art, "sg/" + variant, groundTile(random, organic));
    }
    String[] start = new String[8];
    String[] fill = new String[8];
    for (int y = 0; y < 8; y++) {
      char soil = y == 0 ? '3' : y < 3 ? '2' : '1';
      start[y] = "k" + soil;
      fill[y] = String.valueOf(soil);
    }
    caps(art, "sg/", start, fill);
    JsonObject sprites = new JsonObject();
    sprites.add("lamp", castSprite(art, "sc/lamp", LAMP, 3, 16, "glow", 2));
    sprites.add("orb", castSprite(art, "sc/orb", ORB, 2, 5, "glow", 3));
    JsonObject spots = new JsonObject();
    spots.add("mid", doubles(0.5, 0));
    JsonArray cast = new JsonArray();
    cast.add(corner("lampA", "lamp", "start", "glow"));
    cast.add(corner("lampB", "lamp", "end", "glow"));
    JsonObject orb = new JsonObject();
    orb.addProperty("id", "orb");
    orb.addProperty("sprite", "orb");
    orb.addProperty("at", "mid");
    JsonArray steps = new JsonArray();
    steps.add(step(3, "glow", 5, 8));
    steps.add(step(3, "glow", 8, 5));
    orb.add("steps", steps);
    cast.add(orb);
    return scene(ground(8, 2), sprites, spots, cast);
  }

  private static JsonObject top(Map<String, String[]> art) {
    for (int variant = 0; variant < 3; variant++) {
      put(art, "tg/" + variant, bannerTile(variant));
    }
    String[] start = new String[12];
    String[] fill = new String[12];
    String[] tile = bannerTile(2);
    for (int y = 0; y < 12; y++) {
      boolean cloth = y < 8;
      start[y] = cloth ? (y == 0 || y == 7 ? "kkk" : "kbb") : "...";
      fill[y] = String.valueOf(tile[y].charAt(0));
    }
    caps(art, "tg/", start, fill);
    JsonObject sprites = new JsonObject();
    sprites.add("finial", castSprite(art, "tc/finial", new String[][]{FINIAL}, 2, 9, "still", 0));
    JsonArray cast = new JsonArray();
    cast.add(corner("finialA", "finial", "start", "still"));
    cast.add(corner("finialB", "finial", "end", "still"));
    return scene(ground(12, 3), sprites, new JsonObject(), cast);
  }

  private static String[] groundTile(Random random, boolean organic) {
    char[][] rows = new char[8][16];
    for (int y = 0; y < 8; y++) {
      for (int x = 0; x < 16; x++) {
        rows[y][x] = y == 0 ? (random.next(4) == 0 ? '.' : '3') : y < 3 ? '2' : '1';
      }
    }
    for (int speck = 0; speck < 3; speck++) {
      int x = random.next(14) + 1;
      int y = random.next(4) + 3;
      rows[y][x] = speck == 0 ? 'w' : '2';
    }
    int tuft = random.next(12) + 2;
    if (organic) {
      rows[0][tuft] = '3';
      rows[1][tuft] = '3';
    } else {
      rows[1][tuft] = 'c';
      rows[2][tuft] = 'b';
    }
    String[] tile = new String[8];
    for (int y = 0; y < 8; y++) {
      tile[y] = new String(rows[y]);
    }
    return tile;
  }

  private static String[] bannerTile(int variant) {
    String[] tile = new String[12];
    for (int y = 0; y < 12; y++) {
      StringBuilder row = new StringBuilder(16);
      for (int x = 0; x < 16; x++) {
        char role;
        if (y == 0 || y == 7) {
          role = 'k';
        } else if (y < 7) {
          int reach = y == 3 || y == 4 ? 2 : y == 2 || y == 5 ? 1 : 0;
          boolean emblem = variant == 0 && reach > 0 && x >= 8 - reach && x < 8 + reach;
          boolean stud = variant == 1 && (y == 3 || y == 4) && (x == 7 || x == 8);
          role = emblem ? (reach == 2 && (x == 7 || x == 8) ? 'q' : 'p') : stud ? 'q' : y == 1 && x % 5 == 2 ? 'c' : y == 6 ? 'a' : 'b';
        } else {
          int column = x % 4;
          role = column != 1 ? '.' : y == 11 ? 'p' : 'b';
        }
        row.append(role);
      }
      tile[y] = row.toString();
    }
    return tile;
  }

  private static void caps(Map<String, String[]> art, String prefix, String[] start, String[] fill) {
    put(art, prefix + "start", start);
    put(art, prefix + "fill", fill);
  }

  private static JsonObject castSprite(Map<String, String[]> art, String name, String[][] frames, int anchorX, int anchorY,
                                       String clip, double fps) {
    List<String[]> all = new ArrayList<>();
    int[] order = new int[frames.length];
    for (int frame = 0; frame < frames.length; frame++) {
      put(art, name + "/" + frame, frames[frame]);
      all.add(frames[frame]);
      order[frame] = frame;
    }
    JsonObject entry = new JsonObject();
    entry.addProperty("w", frames[0][0].length());
    entry.addProperty("h", frames[0].length);
    entry.addProperty("ax", anchorX);
    entry.addProperty("ay", anchorY);
    entry.addProperty("frames", frames.length);
    entry.add("pts", new JsonObject());
    entry.add("mask", mask(all));
    JsonObject clips = new JsonObject();
    JsonObject played = new JsonObject();
    played.add("f", ints(order));
    played.addProperty("fps", fps);
    played.addProperty("once", false);
    clips.add(clip, played);
    entry.add("clips", clips);
    return entry;
  }

  private static JsonObject corner(String id, String sprite, String corner, String clip) {
    JsonObject element = new JsonObject();
    element.addProperty("id", id);
    element.addProperty("sprite", sprite);
    element.addProperty("corner", corner);
    JsonArray steps = new JsonArray();
    JsonArray step = new JsonArray();
    step.add(6);
    step.add(clip);
    steps.add(step);
    element.add("steps", steps);
    return element;
  }

  private static JsonArray step(double seconds, String clip, int liftFrom, int liftTo) {
    JsonArray step = new JsonArray();
    step.add(seconds);
    step.add(clip);
    JsonObject options = new JsonObject();
    options.add("y", ints(liftFrom, liftTo));
    options.addProperty("ease", "smooth");
    step.add(options);
    return step;
  }

  private static JsonObject ground(int height, int capWidth) {
    JsonObject ground = new JsonObject();
    ground.addProperty("h", height);
    ground.addProperty("tw", 16);
    ground.addProperty("sw", capWidth);
    ground.addProperty("ew", capWidth);
    ground.addProperty("tiles", 3);
    ground.addProperty("past", 0);
    ground.add("weights", ints(1, 1, 1));
    ground.addProperty("frames", 1);
    ground.addProperty("fps", 0);
    ground.addProperty("legacy", false);
    return ground;
  }

  private static JsonObject scene(JsonObject ground, JsonObject sprites, JsonObject spots, JsonArray cast) {
    JsonObject scene = new JsonObject();
    scene.addProperty("period", 6);
    scene.addProperty("rest", 2);
    scene.add("ground", ground);
    scene.add("sprites", sprites);
    scene.add("spots", spots);
    scene.add("cast", cast);
    return scene;
  }

  /** Adds a sprite and its mirror ({@code name + "f"}); a band strip's end is its start mirrored. */
  private static void put(Map<String, String[]> art, String name, String[] rows) {
    String[] mirrored = mirror(rows);
    art.put(name, rows);
    art.put(name + "f", mirrored);
    if (name.endsWith("/start")) {
      String prefix = name.substring(0, name.length() - "start".length());
      art.put(prefix + "end", mirrored);
      art.put(prefix + "endf", rows);
    }
  }

  private static String[] mirror(String[] rows) {
    String[] mirrored = new String[rows.length];
    for (int y = 0; y < rows.length; y++) {
      mirrored[y] = new StringBuilder(rows[y]).reverse().toString();
    }
    return mirrored;
  }

  private static JsonArray mask(List<String[]> frames) {
    JsonArray mask = new JsonArray();
    String[] first = frames.get(0);
    for (int y = 0; y < first.length; y++) {
      StringBuilder row = new StringBuilder(first[y].length());
      for (int x = 0; x < first[y].length(); x++) {
        boolean opaque = false;
        for (String[] frame : frames) {
          opaque |= frame[y].charAt(x) != '.';
        }
        row.append(opaque ? '#' : '.');
      }
      mask.add(row.toString());
    }
    return mask;
  }

  /** Shelf-packs every sprite in insertion order into the atlas width; returns the atlas height. */
  private static int pack(Map<String, String[]> art, Map<String, int[]> rects) {
    int x = 0;
    int y = 0;
    int shelf = 0;
    for (Map.Entry<String, String[]> entry : art.entrySet()) {
      String[] rows = entry.getValue();
      int width = rows[0].length();
      int height = rows.length;
      if (x + width > ATLAS_WIDTH) {
        x = 0;
        y += shelf + GAP;
        shelf = 0;
      }
      rects.put(entry.getKey(), new int[]{x, y, width, height});
      x += width + GAP;
      shelf = Math.max(shelf, height);
    }
    int used = y + shelf;
    int height = 16;
    while (height < used) {
      height *= 2;
    }
    return height;
  }

  private static void paint(int[] pixels, int left, int top, String[] rows, Map<Character, Integer> palette) {
    for (int y = 0; y < rows.length; y++) {
      for (int x = 0; x < rows[y].length(); x++) {
        char role = rows[y].charAt(x);
        if (role == '.') {
          continue;
        }
        Integer color = palette.get(role);
        if (color == null) {
          throw new IllegalStateException("no colour for role " + role);
        }
        pixels[(top + y) * ATLAS_WIDTH + left + x] = 0xFF000000 | color;
      }
    }
  }

  private static JsonArray ints(int... values) {
    JsonArray array = new JsonArray();
    for (int value : values) {
      array.add(new JsonPrimitive(value));
    }
    return array;
  }

  private static JsonArray doubles(double... values) {
    JsonArray array = new JsonArray();
    for (double value : values) {
      array.add(new JsonPrimitive(value));
    }
    return array;
  }

  /** SplitMix64: a fixed stream for a fixed seed on every JVM. */
  private static final class Random {
    private long state;

    Random(long seed) {
      state = seed;
    }

    int next(int bound) {
      state += 0x9E3779B97F4A7C15L;
      long z = state;
      z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
      z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
      z ^= z >>> 31;
      return (int) Math.floorMod(z, (long) bound);
    }
  }
}
