package art.arcane.adapt.bukkit.feature.ai;

import art.arcane.adapt.platform.entity.ai.GoalEntry;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.value.Id;
import com.destroystokyo.paper.entity.ai.Goal;
import org.bukkit.entity.Mob;

import java.util.Set;

/**
 * A Paper goal with the priority it was found or installed at. Priority reads {@link GoalPriorities#UNKNOWN}
 * when the server classes could not be read; restore such entries with an explicit priority.
 */
record BukkitGoalEntry(Id key, int priority, Goal<Mob> goal) implements GoalEntry {
  static BukkitGoalEntry of(Goal<Mob> goal, int priority) {
    return new BukkitGoalEntry(PaperAdaptGoal.id(goal.getKey()), priority, goal);
  }

  @Override
  public Set<GoalFlag> flags() {
    return PaperAdaptGoal.flags(goal.getTypes());
  }

  @Override
  public boolean isCustom() {
    return goal instanceof PaperAdaptGoal;
  }

  @Override
  public Object unwrap() {
    return goal;
  }

  boolean sameGoal(Goal<Mob> other) {
    if (other == goal || goal.equals(other)) {
      return true;
    }
    Object handle = GoalPriorities.handle(goal);
    return handle != null && handle == GoalPriorities.handle(other);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BukkitGoalEntry entry && entry.priority == priority && entry.sameGoal(goal);
  }

  @Override
  public int hashCode() {
    Object handle = GoalPriorities.handle(goal);
    return System.identityHashCode(handle == null ? goal : handle) * 31 + priority;
  }
}
