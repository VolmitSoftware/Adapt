package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/**
 * A mace hit checks whether it is a smash attack. Mutable: result (DENY forces no smash, ALLOW
 * forces one, DEFAULT keeps originalResult).
 */
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

  public LivingRef entity() {
    return entity;
  }

  public LivingRef target() {
    return target;
  }

  public ItemRef weapon() {
    return weapon;
  }

  public boolean originalResult() {
    return originalResult;
  }

  public UseResult result() {
    return result;
  }

  public void setResult(UseResult result) {
    this.result = Objects.requireNonNull(result, "result");
  }

  /** Whether the attack ends up as a smash attack after handlers ran. */
  public boolean smashes() {
    return result == UseResult.DEFAULT ? originalResult : result == UseResult.ALLOW;
  }
}
