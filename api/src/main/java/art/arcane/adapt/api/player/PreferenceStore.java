package art.arcane.adapt.api.player;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/** One player's saved preference choices, per skill or adaptation. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface PreferenceStore {
  /** The value in effect: the saved choice if the server permits it at the player's level, else the default. */
  <E extends Enum<E>> E get(Id owner, PlayerPreference<E> preference);

  /** The saved choice by name, whether or not it is currently permitted. */
  Optional<String> saved(Id owner, String preference);

  /**
   * Saves a choice.
   *
   * @return false when the owner has no such preference, the value is not one of its choices, or the server does
   *     not permit it
   */
  <E extends Enum<E>> boolean set(Id owner, PlayerPreference<E> preference, E value);

  /** Saves a choice by the name of the preference and of the value, for commands and placeholders. */
  boolean set(Id owner, String preference, String value);

  /** Forgets the saved choice, so the default is in effect again. */
  void clear(Id owner, String preference);
}
