package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;

/** Timed-block tracking: when each player last raised a shield, and whether a hit lands inside that window. */
public final class BlockingParry {
  private final Adaptation<?> owner;
  private final Map<UUID, Long> raiseAt;

  public BlockingParry(Adaptation<?> owner, Map<UUID, Long> raiseAt) {
    this.owner = owner;
    this.raiseAt = raiseAt;
  }

  /** Records a right click with a shield by a player who holds the owner adaptation. */
  public void raise(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_AIR && e.action() != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    if (owner.getActiveLevel(p) <= 0 || !BlockingShields.held(owner, p)) {
      return;
    }
    raiseAt.put(p.uuid(), System.currentTimeMillis());
  }

  /** True when the defender raised a shield no more than {@code windowMillis} before {@code now}. */
  public boolean timed(PlayerRef defender, long windowMillis, long now) {
    Long raised = raiseAt.get(defender.uuid());
    return raised != null && now - raised <= windowMillis;
  }

  /** Spends the raise so one block parries one hit. */
  public void consume(PlayerRef defender) {
    raiseAt.remove(defender.uuid());
  }

  /** The living source of a hit: the damager itself or a projectile's shooter, or null. */
  public static LivingRef attacker(EntityRef damager) {
    if (damager instanceof ProjectileRef projectile) {
      return projectile.shooter() instanceof LivingRef shooter ? shooter : null;
    }
    return damager instanceof LivingRef living ? living : null;
  }

  /** True when the defender looks toward the damager within {@code minAlignment}. */
  public static boolean faces(PlayerRef defender, EntityRef damager, double minAlignment) {
    Loc eye = defender.eyeLocation();
    Vec3 toSource = damager.location().position().subtract(eye.position());
    if (toSource.lengthSquared() <= 1.0E-6) {
      return true;
    }
    return eye.direction().dot(toSource.normalize()) >= minAlignment;
  }
}
