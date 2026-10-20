package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.ChannelMask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.DyeColor;

import java.util.ArrayList;
import java.util.List;

public final class GolemLabel {
  public static final int HEALTH_SEGMENTS = 10;
  public static final String SWATCH = "■";
  public static final String NO_CHANNEL = "□";
  public static final String MASTERWORK_TRIM = "✦";
  private static final String SEGMENT = "|";
  private static final String SEPARATOR = " · ";
  private static final double HEALTHY_FRACTION = 0.5D;
  private static final double WOUNDED_FRACTION = 0.25D;
  private static final int MAX_LINES = 3;

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
    return Component.join(JoinConfiguration.newlines(), lines(content));
  }

  public static List<Component> lines(Content content) {
    List<Component> lines = new ArrayList<>(MAX_LINES);
    if (content.named()) {
      lines.add(nameLine(content));
    }
    lines.add(healthLine(content));
    lines.add(statusLine(content));
    return lines;
  }

  public static List<String> legacyLines(Content content) {
    List<Component> lines = lines(content);
    List<String> legacy = new ArrayList<>(lines.size());
    for (Component line : lines) {
      legacy.add(LegacyComponentSerializer.legacySection().serialize(line));
    }
    return legacy;
  }

  public static String legacyName(Component name) {
    return LegacyComponentSerializer.legacySection().serialize(name.colorIfAbsent(NamedTextColor.WHITE));
  }

  private static Component nameLine(Content content) {
    Component name = content.name().colorIfAbsent(NamedTextColor.WHITE);
    if (!content.masterwork()) {
      return name;
    }
    return trimmed(name);
  }

  private static Component healthLine(Content content) {
    double health = content.health();
    double maxHealth = content.maxHealth();
    int filled = filledSegments(health, maxHealth);
    Component bar = Component.text()
        .append(Component.text(SEGMENT.repeat(filled), healthColor(health, maxHealth)))
        .append(Component.text(SEGMENT.repeat(HEALTH_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
        .append(Component.text(" " + (int) Math.ceil(Math.max(0D, health)) + "/" + Math.round(Math.max(0D, maxHealth)), NamedTextColor.GRAY))
        .build();
    return content.masterwork() && !content.named() ? trimmed(bar) : bar;
  }

  private static Component statusLine(Content content) {
    TextComponent.Builder line = Component.text();
    line.append(Component.text(content.core(), NamedTextColor.AQUA));
    line.append(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY));
    if (!content.mode().isEmpty()) {
      line.append(Component.text(content.mode(), NamedTextColor.YELLOW));
      line.append(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY));
    }
    appendChannels(line, content.channels());
    return line.build();
  }

  private static Component trimmed(Component inner) {
    return Component.text()
        .append(Component.text(MASTERWORK_TRIM + " ", NamedTextColor.GOLD))
        .append(inner)
        .append(Component.text(" " + MASTERWORK_TRIM, NamedTextColor.GOLD))
        .build();
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

  public record Content(Component name, double health, double maxHealth, String core, String mode, int channels, boolean masterwork,
                        boolean named) {
  }
}
