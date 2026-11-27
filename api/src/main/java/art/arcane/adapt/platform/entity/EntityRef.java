package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

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
@ApiStatus.AvailableSince("1.0.0")
public interface EntityRef {
  /** Persistent entity id, the key to store when the entity must be found again later. */
  UUID uuid();

  /** Numeric runtime id the server uses in packets; it changes whenever the entity is recreated. */
  int entityId();

  /** Entity type id, for example {@code minecraft:zombie}. */
  Id type();

  /** True when the entity belongs to the category the trait names. */
  boolean has(EntityTrait trait);

  /** True while the entity is alive and still in its world; false once it died, was removed or unloaded. */
  boolean isValid();

  /** True once the entity died or was removed. */
  boolean isDead();

  /** World the entity is in now. */
  WorldRef world();

  /** Feet position with body yaw and head pitch. */
  Loc location();

  /** Current motion in blocks per tick. */
  Vec3 velocity();

  /** Replaces the current motion, in blocks per tick. */
  void setVelocity(Vec3 velocity);

  /** Collision box in world coordinates. */
  Box boundingBox();

  /** Height of the collision box in blocks, for the current pose. */
  double height();

  /** Width of the collision box in blocks, for the current pose. */
  double width();

  /** True while the entity stands on a block. */
  boolean isOnGround();

  /** True while the entity touches water. */
  boolean isInWater();

  /** True while the entity touches lava. */
  boolean isInLava();

  /** Blocks fallen since the entity last stood on ground; vanilla fall damage is based on it. */
  float fallDistance();

  /** Replaces the fall distance; 0 cancels the fall damage of the current fall. */
  void setFallDistance(float distance);

  /** Ticks the entity keeps burning; 0 or less when it is not on fire. */
  int fireTicks();

  /** Sets the remaining burn ticks; 0 puts the fire out. */
  void setFireTicks(int ticks);

  /** Ticks of powder snow freezing built up, compared against {@link #maxFreezeTicks()}. */
  int freezeTicks();

  /** Sets the built-up freezing ticks. */
  void setFreezeTicks(int ticks);

  /** Freeze ticks at which the entity counts as fully frozen. */
  int maxFreezeTicks();

  /** True when a plugin locked the freeze ticks so the game no longer changes them. */
  boolean isFreezeTickingLocked();

  /** The entity's tick counter: ticks it has existed. */
  int ticksLived();

  /** True when the vanilla Invulnerable flag is set. */
  boolean isInvulnerable();

  /** Sets the vanilla Invulnerable flag. */
  void setInvulnerable(boolean invulnerable);

  /** True when the entity makes no sounds. */
  boolean isSilent();

  /** Mutes or unmutes the sounds the entity makes. */
  void setSilent(boolean silent);

  /** False when the entity ignores gravity and floats. */
  boolean hasGravity();

  /** Turns gravity on or off for the entity. */
  void setGravity(boolean gravity);

  /** True when the entity shows the glowing outline to every player. */
  boolean isGlowing();

  /** Shows or hides the glowing outline for every player. */
  void setGlowing(boolean glowing);

  /** True when the entity is saved with its chunk; false when it is dropped on unload. */
  boolean isPersistent();

  /** Whether the entity is saved with its chunk; despawning is {@link LivingRef#setRemoveWhenFarAway}. */
  void setPersistent(boolean persistent);

  /** Custom name with legacy section colours on every platform, or null when the entity has none. */
  String customName();

  /**
   * Sets the custom name from Adapt markup; {@code visible} shows the name tag at any distance. A null name clears
   * the name and hides the tag.
   */
  void setCustomName(String markup, boolean visible);

  /** Entity this one rides, or null. */
  EntityRef vehicle();

  /** Entities riding this one; empty when none. */
  List<EntityRef> passengers();

  /** Mounts {@code passenger} on this entity. True when it now rides this entity. */
  boolean addPassenger(EntityRef passenger);

  /** Dismounts every passenger. False when there was none to dismount. */
  boolean eject();

  /**
   * Entities whose boxes touch this entity's bounding box grown by the given half-extents, excluding this entity.
   */
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
  @ApiStatus.Internal
  Object unwrap();
}
