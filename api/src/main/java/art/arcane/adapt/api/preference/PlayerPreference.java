package art.arcane.adapt.api.preference;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A choice each player makes for one skill or adaptation: a toggle, a mode, a strength. Declared once as a constant
 * and returned from the content's {@code getPlayerPreferences()}. Adapt saves the choice per player, shows it in the
 * preference menu, and lets the server lock or restrict it through a {@link PreferencePolicy} in the content's
 * config. The save stores a choice by its constant's name, so renaming a constant resets every player who chose
 * it; adding or reordering constants keeps saved choices.
 *
 * <p>A label left empty is looked up by convention, relative to the content that declares the preference:
 * {@code preference.<id>.name} for the preference and {@code preference.<id>.choice.<value>} for a choice, with the
 * value in lower case. In a bundle file for the adaptation {@code alchemist:alchemy/transmute}, with the choices in
 * a table of their own because bundle files take no dotted keys:
 *
 * <pre>
 * [adaptation.alchemy.transmute.preference.delivery]
 * name = "Delivery"
 *
 * [adaptation.alchemy.transmute.preference.delivery.choice]
 * fast = "Fast"
 * gentle = "Gentle"
 * </pre>
 */
@ApiStatus.AvailableSince("1.0.0")
public final class PlayerPreference<E extends Enum<E>> {
  private static final Id DEFAULT_CHOICE_ICON = Id.minecraft("paper");

  private final Class<E> type;
  private final String id;
  private final Optional<Text> label;
  private final E defaultValue;
  private final List<Choice<E>> choices;

  private PlayerPreference(Class<E> type, String id, Optional<Text> label, E defaultValue, List<Choice<E>> choices) {
    this.type = Objects.requireNonNull(type, "type");
    this.id = Objects.requireNonNull(id, "id");
    this.label = Objects.requireNonNull(label, "label");
    this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    this.choices = List.copyOf(choices);
    if (!id.matches("[a-z][a-z0-9-]*") || this.choices.isEmpty()) {
      throw new IllegalArgumentException("Invalid preference definition: " + id);
    }
    Set<E> values = new HashSet<>();
    for (Choice<E> choice : this.choices) {
      if (!values.add(choice.value())) {
        throw new IllegalArgumentException("Duplicate choice " + choice.value() + " for preference " + id);
      }
    }
    if (!values.contains(defaultValue)) {
      throw new IllegalArgumentException("Missing default choice for preference " + id);
    }
  }

  /** A preference offering every constant of {@code type}, each from level 1, with labels by convention. */
  public static <E extends Enum<E>> PlayerPreference<E> of(String id, Class<E> type, E defaultValue) {
    List<Choice<E>> choices = new ArrayList<>();
    for (E value : type.getEnumConstants()) {
      choices.add(new Choice<>(value, Optional.empty(), DEFAULT_CHOICE_ICON, DEFAULT_CHOICE_ICON, 1));
    }
    return new PlayerPreference<>(type, id, Optional.empty(), defaultValue, choices);
  }

  /** A preference with explicit choices, for icons, level gates or a subset of the enum. */
  public static <E extends Enum<E>> PlayerPreference<E> of(String id, Class<E> type, E defaultValue,
                                                             List<Choice<E>> choices) {
    return new PlayerPreference<>(type, id, Optional.empty(), defaultValue, choices);
  }

  /** The same preference with an explicit label instead of the conventional key. */
  public PlayerPreference<E> labelled(Text text) {
    return new PlayerPreference<>(type, id, Optional.of(text), defaultValue, choices);
  }

  /** The enum the choices are constants of. */
  public Class<E> type() {
    return type;
  }

  /** Unique within the content that declares it; the key in the save and in the config's policy map. */
  public String id() {
    return id;
  }

  /** The explicit label; empty means the key {@link #conventionalKey()}. */
  public Optional<Text> label() {
    return label;
  }

  /** The value in effect until the player chooses, unless the server's policy names another. */
  public E defaultValue() {
    return defaultValue;
  }

  /** The selectable values, in menu order. */
  public List<Choice<E>> choices() {
    return choices;
  }

  /**
   * The choice for {@code value}.
   *
   * @throws IllegalArgumentException when the value is not one of the choices
   */
  public Choice<E> choice(E value) {
    for (Choice<E> choice : choices) {
      if (choice.value() == value) {
        return choice;
      }
    }
    throw new IllegalArgumentException("Unknown choice for preference " + id + ": " + value);
  }

  /** The choice with this constant name, or empty. */
  public Optional<E> parse(String name) {
    if (name == null) {
      return Optional.empty();
    }
    for (Choice<E> choice : choices) {
      if (choice.value().name().equals(name)) {
        return Optional.of(choice.value());
      }
    }
    return Optional.empty();
  }

  /** The conventional key of this preference's label below its owner's key. */
  public String conventionalKey() {
    return "preference." + id + ".name";
  }

  /** The conventional key of a choice's label below its owner's key. */
  public String conventionalKey(E value) {
    return "preference." + id + ".choice." + value.name().toLowerCase(Locale.ROOT);
  }

  /**
   * One selectable value.
   *
   * @param value        the enum constant the choice stands for
   * @param label        the shown name; empty for the conventional key
   * @param icon         the item shown for the choice
   * @param fallbackIcon the item shown on a Minecraft version that lacks {@code icon}
   * @param minimumLevel the level of the owning adaptation that unlocks the choice, at least 1
   */
  public record Choice<E extends Enum<E>>(E value, Optional<Text> label, Id icon, Id fallbackIcon, int minimumLevel) {
    public Choice {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(icon, "icon");
      Objects.requireNonNull(fallbackIcon, "fallbackIcon");
      if (minimumLevel < 1) {
        throw new IllegalArgumentException("Preference choices require a positive unlock level");
      }
    }

    /** A choice with an explicit label and one icon on every version. */
    public Choice(E value, Text label, Id icon, int minimumLevel) {
      this(value, Optional.of(label), icon, icon, minimumLevel);
    }

    /** A choice labelled by convention, with one icon on every version. */
    public Choice(E value, Id icon, int minimumLevel) {
      this(value, Optional.empty(), icon, icon, minimumLevel);
    }
  }
}
