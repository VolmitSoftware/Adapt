package art.arcane.adapt.platform.entity.ai;

import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** Goal and target selectors of one mob. Owning-thread only; stale with the mob handle. */
public interface MobGoalsRef {
  /** Every installed goal from both selectors, in priority order. */
  List<GoalEntry> all();

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

  void remove(GoalEntry entry);

  /** Removes every goal with the key. Returns the removed entries so they can be restored. */
  List<GoalEntry> removeAll(Id key);

  boolean has(Id key);
}
