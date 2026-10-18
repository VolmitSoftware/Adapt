package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.reveal.GolemLabel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.DyeColor;

public final class NodeLabel {
  private static final String SEPARATOR = " · ";

  private NodeLabel() {
  }

  public static Component compose(Content content) {
    TextComponent.Builder label = Component.text();
    label.append(Component.text(content.type(), NamedTextColor.AQUA));
    separator(label);
    if (content.channel() == null) {
      label.append(Component.text(GolemLabel.NO_CHANNEL, NamedTextColor.DARK_GRAY));
    } else {
      label.append(Component.text(GolemLabel.SWATCH, TextColor.color(content.channel().getColor().asRGB())));
    }
    separator(label);
    label.append(Component.text(content.priority(), NamedTextColor.YELLOW));
    separator(label);
    label.append(Component.text(content.filters(), NamedTextColor.WHITE));
    separator(label);
    label.append(Component.text(content.statusText(), content.status().color()));
    return label.build();
  }

  private static void separator(TextComponent.Builder label) {
    label.append(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY));
  }

  public record Content(String type, DyeColor channel, String priority, String filters, NodeStatus status, String statusText) {
  }
}
