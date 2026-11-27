package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;

/** Ashwalker's full negation of a magma or campfire burn. */
public final class NetherAshGuard {
  private NetherAshGuard() {
  }

  /** Cancels the burn, puts the player out and plays the ash puff; returns the damage that was prevented. */
  public static double negate(Adaptation<?> owner, PlayerRef p, EntityDamageEvent e, boolean magma, float soundVolume) {
    double before = e.damage();
    NetherAshwalker.fullyNegateDamage(e);
    p.setFireTicks(0);
    NetherAshwalker.ashFx(owner, p, magma, soundVolume);
    return before;
  }
}
