package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalGolemControllerTest {
  private static final List<String> KEPT = List.of(
      "minecraft:melee_attack", "minecraft:ranged_attack", "minecraft:hurt_by", "minecraft:move_towards",
      "minecraft:look_at_player", "minecraft:random_look_around");

  @Test
  void ironRemovesVillageFlowerAndNearestAttackableGoals() {
    assertThat(keys(GolemBody.IRON)).containsExactly(
        "minecraft:iron_golem_defend_village",
        "minecraft:iron_golem_offer_flower",
        "minecraft:move_back_to_village",
        "minecraft:golem_random_stroll_in_village",
        "minecraft:nearest_attackable");
  }

  @Test
  void frostRemovesNearestAttackableAndStrollGoals() {
    assertThat(keys(GolemBody.FROST)).containsExactly("minecraft:nearest_attackable", "minecraft:water_avoiding_random_stroll");
  }

  @Test
  void boneRemovesNearestAttackableStrollAndSunGoals() {
    assertThat(keys(GolemBody.BONE)).containsExactly(
        "minecraft:nearest_attackable",
        "minecraft:water_avoiding_random_stroll",
        "minecraft:restrict_sun",
        "minecraft:flee_sun");
  }

  @Test
  void copperKeepsItsBrainAndRemovesNoGoals() {
    assertThat(GoalGolemController.removedGoals(GolemBody.COPPER)).isEmpty();
  }

  @Test
  void attackLookAndRetaliationGoalsAreKeptForEveryBody() {
    for (GolemBody body : GolemBody.values()) {
      assertThat(keys(body)).doesNotContainAnyElementsOf(KEPT);
    }
  }

  @Test
  void removedGoalsRememberTheirVanillaPriorities() {
    assertThat(priorities(GolemBody.IRON)).containsExactlyInAnyOrderEntriesOf(Map.of(
        "minecraft:iron_golem_defend_village", List.of(1),
        "minecraft:iron_golem_offer_flower", List.of(5),
        "minecraft:move_back_to_village", List.of(2),
        "minecraft:golem_random_stroll_in_village", List.of(4),
        "minecraft:nearest_attackable", List.of(3, 3)));
    assertThat(priorities(GolemBody.FROST)).containsExactlyInAnyOrderEntriesOf(Map.of(
        "minecraft:nearest_attackable", List.of(1),
        "minecraft:water_avoiding_random_stroll", List.of(2)));
    assertThat(priorities(GolemBody.BONE)).containsExactlyInAnyOrderEntriesOf(Map.of(
        "minecraft:nearest_attackable", List.of(2, 3, 3),
        "minecraft:water_avoiding_random_stroll", List.of(5),
        "minecraft:restrict_sun", List.of(2),
        "minecraft:flee_sun", List.of(3)));
  }

  @Test
  void attachRemovesOnlyListedGoalsAndAddsWorkAndTargetGoals() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);

    new GoalGolemController(golem, GolemBody.IRON, goals).attach();

    verify(goals).removeGoal(golem, GolemWorkGoal.KEY);
    verify(goals).removeGoal(golem, GolemTargetGoal.KEY);
    for (Goal<Mob> goal : vanilla) {
      String key = goal.getKey().getNamespacedKey().toString();
      if (keys(GolemBody.IRON).contains(key)) {
        verify(goals).removeGoal(golem, goal);
      } else {
        verify(goals, never()).removeGoal(golem, goal);
      }
    }
    verify(goals).addGoal(eq(golem), eq(GoalGolemController.WORK_PRIORITY), any(GolemWorkGoal.class));
    verify(goals).addGoal(eq(golem), eq(GoalGolemController.TARGET_PRIORITY), any(GolemTargetGoal.class));
  }

  @Test
  void attachTwiceChangesNothingTheSecondTime() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);

    controller.attach();
    controller.attach();

    verify(goals, times(1)).getAllGoals(golem);
    verify(goals, times(2)).addGoal(eq(golem), anyInt(), any());
  }

  @Test
  void releaseRestoresRemovedGoalsAtTheirVanillaPriorities() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemWorkGoal work = added(goals, golem, GoalGolemController.WORK_PRIORITY, GolemWorkGoal.class);
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);

    Goal<Mob> defend = goal(vanilla, "iron_golem_defend_village", 0);
    Goal<Mob> flower = goal(vanilla, "iron_golem_offer_flower", 0);
    Goal<Mob> village = goal(vanilla, "move_back_to_village", 0);
    Goal<Mob> stroll = goal(vanilla, "golem_random_stroll_in_village", 0);
    Goal<Mob> players = goal(vanilla, "nearest_attackable", 0);
    Goal<Mob> monsters = goal(vanilla, "nearest_attackable", 1);
    Goal<Mob> melee = goal(vanilla, "melee_attack", 0);

    controller.release();

    verify(goals).removeGoal(golem, work);
    verify(goals).removeGoal(golem, target);
    verify(goals).addGoal(golem, 1, defend);
    verify(goals).addGoal(golem, 5, flower);
    verify(goals).addGoal(golem, 2, village);
    verify(goals).addGoal(golem, 4, stroll);
    verify(goals).addGoal(golem, 3, players);
    verify(goals).addGoal(golem, 3, monsters);
    verify(goals, never()).addGoal(eq(golem), anyInt(), same(melee));
  }

  @Test
  void boneRestoresNearestAttackableGoalsInRegistrationOrder() {
    Mob golem = mock(Skeleton.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = List.of(
        vanillaGoal("restrict_sun", EnumSet.noneOf(GoalType.class)),
        vanillaGoal("flee_sun", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("water_avoiding_random_stroll", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("hurt_by", EnumSet.of(GoalType.TARGET)),
        vanillaGoal("nearest_attackable", EnumSet.of(GoalType.TARGET)),
        vanillaGoal("nearest_attackable", EnumSet.of(GoalType.TARGET)),
        vanillaGoal("nearest_attackable", EnumSet.of(GoalType.TARGET)));
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.BONE, goals);
    controller.attach();

    controller.release();

    InOrder order = inOrder(goals);
    order.verify(goals).addGoal(golem, 2, vanilla.get(4));
    order.verify(goals).addGoal(golem, 3, vanilla.get(5));
    order.verify(goals).addGoal(golem, 3, vanilla.get(6));
    verify(goals).addGoal(golem, 2, vanilla.get(0));
    verify(goals).addGoal(golem, 3, vanilla.get(1));
    verify(goals).addGoal(golem, 5, vanilla.get(2));
    verify(goals, never()).removeGoal(golem, vanilla.get(3));
  }

  @Test
  void releaseWithoutAttachTouchesNoGoals() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);

    new GoalGolemController(golem, GolemBody.IRON, goals).release();

    verify(goals, never()).addGoal(any(), anyInt(), any());
    verify(goals, never()).removeGoal(any(), any(Goal.class));
  }

  @Test
  void workGoalRunsOnlyWhileTheBrainHasAMoveTarget() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    when(goals.getAllGoals(golem)).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemWorkGoal work = added(goals, golem, GoalGolemController.WORK_PRIORITY, GolemWorkGoal.class);

    assertThat(work.shouldActivate()).isFalse();
    assertThat(work.getTypes()).containsExactly(GoalType.MOVE);
    controller.moveTo(new Location(null, 1, 64, 1), 1D);
    assertThat(work.shouldActivate()).isTrue();
    assertThat(work.shouldStayActive()).isTrue();
    controller.stop();
    assertThat(work.shouldStayActive()).isFalse();
  }

  @Test
  void targetGoalAppliesTheBrainTargetAndClearsOnlyItsOwnTarget() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    when(goals.getAllGoals(golem)).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    LivingEntity zombie = living();

    assertThat(target.getTypes()).containsExactly(GoalType.TARGET);
    assertThat(target.shouldActivate()).isFalse();
    controller.setTarget(zombie);
    assertThat(target.shouldActivate()).isTrue();
    target.start();
    verify(golem).setTarget(zombie);

    when(golem.getTarget()).thenReturn(zombie);
    controller.setTarget(null);
    assertThat(target.shouldStayActive()).isFalse();
    target.stop();
    verify(golem).setTarget(null);
  }

  @Test
  void targetGoalLeavesAnotherTargetInPlaceWhenItStops() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    when(goals.getAllGoals(golem)).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    LivingEntity zombie = living();
    LivingEntity other = living();
    controller.setTarget(zombie);
    target.start();

    when(golem.getTarget()).thenReturn(other);
    target.stop();

    verify(golem, never()).setTarget(null);
  }

  private static List<String> keys(GolemBody body) {
    List<String> keys = new ArrayList<>();
    for (GoalGolemController.VanillaGoal goal : GoalGolemController.removedGoals(body)) {
      keys.add(goal.key().toString());
    }
    return keys;
  }

  private static Map<String, List<Integer>> priorities(GolemBody body) {
    Map<String, List<Integer>> priorities = new LinkedHashMap<>();
    for (GoalGolemController.VanillaGoal goal : GoalGolemController.removedGoals(body)) {
      priorities.put(goal.key().toString(), goal.priorities());
    }
    return priorities;
  }

  private static List<Goal<Mob>> vanillaIronGoals() {
    return List.of(
        vanillaGoal("melee_attack", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("move_towards", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("move_back_to_village", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("golem_random_stroll_in_village", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("iron_golem_offer_flower", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("look_at_player", EnumSet.of(GoalType.LOOK)),
        vanillaGoal("random_look_around", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("iron_golem_defend_village", EnumSet.of(GoalType.TARGET)),
        vanillaGoal("hurt_by", EnumSet.of(GoalType.TARGET)),
        vanillaGoal("nearest_attackable", EnumSet.of(GoalType.TARGET)),
        vanillaGoal("nearest_attackable", EnumSet.of(GoalType.TARGET)));
  }

  @SuppressWarnings("unchecked")
  private static Goal<Mob> vanillaGoal(String key, EnumSet<GoalType> types) {
    Goal<Mob> goal = mock(Goal.class);
    GoalKey<Mob> goalKey = GoalKey.of(Mob.class, NamespacedKey.minecraft(key));
    when(goal.getKey()).thenReturn(goalKey);
    when(goal.getTypes()).thenReturn(types);
    return goal;
  }

  private static Goal<Mob> goal(List<Goal<Mob>> goals, String key, int occurrence) {
    int seen = 0;
    for (Goal<Mob> goal : goals) {
      if (goal.getKey().getNamespacedKey().getKey().equals(key) && seen++ == occurrence) {
        return goal;
      }
    }
    throw new AssertionError("no goal " + key + "#" + occurrence);
  }

  @SuppressWarnings("unchecked")
  private static <G extends Goal<Mob>> G added(MobGoals goals, Mob golem, int priority, Class<G> type) {
    ArgumentCaptor<Goal<Mob>> captor = ArgumentCaptor.forClass(Goal.class);
    verify(goals, atLeastOnce()).addGoal(eq(golem), eq(priority), captor.capture());
    for (Goal<Mob> goal : captor.getAllValues()) {
      if (type.isInstance(goal)) {
        return (G) goal;
      }
    }
    throw new AssertionError("no " + type.getSimpleName() + " added at priority " + priority);
  }

  private static LivingEntity living() {
    LivingEntity entity = mock(Zombie.class);
    when(entity.isValid()).thenReturn(true);
    when(entity.isDead()).thenReturn(false);
    when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    return entity;
  }
}
