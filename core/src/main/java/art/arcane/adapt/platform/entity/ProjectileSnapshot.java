package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.function.Consumer;

/**
 * State captured from a projectile by {@link ProjectileRef#snapshot()}. It holds no live handles, so it may be read
 * and spawned on another thread or region than the one it was captured on.
 */
public interface ProjectileSnapshot {
  /** Entity type of the captured projectile. */
  Id type();

  /** Item the captured projectile carried (trident stack, arrow stack, thrown item); empty when none. */
  ItemRef item();

  /**
   * Spawns a copy at {@code location} with {@code velocity}, shot by {@code shooter}. {@code beforeAdd} runs before
   * the copy enters the world, so listeners of its launch already see what it sets. Must run on the thread that
   * owns {@code location}. Returns null when the copy could not be spawned.
   */
  ProjectileRef spawn(Loc location, Vec3 velocity, PlayerRef shooter, Consumer<ProjectileRef> beforeAdd);
}
