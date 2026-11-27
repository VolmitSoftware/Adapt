package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntToDoubleFunction;

/**
 * Pearl Rebound's bounce: a plain pearl tagged at launch bounces once off the first block it hits, steered toward the
 * thrower's crosshair, instead of teleporting there.
 */
public final class RiftPearlBounce {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");

  private final IntToDoubleFunction aimBias;
  private final DoubleSupplier speed;
  private final Consumer<PlayerRef> onRebound;
  private final DataKey<Byte> reboundedKey = DataKey.adapt("rift_pearl_rebounded", DataCodecs.BYTE);
  private final DataKey<Integer> reboundLevelKey = DataKey.adapt("rift_pearl_rebound_level", DataCodecs.INT);

  /**
   * {@code aimBias} maps the tagged level to the crosshair steering share; {@code onRebound} runs on the thrower's
   * thread after each bounce while the owner passed to {@link #hit} is still active for them.
   */
  public RiftPearlBounce(IntToDoubleFunction aimBias, DoubleSupplier speed,
                         Consumer<PlayerRef> onRebound) {
    this.aimBias = aimBias;
    this.speed = speed;
    this.onRebound = onRebound;
  }

  /** The thrower of a plain, unclaimed ender pearl, or null. */
  public PlayerRef thrower(ProjectileRef pearl) {
    if (!pearl.type().equals(ENDER_PEARL) || !(pearl.shooter() instanceof PlayerRef p)) {
      return null;
    }
    return RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey.id(), reboundedKey.id()) ? p : null;
  }

  /** Marks the pearl to bounce at {@code level}. */
  public void tag(ProjectileRef pearl, int level) {
    pearl.data().set(reboundLevelKey, level);
  }

  /** Bounces a tagged pearl that hit a block; the hit is cancelled and a new pearl is thrown back. */
  public void hit(SimpleAdaptation<?> owner, ProjectileHitEvent e) {
    ProjectileRef pearl = e.projectile();
    if (!pearl.type().equals(ENDER_PEARL) || !(pearl.shooter() instanceof PlayerRef p)) {
      return;
    }

    if (e.hitBlock() == null || e.hitFace() == null) {
      return;
    }

    Integer level = pearl.data().get(reboundLevelKey);
    if (!RiftPearlRebound.shouldRebound(
        level,
        pearl.data().hasAny(reboundedKey.id()),
        RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey.id(), reboundedKey.id())
    )) {
      return;
    }

    BlockFace face = e.hitFace();
    Vec3 normal = new Vec3(face.dx(), face.dy(), face.dz()).normalize();
    Vec3 incoming = pearl.velocity();
    if (incoming.lengthSquared() <= 1.0E-6D) {
      incoming = normal.multiply(-1D);
    }

    Vec3 reflected = reflect(incoming, normal);
    if (reflected.lengthSquared() <= 1.0E-6D) {
      reflected = normal;
    }
    reflected = reflected.normalize();

    Loc spawnAt = pearl.location().add(normal.multiply(0.35));
    Map<Id, DataValue> modifiers = ProjectileCooperativeKeys.snapshotCooperative(pearl.data());
    e.setCancelled(true);
    pearl.remove();

    Vec3 finalReflected = reflected;
    AdaptApi.scheduler().runEntity(p, () -> finishRebound(owner, p, spawnAt, normal, finalReflected, level, modifiers));
  }

  private boolean launchReboundPearl(SimpleAdaptation<?> owner, PlayerRef p, UUID playerId, Loc spawnAt, Vec3 velocity,
                                     Map<Id, DataValue> modifiers) {
    EntityRef pearl = null;
    try {
      pearl = spawnAt.world().spawn(ENDER_PEARL, spawnAt, (EntityRef ignored) -> {
      });
      if (!(pearl instanceof ProjectileRef projectile)) {
        throw new IllegalStateException("No ender pearl was spawned");
      }
      projectile.setShooter(p);
      ProjectileCooperativeKeys.restore(projectile.data(), modifiers);
      projectile.data().set(reboundedKey, (byte) 1);
      projectile.setVelocity(velocity);
      RiftPearlRebound.bounceFx(owner, spawnAt);
      return true;
    } catch (RuntimeException error) {
      if (pearl != null && pearl.isValid()) {
        pearl.remove();
      }
      AdaptApi.log().error("Rift Pearl Rebound failed to create or configure a pearl for " + playerId
          + " at " + spawnAt + ".", null);
      AdaptApi.log().error("RiftPearlBounce failed", error);
      return false;
    }
  }

  private void finishRebound(SimpleAdaptation<?> owner, PlayerRef p, Loc spawnAt, Vec3 normal, Vec3 reflected, int level,
                             Map<Id, DataValue> modifiers) {
    if (!p.isOnline()) {
      return;
    }

    double bias = aimBias.applyAsDouble(level);
    Vec3 aim = p.eyeLocation().direction();
    if (aim.lengthSquared() <= 1.0E-6D) {
      aim = reflected;
    }
    aim = aim.normalize();

    Vec3 velocity = RiftPearlRebound.reboundDirection(reflected, aim, normal, bias).multiply(speed.getAsDouble());
    UUID playerId = p.uuid();
    if (!AdaptApi.scheduler().runAt(spawnAt, () -> completeReboundSpawn(owner, p, playerId, spawnAt, velocity, modifiers))) {
      AdaptApi.log().error("Rift Pearl Rebound could not schedule pearl creation for " + playerId
          + " at " + spawnAt + ".", null);
    }
  }

  private void completeReboundSpawn(SimpleAdaptation<?> owner, PlayerRef p, UUID playerId, Loc spawnAt, Vec3 velocity,
                                    Map<Id, DataValue> modifiers) {
    if (!owner.isRuntimeRegistered()) {
      return;
    }
    if (!launchReboundPearl(owner, p, playerId, spawnAt, velocity, modifiers)) {
      return;
    }
    if (!AdaptApi.scheduler().runEntity(p, () -> rewardRebound(owner, p))) {
      AdaptApi.log().warn("Rift Pearl Rebound created a pearl but could not schedule rewards for " + playerId + ".");
    }
  }

  private void rewardRebound(SimpleAdaptation<?> owner, PlayerRef p) {
    if (!p.isOnline() || !owner.isRuntimeRegistered() || owner.getActiveLevel(p) <= 0) {
      return;
    }
    onRebound.accept(p);
  }

  private static Vec3 reflect(Vec3 velocity, Vec3 normal) {
    double dot = velocity.dot(normal);
    return velocity.subtract(normal.multiply(2D * dot));
  }
}
