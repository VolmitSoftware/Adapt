package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;

/** Takes a share of a fall's damage away and pays skill XP for what it prevented. */
public final class KineticsCushion {
  private final Adaptation<?> owner;
  private final String rewardKey;

  public KineticsCushion(Adaptation<?> owner, String rewardKey) {
    this.owner = owner;
    this.rewardKey = rewardKey;
  }

  public record Softening(double reduction, double xpPerDamagePrevented, double xpPerEventCap) {
  }

  /** Lowers the fall damage by the softening's reduction; returns the damage prevented. */
  public double soften(PlayerRef p, EntityDamageEvent e, Softening softening) {
    double before = e.damage();
    double after = KineticsSoftCatch.reducedDamage(before, softening.reduction());
    double prevented = before - after;
    if (prevented <= 0D) {
      return 0D;
    }

    e.setDamage(after);
    KineticsSoftCatch.cushionFx(owner, p.location());
    double reward = KineticsSoftCatch.rewardXp(prevented, softening.xpPerDamagePrevented(), softening.xpPerEventCap());
    if (reward > 0D) {
      owner.xp(p, p.location(), reward, rewardKey);
    }
    return prevented;
  }
}
