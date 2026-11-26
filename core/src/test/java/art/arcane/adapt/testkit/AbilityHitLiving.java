package art.arcane.adapt.testkit;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

/**
 * A mob that, like a server, fires a player-attack damage event through Adapt when a player damages it directly, and
 * counts how often {@code probe} would have read that event as the player's melee hit.
 */
public final class AbilityHitLiving extends FakeLiving {
  private final TestAdapt adapt;
  private final Adaptation<?> probe;
  private int meleeHits;

  public AbilityHitLiving(TestAdapt adapt, Adaptation<?> probe, Id type, Loc location) {
    super(type, location);
    this.adapt = adapt;
    this.probe = probe;
  }

  @Override
  public void damage(double amount, EntityRef source) {
    if (!(source instanceof PlayerRef)) {
      super.damage(amount, source);
      return;
    }
    EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(this, source, source, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), amount, false, FinalDamage.IDENTITY);
    if (probe.resolveAttackContext(event) != null) {
      meleeHits++;
    }
    adapt.post(event);
    if (!event.isCancelled()) {
      super.damage(event.damage(), source);
    }
  }

  /** Player damage events the probe upgrade took for a melee hit. */
  public int meleeHits() {
    return meleeHits;
  }
}
