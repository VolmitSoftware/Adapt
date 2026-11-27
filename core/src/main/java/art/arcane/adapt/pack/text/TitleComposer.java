package art.arcane.adapt.pack.text;

import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.pack.surface.TitleRun;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Builds a window title that places page art and text at absolute container x positions. The title pen starts at
 * {@code originX}; every jump is written as shift glyphs, every glyph and text run advances the pen by its width, and
 * neighbouring runs of one font and colour are joined. Text arrives as Adapt markup and keeps its colours.
 */
public final class TitleComposer {
  private static final String ELLIPSIS = "..";

  private PackFontMetrics metrics;
  private final ShiftFont shifts;
  private final List<TitleRun> runs = new ArrayList<>();
  private int pen;

  public TitleComposer(PackFontMetrics metrics, ShiftFont shifts, int originX) {
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.shifts = Objects.requireNonNull(shifts, "shifts");
    this.pen = originX;
  }

  /** Runs {@code placing} with text measured in {@code font} (a pack font drawn by its own row fonts), then the default again. */
  public <T> T measuredIn(PackFontMetrics font, Supplier<T> placing) {
    PackFontMetrics normal = metrics;
    metrics = Objects.requireNonNull(font, "font");
    try {
      return placing.get();
    } finally {
      metrics = normal;
    }
  }

  /** The container x the next run starts at. */
  public int pen() {
    return pen;
  }

  public TitleComposer moveTo(int x) {
    int distance = x - pen;
    if (distance != 0) {
      append(shifts.font(), shifts.shift(distance), TitleRun.PLAIN);
      pen = x;
    }
    return this;
  }

  /** Draws {@code text} of {@code font} at the pen, untinted; its glyphs advance {@code advance} pixels in all. */
  public TitleComposer glyph(String font, String text, int advance) {
    return glyph(font, text, advance, TitleRun.PLAIN);
  }

  /** Draws {@code text} of {@code font} at the pen tinted {@code colour}. */
  public TitleComposer glyph(String font, String text, int advance, int colour) {
    append(font, text, colour);
    pen += advance;
    return this;
  }

  /** Draws {@code glyphText} of {@code font} with its left edge at {@code x}. */
  public TitleComposer glyphAt(String font, String glyphText, int x, int advance) {
    return moveTo(x).glyph(font, glyphText, advance);
  }

  /**
   * One line of text in {@code row}, inside {@code width} pixels from {@code x}, cut short with an ellipsis when it
   * does not fit.
   */
  public Placement text(RowFont row, String markup, int x, int width, Align align, int colour) {
    String legacy = legacy(markup);
    if (row.shifted() && !TextScripts.movable(LegacyText.plain(legacy))) {
      return Placement.UNPLACEABLE;
    }
    return place(row, legacy, x, width, align, colour);
  }

  /**
   * Text wrapped at spaces to {@code width} over the given rows, one line each; lines past the last row are dropped and
   * the last shown line ends in two dots, as does a word too wide for its line unless {@code breaks} splits it with a
   * hyphen.
   */
  public Placement lines(List<RowFont> rows, String markup, int x, int width, Align align, int colour, boolean breaks) {
    String legacy = legacy(markup);
    if (rows.isEmpty() || legacy.isEmpty()) {
      return Placement.EMPTY;
    }
    List<LegacyText.Segment> segments = new ArrayList<>();
    for (LegacyText.Segment segment : LegacyText.segments(legacy, colour)) {
      segments.add(new LegacyText.Segment(segment.text().replaceAll(" {2,}", " "), segment.colour()));
    }
    String plain = join(segments);
    for (RowFont row : rows) {
      if (row.shifted() && !TextScripts.movable(plain)) {
        return Placement.UNPLACEABLE;
      }
    }
    List<List<LegacyText.Segment>> wrapped = wrap(segments, plain, width, breaks);
    boolean cut = wrapped.size() > rows.size();
    int shown = Math.min(rows.size(), wrapped.size());
    int widest = 0;
    for (int line = 0; line < shown; line++) {
      List<LegacyText.Segment> text = new ArrayList<>(wrapped.get(line));
      if (cut && line == shown - 1) {
        int last = text.isEmpty() ? colour : text.get(text.size() - 1).colour();
        text.add(new LegacyText.Segment(ELLIPSIS, last));
      }
      Placement placed = place(rows.get(line), text, x, width, align);
      widest = Math.max(widest, placed.width());
      cut |= placed.status() == Placement.Status.TRUNCATED;
    }
    return new Placement(cut ? Placement.Status.TRUNCATED : Placement.Status.PLACED, widest, shown);
  }

