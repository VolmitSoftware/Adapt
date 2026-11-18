package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.util.common.format.C;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.regex.Pattern;

/**
 * Renders Adapt markup (legacy section and {@code &} codes mixed with MiniMessage tags) to legacy section text.
 * Each platform installs a MiniMessage-backed implementation through {@code AdaptPorts}; {@link #NONE} only strips
 * tags and exists for headless use before a platform starts.
 */
public interface MarkupPort {
  MarkupPort NONE = new PlainMarkup();

  /** The installed implementation, or {@link #NONE}. */
  static MarkupPort current() {
    return AdaptPorts.get(MarkupPort.class, NONE);
  }

  /** Converts legacy section and {@code &} codes in mixed markup to MiniMessage tags. */
  String normalize(String mixedMarkup);

  /** Legacy section text of trusted mixed markup. */
  String legacy(String mixedMarkup);

  /** Plain text of trusted mixed markup. */
  String plain(String mixedMarkup);

  /** Legacy section text of MiniMessage source that is already normalized. */
  String renderMiniMessage(String miniMessage);

  /** Escapes MiniMessage tags so the text renders literally. */
  String escapeTags(String text);

  /** Whether normalized MiniMessage source contains a colour, gradient, rainbow, transition, pride or reset tag. */
  boolean hasColorTags(String miniMessage);

  /** MiniMessage markup of a vanilla text-component JSON string (an item or entity name); "" when it does not parse. */
  String jsonToMarkup(String componentJson);

  final class PlainMarkup implements MarkupPort {
    private static final Pattern TAG = Pattern.compile("<[^<>]*>");

    private PlainMarkup() {
    }

    @Override
    public String normalize(String mixedMarkup) {
      return mixedMarkup == null ? "" : mixedMarkup;
    }

    @Override
    public String legacy(String mixedMarkup) {
      return TAG.matcher(C.translateAlternateColorCodes('&', normalize(mixedMarkup))).replaceAll("");
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
      return false;
    }

    @Override
    public String jsonToMarkup(String componentJson) {
      if (componentJson == null || componentJson.isBlank()) {
        return "";
      }
      StringBuilder plain = new StringBuilder();
      try {
        appendText(JsonParser.parseString(componentJson), plain);
      } catch (JsonParseException | IllegalStateException unreadable) {
        return "";
      }
      return escapeTags(plain.toString());
    }

    private static void appendText(JsonElement element, StringBuilder plain) {
      if (element == null || element.isJsonNull()) {
        return;
      }
      if (element.isJsonPrimitive()) {
        plain.append(element.getAsString());
        return;
      }
      if (element.isJsonArray()) {
        for (JsonElement part : element.getAsJsonArray()) {
          appendText(part, plain);
        }
        return;
      }
      JsonObject object = element.getAsJsonObject();
      JsonElement text = object.get("text");
      if (text != null && text.isJsonPrimitive()) {
        plain.append(text.getAsString());
      }
      appendText(object.get("extra"), plain);
    }
  }
}
