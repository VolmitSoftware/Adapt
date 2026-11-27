package art.arcane.adapt.bukkit.feature.ai;

import art.arcane.adapt.bukkit.platform.BukkitGoalsFactory;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;

/** Goal handles for mobs, backed by the server's Paper {@link MobGoals}. */
public final class BukkitGoals implements BukkitGoalsFactory {
  private final MobGoals goals;
  private final GoalPriorities priorities;

  public BukkitGoals(MobGoals goals) {
    this(goals, GoalPriorities.reflective());
  }

  /** The running server's goals. Paper only. */
  public static BukkitGoals server() {
    return new BukkitGoals(Bukkit.getMobGoals());
  }

  BukkitGoals(MobGoals goals, GoalPriorities priorities) {
    this.goals = goals;
    this.priorities = priorities;
  }

  @Override
  public MobGoalsRef goals(Mob mob) {
    return new BukkitMobGoals(mob, goals, priorities);
  }
}
