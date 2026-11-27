package art.arcane.adapt.pack.text;

import art.arcane.adapt.pack.surface.TitleRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TitleComposerTest {
  private static final PackFontMetrics METRICS = PackFontMetrics.vanilla();
  private static final ShiftFont SHIFTS = ShiftFontTest.font();
  private static final RowFont TITLE = new RowFont("minecraft:default", 6, false);
  private static final RowFont ROW = new RowFont("adapt:row_40", 40, true);
  private static final RowFont NEXT_ROW = new RowFont("adapt:row_49", 49, true);

  private final TitleComposer composer = new TitleComposer(METRICS, SHIFTS, 8);

  @Test
  void glyphsArePlacedByShiftingThePenAndAdvancing() {
    composer.glyphAt("adapt:page", "P", -27, 231);
    composer.glyphAt("adapt:page_low", "Q", -27, 231);

    List<TitleRun> runs = composer.runs();
    assertThat(runs).extracting(TitleRun::font).containsExactly("adapt:shift", "adapt:page", "adapt:shift", "adapt:page_low");
    assertThat(ShiftFontTest.advance(SHIFTS, runs.get(0).text())).isEqualTo(-35);
    assertThat(ShiftFontTest.advance(SHIFTS, runs.get(2).text())).isEqualTo(-231);
    assertThat(runs.get(1).colour()).isEqualTo(TitleRun.PLAIN);
    assertThat(composer.pen()).isEqualTo(204);
  }

  @Test
  void penPositionMatchesTheShiftsAndWidthsEmitted() {
    composer.text(TITLE, "Skills", 8, 160, Align.LEFT, 0x404040);
    composer.text(ROW, "Agility", 30, 54, Align.CENTRE, 0x404040);
    composer.moveTo(100);

    int pen = 8;
    for (TitleRun run : composer.runs()) {
      pen += run.font().equals("adapt:shift") ? ShiftFontTest.advance(SHIFTS, run.text()) : METRICS.width(run.text());
    }
    assertThat(pen).isEqualTo(100).isEqualTo(composer.pen());
  }

  @Test
  void alignmentPlacesTextInsideItsWidth() {
    int width = METRICS.width("Agility");
    TitleComposer centre = new TitleComposer(METRICS, SHIFTS, 8);
    centre.text(ROW, "Agility", 20, 60, Align.CENTRE, 0);
    TitleComposer right = new TitleComposer(METRICS, SHIFTS, 8);
    right.text(ROW, "Agility", 20, 60, Align.RIGHT, 0);

    assertThat(ShiftFontTest.advance(SHIFTS, centre.runs().get(0).text())).isEqualTo(12 + ((60 - width) / 2));
    assertThat(ShiftFontTest.advance(SHIFTS, right.runs().get(0).text())).isEqualTo(12 + 60 - width);
    assertThat(right.pen()).isEqualTo(80);
  }

  @Test
  void textStartingAtThePenNeedsNoShift() {
    composer.text(TITLE, "Skills", 8, 160, Align.LEFT, 0x404040);

    assertThat(composer.runs()).containsExactly(new TitleRun("minecraft:default", "Skills", 0x404040));
  }

  @Test
  void coloursBecomeSeparateRunsAndNeighboursOfOneStyleJoin() {
    composer.text(TITLE, "§cRed §cstill §9blue", 8, 160, Align.LEFT, 0x404040);

    assertThat(composer.runs()).containsExactly(new TitleRun("minecraft:default", "Red still ", 0xFF5555),
        new TitleRun("minecraft:default", "blue", 0x5555FF));
  }

  @Test
  void textTooWideIsCutWithTwoDots() {
    Placement placement = composer.text(TITLE, "An adaptation name far too long for its card", 8, 54, Align.LEFT, 0);

    assertThat(placement.status()).isEqualTo(Placement.Status.TRUNCATED);
    assertThat(placement.width()).isLessThanOrEqualTo(54);
    assertThat(composer.runs().get(0).text()).endsWith("..").doesNotEndWith("...");
  }

  @Test
  void wrappedTextTakesOneRowFontPerLine() {
    Placement placement = composer.lines(List.of(ROW, NEXT_ROW), "Jump off walls while airborne to climb", 8, 80,
        Align.LEFT, 0x202020, false);

    assertThat(placement.lines()).isEqualTo(2);
    assertThat(composer.runs()).extracting(TitleRun::font).contains("adapt:row_40", "adapt:row_49");
  }

  @Test
  void hexColoursSurviveTheLineBreak() {
    composer.lines(List.of(ROW, NEXT_ROW), "§x§5§5§f§f§f§fSuper Jump", 8, 40, Align.LEFT, 0x202020, false);

    assertThat(composer.runs()).filteredOn((TitleRun run) -> !run.font().equals("adapt:shift"))
        .extracting(TitleRun::text, TitleRun::colour)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("Super", 0x55FFFF), org.assertj.core.groups.Tuple.tuple("Jump", 0x55FFFF));
  }

  @Test
  void textBeyondTheLastRowIsDroppedAndTheLastLineEndsInTwoDots() {
    Placement placement = composer.lines(List.of(ROW), "one two three four five six seven eight nine ten", 8, 40,
        Align.LEFT, 0, false);

    assertThat(placement.status()).isEqualTo(Placement.Status.TRUNCATED);
    assertThat(placement.lines()).isEqualTo(1);
    assertThat(composer.runs().get(composer.runs().size() - 1).text()).endsWith("..");
  }

  @Test
  void aWordTooWideForItsLineIsCutNotBroken() {
    Placement placement = composer.lines(List.of(ROW, NEXT_ROW), "Hunter's Resistance", 8, 46, Align.LEFT, 0, false);

    assertThat(placement.status()).isEqualTo(Placement.Status.TRUNCATED);
    assertThat(composer.runs()).filteredOn((TitleRun run) -> !run.font().equals("adapt:shift")).extracting(TitleRun::text)
        .containsExactly("Hunter's", "Resistan..");
  }

  @Test
  void aBreakingLabelSplitsAWordTooWideForItsLineWithAHyphen() {
    Placement placement = composer.lines(List.of(ROW, NEXT_ROW), "§x§5§5§f§f§f§fConvergence", 8, 54, Align.LEFT, 0, true);

    assertThat(placement.status()).isEqualTo(Placement.Status.PLACED);
    assertThat(composer.runs()).filteredOn((TitleRun run) -> !run.font().equals("adapt:shift"))
        .extracting(TitleRun::text, TitleRun::colour)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("Conver-", 0x55FFFF), org.assertj.core.groups.Tuple.tuple("gence", 0x55FFFF));
  }

  @Test
  void unifontTextCannotMoveOffTheTitleLine() {
    assertThat(composer.text(ROW, "敏捷", 8, 160, Align.LEFT, 0)).isEqualTo(Placement.UNPLACEABLE);
    assertThat(composer.lines(List.of(ROW, NEXT_ROW), "敏捷 技能", 8, 160, Align.LEFT, 0, false)).isEqualTo(Placement.UNPLACEABLE);
    assertThat(composer.runs()).isEmpty();

    assertThat(composer.text(TITLE, "敏捷", 8, 160, Align.LEFT, 0).placed()).isTrue();
    assertThat(composer.runs()).containsExactly(new TitleRun("minecraft:default", "敏捷", 0));
  }

  @Test
  void emptyTextPlacesNothing() {
    assertThat(composer.text(ROW, "", 8, 160, Align.LEFT, 0)).isEqualTo(Placement.EMPTY);
    assertThat(composer.runs()).isEmpty();
  }
}
