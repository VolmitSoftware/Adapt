package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GoalGolemController implements GolemController {
  static final int TARGET_PRIORITY = 1;
  static final int WORK_PRIORITY = 2;

  private static final int PATH_INTERVAL_TICKS = 10;
  private static final List<VanillaGoal> IRON_GOALS = List.of(
      VanillaGoal.of("iron_golem_defend_village", 1),
      VanillaGoal.of("iron_golem_offer_flower", 5),
      VanillaGoal.of("move_back_to_village", 2),
      VanillaGoal.of("golem_random_stroll_in_village", 4),
      VanillaGoal.of("nearest_attackable", 3));
  private static final List<VanillaGoal> FROST_GOALS = List.of(
      VanillaGoal.of("nearest_attackable", 1),
      VanillaGoal.of("water_avoiding_random_stroll", 2));
  private static final List<VanillaGoal> BONE_GOALS = List.of(
      VanillaGoal.of("restrict_sun", 2),
      VanillaGoal.of("flee_sun", 3),
      VanillaGoal.of("avoid_entity", 3),
      VanillaGoal.of("water_avoiding_random_stroll", 5));

  private static final List<VanillaGoal> IRON_COMBAT_GOALS = List.of(
      VanillaGoal.of("melee_attack", 1),
      VanillaGoal.of("hurt_by", 2));
  private static final List<VanillaGoal> FROST_COMBAT_GOALS = List.of(
      VanillaGoal.of("ranged_attack", 1));
  private static final List<VanillaGoal> BONE_COMBAT_GOALS = List.of(
      VanillaGoal.of("hurt_by", 1),
      VanillaGoal.of("nearest_attackable", 3),
      VanillaGoal.of("ranged_bow_attack", 4),
      VanillaGoal.of("skeleton_melee", 4));

  private final Mob golem;
  private final GolemBody body;
  private final MobGoals goals;
  private final GolemPathing pathing;
  private final GolemWorkGoal workGoal;
  private final GolemTargetGoal targetGoal;
  private final List<RemovedGoal> removed = new ArrayList<>();
  private final List<RemovedGoal> silenced = new ArrayList<>();
  private boolean attached;
  private boolean combat = true;

  GoalGolemController(Mob golem, GolemBody body, MobGoals goals) {
    this.golem = Objects.requireNonNull(golem);
    this.body = Objects.requireNonNull(body);
    this.goals = Objects.requireNonNull(goals);
    this.pathing = new GolemPathing(golem, () -> PATH_INTERVAL_TICKS, false);
    this.workGoal = new GolemWorkGoal(golem, pathing);
    this.targetGoal = new GolemTargetGoal(golem);
  }

  static List<VanillaGoal> removedGoals(GolemBody body) {
    return switch (body) {
      case IRON -> IRON_GOALS;
      case FROST -> FROST_GOALS;
      case BONE -> BONE_GOALS;
      case COPPER -> List.of();
    };
  }

  static List<VanillaGoal> combatGoals(GolemBody body) {
    return switch (body) {
      case IRON -> IRON_COMBAT_GOALS;
      case FROST -> FROST_COMBAT_GOALS;
      case BONE -> BONE_COMBAT_GOALS;
      case COPPER -> List.of();
    };
  }

  @Override
  public void attach() {
    if (attached) {
      return;
    }
    attached = true;
    goals.removeGoal(golem, GolemWorkGoal.KEY);
    goals.removeGoal(golem, GolemTargetGoal.KEY);
    captureVanillaGoals();
    for (RemovedGoal goal : removed) {
      goals.removeGoal(golem, goal.goal());
    }
    goals.addGoal(golem, TARGET_PRIORITY, targetGoal);
    goals.addGoal(golem, WORK_PRIORITY, workGoal);
  }

  @Override
  public void moveTo(Location destination, double speed) {
    pathing.moveTo(destination, speed);
  }

  @Override
  public void stop() {
    pathing.stop();
  }

  @Override
  public void setTarget(LivingEntity target) {
    targetGoal.assign(target);
  }

  @Override
  public void setCombat(boolean enabled) {
    if (!attached || enabled == combat) {
      return;
    }
    combat = enabled;
    if (enabled) {
      restoreCombatGoals();
      goals.addGoal(golem, TARGET_PRIORITY, targetGoal);
      return;
    }
    targetGoal.assign(null);
    goals.removeGoal(golem, targetGoal);
    silenceCombatGoals();
  }

  @Override
  public void interact(Block container, int ticks) {
  }

  @Override
  public void tick() {
  }

  @Override
  public void release() {
    if (!attached) {
      return;
    }
    attached = false;
    pathing.stop();
    targetGoal.assign(null);
    goals.removeGoal(golem, workGoal);
    goals.removeGoal(golem, targetGoal);
    for (RemovedGoal goal : removed) {
      goals.addGoal(golem, goal.priority(), goal.goal());
    }
    removed.clear();
    restoreCombatGoals();
    combat = true;
  }

  private void silenceCombatGoals() {
    List<VanillaGoal> listed = combatGoals(body);
    for (Goal<Mob> goal : List.copyOf(goals.getAllGoals(golem))) {
      VanillaGoal vanilla = find(listed, goal.getKey().getNamespacedKey());
      if (vanilla != null) {
        silenced.add(new RemovedGoal(goal, vanilla.priority()));
        goals.removeGoal(golem, goal);
      }
    }
  }

  private void restoreCombatGoals() {
    for (RemovedGoal goal : silenced) {
      goals.addGoal(golem, goal.priority(), goal.goal());
    }
    silenced.clear();
  }

  private void captureVanillaGoals() {
    List<VanillaGoal> listed = removedGoals(body);
    for (Goal<Mob> goal : List.copyOf(goals.getAllGoals(golem))) {
      VanillaGoal vanilla = find(listed, goal.getKey().getNamespacedKey());
      if (vanilla != null) {
        removed.add(new RemovedGoal(goal, vanilla.priority()));
      }
    }
  }

  private static VanillaGoal find(List<VanillaGoal> listed, NamespacedKey key) {
    for (VanillaGoal vanilla : listed) {
      if (vanilla.key().equals(key)) {
        return vanilla;
      }
    }
    return null;
  }

  record VanillaGoal(NamespacedKey key, int priority) {
    static VanillaGoal of(String key, int priority) {
      return new VanillaGoal(NamespacedKey.minecraft(key), priority);
    }
  }

  private record RemovedGoal(Goal<Mob> goal, int priority) {
  }
}
