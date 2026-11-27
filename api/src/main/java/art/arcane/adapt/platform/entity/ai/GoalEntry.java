package art.arcane.adapt.platform.entity.ai;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;

/**
 * A goal currently installed on a mob, vanilla or custom. Vanilla goal keys use Paper's naming
 * (snake case of the goal class without the {@code Goal} suffix, for example
 * {@code minecraft:melee_attack}); modded adapters derive the same names so content can match them.
 * An entry can be removed and later re-added to the same mob to restore the original goal.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface GoalEntry {
  /** Goal key: the custom goal's {@link AdaptGoal#key()}, or the derived vanilla name. */
  Id key();

  /** Selector priority, or -1 when the platform cannot read it (vanilla goals on Bukkit without server access). */
  int priority();

  /** Controls the goal claims. */
  Set<GoalFlag> flags();

  /** True for goals added through {@link MobGoalsRef#add}. */
  boolean isCustom();

  /** Adapter-internal access to the native goal. Content code must not call this. */
  @ApiStatus.Internal
  Object unwrap();
}
