package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;

import java.util.function.ObjDoubleConsumer;

/**
 * Hungry Shield's absorption: part of a hit is paid from food and saturation instead of health, never taking food under
 * {@link #MIN_FOOD}. Damage over time is charged at most once per interval.
 */
public final class HerbalismHungerShield {
  public static final int MIN_FOOD = 6;

  private final Adaptation<?> owner;
  private final Cooldowns shieldBreakCooldown;
  private final Cooldowns absorbFxCooldown;
  private final Cooldowns dotChargeCooldown;
  private final ObjDoubleConsumer<PlayerRef> onAbsorbed;

  /** {@code onAbsorbed} receives the damage taken from hunger. */
  public HerbalismHungerShield(Adaptation<?> owner, Cooldowns shieldBreakCooldown, Cooldowns absorbFxCooldown,
                               Cooldowns dotChargeCooldown, ObjDoubleConsumer<PlayerRef> onAbsorbed) {
    this.owner = owner;
    this.shieldBreakCooldown = shieldBreakCooldown;
    this.absorbFxCooldown = absorbFxCooldown;
    this.dotChargeCooldown = dotChargeCooldown;
    this.onAbsorbed = onAbsorbed;
  }

  /** Absorbs part of the hit; {@code keepReserve} refuses any absorption that leaves under 8 food and saturation. */
  public void absorb(PlayerRef p, EntityDamageEvent e, double effectiveness, boolean keepReserve, long dotChargeIntervalMs) {
    boolean overTime = isDamageOverTime(e.cause());
    if (overTime && !dotChargeCooldown.isReady(p.uuid(), dotChargeIntervalMs)) {
      return;
    }

    double available = p.foodLevel() + p.saturation();
    double absorbed = absorbedDamage(e.damage(), effectiveness, available, MIN_FOOD);
    if (absorbed <= 0) {
      shieldBreak(p, e.damage());
      return;
    }

    if (keepReserve && available - absorbed < 8) {
      return;
    }
    PlayerProfile profile = AdaptApi.players().find(p).orElse(null);
    if (profile == null || !owner.payHungerCost(p, "hunger", (int) Math.ceil(absorbed), () -> {
      profile.chargeFood(absorbed);
      return true;
    })) {
      shieldBreak(p, e.damage());
      return;
    }

    if (overTime) {
      dotChargeCooldown.mark(p.uuid());
    }

    e.setDamage(Math.max(0D, e.damage() - absorbed));
    onAbsorbed.accept(p, absorbed);

    if (absorbFxCooldown.isReady(p.uuid(), 500L)) {
      absorbFxCooldown.mark(p.uuid());
      int intensity = (int) Math.max(3, Math.min(12, Math.ceil(absorbed)));
      double ringRadius = Math.min(1.6D, 0.7D + (absorbed * 0.08D));
      HerbalismHungryShield.absorbFx(owner, p, ringRadius, intensity);
    }
  }

  private void shieldBreak(PlayerRef p, double damage) {
    if (damage <= 2 || !shieldBreakCooldown.isReady(p.uuid(), 1500L)) {
      return;
    }

    shieldBreakCooldown.mark(p.uuid());
    HerbalismHungryShield.shieldBreakFx(owner, p);
  }

  static double absorbedDamage(double damage, double effectiveness, double availableFood, int minFood) {
    if (!Double.isFinite(damage)
        || !Double.isFinite(effectiveness)
        || !Double.isFinite(availableFood)
        || damage <= 0D
        || effectiveness <= 0D) {
      return 0D;
    }

    return Math.min(damage * effectiveness, Math.max(0D, availableFood - minFood));
  }

  static boolean isDamageOverTime(DamageCause cause) {
    if (cause == null) {
      return false;
    }

    return switch (cause) {
      case FIRE, FIRE_TICK, LAVA, CAMPFIRE, HOT_FLOOR, POISON, WITHER, DROWNING, FREEZE -> true;
      default -> false;
    };
  }
}
