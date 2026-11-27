package art.arcane.adapt.api.event;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player is about to split a mutation back into its two skills. */
@ApiStatus.Experimental
public final class SkillUnmutateEvent extends ProgressEvent {
  private final SkillPair pair;

  public SkillUnmutateEvent(PlayerRef player, SkillPair pair) {
    super(player);
    this.pair = Objects.requireNonNull(pair, "pair");
  }

  /** The lead and partner skill of the mutation being split. */
  public SkillPair pair() {
    return pair;
  }
}
