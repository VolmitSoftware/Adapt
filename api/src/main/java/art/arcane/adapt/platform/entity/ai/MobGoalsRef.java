package art.arcane.adapt.platform.entity.ai;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** Goal and target selectors of one mob. Owning-thread only; stale with the mob handle. */
@ApiStatus.AvailableSince("1.0.0")
public interface MobGoalsRef {
  /** Every installed goal from both selectors, in priority order. */
  List<GoalEntry> all();

  /** Installed goals with the key, from both selectors; empty when none. */
  List<GoalEntry> withKey(Id key);

  /** Installs a custom goal; returns its entry. A TARGET flag selects the target selector. */
  GoalEntry add(int priority, AdaptGoal goal);

  /** Re-installs a previously removed entry at its original priority. */
  void restore(GoalEntry entry);

  /**
   * Re-installs a previously removed entry at {@code priority}. Bukkit cannot always read vanilla goal priorities
   * ({@link GoalEntry#priority()} may be -1 there), so content that knows the priority passes it here.
   */
  void restore(GoalEntry entry, int priority);

  /** Removes the goal; keep the entry to {@link #restore(GoalEntry)} it later. */
  void remove(GoalEntry entry);

  /** Removes every goal with the key. Returns the removed entries so they can be restored. */
  List<GoalEntry> removeAll(Id key);

  /** True when a goal with the key is installed. */
  boolean has(Id key);
}
