package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.text.Markup;
import art.arcane.adapt.content.golem.reveal.GolemLabel;
import art.arcane.adapt.platform.value.DyeColor;

import java.util.Locale;

public final class NodeLabel {
  private static final String SEPARATOR = " · ";

  private NodeLabel() {
  }

  public static String compose(Content content) {
    StringBuilder label = new StringBuilder();
    label.append(colored("aqua", content.type()));
    separator(label);
    if (content.channel() == null) {
      label.append(colored("dark_gray", GolemLabel.NO_CHANNEL));
    } else {
      label.append(colored(hex(content.channel().color().packed()), GolemLabel.SWATCH));
    }
    separator(label);
    label.append(colored("yellow", content.priority()));
    separator(label);
    label.append(colored("white", content.filters()));
    separator(label);
    label.append(colored(content.status().color(), content.statusText()));
    return label.toString();
  }

  static String colored(String color, String text) {
    return "<" + color + ">" + Markup.escape(text) + "</" + color + ">";
  }

  static String hex(int packedRgb) {
    return String.format(Locale.ROOT, "#%06x", packedRgb & 0xFFFFFF);
  }

  private static void separator(StringBuilder label) {
    label.append(colored("dark_gray", SEPARATOR));
  }

  public record Content(String type, DyeColor channel, String priority, String filters, NodeStatus status, String statusText) {
  }
}
