package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.content.golem.reveal.GolemLabel;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.value.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NodeLabelTest {
  private static final Pattern RUN = Pattern.compile("<([^/<>]+)>([^<]*)</\\1>");

  @Test
  void theLabelReadsTypeChannelPriorityFilterAndStatusOnOneLine() {
    String label = NodeLabel.compose(new NodeLabel.Content("Deposit", DyeColor.ORANGE, "P3", "2 filters", NodeStatus.IDLE, "Idle"));

    String plain = MarkupPort.NONE.plain(label);

    assertThat(plain).isEqualTo("Deposit · " + GolemLabel.SWATCH + " · P3 · 2 filters · Idle");
    assertThat(colorsOf(label, GolemLabel.SWATCH)).containsExactly(String.format(Locale.ROOT, "#%06x", DyeColor.ORANGE.color().packed()));
    assertThat(colorsOf(label, "Idle")).containsExactly(NodeStatus.IDLE.color());
  }

  @Test
  void aNodeWithoutAChannelShowsTheEmptySwatch() {
    String label = NodeLabel.compose(new NodeLabel.Content("Guard", null, "P1", "0 filters", NodeStatus.BLOCKED, "Blocked"));

    String plain = MarkupPort.NONE.plain(label);

    assertThat(plain).contains(GolemLabel.NO_CHANNEL).doesNotContain(GolemLabel.SWATCH);
    assertThat(colorsOf(label, "Blocked")).containsExactly(NodeStatus.BLOCKED.color());
  }

  @Test
  void everyStatusHasItsOwnColor() {
    assertThat(List.of(NodeStatus.IDLE.color(), NodeStatus.WORKING.color(), NodeStatus.BLOCKED.color(), NodeStatus.FULL.color(),
        NodeStatus.NO_CONTAINER.color()))
        .doesNotHaveDuplicates();
  }

  private static List<String> colorsOf(String label, String content) {
    List<String> colors = new ArrayList<>();
    Matcher run = RUN.matcher(label);
    while (run.find()) {
      if (run.group(2).equals(content)) {
        colors.add(run.group(1));
      }
    }
    return colors;
  }
}
