package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A mace hit checks whether it counts as a smash attack. Posted once per hit. Not cancellable; set
 * {@link #setResult(UseResult)} instead: {@link UseResult#DENY} forces no smash, {@link UseResult#ALLOW} forces one and
 * {@link UseResult#DEFAULT} keeps {@link #originalResult()}.
 *
 * <p>On Bukkit servers only Paper 26.x posts it.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityAttemptSmashAttackEvent extends AdaptEvent {
  private final LivingRef entity;
  private final LivingRef target;
  private final ItemRef weapon;
  private final boolean originalResult;
  private UseResult result;

  public EntityAttemptSmashAttackEvent(LivingRef entity, LivingRef target, ItemRef weapon, boolean originalResult, UseResult result) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.target = Objects.requireNonNull(target, "target");
    this.weapon = Objects.requireNonNull(weapon, "weapon");
    this.originalResult = originalResult;
    this.result = Objects.requireNonNull(result, "result");
  }

  /** The attacker swinging the mace. */
  public LivingRef entity() {
    return entity;
  }

  /** The living entity being hit. */
  public LivingRef target() {
    return target;
  }

  /** The weapon the attacker holds. */
  public ItemRef weapon() {
    return weapon;
  }

  /** Whether vanilla treats the hit as a smash attack. */
  public boolean originalResult() {
    return originalResult;
  }

  /** The override handlers chose; {@link UseResult#DEFAULT} keeps the vanilla decision. */
  public UseResult result() {
    return result;
  }

  /**
   * Overrides the smash decision.
   *
   * @throws NullPointerException when {@code result} is null
   */
  public void setResult(UseResult result) {
    this.result = Objects.requireNonNull(result, "result");
  }

  /** Whether the attack ends up as a smash attack after handlers ran. */
  public boolean smashes() {
    return result == UseResult.DEFAULT ? originalResult : result == UseResult.ALLOW;
  }
}
