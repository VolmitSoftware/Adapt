package art.arcane.adapt.api.event;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player is about to merge two skills into a mutation. */
@ApiStatus.Experimental
public final class SkillMutateEvent extends ProgressEvent {
  private final SkillPair pair;

  public SkillMutateEvent(PlayerRef player, SkillPair pair) {
    super(player);
    this.pair = Objects.requireNonNull(pair, "pair");
  }

  /** The lead and partner skill being merged. */
  public SkillPair pair() {
    return pair;
  }
}
