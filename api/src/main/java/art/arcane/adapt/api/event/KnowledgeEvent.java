package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player's unspent knowledge in a skill is about to change outside of learning: a level-up grant, a command, an
 * API call. Spending and refunds are reported by the learn and unlearn events instead.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class KnowledgeEvent extends ProgressEvent {
  private final Id skill;
  private long amount;

  public KnowledgeEvent(PlayerRef player, Id skill, long amount) {
    super(player);
    this.skill = Objects.requireNonNull(skill, "skill");
    this.amount = amount;
  }

  /** The skill whose knowledge changes; for a mutated line, the lead skill. */
  public Id skill() {
    return skill;
  }

  /** Knowledge added; negative when it is taken away. */
  public long amount() {
    return amount;
  }

  /** Changes the knowledge added; cancelling the event adds none. */
  public void setAmount(long amount) {
    this.amount = amount;
  }
}
