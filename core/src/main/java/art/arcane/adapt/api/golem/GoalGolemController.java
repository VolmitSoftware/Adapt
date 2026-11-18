package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.GoalEntry;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

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

  private final MobRef golem;
  private final GolemBody body;
  private final MobGoalsRef goals;
  private final GolemPathing pathing;
  private final GolemWorkGoal workGoal;
  private final GolemTargetGoal targetGoal;
  private final List<RemovedGoal> removed = new ArrayList<>();
  private final List<RemovedGoal> silenced = new ArrayList<>();
  private GoalEntry workEntry;
  private GoalEntry targetEntry;
  private boolean attached;
  private boolean combat = true;

  GoalGolemController(MobRef golem, GolemBody body, MobGoalsRef goals) {
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
    goals.removeAll(GolemWorkGoal.KEY);
    goals.removeAll(GolemTargetGoal.KEY);
    captureVanillaGoals();
    for (RemovedGoal goal : removed) {
      goals.remove(goal.goal());
    }
    targetEntry = goals.add(TARGET_PRIORITY, targetGoal);
    workEntry = goals.add(WORK_PRIORITY, workGoal);
  }

  @Override
  public void moveTo(Loc destination, double speed) {
    pathing.moveTo(destination, speed);
  }

  @Override
  public void stop() {
    pathing.stop();
  }

  @Override
  public void setTarget(LivingRef target) {
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
      targetEntry = goals.add(TARGET_PRIORITY, targetGoal);
      return;
    }
    targetGoal.assign(null);
    removeEntry(targetEntry);
    targetEntry = null;
    silenceCombatGoals();
  }

  @Override
  public void interact(BlockRef container, int ticks) {
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
    removeEntry(workEntry);
    removeEntry(targetEntry);
    workEntry = null;
    targetEntry = null;
    for (RemovedGoal goal : removed) {
      goals.restore(goal.goal(), goal.priority());
    }
    removed.clear();
    restoreCombatGoals();
    combat = true;
  }

  private void removeEntry(GoalEntry entry) {
    if (entry != null) {
      goals.remove(entry);
    }
  }

  private void silenceCombatGoals() {
    List<VanillaGoal> listed = combatGoals(body);
    for (GoalEntry goal : List.copyOf(goals.all())) {
      VanillaGoal vanilla = goal.isCustom() ? null : find(listed, goal.key());
      if (vanilla != null) {
        silenced.add(new RemovedGoal(goal, vanilla.priority()));
        goals.remove(goal);
      }
    }
  }

  private void restoreCombatGoals() {
    for (RemovedGoal goal : silenced) {
      goals.restore(goal.goal(), goal.priority());
    }
    silenced.clear();
  }

  private void captureVanillaGoals() {
    List<VanillaGoal> listed = removedGoals(body);
    for (GoalEntry goal : List.copyOf(goals.all())) {
      VanillaGoal vanilla = goal.isCustom() ? null : find(listed, goal.key());
      if (vanilla != null) {
        removed.add(new RemovedGoal(goal, vanilla.priority()));
      }
    }
  }

  private static VanillaGoal find(List<VanillaGoal> listed, Id key) {
    for (VanillaGoal vanilla : listed) {
      if (vanilla.key().equals(key)) {
        return vanilla;
      }
    }
    return null;
  }

  record VanillaGoal(Id key, int priority) {
    static VanillaGoal of(String key, int priority) {
      return new VanillaGoal(Id.minecraft(key), priority);
    }
  }

  private record RemovedGoal(GoalEntry goal, int priority) {
  }
}
