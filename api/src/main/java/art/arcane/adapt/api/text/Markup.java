package art.arcane.adapt.api.text;

import art.arcane.adapt.api.internal.Engine;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Converts between the markup Adapt renders (MiniMessage tags mixed with legacy {@code §} codes), legacy formatted
 * text and the JSON text components the platform stores for custom names. Without a markup engine on the platform,
 * tags are dropped and component JSON is read as plain text. Needs a running core.
 */
@ApiStatus.Experimental
public final class Markup {
  private Markup() {
  }

  /** {@code markup} as legacy {@code §}-formatted text, for places that only show legacy formatting. */
  public static String legacy(String markup) {
    return Engine.bridge().text().markupLegacy(Objects.requireNonNull(markup, "markup"));
  }

  /** {@code text} with every tag opener escaped, so player text shows as written inside markup. */
  public static String escape(String text) {
    return Engine.bridge().text().markupEscape(Objects.requireNonNull(text, "text"));
  }

  /** A JSON text component, such as a stored custom name, as markup. */
  public static String fromComponentJson(String json) {
    return Engine.bridge().text().markupFromJson(Objects.requireNonNull(json, "json"));
  }
}
