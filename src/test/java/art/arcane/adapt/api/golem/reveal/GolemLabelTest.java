package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.ChannelMask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GolemLabelTest {
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
    assertThat(GolemLabel.healthColor(80D, 100D)).isEqualTo(NamedTextColor.GREEN);
    assertThat(GolemLabel.healthColor(40D, 100D)).isEqualTo(NamedTextColor.YELLOW);
    assertThat(GolemLabel.healthColor(10D, 100D)).isEqualTo(NamedTextColor.RED);
  }

  @Test
  void labelShowsNameHealthCoreModeAndChannel() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Steve's Iron Golem"), 73.2D, 100D, "Guardian", "Work", ChannelMask.of(DyeColor.RED), false, true));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain).contains("Steve's Iron Golem", "74/100", "Guardian", "Work", GolemLabel.SWATCH);
    assertThat(plain.lines().toList()).hasSize(3);
    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(TextColor.color(DyeColor.RED.getColor().asRGB()));
  }

  @Test
  void aGolemWithoutAChannelShowsTheEmptySwatch() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Golem"), 4D, 4D, "Companion", "Follow", ChannelMask.NONE, false, true));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain).contains(GolemLabel.NO_CHANNEL).doesNotContain(GolemLabel.SWATCH);
  }

  @Test
  void everyChannelGetsItsOwnSwatch() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Golem"), 4D, 4D, "Sorter", "Work", ChannelMask.of(DyeColor.BLUE, DyeColor.LIME), false, true));

    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(
        TextColor.color(DyeColor.LIME.getColor().asRGB()), TextColor.color(DyeColor.BLUE.getColor().asRGB()));
  }

  @Test
  void equalContentComparesEqualSoRefreshesCanBeSkipped() {
    GolemLabel.Content first = new GolemLabel.Content(Component.text("Golem"), 4D, 4D, "Sorter", "Work", 3, false, true);
    GolemLabel.Content second = new GolemLabel.Content(Component.text("Golem"), 4D, 4D, "Sorter", "Work", 3, false, true);

    assertThat(first).isEqualTo(second);
    assertThat(first).isNotEqualTo(new GolemLabel.Content(Component.text("Golem"), 3D, 4D, "Sorter", "Work", 3, false, true));
  }

  @Test
  void aMasterworkLabelFramesItsNameInGold() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Steve's Iron Golem"), 100D, 125D, "Harvester + Guardian", "Work", ChannelMask.NONE, true, true));
    Component plainLabel = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Steve's Iron Golem"), 100D, 125D, "Harvester + Guardian", "Work", ChannelMask.NONE, false, true));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain.lines().findFirst()).contains(GolemLabel.MASTERWORK_TRIM + " Steve's Iron Golem " + GolemLabel.MASTERWORK_TRIM);
    assertThat(plain).contains("Harvester + Guardian");
    assertThat(colorsOf(label, GolemLabel.MASTERWORK_TRIM + " ")).containsExactly(NamedTextColor.GOLD);
    assertThat(colorsOf(label, " " + GolemLabel.MASTERWORK_TRIM)).containsExactly(NamedTextColor.GOLD);
    assertThat(PlainTextComponentSerializer.plainText().serialize(plainLabel)).doesNotContain(GolemLabel.MASTERWORK_TRIM);
  }

  @Test
  void anUnnamedLabelLeavesTheNameToTheNametagAndKeepsTheStatusLines() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Steve's Iron Golem"), 50D, 100D, "Guardian", "Work", ChannelMask.of(DyeColor.RED), false, false));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain).doesNotContain("Steve's Iron Golem").contains("50/100", "Guardian", "Work", GolemLabel.SWATCH);
    assertThat(plain.lines().toList()).hasSize(2);
  }

  @Test
  void anUnnamedMasterworkLabelMarksItsHealthLineInGold() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Steve's Iron Golem"), 100D, 125D, "Harvester + Guardian", "Work", ChannelMask.NONE, true, false));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain.lines().findFirst()).hasValueSatisfying(line -> assertThat(line)
        .startsWith(GolemLabel.MASTERWORK_TRIM + " ").endsWith(" " + GolemLabel.MASTERWORK_TRIM).contains("100/125"));
    assertThat(colorsOf(label, GolemLabel.MASTERWORK_TRIM + " ")).containsExactly(NamedTextColor.GOLD);
  }

  @Test
  void legacyLinesCarryTheSameLinesAsFormattedText() {
    GolemLabel.Content named = new GolemLabel.Content(
        Component.text("Clank"), 40D, 100D, "Sorter", "Work", ChannelMask.of(DyeColor.BLUE), false, true);

    List<String> lines = GolemLabel.legacyLines(named);
    List<String> unnamed = GolemLabel.legacyLines(new GolemLabel.Content(
        Component.text("Clank"), 40D, 100D, "Sorter", "Work", ChannelMask.of(DyeColor.BLUE), false, false));

    assertThat(lines).hasSize(3);
    assertThat(lines.getFirst()).contains("Clank").contains("§");
    assertThat(lines.get(1)).contains("40/100");
    assertThat(lines.get(2)).contains("Sorter", "Work", GolemLabel.SWATCH);
    assertThat(unnamed).containsExactlyElementsOf(lines.subList(1, 3));
    assertThat(GolemLabel.legacyName(Component.text("Clank"))).contains("Clank").startsWith("§");
  }

  private static List<TextColor> colorsOf(Component root, String content) {
    List<TextColor> colors = new ArrayList<>();
    collect(root, content, colors);
    return colors;
  }

  private static void collect(Component component, String content, List<TextColor> colors) {
    if (component instanceof TextComponent text && text.content().equals(content)) {
      colors.add(text.color());
    }
    for (Component child : component.children()) {
      collect(child, content, colors);
    }
  }
}
