package art.arcane.adapt.api.text;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A message that is not yet in any language: a key into the installed {@link MessageBundle}s plus its arguments, or
 * a literal that is shown as written.
 *
 * <p>Immutable. {@link #with} and {@link #withMarkup} return a new instance. A message refers to an argument as
 * {@code {name}}. A message whose placeholders and arguments do not match still renders: an unfilled placeholder
 * shows as written, an argument the message does not name is dropped, and the key is logged once.
 *
 * <p>{@link #resolve()} gives the string for the player the engine is currently rendering for. Strings shown to
 * players use section-sign formatting codes, see {@link TextFormat}.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class Text {
  private final String key;
  private final String literal;
  private final List<Argument> arguments;

  private Text(String key, String literal, List<Argument> arguments) {
    this.key = key;
    this.literal = literal;
    this.arguments = arguments;
  }

  /** A message by its full key, such as {@code alchemist.skill.alchemy.name}. */
  public static Text key(String key) {
    Objects.requireNonNull(key, "key");
    if (key.isEmpty() || key.charAt(0) == '.' || key.charAt(key.length() - 1) == '.' || key.contains("..")) {
      throw new IllegalArgumentException("Invalid message key: '" + key + "'");
    }
    return new Text(key, null, List.of());
  }

  /** Text that is shown exactly as given, in every language. */
  public static Text literal(String text) {
    return new Text(null, Objects.requireNonNull(text, "text"), List.of());
  }

  /** Adds an argument whose value is shown as plain text; any formatting inside it is escaped. */
  public Text with(String name, Object value) {
    return withArgument(new Argument(name, String.valueOf(value), false));
  }

  /** Adds an argument whose value may carry formatting. Never pass player input here. */
  public Text withMarkup(String name, String markup) {
    return withArgument(new Argument(name, markup, true));
  }

  /** Adds another message as an argument; it is resolved in the same language as this one. */
  public Text with(String name, Text nested) {
    return withArgument(new Argument(name, Objects.requireNonNull(nested, "nested"), true));
  }

  /** Whether this is a literal rather than a keyed message. */
  public boolean isLiteral() {
    return literal != null;
  }

  /** The message key; throws for a literal. */
  public String key() {
    if (key == null) {
      throw new IllegalStateException("A literal text has no key");
    }
    return key;
  }

  /** The literal string; throws for a keyed message. */
  public String literal() {
    if (literal == null) {
      throw new IllegalStateException("A keyed text has no literal");
    }
    return literal;
  }

  /** The arguments in the order they were added. */
  public List<Argument> arguments() {
    return arguments;
  }

  /**
   * The string in the language of the player being rendered for, or the server's language outside a render. A key
   * no bundle defines resolves to the key itself and is logged once.
   */
  public String resolve() {
    return Engine.bridge().text().resolve(this);
  }

  /**
   * Runs {@code action} as a render for {@code viewer}: every {@link #resolve()} inside it, on this thread, uses the
   * viewer's language, or the server's for a null viewer such as the console. For building a menu or a label out of
   * many texts for one player.
   */
  @ApiStatus.Experimental
  public static void renderFor(@Nullable UUID viewer, Runnable action) {
    Objects.requireNonNull(action, "action");
    renderFor(viewer, () -> {
      action.run();
      return null;
    });
  }

  /** As {@link #renderFor(UUID, Runnable)}, returning what {@code action} returns. */
  @ApiStatus.Experimental
  public static <T> T renderFor(@Nullable UUID viewer, Supplier<T> action) {
    return Engine.bridge().text().renderFor(viewer, Objects.requireNonNull(action, "action"));
  }

  /** The string in {@code viewer}'s language. */
  public String resolve(PlayerRef viewer) {
    return Engine.bridge().text().resolve(this, Objects.requireNonNull(viewer, "viewer"));
  }

  /**
   * The string in a named locale such as {@code de_DE}, whoever is being rendered for; falls back to {@code en_US}
   * per message. A locale the server has no language file for renders in the server's language.
   */
  public String resolve(String locale) {
    return Engine.bridge().text().resolve(this, Objects.requireNonNull(locale, "locale"));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Text text && Objects.equals(key, text.key) && Objects.equals(literal, text.literal)
        && arguments.equals(text.arguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, literal, arguments);
  }

  @Override
  public String toString() {
    return literal != null ? "Text[\"" + literal + "\"]" : "Text[" + key + "]";
  }

  private Text withArgument(Argument argument) {
    List<Argument> extended = new ArrayList<>(arguments.size() + 1);
    extended.addAll(arguments);
    extended.add(argument);
    return new Text(key, literal, List.copyOf(extended));
  }

  /**
   * One named argument. {@code value} is a {@link String} or a nested {@link Text}; an untrusted value is escaped
   * before it is placed in the message.
   *
   * @param name    the placeholder it fills, {@code {name}} in the message; not empty
   * @param value   a {@link String} or a nested {@link Text}
   * @param trusted whether the value may carry formatting; false escapes it
   */
  public record Argument(String name, Object value, boolean trusted) {
    public Argument {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(value, "value");
      if (name.isEmpty()) {
        throw new IllegalArgumentException("A message argument needs a name");
      }
    }
  }
}
