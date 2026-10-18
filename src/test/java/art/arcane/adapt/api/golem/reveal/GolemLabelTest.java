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
        Component.text("Steve's Iron Golem"), 73.2D, 100D, "Guardian", "Work", ChannelMask.of(DyeColor.RED)));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain).contains("Steve's Iron Golem", "74/100", "Guardian", "Work", GolemLabel.SWATCH);
    assertThat(plain.lines().toList()).hasSize(3);
    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(TextColor.color(DyeColor.RED.getColor().asRGB()));
  }

  @Test
  void aGolemWithoutAChannelShowsTheEmptySwatch() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Golem"), 4D, 4D, "Companion", "Follow", ChannelMask.NONE));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain).contains(GolemLabel.NO_CHANNEL).doesNotContain(GolemLabel.SWATCH);
  }

  @Test
  void everyChannelGetsItsOwnSwatch() {
    Component label = GolemLabel.compose(new GolemLabel.Content(
        Component.text("Golem"), 4D, 4D, "Sorter", "Work", ChannelMask.of(DyeColor.BLUE, DyeColor.LIME)));

    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(
        TextColor.color(DyeColor.LIME.getColor().asRGB()), TextColor.color(DyeColor.BLUE.getColor().asRGB()));
  }

  @Test
  void equalContentComparesEqualSoRefreshesCanBeSkipped() {
    GolemLabel.Content first = new GolemLabel.Content(Component.text("Golem"), 4D, 4D, "Sorter", "Work", 3);
    GolemLabel.Content second = new GolemLabel.Content(Component.text("Golem"), 4D, 4D, "Sorter", "Work", 3);

    assertThat(first).isEqualTo(second);
    assertThat(first).isNotEqualTo(new GolemLabel.Content(Component.text("Golem"), 3D, 4D, "Sorter", "Work", 3));
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
