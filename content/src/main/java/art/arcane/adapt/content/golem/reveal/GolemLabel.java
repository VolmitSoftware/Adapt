package art.arcane.adapt.content.golem.reveal;

import art.arcane.adapt.api.text.Markup;
import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.platform.value.DyeColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

  /** MiniMessage colour name of the health bar. */
  public static String healthColor(double health, double maxHealth) {
    double fraction = maxHealth > 0D ? health / maxHealth : 0D;
    if (fraction > HEALTHY_FRACTION) {
      return "green";
    }
    return fraction > WOUNDED_FRACTION ? "yellow" : "red";
  }

  public static String compose(Content content) {
    return String.join("\n", lines(content));
  }

  public static List<String> lines(Content content) {
    List<String> lines = new ArrayList<>(MAX_LINES);
    if (content.named()) {
      lines.add(nameLine(content));
    }
    lines.add(healthLine(content));
    lines.add(statusLine(content));
    return lines;
  }

  public static List<String> legacyLines(Content content) {
    List<String> lines = lines(content);
    List<String> legacy = new ArrayList<>(lines.size());
    for (String line : lines) {
      legacy.add(Markup.legacy(line));
    }
    return legacy;
  }

  public static String legacyName(String name) {
    return Markup.legacy(withDefaultColor(name));
  }

  private static String nameLine(Content content) {
    String name = withDefaultColor(content.name());
    if (!content.masterwork()) {
      return name;
    }
    return trimmed(name);
  }

  private static String healthLine(Content content) {
    double health = content.health();
    double maxHealth = content.maxHealth();
    int filled = filledSegments(health, maxHealth);
    String bar = colored(healthColor(health, maxHealth), SEGMENT.repeat(filled))
        + colored("dark_gray", SEGMENT.repeat(HEALTH_SEGMENTS - filled))
        + colored("gray", " " + (int) Math.ceil(Math.max(0D, health)) + "/" + Math.round(Math.max(0D, maxHealth)));
    return content.masterwork() && !content.named() ? trimmed(bar) : bar;
  }

  private static String statusLine(Content content) {
    StringBuilder line = new StringBuilder();
    line.append(colored("aqua", content.core()));
    line.append(colored("dark_gray", SEPARATOR));
    if (!content.mode().isEmpty()) {
      line.append(colored("yellow", content.mode()));
      line.append(colored("dark_gray", SEPARATOR));
    }
    appendChannels(line, content.channels());
    return line.toString();
  }

  private static String trimmed(String inner) {
    return colored("gold", MASTERWORK_TRIM + " ") + inner + colored("gold", " " + MASTERWORK_TRIM);
  }

  private static void appendChannels(StringBuilder label, int channels) {
    if (ChannelMask.isNone(channels)) {
      label.append(colored("dark_gray", NO_CHANNEL));
      return;
    }
    for (DyeColor color : ChannelMask.colors(channels)) {
      label.append(colored(String.format(Locale.ROOT, "#%06x", color.color().packed() & 0xFFFFFF), SWATCH));
    }
  }

  private static String withDefaultColor(String markup) {
    return "<white>" + (markup == null ? "" : markup) + "</white>";
  }

  private static String colored(String color, String text) {
    return "<" + color + ">" + Markup.escape(text) + "</" + color + ">";
  }

  /** {@code name} is Adapt markup; the other texts are plain and shown literally. */
  public record Content(String name, double health, double maxHealth, String core, String mode, int channels, boolean masterwork,
                        boolean named) {
  }
}
