package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.pack.link.MenuStyle;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;

/**
 * What pack screens ask the server to do, through the same operations and checks the native client's frames and the
 * chest GUI use. Every call runs on the viewer's owning thread.
 */
public interface PackActions {
  Outcome learn(PlayerRef viewer, String adaptationId, int level);

  Outcome unlearn(PlayerRef viewer, String adaptationId, int level);

  boolean setPreference(PlayerRef viewer, PreferenceOwner owner, String ownerId, String preferenceId, String value);

  boolean resetPreference(PlayerRef viewer, PreferenceOwner owner, String ownerId);

  /** Merges {@code partner} into {@code lead}; null when the viewer cannot mutate now. */
  Mutation mutate(PlayerRef viewer, String lead, String partner);

  /** Undoes the viewer's mutation {@code skillId}; the result text, or null when nothing was undone. */
  String unmutate(PlayerRef viewer, String skillId);

  /** Sets the viewer's own choice of menus; null follows the pack status again. */
  void menus(PlayerRef viewer, MenuStyle style);

  /** Opens a screen with the checks of a player opening it themselves, as the native client's Open frame does. */
  void open(PlayerRef viewer, Route route);

  record Outcome(boolean changed, String message) {
    public Outcome {
      Objects.requireNonNull(message, "message");
    }
  }

  record Mutation(boolean mutated, String text, MutationSpreadView spread) {
    public Mutation {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(spread, "spread");
    }
  }
}
