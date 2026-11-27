package art.arcane.adapt.api.event;

import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player is about to gain XP in a skill. The amount is what remains after multipliers and anti-farm rules; a
 * listener may change it. Not fired for XP that those rules reduced to nothing.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class SkillXpEvent extends ProgressEvent {
  private final Id skill;
  private final String source;
  private final XpAward.Kind kind;
  private double amount;

  public SkillXpEvent(PlayerRef player, Id skill, double amount, String source, XpAward.Kind kind) {
    super(player);
    this.skill = Objects.requireNonNull(skill, "skill");
    this.source = Objects.requireNonNull(source, "source");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.amount = amount;
  }

  /** The line that receives the XP. For a mutated line this is the lead skill. */
  public Id skill() {
    return skill;
  }

  /** The XP to be granted, positive and finite. */
  public double amount() {
    return amount;
  }

  /** Changes the XP granted; a value that is not positive and finite cancels the event. */
  public void setAmount(double amount) {
    if (!Double.isFinite(amount) || amount <= 0D) {
      setCancelled(true);
      return;
    }
    this.amount = amount;
  }

  /**
   * The reward key the XP was granted under: free-form, as the granting skill passed it. {@link
   * art.arcane.adapt.api.content.XpSource} awards use {@code <skill id>:<source>} with an optional {@code :<detail>},
   * such as {@code alchemist:alchemy:brewing} or {@code example:geology:stone:stone}.
   */
  public String source() {
    return source;
  }

  /** Whether the XP comes from play, from a consumable or from an admin action. */
  public XpAward.Kind kind() {
    return kind;
  }
}
