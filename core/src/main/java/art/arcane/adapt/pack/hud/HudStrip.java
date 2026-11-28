package art.arcane.adapt.pack.hud;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.engine.notification.HudStatusSpec;
import art.arcane.adapt.pack.manifest.PackSprites;
import art.arcane.adapt.pack.text.PackFontMetrics;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Composes the action bar strip as Adapt markup: ability glyphs in wells with a clock wipe and counts, the xp line with
 * its bar, then the text notices. Glyph runs use font {@link HudGlyphs#FONT} without shadow; text keeps the vanilla
 * font and shadow. The client centres the whole line, so only relative pen moves are needed.
 */
public final class HudStrip {
  public static final int MAX_ABILITIES = 3;

  private static final String GLYPH_RUN = "<reset><font:" + HudGlyphs.FONT + "><shadow:#00000000>";
  private static final int ICON_INSET = 2;
  private static final int WELL_WIDTH = 20;
  private static final int WELL_GAP = 2;
  private static final int SECTION_GAP = 6;
  private static final int BAR_GAP = 4;
  private static final int NOTICE_GAP = 8;
  private static final int GAINED_COLOUR = 0xAAAAAA;

  private HudStrip() {
  }

  public static String compose(List<HudStatusSpec> abilities, HudXp xp, List<String> notices,
                               Function<String, String> icons, long nowMillis) {
    StringBuilder out = new StringBuilder();
    int shown = Math.min(MAX_ABILITIES, abilities.size());
    for (int index = 0; index < shown; index++) {
      if (index > 0) {
        out.append(GLYPH_RUN).append(HudGlyphs.shift(WELL_GAP + WELL_WIDTH - (ICON_INSET + HudGlyphs.GLYPH_ADVANCE)));
      }
      ability(out, abilities.get(index), icons, nowMillis);
    }
    if (shown > 0) {
      out.append(GLYPH_RUN).append(HudGlyphs.shift(WELL_WIDTH - (ICON_INSET + HudGlyphs.GLYPH_ADVANCE) + SECTION_GAP));
    }
    if (xp != null) {
      xp(out, xp);
    }
    boolean first = out.isEmpty();
    for (String notice : notices) {
      if (notice.isBlank()) {
        continue;
      }
      if (!first) {
        out.append(GLYPH_RUN).append(HudGlyphs.shift(NOTICE_GAP));
      }
      out.append("<reset>").append(notice);
      first = false;
    }
    return out.toString();
  }

  /** Shade eighths over the icon: remaining cooldown or recharge, spent duration, an unlit toggle or empty charges. */
  public static int wipeStep(HudStatusSpec spec, long nowMillis) {
    return switch (spec.glyph()) {
      case COOLDOWN -> remainingSteps(spec, nowMillis);
      case DURATION -> spec.durationMillis() <= 0L ? 0 : HudGlyphs.WIPE_STEPS - remainingSteps(spec, nowMillis);
      case CHARGES -> {
        int recharge = remainingSteps(spec, nowMillis);
        yield recharge > 0 ? recharge : spec.stacks() == 0 && spec.maxStacks() > 0 ? HudGlyphs.WIPE_STEPS : 0;
      }
      case TOGGLE -> spec.lit() ? 0 : HudGlyphs.WIPE_STEPS;
    };
  }

  /** True once a timed cooldown or effect has run out; charges and toggles stay until cleared. */
  public static boolean expired(HudStatusSpec spec, long nowMillis) {
    return (spec.glyph() == HudGlyph.COOLDOWN || spec.glyph() == HudGlyph.DURATION && spec.durationMillis() > 0L)
        && nowMillis >= spec.startedAtMillis() + spec.durationMillis();
  }

  /** The first time after {@code nowMillis} at which a wipe step changes or a status expires, or Long.MAX_VALUE. */
  public static long nextChange(List<HudStatusSpec> abilities, long nowMillis) {
    long next = Long.MAX_VALUE;
    for (HudStatusSpec spec : abilities) {
      long duration = spec.durationMillis();
      long end = spec.startedAtMillis() + duration;
      if (duration <= 0L || spec.glyph() == HudGlyph.TOGGLE || nowMillis >= end) {
        continue;
      }
      long remaining = Math.min(duration, end - nowMillis);
      long step = ceilSteps(remaining, duration);
      long boundaryRemaining = (step - 1L) * duration / HudGlyphs.WIPE_STEPS;
      next = Math.min(next, Math.max(nowMillis + 1L, end - boundaryRemaining));
    }
    return next;
  }

  private static void ability(StringBuilder out, HudStatusSpec spec, Function<String, String> icons,
                              long nowMillis) {
    String sprite = PackSprites.packId(spec.icon().spriteId());
    String icon = sprite == null ? null : icons.apply(sprite);
    out.append(GLYPH_RUN)
        .append(HudGlyphs.well())
        .append(HudGlyphs.shift(ICON_INSET - HudGlyphs.WELL_ADVANCE))
        .append(icon == null ? HudGlyphs.fallbackIcon() : icon);
    int wipe = wipeStep(spec, nowMillis);
    if (wipe > 0) {
      out.append(HudGlyphs.shift(-HudGlyphs.GLYPH_ADVANCE)).append(HudGlyphs.wipe(wipe));
    }
    String count = count(spec);
    if (!count.isEmpty()) {
      out.append(HudGlyphs.shift(-PackFontMetrics.vanilla().width(count)))
          .append("<reset><white>").append(count);
    }
  }

  static String count(HudStatusSpec spec) {
    if (spec.glyph() == HudGlyph.CHARGES && spec.maxStacks() != 1) {
      return Integer.toString(spec.stacks());
    }
    if (spec.glyph() == HudGlyph.DURATION && spec.stacks() > 1) {
      return Integer.toString(spec.stacks());
    }
    return "";
  }

  private static void xp(StringBuilder out, HudXp xp) {
    int fill = (int) Math.round(xp.progress() * HudGlyphs.XP_FILL_STEPS);
    out.append("<reset>").append(colour(xp.colour())).append(escape(xp.name())).append(' ').append(xp.level())
        .append(GLYPH_RUN).append(HudGlyphs.shift(BAR_GAP)).append(HudGlyphs.xpTrack())
        .append(HudGlyphs.shift(-HudGlyphs.XP_BAR_ADVANCE))
        .append(colour(xp.colour())).append(HudGlyphs.xpFill(fill));
    long gained = (long) Math.floor(xp.gained());
    if (gained > 0L) {
      out.append("<reset>").append(colour(GAINED_COLOUR)).append(" +").append(gained);
    }
  }

  private static int remainingSteps(HudStatusSpec spec, long nowMillis) {
    long duration = spec.durationMillis();
    if (duration <= 0L) {
      return 0;
    }
    long remaining = spec.startedAtMillis() + duration - nowMillis;
    if (remaining <= 0L) {
      return 0;
    }
    return (int) ceilSteps(Math.min(duration, remaining), duration);
  }

  private static long ceilSteps(long remaining, long duration) {
    return (remaining * HudGlyphs.WIPE_STEPS + duration - 1L) / duration;
  }

  private static String colour(int rgb) {
    return String.format(Locale.ROOT, "<color:#%06x>", rgb & 0xFFFFFF);
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("<", "\\<");
  }
}
