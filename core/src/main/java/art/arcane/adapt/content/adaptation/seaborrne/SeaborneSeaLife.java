package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;

/** Fish Whisperer's sea luck and the dolphins and axolotls that join the angler's fights. */
public final class SeaborneSeaLife {
  private static final String LUCK_SLOT = "luck";
  private static final long LUCK_DURATION_TICKS = 400L;
  private static final int MAX_ASSIST = 8;
  private static final Id DOLPHIN = Id.minecraft("dolphin");
  private static final Id AXOLOTL = Id.minecraft("axolotl");

  private SeaborneSeaLife() {
  }

  public static void applyLuck(Adaptation<?> owner, PlayerRef player, int amount) {
    AdaptAttributeService.get().applyTimed(player, owner.getName(), LUCK_SLOT, Attributes.LUCK, amount,
        AttributeOperation.ADD_VALUE, LUCK_DURATION_TICKS);
  }

  public static void removeLuck(Adaptation<?> owner, PlayerRef player) {
    AdaptAttributeService.get().remove(player, owner.getName(), LUCK_SLOT, Attributes.LUCK);
  }

  /** Sends nearby dolphins charging at {@code victim} and turns nearby axolotls on it. */
  public static void rally(Adaptation<?> owner, MobRef victim, double range, double dolphinStrength) {
    Loc victimLocation = victim.location();
    int assisted = 0;
    for (EntityRef nearby : victimLocation.world().entitiesIn(Box.around(victimLocation.position(), range, range, range),
        (EntityRef entity) -> entity instanceof MobRef)) {
      if (!(nearby instanceof MobRef entity) || entity.equals(victim)) {
        continue;
      }

      if (DOLPHIN.equals(entity.type())) {
        Tasks.runEntity(entity, () -> chargeDolphin(owner, entity, victimLocation, dolphinStrength));
      } else if (AXOLOTL.equals(entity.type())) {
        Tasks.runEntity(entity, () -> {
          if (!victim.equals(entity.target())) {
            entity.setTarget(victim);
          }
        });
      } else {
        continue;
      }

      if (++assisted >= MAX_ASSIST) {
        break;
      }
    }
  }

  private static void chargeDolphin(Adaptation<?> owner, MobRef dolphin, Loc victimLocation, double dolphinStrength) {
    if (!dolphin.isValid() || !victimLocation.sameWorld(dolphin.location())) {
      return;
    }
    Vec3 toVictim = victimLocation.position().subtract(dolphin.location().position());
    if (toVictim.lengthSquared() <= 0.01D) {
      return;
    }
    double strength = Math.max(0D, dolphinStrength);
    dolphin.setVelocity(dolphin.velocity().multiply(0.4D).add(toVictim.normalize().multiply(strength)));
    Fx.now(owner, dolphin.location(), FxPriority.AMBIENT)
        .particle(Particles.BUBBLE, 4, 0D, 0.1D, 0D, 0.2D, 0.03D);
  }

  public static int luckAmplifier(int level, int maxLevel) {
    return Math.max(0, Math.min(maxLevel - 1, level - 1));
  }

  public static int luckAmount(int level, int maxLevel) {
    return luckAmplifier(level, maxLevel) + 1;
  }
}
