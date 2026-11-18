package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handle to a server entity.
 *
 * <p>Threading: {@link #uuid()}, {@link #entityId()} and {@link #type()} are safe on any thread.
 * Everything else must run on the thread that owns the entity (its region on Folia, the server
 * thread elsewhere). Use {@code AdaptScheduler.runEntity} to get there.
 *
 * <p>Staleness: a non-player handle goes stale when the entity is removed, unloaded or moved to
 * another dimension (vanilla recreates the entity). Stale handles return {@code false} from
 * {@link #isValid()} and must not be mutated. Store {@link #uuid()} across ticks, not the handle.
 * Player handles have their own rule, see {@link PlayerRef}.
 *
 * <p>Typing: adapters return a {@link PlayerRef} for players, {@link MobRef} for mobs,
 * {@link LivingRef} for other living entities, {@link ProjectileRef} for projectiles and
 * {@link ItemEntityRef} for dropped items, so {@code instanceof} checks work.
 *
 * <p>Equality is by {@link #uuid()}.
 */
public interface EntityRef {
  UUID uuid();

  int entityId();

  Id type();

  boolean has(EntityTrait trait);

  boolean isValid();

  boolean isDead();

  WorldRef world();

  Loc location();

  Vec3 velocity();

  void setVelocity(Vec3 velocity);

  Box boundingBox();

  double height();

  double width();

  boolean isOnGround();

  boolean isInWater();

  boolean isInLava();

  float fallDistance();

  void setFallDistance(float distance);

  int fireTicks();

  void setFireTicks(int ticks);

  int freezeTicks();

  void setFreezeTicks(int ticks);

  /** Freeze ticks at which the entity counts as fully frozen. */
  int maxFreezeTicks();

  /** True when a plugin locked the freeze ticks so the game no longer changes them. */
  boolean isFreezeTickingLocked();

  int ticksLived();

  boolean isInvulnerable();

  void setInvulnerable(boolean invulnerable);

  boolean isSilent();

  void setSilent(boolean silent);

  boolean hasGravity();

  void setGravity(boolean gravity);

  boolean isGlowing();

  void setGlowing(boolean glowing);

  boolean isPersistent();

  void setPersistent(boolean persistent);

  String customName();

  void setCustomName(String markup, boolean visible);

  EntityRef vehicle();

  List<EntityRef> passengers();

  boolean addPassenger(EntityRef passenger);

  boolean eject();

  List<EntityRef> nearbyEntities(double radiusX, double radiusY, double radiusZ);

  /**
   * Teleports, asynchronously where the platform supports it. Completes with false when refused.
   * The future may complete on another thread; hop back with the scheduler before touching the entity.
   */
  CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause);

  /** Custom name as vanilla text-component JSON, or null when the entity has no custom name. */
  String customNameJson();

  /** Sets the custom name from vanilla text-component JSON; null or unreadable JSON clears it. Leaves visibility alone. */
  void setCustomNameJson(String json);

  /**
   * Teleports like {@link #teleport(Loc, TeleportCause)}; with {@code retainPassengers} the passengers travel along and
   * stay mounted.
   */
  CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause, boolean retainPassengers);

  /** Removes a non-player entity from the world. */
  void remove();

  /** Persistent data (entity persistent data container, vanilla attachment). */
  DataHolder data();

  /** Owner of a tamed entity, or null when untamed or not tameable. */
  UUID tameOwner();

  /**
   * The entity responsible for this one when it is primed TNT (its igniter), an area effect cloud (its source) or
   * evoker fangs (their owner), if still loaded; otherwise null. Projectiles use {@link ProjectileRef#shooter()}.
   */
  EntityRef responsibleEntity();

  /** Adapter-internal access to the native entity. Content code must not call this. */
  Object unwrap();
}
