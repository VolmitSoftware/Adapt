package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.surface.PackSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Every piece of art the pack screens ask the baked pack for, by the key the manifest lists it under. The renderer
 * draws with these keys and the bake makes exactly these, so a key either side spells differently fails the pack
 * coverage test instead of leaving a hole on a page.
 * <ul>
 *   <li>Items (manifest {@code art}, by name): drawn in a slot, 16x16 unless an offset says how far the art reaches
 *   past the slot. See {@link #items()}.</li>
 *   <li>Icons (manifest {@code icons}, by sprite id): every 16x16 sprite under {@code gui/}, with a greyed
 *   {@code #dim} copy.</li>
 *   <li>Pages (manifest {@code pages}, by kit and layout): each skill kit's {@code single}, {@code single-partner} and
 *   {@code fallback}; the tome's {@code single}, {@code fallback} and {@code ledger-r1} to
 *   {@code ledger-r6}.</li>
 *   <li>Tooltip styles (manifest {@code kits} and {@code tooltipStyles}): one per skill kit and {@code adapt:tome}.</li>
 *   <li>Paint (manifest {@code paint}, by key): title glyphs at a fixed container y (a glyph's height is baked into its
 *   font), placed at {@code anchor + dx}: slot faces, header chips, marks beside text, bars and rules (see
 *   {@link #paint(PackLayout)}), and each kit's crumb ribbon in three parts (see {@link #ribbonHead}).</li>
 * </ul>
 */
public final class PackArt {
  public static final String TOME = "adapt:tome";
  public static final String SINGLE = "single";
  public static final String PARTNER = "single-partner";
  public static final String FALLBACK = "fallback";
  /** The slot role of a screen's dressing host items; a screen without it shows a kit's page as its still copy. */
  public static final String ART = "art";
  public static final String LEDGER = "ledger-r";
  public static final int LEDGER_ROWS = 6;

  public static final String BLANK = "blank";
  public static final String BACK = "ctl/back";
  public static final String BACK_OFF = "ctl/back_off";
  public static final String PREVIOUS = "ctl/prev";
  public static final String PREVIOUS_OFF = "ctl/prev_off";
  public static final String NEXT = "ctl/next";
  public static final String NEXT_OFF = "ctl/next_off";
  public static final String MORE = "ctl/more";
  public static final String CHECK = "ctl/check";
  public static final String CHECK_ON = "ctl/check_on";
  public static final String LOCK_OFF = "ctl/lock_off";
  /** The wine wash over an armed button's slots, blinking every 6 ticks. */
  public static final String ARMED = "ctl/armed";
  /** The design's foot-bar faces ({@code ctl13/<control>}): 13 tall, so the foot scene under the hotbar row stays clear. */
  public static final String FOOT_FACE_ART = "ctl13/";
  public static final int FOOT_FACE_HEIGHT = 13;
  private static final List<String> FOOT_FACES = List.of("back", "back_off", "prev", "prev_off", "next", "next_off", "tab_levels",
      "tab_levels_on", "tab_list", "tab_list_on", "tab_recipes", "tab_recipes_on", "tab_settings", "tab_settings_on");
  /** The seal mark resting: four glint frames of 4 ticks, then 64 ticks still. */
  public static final String SEAL_IDLE = "seal/mark_idle";
  /** The seal mark armed: its glint frames every 3 ticks. */
  public static final String SEAL_ARMED = "seal/mark_armed";

  public static final String KNOWLEDGE_SPRITE = "adapt:gui/mark/knowledge";
  public static final String POWER_SPRITE = "adapt:gui/mark/power";
  public static final String SWAP_SPRITE = "adapt:gui/mark/swap";
  /** The icon id suffix of an icon's greyed copy. */
  public static final String DIM = PackManifest.DIM;

  public static final String BUTTON = "button";
  public static final String BUTTON_PRIMARY = "button_primary";
  public static final String BUTTON_DESTRUCTIVE = "button_destructive";
  public static final String FIELD = "field";
  public static final String TILE = "tile";
  public static final String TILE_SELECTED = "tile_selected";
  /** Kit pieces drawn under a run of slots, and how many slots wide. */
  public static final Map<String, Integer> FACES = faces();
  /** The header labels of knowledge and power, and the size of the mark drawn before each number. */
  public static final String STAT_KNOWLEDGE = "stat.knowledge";
  public static final String STAT_POWER = "stat.power";
  public static final int STAT_MARK = 8;

  /** The crumb ribbon: its top, and the columns of its two ends; the middle column between them repeats. */
  public static final int RIBBON_Y = 1;
  public static final int RIBBON_HEAD = 6;
  public static final int RIBBON_TAIL = 10;
  /** Ribbon width past the crumbs' text: four before it, eight after. */
  public static final int RIBBON_LEAD = 4;
  public static final int RIBBON_PAD = 12;

  /** Bar roles drawn with an outlined track under the fill; every other bar is a rule, one colour across. */
  public static final String XP = "xp";
  /**
   * The item that draws a stacked sprite: a composite of up to three layers, each the sprite its custom model data string
   * at that index names, tinted by the colour at that index (26.x; see BAKE-NEEDS for the bake side).
   */
  public static final String STACK = "stack";
  /** The identity XP bar drawn under the level alone, when the skill's name does not fit beside the icon. */
  public static final String XP_ALONE = "xp.alone";
  /** The identity XP bar under the level at the right of a skill name set on two lines. */
  public static final String XP_SIDE = "xp.side";
  /** Rules repeated beside every slot of a role, relative to that slot. */
  public static final Map<String, String> ROW_RULES = Map.of("pref.rule", "pref.rows");
  /** Labels repeated beside every slot of a role, relative to that slot. */
  public static final Map<String, String> ROW_TEXT = Map.of("pref.label", "pref.rows", "pref.label.single", "pref.rows",
      "pref.value", "pref.rows");
  /** The font's line height, the pitch of labels that set none. */
  public static final int LINE_HEIGHT = 9;
  public static final int OUTLINE = 0xFF896C48;
  public static final int TRACK = 0xFFDCC9A0;
  /** The plain paper of every kit's page. */
  public static final int PAPER = 0xFFEDE1C6;
  /** Bars and rules are built from runs of these widths. */
  public static final int SEGMENT_POWERS = 8;

  public static final List<String> PIP_STATES = List.of("empty", "learned", "kept", "target", "remove");
  public static final int PIP_LEVELS = 10;
  public static final String PIP_LEARNED = "learned";
  public static final List<String> SEAL_PARTS = List.of("l", "m", "r", "c");
  private static final List<String> TABS = List.of("levels", "recipes", "settings", "kept", "list");

  /** Container y of the top of each slot row: six chest rows, three inventory rows, the hotbar. */
  public static final List<Integer> ROWS = List.of(18, 36, 54, 72, 90, 108, 139, 157, 175, 197);
  /** A list row's box: the light wash under its count, icon and name; its role among a card's bars. */
  public static final String BOX = "box";
  public static final int SHADE = 0x165A4430;
  /** How strongly a row's learned share is washed in its skill's ink: the usual alpha, and the one for pale or grey inks. */
  public static final int WASH_ALPHA = 0x40;
  public static final int WASH_STRONG_ALPHA = 0x70;
  /** The widest a kit's ornament on a maxed row may be, and the paper kept between it and the box's end and the name. */
  public static final int ORNAMENT_WIDTH = 7;
  public static final int ORNAMENT_HEIGHT = 13;
  public static final int ORNAMENT_GAP = 2;

  private PackArt() {
  }

  /** How the bake makes a piece of paint. */
  public enum Kind {
    /** A kit piece ({@code source} is its sprite id) nine-sliced to the size. */
    NINE,
    /** A 16x16 sprite ({@code source} is its id). */
    SPRITE,
    /** A piece of the design's art folder ({@code source} is its name). */
    ART,
    /** The arrow between a recipe grid and its result. */
    ARROW,
    /** A bar's track: outline above and below the track colour. */
    TRACK,
    /** One outline column closing a bar's track at either end. */
    CAP,
    /** White, tinted by the run colour: a bar's fill or a rule. */
    FILL,
    /** The row wash colour {@link #SHADE} across the piece. */
    SHADE,
    /** White at {@link #WASH_ALPHA} ({@code source} "light") or {@link #WASH_STRONG_ALPHA} ("strong"), tinted by the run colour. */
    WASH,
    /** White dots on every other pixel of the top and bottom rows ({@code source} "rows") or of the one column ("column"). */
    DOTS
  }

  /** A fixed item: its key, its art's size and offset from the slot's corner, and where the bake takes it from. */
  public record Item(String key, int width, int height, int offsetX, int offsetY, String source) {
  }

  /** A paint glyph: its key, how it is made, its size and container y, and its left edge relative to its anchor x. */
  public record Paint(String key, Kind kind, String source, int width, int height, int y, int dx) {
  }

  public static String tab(String id, boolean on) {
    return "ctl/tab_" + id + (on ? "_on" : "");
  }

  public static String pip(String state, int level) {
    return "pip/" + state + "_" + level;
  }

  /** The pip with the previewed-level ring round it. */
  public static String pipRing(String state, int level) {
    return pip(state, level) + "_ring";
  }

  public static String seal(boolean filled, String part) {
    return (filled ? "seal/fill_" : "seal/track_") + part;
  }

  public static String ledger(int rows) {
    return LEDGER + rows;
  }

  /** The face of kit piece {@code piece} under the run of slots starting at a slot whose top is {@code rowY}. */
  public static String face(String piece, int rowY) {
    return "face/" + piece + "@" + rowY;
  }

  /** The knowledge mark beside text on the slot row whose top is {@code rowY}. */
  public static String knowledgeMark(int rowY) {
    return "mark/knowledge@" + rowY;
  }

  /**
   * The face of control item {@code key} (or control sprite id) painted under a hotbar slot whose top is {@code rowY}: the client mirrors the
   * window's hotbar row in its own hotbar, so controls there draw in the title and carry the blank model.
   */
  public static String controlFace(String key, int rowY) {
    return "face/" + key + "@" + rowY;
  }

  /** The small mark of sprite {@code sprite} before a header number whose line top is {@code lineY}. */
  public static String statMark(String sprite, int lineY) {
    return "mark/" + sprite.substring(sprite.lastIndexOf('/') + 1) + ".small@" + lineY;
  }

  /** {@code width} columns of a row box whose top is {@code y}. */
  public static String box(int width, int y) {
    return "box/" + width + "@" + y;
  }

  /** {@code width} columns of the learned wash over a row box whose top is {@code y}, tinted by the run colour. */
  public static String wash(int width, int y, boolean strong) {
    return "wash/" + (strong ? "strong" : "light") + width + "@" + y;
  }

  /** {@code width} columns of an unlearned row box's dotted top and bottom edges, whose top is {@code y}. */
  public static String dots(int width, int y) {
    return "box/dots" + width + "@" + y;
  }

  /** An unlearned row box's dotted side whose top is {@code y}. */
  public static String dotSide(int y) {
    return "box/dotside@" + y;
  }

  /** {@code kit}'s ornament at the end of a maxed row whose slots' top is {@code rowY}, drawn left of its anchor. */
  public static String ornament(String kit, int rowY) {
    return "ornament/" + kit + "@" + rowY;
  }

  /**
   * {@code kit}'s Mutate button: the primary button framed in the kit's colours, a piece {@link #face} draws 3 slots wide
   * on every row of {@link #ROWS} for every skill kit (not the tome).
   */
  public static String mutate(String kit) {
    return "mutate/" + kit;
  }

  /** The arrow pointing at a recipe result slot whose top is {@code rowY}, drawn relative to that slot's x. */
  public static String arrow(int rowY) {
    return "arrow/right@" + rowY;
  }

  /** The copy of page layout {@code layout} with every moving region held still, for screens without host items. */
  public static String still(String layout) {
    return layout + "-still";
  }

  /** The left end of {@code kit}'s crumb ribbon ({@code ns:skill}, or {@link #TOME}). */
  public static String ribbonHead(String kit) {
    return "ribbon/" + kit + "/head";
  }

  /** {@code width} columns of the middle of {@code kit}'s crumb ribbon. */
  public static String ribbonBody(String kit, int width) {
    return "ribbon/" + kit + "/body" + width;
  }

  /** The right end, with its notch, of {@code kit}'s crumb ribbon. */
  public static String ribbonTail(String kit) {
    return "ribbon/" + kit + "/tail";
  }

  public static String track(int width, BarSpec bar) {
    return "bar/track" + width + "@" + bar.y() + "x" + bar.height();
  }

  public static String cap(BarSpec bar) {
    return "bar/cap@" + bar.y() + "x" + bar.height();
  }

  public static String fill(int width, BarSpec bar) {
    return "bar/fill" + width + "@" + bar.y() + "x" + bar.height();
  }

  /** The segment widths, widest first, that add up to {@code width}. */
  public static List<Integer> segments(int width) {
    List<Integer> parts = new ArrayList<>();
    for (int power = SEGMENT_POWERS - 1; power >= 0; power--) {
      int size = 1 << power;
      while (width >= size) {
        parts.add(size);
        width -= size;
      }
    }
    return parts;
  }

  /** Every fixed item the screens can put in a slot. */
  public static List<Item> items() {
    List<Item> items = new ArrayList<>();
    items.add(new Item(BLANK, 16, 16, 0, 0, "rule: an item model that draws nothing"));
    for (String control : List.of("back", "prev", "next", "more", "lock", "reset", "search")) {
      items.add(art("ctl/" + control));
      items.add(art("ctl/" + control + "_off"));
    }
    items.add(art(CHECK));
    items.add(art(CHECK_ON));
    for (String tab : TABS) {
      items.add(art(tab(tab, false)));
      items.add(art(tab(tab, true)));
    }
    items.add(new Item(ARMED, 18, 18, -1, -1, "rule: design animation.armedButton, rgba(178,82,63,0.30) on and off every 6 ticks"));
    for (String state : PIP_STATES) {
      for (int level = 1; level <= PIP_LEVELS; level++) {
        String learned = state.equals(PIP_LEARNED) ? " over art/pip/fill.png tinted by custom model data colour 0" : "";
        items.add(new Item(pip(state, level), 16, 16, 0, 0, "art/" + pip(state, level) + ".png" + learned));
        items.add(new Item(pipRing(state, level), 18, 18, -1, -1, "art/" + pip(state, level) + ".png" + learned
            + ", art/pip/ring.png over it"));
      }
    }
    for (String part : SEAL_PARTS) {
      items.add(new Item(seal(false, part), 18, 18, -1, -1, "art/" + seal(false, part) + ".png"));
      items.add(new Item(seal(true, part), 18, 18, -1, -1, "art/" + seal(true, part) + ".png at 80% alpha, tinted by custom model data colour 0"));
    }
    for (int frame = 0; frame <= 4; frame++) {
      items.add(new Item("seal/mark_" + frame, 22, 22, -3, -3, "art/seal/mark_" + frame + ".png"));
    }
    items.add(new Item(SEAL_IDLE, 22, 22, -3, -3, "rule: design animation.seal.idle over art/seal/mark_0-4.png"));
    items.add(new Item(SEAL_ARMED, 22, 22, -3, -3, "rule: design animation.seal.armed over art/seal/mark_1-4.png"));
    return items;
  }

  private static Item art(String key) {
    return new Item(key, 16, 16, 0, 0, "art/" + key + ".png");
  }

  /** Every paint glyph the screens of {@code layout} can draw. */
  public static List<Paint> paint(PackLayout layout) {
    Map<String, Paint> paint = new LinkedHashMap<>();
    for (int rowY : ROWS) {
      for (Map.Entry<String, Integer> face : FACES.entrySet()) {
        add(paint, face(face.getKey(), face.getValue(), rowY));
      }
      add(paint, new Paint(knowledgeMark(rowY), Kind.SPRITE, KNOWLEDGE_SPRITE, 16, 16, rowY, 0));
      add(paint, new Paint(arrow(rowY), Kind.ARROW, "rule: design recipe arrow", 19, 11, rowY + 3, -30));
    }
    for (String id : new TreeSet<>(layout.screens().keySet())) {
      for (Card card : layout.screens().get(id).cards()) {
        int iconY = layout.grid().y(card.icon());
        BarSpec box = card.bar(BOX);
        for (int power = 0; box != null && power < SEGMENT_POWERS; power++) {
          int width = 1 << power;
          add(paint, new Paint(box(width, box.y()), Kind.SHADE, "rule: design row wash", width, box.height(), box.y(), 0));
          add(paint, new Paint(wash(width, box.y(), false), Kind.WASH, "light", width, box.height(), box.y(), 0));
          add(paint, new Paint(wash(width, box.y(), true), Kind.WASH, "strong", width, box.height(), box.y(), 0));
          add(paint, new Paint(dots(width, box.y()), Kind.DOTS, "rows", width, box.height(), box.y(), 0));
        }
        if (box != null) {
          add(paint, new Paint(dotSide(box.y()), Kind.DOTS, "column", 1, box.height(), box.y(), 0));
        }
        add(paint, face(TILE, FACES.get(TILE), iconY));
        add(paint, face(TILE_SELECTED, FACES.get(TILE_SELECTED), iconY));
      }
    }
    int hotbar = ROWS.get(ROWS.size() - 1);
    for (Item item : items()) {
      String control = item.key().substring(item.key().indexOf('/') + 1);
      if (item.key().startsWith("ctl/") && FOOT_FACES.contains(control)) {
        add(paint, new Paint(controlFace(item.key(), hotbar), Kind.ART, FOOT_FACE_ART + control, 16, FOOT_FACE_HEIGHT, hotbar, 0));
      } else if (item.key().startsWith("ctl/") && !item.key().equals(ARMED)) {
        add(paint, new Paint(controlFace(item.key(), hotbar), Kind.ART, item.key(), 16, 16, hotbar, 0));
      }
    }
    for (ScreenLayout screen : layout.screens().values()) {
      for (List<String> stat : List.of(List.of(STAT_KNOWLEDGE, KNOWLEDGE_SPRITE), List.of(STAT_POWER, POWER_SPRITE))) {
        TextSpec spec = screen.text(stat.get(0));
        if (spec != null) {
          add(paint, new Paint(statMark(stat.get(1), spec.y()), Kind.SPRITE, stat.get(1), STAT_MARK, STAT_MARK, spec.y(), 0));
        }
      }
    }
    for (BarSpec bar : bars(layout, false)) {
      for (int power = 0; power < SEGMENT_POWERS; power++) {
        int width = 1 << power;
        add(paint, new Paint(fill(width, bar), Kind.FILL, "rule: white", width, bar.height(), bar.y(), 0));
      }
    }
    for (BarSpec bar : bars(layout, true)) {
      for (int power = 0; power < SEGMENT_POWERS; power++) {
        int width = 1 << power;
        add(paint, new Paint(track(width, bar), Kind.TRACK, "rule: outline and track colours", width, bar.height() + 2, bar.y() - 1, 0));
      }
      add(paint, new Paint(cap(bar), Kind.CAP, "rule: outline colour", 1, bar.height(), bar.y(), 0));
    }
    return new ArrayList<>(paint.values());
  }

  /**
   * Every container y a label of {@code layout} can put a line top at: each label's lines, every centring of a label
   * of up to two lines, and labels that follow another's first line (a row's level beside its name) anywhere in the
   * first half of a line below the label's top.
   */
  public static List<Integer> textRows(PackLayout layout) {
    TreeSet<Integer> rows = new TreeSet<>();
    for (ScreenLayout screen : layout.screens().values()) {
      for (Map.Entry<String, TextSpec> entry : screen.text().entrySet()) {
        String repeated = ROW_TEXT.get(entry.getKey());
        if (repeated == null) {
          rows(rows, entry.getValue());
        } else {
          for (PackSlot slot : screen.slots(repeated)) {
            rows(rows, entry.getValue().moved(0, layout.grid().y(slot)));
          }
        }
      }
      for (Card card : withGaps(screen.cards())) {
        card.text().values().forEach((TextSpec text) -> rows(rows, text));
      }
    }
    return new ArrayList<>(rows);
  }

  private static void rows(TreeSet<Integer> rows, TextSpec text) {
    int pitch = text.pitch(LINE_HEIGHT);
    for (int line = 0; line < text.lines(); line++) {
      for (int dy = 0; dy <= pitch / 2; dy++) {
        rows.add(text.y() + (line * pitch) + dy);
      }
    }
  }

  private static Paint face(String piece, int slots, int rowY) {
    return new Paint(face(piece, rowY), Kind.NINE, "adapt:gui/kit/" + piece, 18 * slots, 18, rowY - 1, -1);
  }

  private static void add(Map<String, Paint> paint, Paint piece) {
    paint.putIfAbsent(piece.key(), piece);
  }

  /** One bar per distinct row and height, among the tracked bars or among all bars and rules. */
  private static List<BarSpec> bars(PackLayout layout, boolean tracked) {
    Map<String, BarSpec> found = new LinkedHashMap<>();
    for (String id : new TreeSet<>(layout.screens().keySet())) {
      ScreenLayout screen = layout.screens().get(id);
      for (Map.Entry<String, BarSpec> entry : new TreeMap<>(screen.bars()).entrySet()) {
        String rows = ROW_RULES.get(entry.getKey());
        if (rows != null) {
          for (PackSlot slot : screen.slots(rows)) {
            keep(found, entry.getValue().moved(layout.grid().x(slot), layout.grid().y(slot)), entry.getKey(), tracked);
          }
        } else {
          keep(found, entry.getValue(), entry.getKey(), tracked);
        }
      }
      for (Card card : withGaps(screen.cards())) {
        new TreeMap<>(card.bars()).forEach((String role, BarSpec bar) -> {
          if (role.equals(BOX)) {
            for (BarSpec edge : outline(bar)) {
              keep(found, edge, role, tracked);
            }
            return;
          }
          keep(found, bar, role, tracked);
        });
      }
    }
    return new ArrayList<>(found.values());
  }

  /** The one-pixel outline of row box {@code box} in its colour: its left and right edges, then its top and bottom rows. */
  public static List<BarSpec> outline(BarSpec box) {
    List<BarSpec> edges = new ArrayList<>(border(box));
    edges.add(new BarSpec(box.x(), box.y(), box.width(), 1, box.colour()));
    edges.add(new BarSpec(box.x(), box.y() + box.height() - 1, box.width(), 1, box.colour()));
    return edges;
  }

  /** {@code cards} and the heading place in each gap between two of them that follow on (see {@link Card#between}). */
  private static List<Card> withGaps(List<Card> cards) {
    List<Card> all = new ArrayList<>(cards);
    for (int index = 1; index < cards.size(); index++) {
      if (Card.gap(cards.get(index - 1), cards.get(index))) {
        all.add(Card.between(cards.get(index - 1), cards.get(index)));
      }
    }
    return all;
  }

  /** The left and right one-pixel edges of row box {@code box}, in its colour. */
  public static List<BarSpec> border(BarSpec box) {
    int right = box.x() + box.width() - 1;
    return List.of(new BarSpec(box.x(), box.y(), 1, box.height(), box.colour()), new BarSpec(right, box.y(), 1, box.height(), box.colour()));
  }

  private static void keep(Map<String, BarSpec> found, BarSpec bar, String role, boolean tracked) {
    if (!tracked || role.equals(XP) || role.equals(XP_ALONE) || role.equals(XP_SIDE)) {
      found.putIfAbsent(bar.y() + "x" + bar.height(), bar);
    }
  }

  private static Map<String, Integer> faces() {
    Map<String, Integer> faces = new LinkedHashMap<>();
    for (String piece : List.of(BUTTON, BUTTON_PRIMARY, BUTTON_DESTRUCTIVE, FIELD)) {
      faces.put(piece, 3);
    }
    faces.put(TILE, 1);
    faces.put(TILE_SELECTED, 1);
    return Collections.unmodifiableMap(faces);
  }
}
