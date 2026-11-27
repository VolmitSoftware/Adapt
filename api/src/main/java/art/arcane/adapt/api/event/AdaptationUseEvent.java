package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An adaptation is about to act for a player. Fired when its code asks {@code canUse(player)}; cancelling makes that
 * call return false. Passive adaptations that never ask are not reported.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptationUseEvent extends ProgressEvent {
  private final Id adaptation;
  private final Id skill;
  private final int level;

  public AdaptationUseEvent(PlayerRef player, Id adaptation, Id skill, int level) {
    super(player);
    this.adaptation = Objects.requireNonNull(adaptation, "adaptation");
    this.skill = Objects.requireNonNull(skill, "skill");
    this.level = level;
  }

  /** The adaptation about to act. */
  public Id adaptation() {
    return adaptation;
  }

  /** The skill the adaptation belongs to. */
  public Id skill() {
    return skill;
  }

  /** The player's level in the adaptation. */
  public int level() {
    return level;
  }
}
