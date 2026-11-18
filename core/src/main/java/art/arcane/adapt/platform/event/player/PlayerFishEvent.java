package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;

/** Fishing rod state change. Mutable: experience, cancelled. */
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

  public EntityRef hook() {
    return hook;
  }

  public State state() {
    return state;
  }

  public int experience() {
    return experience;
  }

  public void setExperience(int experience) {
    this.experience = Math.max(0, experience);
  }

  public enum State {
    FISHING,
    CAUGHT_FISH,
    CAUGHT_ENTITY,
    IN_GROUND,
    FAILED_ATTEMPT,
    REEL_IN,
    BITE,
    LURED
  }
}
