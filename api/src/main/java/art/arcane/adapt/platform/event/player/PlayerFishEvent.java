package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A fishing rod changes state: a cast, a bite, a catch or a reel in, as {@link #state()} says. Cancelling stops
 * that step: no cast, no bite, no catch, or the hook stays out on a reel in. Mutable: experience, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerFishEvent extends PlayerEvent {
  private final EntityRef caught;
  private final EntityRef hook;
  private final State state;
  private int experience;

  public PlayerFishEvent(PlayerRef player, EntityRef caught, EntityRef hook, State state, int experience) {
    super(player);
    this.caught = caught;
    this.hook = Objects.requireNonNull(hook, "hook");
    this.state = Objects.requireNonNull(state, "state");
    this.experience = experience;
  }

  /** Caught item entity or hooked entity, or null. */
  public EntityRef caught() {
    return caught;
  }

  /** The fishing hook entity. */
  public EntityRef hook() {
    return hook;
  }

  /** Which step of fishing this is. */
  public State state() {
    return state;
  }

  /** Experience dropped for a caught fish; 0 for other states. */
  public int experience() {
    return experience;
  }

  /** Changes the experience a caught fish drops, clamped at 0. */
  public void setExperience(int experience) {
    this.experience = Math.max(0, experience);
  }

  /**
   * Fishing rod states, matching Bukkit's fish states.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum State {
    /** The rod was cast. */
    FISHING,
    /** An item was reeled in. */
    CAUGHT_FISH,
    /** A hooked entity was pulled in. */
    CAUGHT_ENTITY,
    /** The rod was reeled in while the hook rested on a block. */
    IN_GROUND,
    /** A fish got away before the player reeled in. */
    FAILED_ATTEMPT,
    /** The rod was reeled in with nothing caught. */
    REEL_IN,
    /** A fish bites the hook. */
    BITE,
    /** A fish is approaching the hook. */
    LURED
  }
}
