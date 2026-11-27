package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player is about to unlearn an adaptation or lower its level. */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptationUnlearnEvent extends ProgressEvent {
  private final Id adaptation;
  private final Id skill;
  private final int fromLevel;
  private final int level;
  private final long refund;
  private final boolean forced;

  public AdaptationUnlearnEvent(PlayerRef player, Id adaptation, Id skill, int fromLevel, int level, long refund,
                                boolean forced) {
    super(player);
    this.adaptation = Objects.requireNonNull(adaptation, "adaptation");
    this.skill = Objects.requireNonNull(skill, "skill");
    this.fromLevel = fromLevel;
    this.level = level;
    this.refund = refund;
    this.forced = forced;
  }

  /** The adaptation being unlearned. */
  public Id adaptation() {
    return adaptation;
  }

  /** The skill whose knowledge the refund returns to; for a mutated line, the lead skill. */
  public Id skill() {
    return skill;
  }

  /** The level the player has now. */
  public int fromLevel() {
    return fromLevel;
  }

  /** The level the player will have; 0 when the adaptation is dropped. */
  public int level() {
    return level;
  }

  /** Knowledge that will be returned; 0 when forced. */
  public long refund() {
    return refund;
  }

  /** True for an admin command or a forced API call, which return no knowledge. */
  public boolean forced() {
    return forced;
  }
}
