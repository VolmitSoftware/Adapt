package art.arcane.adapt.api.text;

import org.jetbrains.annotations.ApiStatus;

/** Where a {@link MessageBundle} puts one locale's messages. The engine supplies it. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface MessageSink {
  /** One message; {@code key} is relative to the bundle's namespace. */
  void message(String key, String text);

  /**
   * A whole TOML document. Tables nest keys: {@code [skill.alchemy]} with {@code name = "Alchemy"} is the message
   * {@code skill.alchemy.name}. An array of strings is a multi-line message. A table of plural forms ({@code one},
   * {@code other} and the other plural categories, {@code other} required) whose forms use one placeholder between
   * them is a plural message: {@code [item.count]} with {@code one = "{count} item"} and
   * {@code other = "{count} items"} picks its form by the number given for {@code count}.
   */
  void toml(String document);

  /** As {@link #toml(String)} for the document read from {@code file}, which errors about the document name. */
  void toml(String file, String document);
}
