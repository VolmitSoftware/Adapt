package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * What a button means, for clients that draw menus natively: a toggle, a number or text field, a choice from a list,
 * navigation or a destructive action. Chest menus ignore hints. Made through the factories.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class MenuHint {
  private final Kind kind;
  private final String value;
  private final MenuInput input;
  private final List<String> options;
  private final Double min;
  private final Double max;
  private final Double step;

  private MenuHint(Kind kind, String value, MenuInput input, List<String> options, Double min, Double max,
                   Double step) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.value = value == null ? "" : value;
    this.input = input;
    this.options = options == null ? List.of() : List.copyOf(options);
    this.min = min;
    this.max = max;
    this.step = step;
  }

  /** An on/off toggle; the value is {@code ON} or {@code OFF}. */
  public static MenuHint toggle(boolean on) {
    return new MenuHint(Kind.TOGGLE, on ? "ON" : "OFF", null, null, null, null, null);
  }

  /** A number field without known bounds or step. */
  public static MenuHint number(String current, MenuInput input) {
    return new MenuHint(Kind.NUMBER, current, input, null, null, null, null);
  }

  /** A number field; any bound or the step may be null. */
  public static MenuHint number(String current, Double min, Double max, Double step, MenuInput input) {
    return new MenuHint(Kind.NUMBER, current, input, null, min, max, step);
  }

  /** A free text field. */
  public static MenuHint text(String current, MenuInput input) {
    return new MenuHint(Kind.TEXT, current, input, null, null, null, null);
  }

  /** A choice from {@code options}, in order. */
  public static MenuHint choice(String current, List<String> options, MenuInput input) {
    return new MenuHint(Kind.CHOICE, current, input, options, null, null, null);
  }

  /** A button that opens {@code target}, the name of another menu. */
  public static MenuHint navigation(String target) {
    return new MenuHint(Kind.NAVIGATION, target, null, null, null, null, null);
  }

  /** A destructive action described by {@code label}. */
  public static MenuHint destructive(String label) {
    return new MenuHint(Kind.DESTRUCTIVE, label, null, null, null, null, null);
  }

  /** What the button means. */
  public Kind kind() {
    return kind;
  }

  /** The current value as text, never null. */
  public String value() {
    return value;
  }

  /** Receives typed values of a NUMBER, TEXT or CHOICE hint, or null. */
  public MenuInput input() {
    return input;
  }

  /** Choices of a CHOICE hint in order, never null. */
  public List<String> options() {
    return options;
  }

  /** Lowest NUMBER value, or null. */
  public Double min() {
    return min;
  }

  /** Highest NUMBER value, or null. */
  public Double max() {
    return max;
  }

  /** NUMBER increment, or null. */
  public Double step() {
    return step;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MenuHint hint && kind == hint.kind && value.equals(hint.value)
        && Objects.equals(input, hint.input) && options.equals(hint.options) && Objects.equals(min, hint.min)
        && Objects.equals(max, hint.max) && Objects.equals(step, hint.step);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, value, input, options, min, max, step);
  }

  @Override
  public String toString() {
    return "MenuHint[kind=" + kind + ", value=" + value + ", input=" + input + ", options=" + options + ", min=" + min
        + ", max=" + max + ", step=" + step + "]";
  }

  /** What a hinted button means. A {@code switch} over this enum needs a default branch: minor releases add constants. */
  public enum Kind {
    TOGGLE,
    NUMBER,
    TEXT,
    NAVIGATION,
    DESTRUCTIVE,
    CHOICE
  }
}
