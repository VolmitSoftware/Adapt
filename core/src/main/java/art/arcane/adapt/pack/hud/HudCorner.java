package art.arcane.adapt.pack.hud;

import art.arcane.adapt.engine.notification.HudStatusSpec;
import art.arcane.adapt.pack.manifest.PackSprites;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.pack.text.PackInk;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Composes the action bar line that the pack's text shader lays out in the top left corner, as the client mod's HUD
 * does: one parchment slip per skill earning xp (small icon, the gain in the skill's ink, its name, a thin progress bar
 * and its level), then a row of ability wells with their cooldown wipes and counts. The text notices stay where the
 * action bar always shows text, centred above the hotbar.
 * <p>
 * The action bar centres its line on the screen, so the line's total advance is kept at 0 and every corner glyph is set
 * at {@link #BAND} plus {@link #ROW} times its row ({@code y + }{@link #ROW_BIAS}) plus its x: far right of any screen,
 * where the shader ({@code core/text.vsh} in the pack) finds it and moves it to {@code (MARGIN + x, MARGIN + y)}. A
 * glyph's y is the top of a default-font glyph set there; a bitmap glyph's top sits {@code 7 - ascent} lower.
 */
public final class HudCorner {
  public static final int BAND = 16_384;
  public static final int ROW = 1_024;
  public static final int ROW_BIAS = 64;
  public static final int MARGIN = 4;
  public static final int MAX_SKILLS = 4;
  public static final int MAX_ABILITIES = 5;
  /** Mirrors the client HUD's skill rows: padding, gaps and one slot per row including a 1-pixel gap. */
  static final int PAD = 2;
  static final int GAP = 3;
  static final int SLOT = HudGlyphs.PLATE_HEIGHT + 1;
  static final int WELL_GAP = 2;
  static final int NOTICE_GAP = 8;
  static final int MUTED = 0x5A4430;
  static final int LEVEL_GOLD = 0x6B440C;

  private static final String GLYPHS = "<reset><font:" + HudGlyphs.FONT + "><shadow:#00000000>";
  private static final String INK = "<reset><shadow:#00000000>";
  private static final int TEXT_ASCENT = 7;
  private static final int TEXT_TOP = (HudGlyphs.PLATE_HEIGHT - PackFontMetrics.LINE_HEIGHT) / 2 + 1;

  private HudCorner() {
  }

  /** One skill row: the xp line and whether the skill just levelled up, which inks its level gold. */
  public record Skill(HudXp xp, boolean levelledUp) {
  }

  /** A text notice centred above the hotbar: Adapt markup and its width in GUI pixels. */
  public record Notice(String markup, int width) {
  }

  /**
   * The line for {@code skills}, {@code abilities} and {@code notices}; {@code levelLabel} names a level ("Lv 46"),
   * {@code icons} gives the full-size {@code adapt:hud} glyph of a sprite id or null.
   */
  public static String compose(List<Skill> skills, List<HudStatusSpec> abilities, List<Notice> notices,
                               Function<String, String> icons, IntFunction<String> levelLabel, long nowMillis) {
    Pen pen = new Pen();
    int y = 0;
    int shownSkills = Math.min(MAX_SKILLS, skills.size());
    for (int index = 0; index < shownSkills; index++) {
      skill(pen, skills.get(index), icons, levelLabel, y);
      y += SLOT;
    }
    int shownAbilities = Math.min(MAX_ABILITIES, abilities.size());
    for (int index = 0; index < shownAbilities; index++) {
      ability(pen, abilities.get(index), icons, index * (HudGlyphs.WELL_SIZE + WELL_GAP), y, nowMillis);
    }
    pen.home();
    List<Notice> shown = notices.stream().filter((Notice notice) -> !notice.markup().isBlank()).toList();
    if (!shown.isEmpty()) {
      int total = NOTICE_GAP * (shown.size() - 1);
      for (Notice notice : shown) {
        total += notice.width();
      }
      pen.shiftBy(-total / 2);
      for (int index = 0; index < shown.size(); index++) {
        if (index > 0) {
          pen.shiftBy(NOTICE_GAP);
        }
        pen.out.append("<reset>").append(shown.get(index).markup());
        pen.x += shown.get(index).width();
      }
      pen.home();
    }
    return pen.out.toString();
  }

  /** The row's slip width: padding, icon, gain, name, bar and level with a gap between each. */
  static int width(HudXp xp, String level) {
    PackFontMetrics metrics = PackFontMetrics.vanilla();
    return PAD + HudGlyphs.SMALL_ICON_SIZE + GAP + metrics.width(gain(xp)) + GAP + metrics.width(xp.name()) + GAP
        + HudGlyphs.BAR_WIDTH + GAP + metrics.width(level) + PAD;
  }

  private static void skill(Pen pen, Skill row, Function<String, String> icons, IntFunction<String> levelLabel, int top) {
    HudXp xp = row.xp();
    PackFontMetrics metrics = PackFontMetrics.vanilla();
    String level = levelLabel.apply(xp.level());
    plate(pen, top, width(xp, level));
    String sprite = PackSprites.packId(xp.icon());
    String icon = sprite == null ? null : icons.apply(sprite);
    int x = PAD;
    pen.at(x, top + (HudGlyphs.PLATE_HEIGHT - HudGlyphs.SMALL_ICON_SIZE) / 2, HudGlyphs.SMALL_ICON_ASCENT);
    pen.glyphs(icon == null ? HudGlyphs.small(HudGlyphs.fallbackIcon()) : HudGlyphs.small(icon), HudGlyphs.SMALL_ICON_ADVANCE);
    x += HudGlyphs.SMALL_ICON_SIZE + GAP;
    int ink = PackInk.of(xp.colour());
    String gain = gain(xp);
    pen.at(x, top + TEXT_TOP, TEXT_ASCENT);
    pen.text(ink, gain, metrics.width(gain));
    x += metrics.width(gain) + GAP;
    pen.at(x, top + TEXT_TOP, TEXT_ASCENT);
    pen.text(MUTED, escape(xp.name()), metrics.width(xp.name()));
    x += metrics.width(xp.name()) + GAP;
    int barTop = top + (HudGlyphs.PLATE_HEIGHT - HudGlyphs.BAR_HEIGHT) / 2;
    pen.at(x, barTop, HudGlyphs.BAR_ASCENT);
    pen.glyphs(HudGlyphs.glyph(HudGlyphs.BAR_TRACK), HudGlyphs.BAR_WIDTH + 1);
    int filled = (int) Math.round(xp.progress() * HudGlyphs.BAR_WIDTH);
    if (filled > 0) {
      pen.at(x, barTop, HudGlyphs.BAR_ASCENT);
      pen.out.append(colour(ink));
      pen.glyphs(HudGlyphs.barFill(filled), HudGlyphs.BAR_WIDTH + 1);
    }
    x += HudGlyphs.BAR_WIDTH + GAP;
    pen.at(x, top + TEXT_TOP, TEXT_ASCENT);
    pen.text(row.levelledUp() ? LEVEL_GOLD : MUTED, escape(level), metrics.width(level));
  }

  /** A slip {@code width} wide: its two edges and power-of-two middles laid end to end. */
  private static void plate(Pen pen, int top, int width) {
    pen.at(0, top, HudGlyphs.PLATE_ASCENT);
    pen.glyphs(HudGlyphs.glyph(HudGlyphs.PLATE_LEFT), 2);
    int x = 1;
    int middle = Math.max(0, width - 2);
    for (int power = HudGlyphs.PLATE_POWERS - 1; power >= 0; power--) {
      int piece = 1 << power;
      while (middle >= piece) {
        pen.at(x, top, HudGlyphs.PLATE_ASCENT);
        pen.glyphs(HudGlyphs.plate(power), piece + 1);
        x += piece;
        middle -= piece;
      }
    }
    pen.at(x, top, HudGlyphs.PLATE_ASCENT);
    pen.glyphs(HudGlyphs.glyph(HudGlyphs.PLATE_RIGHT), 2);
  }

  private static void ability(Pen pen, HudStatusSpec spec, Function<String, String> icons, int left, int top, long nowMillis) {
    String sprite = PackSprites.packId(spec.icon().spriteId());
    String icon = sprite == null ? null : icons.apply(sprite);
    pen.at(left, top, HudGlyphs.WELL_ASCENT);
    pen.glyphs(HudGlyphs.well(), HudGlyphs.WELL_ADVANCE);
    int inset = (HudGlyphs.WELL_SIZE - HudGlyphs.ICON_SIZE) / 2;
    pen.at(left + inset, top + inset, HudGlyphs.ICON_ASCENT);
    pen.glyphs(icon == null ? HudGlyphs.fallbackIcon() : icon, HudGlyphs.GLYPH_ADVANCE);
    int wipe = HudStrip.wipeStep(spec, nowMillis);
    if (wipe > 0) {
      pen.at(left + inset, top + inset, HudGlyphs.ICON_ASCENT);
      pen.glyphs(HudGlyphs.wipe(wipe), HudGlyphs.GLYPH_ADVANCE);
    }
    String count = HudStrip.count(spec);
    if (!count.isEmpty()) {
      int width = PackFontMetrics.vanilla().width(count);
      pen.at(left + HudGlyphs.WELL_SIZE - width, top + HudGlyphs.WELL_SIZE - PackFontMetrics.LINE_HEIGHT, TEXT_ASCENT);
      pen.out.append("<reset><white>").append(count);
      pen.x += width;
    }
  }

  private static String gain(HudXp xp) {
    return "+" + (long) Math.floor(xp.gained());
  }

  private static String colour(int rgb) {
    return String.format(Locale.ROOT, "<color:#%06x>", rgb & 0xFFFFFF);
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("<", "\\<");
  }

  /** The action bar pen: where the next glyph lands, in line pixels from the line's start. */
  private static final class Pen {
    private final StringBuilder out = new StringBuilder();
    private int x;

    /** Moves to corner {@code (left, top)} for a glyph whose ascent is {@code ascent}. */
    private void at(int left, int top, int ascent) {
      shiftTo(BAND + (top - (TEXT_ASCENT - ascent) + ROW_BIAS) * ROW + left);
    }

    private void glyphs(String glyphs, int advance) {
      out.append(glyphs);
      x += advance;
    }

    private void text(int rgb, String markup, int width) {
      out.append(INK).append(colour(rgb)).append(markup);
      x += width;
    }

    private void home() {
      shiftTo(0);
    }

    private void shiftBy(int pixels) {
      shiftTo(x + pixels);
    }

    private void shiftTo(int target) {
      if (target != x) {
        out.append(GLYPHS).append(HudGlyphs.shift(target - x));
        x = target;
      }
    }
  }
}
