package art.arcane.adapt.api.preference;

import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PlayerPreference<E extends Enum<E>> {
  private final Class<E> type;
  private final Definition<E> definition;
  private final PreferencePolicy defaultPolicy;

  public PlayerPreference(Class<E> type, Definition<E> definition) {
    this.type = Objects.requireNonNull(type);
    this.definition = Objects.requireNonNull(definition);
    Set<E> values = new HashSet<>();
    for (Choice<E> choice : definition.choices()) {
      if (!type.isInstance(choice.value()) || !values.add(choice.value())) {
        throw new IllegalArgumentException("Invalid or duplicate choice for preference " + definition.id());
      }
    }
    if (!values.contains(definition.defaultValue())) {
      throw new IllegalArgumentException("Missing default choice for preference " + definition.id());
    }
    defaultPolicy = PreferencePolicy.defaults(this);
  }

  public String id() {
    return definition.id();
  }

  public TextKey label() {
    return definition.label();
  }

  public E defaultValue() {
    return definition.defaultValue();
  }

  public List<Choice<E>> choices() {
    return definition.choices();
  }

  public Choice<E> choice(E value) {
    for (Choice<E> choice : choices()) {
      if (choice.value() == value) {
        return choice;
      }
    }
    throw new IllegalArgumentException("Unknown choice for preference " + id() + ": " + value);
  }

  public E parse(String value) {
    if (value == null) {
      return null;
    }
    for (Choice<E> choice : choices()) {
      if (choice.value().name().equals(value)) {
        return choice.value();
      }
    }
    return null;
  }

  PreferencePolicy defaultPolicy() {
    return defaultPolicy;
  }

  public record Definition<E extends Enum<E>>(String id, TextKey label, E defaultValue, List<Choice<E>> choices) {
    public Definition {
      Objects.requireNonNull(id);
      Objects.requireNonNull(label);
      Objects.requireNonNull(defaultValue);
      choices = List.copyOf(choices);
      if (!id.matches("[a-z][a-z0-9-]*") || choices.isEmpty()) {
        throw new IllegalArgumentException("Invalid preference definition: " + id);
      }
    }
  }

  public record Choice<E extends Enum<E>>(E value, TextKey label, Material icon, int minimumLevel) {
    public Choice {
      Objects.requireNonNull(value);
      Objects.requireNonNull(label);
      Objects.requireNonNull(icon);
      if (minimumLevel < 1) {
        throw new IllegalArgumentException("Preference choices require a positive unlock level");
      }
    }
  }
}
