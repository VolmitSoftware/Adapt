package art.arcane.adapt.pack.hud;

import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.engine.notification.HudStatusSpec;
import art.arcane.adapt.pack.text.PackFontMetrics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class HudCornerTest {
  private static final Map<String, String> GLYPHS = Map.of("adapt:gui/skill/herbalism", "",
      "adapt:gui/adaptation/agility-air-dash", "");
  private static final IntFunction<String> LEVEL = (int level) -> "Lv " + level;
  private static final IconRef DASH = new IconRef("minecraft:feather", 0, null, "adapt:textures/gui/adaptation/agility-air-dash.png");
  private static final HudXp HERBALISM = new HudXp("herbalism", "Herbalism", "adapt:gui/skill/herbalism", 0x4DB344, 46, 0.5, 35.4);
  private static final HudXp MINING = new HudXp("mining", "Mining", "adapt:gui/skill/mining", 0x808080, 7, 0.0, 2.0);

  /** A glyph the shader draws: its codepoint (or text) and its top left on screen, in GUI pixels from the corner. */
  private record Placed(String what, int x, int y) {
  }

  @Test
  void nothingToShowIsEmpty() {
    assertThat(HudCorner.compose(List.of(), List.of(), List.of(), GLYPHS::get, LEVEL, 0L)).isEmpty();
  }

  @Test
  void skillRowsAndWellsLandWhereTheShaderPutsThem() {
    String line = HudCorner.compose(List.of(new HudCorner.Skill(HERBALISM, false), new HudCorner.Skill(MINING, false)),
        List.of(HudStatusSpec.cooldown("agility", DASH, "Air Dash", 0L, 8_000L)), List.of(), GLYPHS::get, LEVEL, 3_000L);
    List<Placed> placed = place(line);

    int slot = HudGlyphs.PLATE_HEIGHT + 1;
    assertThat(placed).contains(
        new Placed(glyph(HudGlyphs.PLATE_LEFT), HudCorner.MARGIN, HudCorner.MARGIN),
        new Placed(glyph(0xE105 + HudGlyphs.SMALL_ICON_OFFSET), HudCorner.MARGIN + 2, HudCorner.MARGIN + 1),
        new Placed("+35", HudCorner.MARGIN + 15, HudCorner.MARGIN + 2),
        new Placed(glyph(HudGlyphs.PLATE_LEFT), HudCorner.MARGIN, HudCorner.MARGIN + slot),
        new Placed(glyph(HudGlyphs.FALLBACK_ICON + HudGlyphs.SMALL_ICON_OFFSET), HudCorner.MARGIN + 2, HudCorner.MARGIN + slot + 1),
        new Placed(glyph(HudGlyphs.WELL), HudCorner.MARGIN, HudCorner.MARGIN + 2 * slot),
        new Placed(glyph(0xE100), HudCorner.MARGIN + 2, HudCorner.MARGIN + 2 * slot + 2),
        new Placed(glyph(HudGlyphs.WIPE_BASE + 5), HudCorner.MARGIN + 2, HudCorner.MARGIN + 2 * slot + 2));
    Placed right = placed.stream().filter((Placed glyph) -> glyph.what().equals(glyph(HudGlyphs.PLATE_RIGHT))).findFirst().orElseThrow();
    assertThat(right.x() - HudCorner.MARGIN + 1).isEqualTo(HudCorner.width(HERBALISM, "Lv 46"));
  }

  @Test
  void theLineAdvancesNothingSoTheActionBarStartsAtTheScreensCentre() {
    String line = HudCorner.compose(List.of(new HudCorner.Skill(HERBALISM, true)), List.of(),
        List.of(new HudCorner.Notice("<yellow>Saved", PackFontMetrics.vanilla().width("Saved"))), GLYPHS::get, LEVEL, 0L);

    assertThat(advance(line)).isZero();
    Placed saved = place(line).stream().filter((Placed glyph) -> glyph.what().equals("Saved")).findFirst().orElseThrow();
    assertThat(saved.x()).isEqualTo(-PackFontMetrics.vanilla().width("Saved") / 2);
  }

  @Test
  void levelUpInksTheLevelGoldAndNamesAreEscaped() {
    HudXp odd = new HudXp("x", "<b>X", "", 0xFFFFFF, 2, 1.0, 1.0);
    String line = HudCorner.compose(List.of(new HudCorner.Skill(odd, true)), List.of(), List.of(), GLYPHS::get, LEVEL, 0L);

    assertThat(line).contains("\\<b>X").contains("<color:#6b440c>Lv 2").contains(glyph(HudGlyphs.barFill(HudGlyphs.BAR_WIDTH).codePointAt(0)));
  }

  @Test
  void rowsStopAtTheirCaps() {
    List<HudCorner.Skill> skills = new ArrayList<>();
    List<HudStatusSpec> wells = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      skills.add(new HudCorner.Skill(MINING, false));
      wells.add(HudStatusSpec.toggle("stealth", DASH, "Hide", true));
    }
    String line = HudCorner.compose(skills, wells, List.of(), GLYPHS::get, LEVEL, 0L);

    assertThat(line.chars().filter((int c) -> c == HudGlyphs.PLATE_LEFT).count()).isEqualTo(HudCorner.MAX_SKILLS);
    assertThat(line.chars().filter((int c) -> c == HudGlyphs.WELL).count()).isEqualTo(HudCorner.MAX_ABILITIES);
  }

  private static String glyph(int codepoint) {
    return Character.toString(codepoint);
  }

  /** Every visible run of {@code line} where the shader draws it: corner glyphs moved, the rest left at the line's x. */
  private static List<Placed> place(String line) {
    List<Placed> placed = new ArrayList<>();
    walk(line, (String what, Integer pen) -> {
      if (pen < 12_288) {
        placed.add(new Placed(what, pen, 0));
        return;
      }
      int offset = pen - HudCorner.BAND;
      int row = Math.floorDiv(offset + 256, HudCorner.ROW);
      int top = HudCorner.MARGIN + row - HudCorner.ROW_BIAS + 7 - ascent(what.codePointAt(0));
      placed.add(new Placed(what, HudCorner.MARGIN + offset - row * HudCorner.ROW, top));
    });
    return placed;
  }

  private static int advance(String line) {
    return walk(line, (String what, Integer pen) -> {
    });
  }

  /**
   * Calls {@code visit} with each glyph or text run of {@code line} and the pen x it starts at, tags skipped; returns the
   * pen after the line.
   */
  private static int walk(String line, BiConsumer<String, Integer> visit) {
    int pen = 0;
    int index = 0;
    StringBuilder text = new StringBuilder();
    while (index < line.length()) {
      char c = line.charAt(index);
      if (c == '\\' && index + 1 < line.length()) {
        text.append(line.charAt(index + 1));
        index += 2;
        continue;
      }
      if (c == '<') {
        pen = flush(text, pen, visit);
        index = line.indexOf('>', index) + 1;
        continue;
      }
      if (c >= 0xE000 && c <= 0xF8FF) {
        pen = flush(text, pen, visit);
        if (!isShift(c)) {
          visit.accept(String.valueOf(c), pen);
        }
        pen += width(String.valueOf(c));
        index++;
        continue;
      }
      text.append(c);
      index++;
    }
    return flush(text, pen, visit);
  }

  private static int flush(StringBuilder text, int pen, BiConsumer<String, Integer> visit) {
    if (text.isEmpty()) {
      return pen;
    }
    visit.accept(text.toString(), pen);
    int next = pen + PackFontMetrics.vanilla().width(text.toString());
    text.setLength(0);
    return next;
  }

  private static boolean isShift(char c) {
    return c >= HudGlyphs.NEGATIVE_BASE && c < HudGlyphs.NEGATIVE_BASE + HudGlyphs.SHIFT_BITS
        || c >= HudGlyphs.POSITIVE_BASE && c < HudGlyphs.POSITIVE_BASE + HudGlyphs.SHIFT_BITS;
  }

  private static int width(String what) {
    int c = what.codePointAt(0);
    if (c >= HudGlyphs.NEGATIVE_BASE && c < HudGlyphs.NEGATIVE_BASE + HudGlyphs.SHIFT_BITS) {
      return -(1 << (c - HudGlyphs.NEGATIVE_BASE));
    }
    if (c >= HudGlyphs.POSITIVE_BASE && c < HudGlyphs.POSITIVE_BASE + HudGlyphs.SHIFT_BITS) {
      return 1 << (c - HudGlyphs.POSITIVE_BASE);
    }
    if (c == HudGlyphs.PLATE_LEFT || c == HudGlyphs.PLATE_RIGHT) {
      return 2;
    }
    if (c >= HudGlyphs.PLATE_BASE && c < HudGlyphs.PLATE_BASE + HudGlyphs.PLATE_POWERS) {
      return (1 << (c - HudGlyphs.PLATE_BASE)) + 1;
    }
    if (c == HudGlyphs.BAR_TRACK || c >= HudGlyphs.BAR_FILL_BASE && c <= HudGlyphs.BAR_FILL_BASE + HudGlyphs.BAR_WIDTH) {
      return HudGlyphs.BAR_WIDTH + 1;
    }
    if (c == HudGlyphs.WELL) {
      return HudGlyphs.WELL_ADVANCE;
    }
    if (c >= HudGlyphs.FIRST_ICON + HudGlyphs.SMALL_ICON_OFFSET || c == HudGlyphs.FALLBACK_ICON + HudGlyphs.SMALL_ICON_OFFSET) {
      return HudGlyphs.SMALL_ICON_ADVANCE;
    }
    if (c >= 0xE000 && c <= 0xF8FF) {
      return HudGlyphs.GLYPH_ADVANCE;
    }
    return PackFontMetrics.vanilla().width(what);
  }

  private static int ascent(int c) {
    if (c == HudGlyphs.WELL) {
      return HudGlyphs.WELL_ASCENT;
    }
    if (c == HudGlyphs.BAR_TRACK || c >= HudGlyphs.BAR_FILL_BASE && c <= HudGlyphs.BAR_FILL_BASE + HudGlyphs.BAR_WIDTH) {
      return HudGlyphs.BAR_ASCENT;
    }
    if (c == HudGlyphs.PLATE_LEFT || c == HudGlyphs.PLATE_RIGHT || c >= HudGlyphs.PLATE_BASE && c < HudGlyphs.PLATE_BASE + HudGlyphs.PLATE_POWERS) {
      return HudGlyphs.PLATE_ASCENT;
    }
    if (c >= HudGlyphs.FIRST_ICON + HudGlyphs.SMALL_ICON_OFFSET || c == HudGlyphs.FALLBACK_ICON + HudGlyphs.SMALL_ICON_OFFSET) {
      return HudGlyphs.SMALL_ICON_ASCENT;
    }
    if (c >= 0xE000 && c <= 0xF8FF) {
      return HudGlyphs.ICON_ASCENT;
    }
    return 7;
  }
}
