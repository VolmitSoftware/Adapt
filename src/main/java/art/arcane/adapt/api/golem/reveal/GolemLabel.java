package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.ChannelMask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.DyeColor;

public final class GolemLabel {
  public static final int HEALTH_SEGMENTS = 10;
  public static final String SWATCH = "■";
  public static final String NO_CHANNEL = "□";
  public static final String MASTERWORK_TRIM = "✦";
  private static final String SEGMENT = "|";
  private static final String SEPARATOR = " · ";
  private static final double HEALTHY_FRACTION = 0.5D;
  private static final double WOUNDED_FRACTION = 0.25D;

  private GolemLabel() {
  }

  public static int filledSegments(double health, double maxHealth) {
    if (!(maxHealth > 0D) || !(health > 0D)) {
      return 0;
    }
    double fraction = Math.min(1D, health / maxHealth);
    return Math.max(1, (int) Math.round(fraction * HEALTH_SEGMENTS));
  }

  public static NamedTextColor healthColor(double health, double maxHealth) {
    double fraction = maxHealth > 0D ? health / maxHealth : 0D;
    if (fraction > HEALTHY_FRACTION) {
      return NamedTextColor.GREEN;
    }
    return fraction > WOUNDED_FRACTION ? NamedTextColor.YELLOW : NamedTextColor.RED;
  }

  public static Component compose(Content content) {
    TextComponent.Builder label = Component.text();
    appendName(label, content);
    label.append(Component.newline());
    appendHealth(label, content.health(), content.maxHealth());
    label.append(Component.newline());
    label.append(Component.text(content.core(), NamedTextColor.AQUA));
    label.append(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY));
    label.append(Component.text(content.mode(), NamedTextColor.YELLOW));
    label.append(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY));
    appendChannels(label, content.channels());
    return label.build();
  }

  private static void appendName(TextComponent.Builder label, Content content) {
    Component name = content.name().colorIfAbsent(NamedTextColor.WHITE);
    if (!content.masterwork()) {
      label.append(name);
      return;
    }
    label.append(Component.text(MASTERWORK_TRIM + " ", NamedTextColor.GOLD));
    label.append(name);
    label.append(Component.text(" " + MASTERWORK_TRIM, NamedTextColor.GOLD));
  }

  private static void appendHealth(TextComponent.Builder label, double health, double maxHealth) {
    int filled = filledSegments(health, maxHealth);
    label.append(Component.text(SEGMENT.repeat(filled), healthColor(health, maxHealth)));
    label.append(Component.text(SEGMENT.repeat(HEALTH_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
    label.append(Component.text(" " + (int) Math.ceil(Math.max(0D, health)) + "/" + Math.round(Math.max(0D, maxHealth)), NamedTextColor.GRAY));
  }

  private static void appendChannels(TextComponent.Builder label, int channels) {
    if (ChannelMask.isNone(channels)) {
      label.append(Component.text(NO_CHANNEL, NamedTextColor.DARK_GRAY));
      return;
    }
    for (DyeColor color : ChannelMask.colors(channels)) {
      label.append(Component.text(SWATCH, TextColor.color(color.getColor().asRGB())));
    }
  }

  public record Content(Component name, double health, double maxHealth, String core, String mode, int channels, boolean masterwork) {
  }
}