  /** The coloured segments broken into lines where the client would wrap their plain text. */
  private List<List<LegacyText.Segment>> wrap(List<LegacyText.Segment> segments, String plain, int width, boolean breaks) {
    int[] colours = new int[plain.length()];
    int at = 0;
    for (LegacyText.Segment segment : segments) {
      for (int index = 0; index < segment.text().length(); index++) {
        colours[at++] = segment.colour();
      }
    }
    List<List<LegacyText.Segment>> lines = new ArrayList<>();
    int cursor = 0;
    for (String wrapped : metrics.wrapWords(plain, width, breaks)) {
      String body = wrapped.substring(0, wrapped.length() - Hyphenation.HYPHEN.length());
      boolean broken = breaks && wrapped.endsWith(Hyphenation.HYPHEN) && plain.indexOf(wrapped, cursor) != plain.indexOf(body, cursor);
      String line = broken ? body : wrapped;
      while (cursor < plain.length() && !plain.startsWith(line, cursor)) {
        cursor++;
      }
      List<LegacyText.Segment> parts = new ArrayList<>();
      StringBuilder part = new StringBuilder();
      int partColour = cursor < colours.length ? colours[cursor] : 0;
      for (int index = 0; index < line.length(); index++) {
        int charColour = cursor + index < colours.length ? colours[cursor + index] : partColour;
        if (charColour != partColour && !part.isEmpty()) {
          parts.add(new LegacyText.Segment(part.toString(), partColour));
          part.setLength(0);
        }
        partColour = charColour;
        part.append(line.charAt(index));
      }
      addSegment(parts, part.toString() + (broken ? Hyphenation.HYPHEN : ""), partColour);
      lines.add(parts);
      cursor += line.length();
    }
    return lines;
  }

  public List<TitleRun> runs() {
    return List.copyOf(runs);
  }

  private Placement place(RowFont row, String legacy, int x, int width, Align align, int colour) {
    return place(row, LegacyText.segments(legacy, colour), x, width, align);
  }

  private Placement place(RowFont row, List<LegacyText.Segment> segments, int x, int width, Align align) {
    String plain = join(segments);
    if (plain.isEmpty()) {
      return Placement.EMPTY;
    }
    boolean cut = false;
    if (!metrics.fits(plain, width)) {
      segments = fit(segments, width);
      plain = join(segments);
      cut = true;
    }
    int textWidth = metrics.width(plain);
    int start = switch (align) {
      case LEFT -> x;
      case CENTRE -> x + ((width - textWidth) / 2);
      case RIGHT -> x + width - textWidth;
    };
    moveTo(start);
    for (LegacyText.Segment segment : segments) {
      append(row.font(), segment.text(), segment.colour());
    }
    pen += textWidth;
    return new Placement(cut ? Placement.Status.TRUNCATED : Placement.Status.PLACED, textWidth, 1);
  }

  private List<LegacyText.Segment> fit(List<LegacyText.Segment> segments, int width) {
    List<LegacyText.Segment> kept = new ArrayList<>();
    int room = width - metrics.width(ELLIPSIS);
    int used = 0;
    int lastColour = segments.isEmpty() ? TitleRun.PLAIN : segments.get(0).colour();
    for (LegacyText.Segment segment : segments) {
      StringBuilder part = new StringBuilder();
      String text = segment.text();
      for (int index = 0; index < text.length(); ) {
        int codePoint = text.codePointAt(index);
        int advance = metrics.width(codePoint, false);
        if (used + advance > room) {
          addSegment(kept, part.toString(), segment.colour());
          addSegment(kept, ELLIPSIS, segment.colour());
          return kept;
        }
        used += advance;
        part.appendCodePoint(codePoint);
        index += Character.charCount(codePoint);
      }
      addSegment(kept, part.toString(), segment.colour());
      lastColour = segment.colour();
    }
    addSegment(kept, ELLIPSIS, lastColour);
    return kept;
  }

  private static void addSegment(List<LegacyText.Segment> segments, String text, int colour) {
    if (!text.isEmpty()) {
      segments.add(new LegacyText.Segment(text, colour));
    }
  }

  private static String join(List<LegacyText.Segment> segments) {
    StringBuilder out = new StringBuilder();
    for (LegacyText.Segment segment : segments) {
      out.append(segment.text());
    }
    return out.toString();
  }

  private void append(String font, String text, int colour) {
    if (text.isEmpty()) {
      return;
    }
    int last = runs.size() - 1;
    if (last >= 0 && runs.get(last).font().equals(font) && runs.get(last).colour() == (colour & 0xFFFFFF)) {
      runs.set(last, runs.get(last).append(text));
      return;
    }
    runs.add(new TitleRun(font, text, colour));
  }

  private static String legacy(String markup) {
    return markup == null || markup.isEmpty() ? "" : MarkupPort.current().legacy(markup);
  }
}
