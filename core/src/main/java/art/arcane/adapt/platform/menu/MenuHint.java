package art.arcane.adapt.platform.menu;

import java.util.List;
import java.util.Objects;

/**
 * What a button means, for clients that draw menus natively: a toggle, a number or text field, a choice from a list,
 * navigation or a destructive action. {@code value} is the current value as text. {@code input} receives typed values
 * (NUMBER, TEXT, CHOICE). A NUMBER may name its bounds and step, null when unknown; a CHOICE lists its options in
 * order. Chest menus ignore hints.
 */
public record MenuHint(Kind kind, String value, MenuInput input, List<String> options, Double min, Double max,
                       Double step) {
  public MenuHint {
    Objects.requireNonNull(kind, "kind");
    value = value == null ? "" : value;
    options = options == null ? List.of() : List.copyOf(options);
    if (input != null && kind != Kind.NUMBER && kind != Kind.TEXT && kind != Kind.CHOICE) {
      throw new IllegalArgumentException("only NUMBER, TEXT and CHOICE hints take input: " + kind);
    }
  }

  public static MenuHint toggle(boolean on) {
    return new MenuHint(Kind.TOGGLE, on ? "ON" : "OFF", null, null, null, null, null);
  }

  public static MenuHint number(String current, MenuInput input) {
    return new MenuHint(Kind.NUMBER, current, input, null, null, null, null);
  }

  public static MenuHint number(String current, Double min, Double max, Double step, MenuInput input) {
    return new MenuHint(Kind.NUMBER, current, input, null, min, max, step);
  }

  public static MenuHint text(String current, MenuInput input) {
    return new MenuHint(Kind.TEXT, current, input, null, null, null, null);
  }

  public static MenuHint choice(String current, List<String> options, MenuInput input) {
    return new MenuHint(Kind.CHOICE, current, input, options, null, null, null);
  }

  public static MenuHint navigation(String target) {
    return new MenuHint(Kind.NAVIGATION, target, null, null, null, null, null);
  }

  public static MenuHint destructive(String label) {
    return new MenuHint(Kind.DESTRUCTIVE, label, null, null, null, null, null);
  }

  public enum Kind {
    TOGGLE,
    NUMBER,
    TEXT,
    NAVIGATION,
    DESTRUCTIVE,
    CHOICE
  }
}
