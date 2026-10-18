package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.reveal.GolemLabel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeLabelTest {
  @Test
  void theLabelReadsTypeChannelPriorityFilterAndStatusOnOneLine() {
    Component label = NodeLabel.compose(new NodeLabel.Content("Deposit", DyeColor.ORANGE, "P3", "2 filters", NodeStatus.IDLE, "Idle"));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain).isEqualTo("Deposit · " + GolemLabel.SWATCH + " · P3 · 2 filters · Idle");
    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(TextColor.color(DyeColor.ORANGE.getColor().asRGB()));
    assertThat(colorsOf(label, "Idle")).containsExactly(NodeStatus.IDLE.color());
  }

  @Test
  void aNodeWithoutAChannelShowsTheEmptySwatch() {
    Component label = NodeLabel.compose(new NodeLabel.Content("Guard", null, "P1", "0 filters", NodeStatus.BLOCKED, "Blocked"));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain).contains(GolemLabel.NO_CHANNEL).doesNotContain(GolemLabel.SWATCH);
    assertThat(colorsOf(label, "Blocked")).containsExactly(NodeStatus.BLOCKED.color());
  }

  @Test
  void everyStatusHasItsOwnColor() {
    assertThat(List.of(NodeStatus.IDLE.color(), NodeStatus.WORKING.color(), NodeStatus.BLOCKED.color(), NodeStatus.FULL.color()))
        .doesNotHaveDuplicates();
  }

  private static List<TextColor> colorsOf(Component component, String content) {
    List<TextColor> colors = new ArrayList<>();
    collect(component, content, colors);
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
