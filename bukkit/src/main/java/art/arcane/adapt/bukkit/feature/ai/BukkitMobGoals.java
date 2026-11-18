package art.arcane.adapt.bukkit.feature.ai;

import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalEntry;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Id;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One mob's goals through Paper's {@link MobGoals}, the API golemancy drives today. */
final class BukkitMobGoals implements MobGoalsRef {
  private static final Comparator<GoalEntry> PRIORITY = Comparator.comparingInt(GoalEntry::priority);
  private final Mob mob;
  private final MobGoals goals;
  private final GoalPriorities priorities;

  BukkitMobGoals(Mob mob, MobGoals goals, GoalPriorities priorities) {
    this.mob = mob;
    this.goals = goals;
    this.priorities = priorities;
  }

  @Override
  public List<GoalEntry> all() {
    GoalPriorities.Index index = priorities.index(mob);
    List<GoalEntry> entries = new ArrayList<>();
    for (Goal<Mob> goal : List.copyOf(goals.getAllGoals(mob))) {
      entries.add(BukkitGoalEntry.of(goal, index.priority(goal)));
    }
    entries.sort(PRIORITY);
    return entries;
  }

  @Override
  public List<GoalEntry> withKey(Id key) {
    List<GoalEntry> matching = new ArrayList<>();
    for (GoalEntry entry : all()) {
      if (entry.key().equals(key)) {
        matching.add(entry);
      }
    }
    return matching;
  }

  @Override
  public GoalEntry add(int priority, AdaptGoal goal) {
    PaperAdaptGoal paper = new PaperAdaptGoal(goal);
    goals.addGoal(mob, priority, paper);
    return new BukkitGoalEntry(goal.key(), priority, paper);
  }

  @Override
  public void restore(GoalEntry entry) {
    BukkitGoalEntry bukkit = bukkit(entry);
    if (bukkit.priority() == GoalPriorities.UNKNOWN) {
      throw new IllegalStateException("Priority of " + entry.key() + " is unknown; restore it with an explicit priority");
    }
    restore(bukkit, bukkit.priority());
  }

  @Override
  public void restore(GoalEntry entry, int priority) {
    BukkitGoalEntry bukkit = bukkit(entry);
    for (Goal<Mob> installed : goals.getAllGoals(mob)) {
      if (bukkit.sameGoal(installed)) {
        return;
      }
    }
    goals.addGoal(mob, priority, bukkit.goal());
  }

  @Override
  public void remove(GoalEntry entry) {
    if (entry instanceof BukkitGoalEntry bukkit) {
      goals.removeGoal(mob, bukkit.goal());
    }
  }

  @Override
  public List<GoalEntry> removeAll(Id key) {
    List<GoalEntry> removed = withKey(key);
    for (GoalEntry entry : removed) {
      remove(entry);
    }
    return removed;
  }

  @Override
  public boolean has(Id key) {
    for (Goal<Mob> goal : goals.getAllGoals(mob)) {
      if (PaperAdaptGoal.id(goal.getKey()).equals(key)) {
        return true;
      }
    }
    return false;
  }

  private static BukkitGoalEntry bukkit(GoalEntry entry) {
    if (!(entry instanceof BukkitGoalEntry bukkit)) {
      throw new IllegalArgumentException("Not a Bukkit goal entry: " + entry);
    }
    return bukkit;
  }
}
