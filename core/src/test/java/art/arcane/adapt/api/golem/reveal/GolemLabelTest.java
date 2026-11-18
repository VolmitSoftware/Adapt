package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.testkit.TestMarkup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class GolemLabelTest {
  private static final Pattern SPAN = Pattern.compile("<([^/<>]+)>((?:\\\\<|[^<])*)</\\1>");

  @BeforeEach
  void installMarkup() {
    AdaptPorts.install(MarkupPort.class, new TestMarkup());
  }

  @AfterEach
  void removeMarkup() {
    AdaptPorts.uninstall(MarkupPort.class);
  }

  @Test
  void healthBarFillsProportionallyAndNeverHidesALivingGolem() {
    assertThat(GolemLabel.filledSegments(100D, 100D)).isEqualTo(GolemLabel.HEALTH_SEGMENTS);
    assertThat(GolemLabel.filledSegments(50D, 100D)).isEqualTo(5);
    assertThat(GolemLabel.filledSegments(74D, 100D)).isEqualTo(7);
    assertThat(GolemLabel.filledSegments(0.1D, 100D)).isEqualTo(1);
    assertThat(GolemLabel.filledSegments(0D, 100D)).isZero();
    assertThat(GolemLabel.filledSegments(150D, 100D)).isEqualTo(GolemLabel.HEALTH_SEGMENTS);
    assertThat(GolemLabel.filledSegments(10D, 0D)).isZero();
  }

  @Test
  void healthColorShiftsFromGreenToRed() {
    assertThat(GolemLabel.healthColor(80D, 100D)).isEqualTo("green");
    assertThat(GolemLabel.healthColor(40D, 100D)).isEqualTo("yellow");
    assertThat(GolemLabel.healthColor(10D, 100D)).isEqualTo("red");
  }

  @Test
  void labelShowsNameHealthCoreModeAndChannel() {
    String label = GolemLabel.compose(new GolemLabel.Content(
        "Steve's Iron Golem", 73.2D, 100D, "Guardian", "Work", ChannelMask.of(DyeColor.RED), false, true));
    String plain = plain(label);

    assertThat(plain).contains("Steve's Iron Golem", "74/100", "Guardian", "Work", GolemLabel.SWATCH);
    assertThat(plain.lines().toList()).hasSize(3);
    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(hex(DyeColor.RED));
  }

  @Test
  void aGolemWithoutAChannelShowsTheEmptySwatch() {
    String label = GolemLabel.compose(new GolemLabel.Content(
        "Golem", 4D, 4D, "Companion", "Follow", ChannelMask.NONE, false, true));
    String plain = plain(label);

    assertThat(plain).contains(GolemLabel.NO_CHANNEL).doesNotContain(GolemLabel.SWATCH);
  }

  @Test
  void everyChannelGetsItsOwnSwatch() {
    String label = GolemLabel.compose(new GolemLabel.Content(
        "Golem", 4D, 4D, "Sorter", "Work", ChannelMask.of(DyeColor.BLUE, DyeColor.LIME), false, true));

    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(hex(DyeColor.LIME), hex(DyeColor.BLUE));
  }

  @Test
  void equalContentComparesEqualSoRefreshesCanBeSkipped() {
    GolemLabel.Content first = new GolemLabel.Content("Golem", 4D, 4D, "Sorter", "Work", 3, false, true);
    GolemLabel.Content second = new GolemLabel.Content("Golem", 4D, 4D, "Sorter", "Work", 3, false, true);

    assertThat(first).isEqualTo(second);
    assertThat(first).isNotEqualTo(new GolemLabel.Content("Golem", 3D, 4D, "Sorter", "Work", 3, false, true));
  }

  @Test
  void aMasterworkLabelFramesItsNameInGold() {
    String label = GolemLabel.compose(new GolemLabel.Content(
        "Steve's Iron Golem", 100D, 125D, "Harvester + Guardian", "Work", ChannelMask.NONE, true, true));
    String plainLabel = GolemLabel.compose(new GolemLabel.Content(
        "Steve's Iron Golem", 100D, 125D, "Harvester + Guardian", "Work", ChannelMask.NONE, false, true));
    String plain = plain(label);

    assertThat(plain.lines().findFirst()).contains(GolemLabel.MASTERWORK_TRIM + " Steve's Iron Golem " + GolemLabel.MASTERWORK_TRIM);
    assertThat(plain).contains("Harvester + Guardian");
    assertThat(colorsOf(label, GolemLabel.MASTERWORK_TRIM + " ")).containsExactly("gold");
    assertThat(colorsOf(label, " " + GolemLabel.MASTERWORK_TRIM)).containsExactly("gold");
    assertThat(plain(plainLabel)).doesNotContain(GolemLabel.MASTERWORK_TRIM);
  }

  @Test
  void anUnnamedLabelLeavesTheNameToTheNametagAndKeepsTheStatusLines() {
    String label = GolemLabel.compose(new GolemLabel.Content(
        "Steve's Iron Golem", 50D, 100D, "Guardian", "Work", ChannelMask.of(DyeColor.RED), false, false));
    String plain = plain(label);

    assertThat(plain).doesNotContain("Steve's Iron Golem").contains("50/100", "Guardian", "Work", GolemLabel.SWATCH);
    assertThat(plain.lines().toList()).hasSize(2);
  }

  @Test
  void anUnnamedMasterworkLabelMarksItsHealthLineInGold() {
    String label = GolemLabel.compose(new GolemLabel.Content(
        "Steve's Iron Golem", 100D, 125D, "Harvester + Guardian", "Work", ChannelMask.NONE, true, false));
    String plain = plain(label);

    assertThat(plain.lines().findFirst()).hasValueSatisfying(line -> assertThat(line)
        .startsWith(GolemLabel.MASTERWORK_TRIM + " ").endsWith(" " + GolemLabel.MASTERWORK_TRIM).contains("100/125"));
    assertThat(colorsOf(label, GolemLabel.MASTERWORK_TRIM + " ")).containsExactly("gold");
  }

  @Test
  void legacyLinesCarryTheSameLinesAsFormattedText() {
    GolemLabel.Content named = new GolemLabel.Content(
        "Clank", 40D, 100D, "Sorter", "Work", ChannelMask.of(DyeColor.BLUE), false, true);

    List<String> lines = GolemLabel.legacyLines(named);
    List<String> unnamed = GolemLabel.legacyLines(new GolemLabel.Content(
        "Clank", 40D, 100D, "Sorter", "Work", ChannelMask.of(DyeColor.BLUE), false, false));

    assertThat(lines).hasSize(3);
    assertThat(lines.getFirst()).contains("Clank").contains("§");
    assertThat(lines.get(1)).contains("40/100");
    assertThat(lines.get(2)).contains("Sorter", "Work", GolemLabel.SWATCH);
    assertThat(unnamed).containsExactlyElementsOf(lines.subList(1, 3));
    assertThat(GolemLabel.legacyName("Clank")).contains("Clank").startsWith("§");
  }

  private static String plain(String markup) {
    return MarkupPort.current().plain(markup);
  }

  private static String hex(DyeColor dye) {
    return String.format(Locale.ROOT, "#%06x", dye.color().packed());
  }

  private static List<String> colorsOf(String markup, String content) {
    List<String> colors = new ArrayList<>();
    Matcher span = SPAN.matcher(markup);
    while (span.find()) {
      if (span.group(2).replace("\\<", "<").equals(content)) {
        colors.add(span.group(1));
      }
    }
    return colors;
  }
}
