package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player is about to learn an adaptation or raise its level. Fired after every rule passed, before payment. */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptationLearnEvent extends ProgressEvent {
  private final Id adaptation;
  private final Id skill;
  private final int fromLevel;
  private final int level;
  private final long cost;
  private final boolean forced;

  public AdaptationLearnEvent(PlayerRef player, Id adaptation, Id skill, int fromLevel, int level, long cost,
                              boolean forced) {
    super(player);
    this.adaptation = Objects.requireNonNull(adaptation, "adaptation");
    this.skill = Objects.requireNonNull(skill, "skill");
    this.fromLevel = fromLevel;
    this.level = level;
    this.cost = cost;
    this.forced = forced;
  }

  /** The adaptation being learned. */
  public Id adaptation() {
    return adaptation;
  }

  /** The skill whose knowledge pays for it. */
  public Id skill() {
    return skill;
  }

  /** The level the player has now; 0 when they have not learned it yet. */
  public int fromLevel() {
    return fromLevel;
  }

  /** The level the player will have. */
  public int level() {
    return level;
  }

  /** Knowledge that will be spent; 0 when forced. */
  public long cost() {
    return cost;
  }

  /** True for an admin command or a forced API call, which charge nothing and skip the rules. */
  public boolean forced() {
    return forced;
  }
}
