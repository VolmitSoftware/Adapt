package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AbilityDamage;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The frontal swing arc shared by Cleave and Sundering Cleave. Targets struck by the arc are marked for a moment so the
 * damage the arc deals does not start another arc.
 */
public final class AxeCleaveArc {
  private static final long CLEAVE_MARK_MS = 250L;
  private final Map<UUID, Long> cleaving = new ConcurrentHashMap<>();
  private final Adaptation<?> owner;
  private final BooleanSupplier ignorePassiveMobs;

  /** {@code ignorePassiveMobs} is read again when each scheduled hit lands. */
  public AxeCleaveArc(Adaptation<?> owner, BooleanSupplier ignorePassiveMobs) {
    this.owner = owner;
    this.ignorePassiveMobs = ignorePassiveMobs;
  }

  /** True while the entity is taking damage from an arc this owner started. */
  public boolean isCleaving(UUID entityId, long now) {
    Long marked = cleaving.get(entityId);
    return marked != null && marked > now;
  }

  /** Living entities around the primary target inside the player's frontal cone, up to {@code cap}. */
  public List<LivingRef> targets(PlayerRef p, LivingRef primary, double radius, double halfArcDegrees, int cap,
                                 boolean includePlayers, boolean ignorePassive) {
    double cosHalf = Math.cos(Math.toRadians(halfArcDegrees));
    Loc origin = p.eyeLocation();
    Vec3 look = origin.direction().normalize();
    List<LivingRef> targets = new ArrayList<>();
    for (EntityRef nearby : primary.world().nearbyEntities(primary.location().position(), radius, radius, radius)) {
      if (targets.size() >= cap) {
        break;
      }
      if (!(nearby instanceof LivingRef target) || target.uuid().equals(p.uuid()) || target.uuid().equals(primary.uuid())
          || target.has(EntityTrait.ARMOR_STAND)) {
        continue;
      }
      if ((!includePlayers && target instanceof PlayerRef)
          || !AdaptationDamageTargets.allows(target, ignorePassive) || !owner.canDamageTarget(p, target)) {
        continue;
      }

      Vec3 to = target.location().add(0D, target.height() * 0.5D, 0D).position().subtract(origin.position());
      double distance = to.length();
      if (distance < 1.0E-4D) {
        continue;
      }
      if (look.dot(to.multiply(1D / distance)) < cosHalf) {
        continue;
      }

      targets.add(target);
    }
    return targets;
  }

  /**
   * Marks the target and deals the arc damage on its thread, checking protection again first. {@code onDamaged} runs
   * there after the damage.
   */
  public void strike(LivingRef target, PlayerRef attacker, double damage, long now, Consumer<LivingRef> onDamaged) {
    UUID id = target.uuid();
    cleaving.put(id, now + CLEAVE_MARK_MS);
    boolean scheduled = Tasks.runEntity(target, () -> {
      try {
        if (target.isValid() && !target.isDead() && !owner.isProtectedFriendly(attacker, target)
            && AdaptationDamageTargets.allows(target, ignorePassiveMobs.getAsBoolean())) {
          AbilityDamage.deal(target, damage, attacker);
          onDamaged.accept(target);
        }
      } finally {
        cleaving.remove(id);
      }
    });
    if (!scheduled) {
      cleaving.remove(id);
    }
  }

  public void prune(long now) {
    cleaving.entrySet().removeIf((Map.Entry<UUID, Long> entry) -> entry.getValue() <= now);
  }

  public void clear() {
    cleaving.clear();
  }
}
