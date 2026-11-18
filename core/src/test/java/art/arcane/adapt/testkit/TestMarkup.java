package art.arcane.adapt.testkit;

import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.util.common.format.C;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markup port for tests: named and hex colour tags become legacy section codes (hex as the {@code §x} form), closing
 * tags and every other tag are dropped. Enough to check that labels carry colours without MiniMessage.
 */
public final class TestMarkup implements MarkupPort {
  private static final Pattern TAG = Pattern.compile("<(/?)([^<>]*)>");

  @Override
  public String normalize(String mixedMarkup) {
    return mixedMarkup == null ? "" : mixedMarkup;
  }

  @Override
  public String legacy(String mixedMarkup) {
    String source = C.translateAlternateColorCodes('&', normalize(mixedMarkup));
    Matcher tag = TAG.matcher(source);
    StringBuilder out = new StringBuilder();
    while (tag.find()) {
      tag.appendReplacement(out, Matcher.quoteReplacement(tag.group(1).isEmpty() ? code(tag.group(2)) : ""));
    }
    tag.appendTail(out);
    return out.toString().replace("\\<", "<");
  }

  @Override
  public String plain(String mixedMarkup) {
    return C.stripColor(legacy(mixedMarkup));
  }

  @Override
  public String renderMiniMessage(String miniMessage) {
    return legacy(miniMessage);
  }

  @Override
  public String escapeTags(String text) {
    return text == null ? "" : text.replace("<", "\\<");
  }

  @Override
  public boolean hasColorTags(String miniMessage) {
    return miniMessage != null && TAG.matcher(miniMessage).find();
  }

  @Override
  public String jsonToMarkup(String componentJson) {
    return MarkupPort.NONE.jsonToMarkup(componentJson);
  }

  private static String code(String name) {
    if (name.startsWith("#") && name.length() == 7) {
      StringBuilder hex = new StringBuilder(String.valueOf(C.COLOR_CHAR)).append('x');
      for (char digit : name.substring(1).toCharArray()) {
        hex.append(C.COLOR_CHAR).append(digit);
      }
      return hex.toString();
    }
    try {
      return C.valueOf(name.toUpperCase(Locale.ROOT)).toString();
    } catch (IllegalArgumentException unknown) {
      return "";
    }
  }
}
