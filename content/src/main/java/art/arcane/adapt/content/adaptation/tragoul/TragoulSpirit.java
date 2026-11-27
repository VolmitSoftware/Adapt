package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

/**
 * The spirit form a defied killing blow leaves behind: the hit is cancelled, the player stays at 1 HP, turns invisible
 * and resistant for the spirit's duration, and mobs hunting them lose their target.
 */
public final class TragoulSpirit {
  public static final Id INVISIBILITY = Id.minecraft("invisibility");
  public static final Id RESISTANCE = Id.minecraft("resistance");

  private TragoulSpirit() {
  }

  public static void enter(Adaptation<?> owner, PlayerRef p, EntityDamageEvent e, int spiritTicks,
                           int resistanceAmplifier, double clearRadius) {
    e.setCancelled(true);
    p.setHealth(1.0);
    p.addEffect(EffectInstance.of(INVISIBILITY, spiritTicks, 0).withAmbient(true).withParticles(false));
    p.addEffect(EffectInstance.of(RESISTANCE, spiritTicks, resistanceAmplifier).withAmbient(true).withParticles(false));

    for (EntityRef entity : p.nearbyEntities(clearRadius, clearRadius, clearRadius)) {
      if (!(entity instanceof MobRef mob) || !isTargeting(mob, p)) {
        continue;
      }

      AdaptApi.scheduler().runEntity(mob, () -> {
        if (mob.isValid() && isTargeting(mob, p)) {
          mob.setTarget(null);
        }
      });
    }

    TragoulLastRites.spiritEnterFx(owner, p, clearRadius);
    AdaptApi.scheduler().runEntity(p, () -> TragoulLastRites.spiritExitFx(owner, p), spiritTicks);
  }

  private static boolean isTargeting(MobRef mob, PlayerRef player) {
    EntityRef target = mob.target();
    return target != null && target.uuid().equals(player.uuid());
  }
}
