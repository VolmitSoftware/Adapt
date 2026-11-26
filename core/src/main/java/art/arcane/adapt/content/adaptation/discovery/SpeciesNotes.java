package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;

/** Field Notes' per-species kill book and the damage bonus it banks. */
public final class SpeciesNotes {
  private SpeciesNotes() {
  }

  /** The player credited with a kill Field Notes counts, or null for player victims, golem kills and kills with no player. */
  public static PlayerRef notedKiller(LivingRef victim) {
    PlayerRef killer = victim.killer();
    if (killer == null || victim instanceof PlayerRef || GolemKillCredit.isGolemKill(victim)) {
      return null;
    }
    return killer;
  }

  public static PlayerRef attacker(EntityRef damager) {
    if (damager instanceof PlayerRef direct) {
      return direct;
    }
    if (damager instanceof ProjectileRef projectile && projectile.shooter() instanceof PlayerRef shooter) {
      return shooter;
    }
    return null;
  }

  /** Banks {@code increment} more bonus against the species, up to {@code cap}; true on the first kill of the species. */
  public static boolean record(PlayerData data, Id species, double increment, double cap) {
    String key = bonusKey(species);
    double current = data.getStat(key);
    double target = DiscoveryFieldNotes.bankTarget(current, increment, cap);
    if (target > current) {
      data.addStat(key, target - current);
    }
    return current <= 0;
  }

  public static double bankedBonus(PlayerData data, Id species, double cap) {
    return Math.min(cap, data.getStat(bonusKey(species)));
  }

  private static String bonusKey(Id species) {
    return "discovery.field-notes.bonus." + DiscoveryNames.enumName(species);
  }
}
