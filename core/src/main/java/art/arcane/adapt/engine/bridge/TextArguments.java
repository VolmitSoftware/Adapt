package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fits the arguments of an API {@code Text} to the placeholders its message declares, so one wrong placeholder in an
 * addon's bundle shows in the message instead of failing the code that renders it.
 */
final class TextArguments {
  private static final Set<String> MISMATCHED = ConcurrentHashMap.newKeySet();

  private TextArguments() {
  }

  /**
   * {@code given} restricted to the placeholders of {@code key}. A placeholder no argument fills keeps its
   * {@code {name}} form, an argument the message does not name is dropped, and the mismatch is logged once per key.
   */
  static MessageArgs fit(MessageKey key, MessageArgs given) {
    Set<String> expected = key.placeholders();
    if (expected.equals(given.names()) || AdaptLanguage.declared(key.id()) == null) {
      return given;
    }
    MessageArgs.Builder fitted = MessageArgs.builder();
    Set<String> unfilled = new LinkedHashSet<>();
    for (String name : expected) {
      MessageArgument argument = given.argument(name);
      if (argument != null) {
        fitted.add(argument);
      } else if (key instanceof PluralKey plural && plural.selectorArgument().equals(name)) {
        unfilled.add(name);
        fitted.untrusted(name, 0);
      } else {
        unfilled.add(name);
        fitted.untrusted(name, "{" + name + "}");
      }
    }
    Set<String> unused = new LinkedHashSet<>(given.names());
    unused.removeAll(expected);
    if (MISMATCHED.add(key.id())) {
      AdaptLog.warn("Message " + key.id() + " does not match its arguments: no argument for "
          + placeholders(unfilled) + ", nothing in the message for " + placeholders(unused)
          + ". Placeholders are written {name}.");
    }
    return fitted.build();
  }

  private static String placeholders(Set<String> names) {
    if (names.isEmpty()) {
      return "none";
    }
    StringBuilder listed = new StringBuilder();
    for (String name : names) {
      if (!listed.isEmpty()) {
        listed.append(", ");
      }
      listed.append('{').append(name).append('}');
    }
    return listed.toString();
  }
}
