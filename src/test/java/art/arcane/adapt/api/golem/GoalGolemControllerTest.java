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
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoalGolemControllerTest {
  private static final List<String> KEPT = List.of(
      "minecraft:melee_attack", "minecraft:ranged_attack", "minecraft:ranged_bow_attack", "minecraft:skeleton_melee", "minecraft:hurt_by",
      "minecraft:move_towards", "minecraft:look_at_player", "minecraft:random_look_around");

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
  void boneRemovesItsSunWolfAvoidingAndStrollGoals() {
    assertThat(keys(GolemBody.BONE)).containsExactly(
        "minecraft:restrict_sun",
        "minecraft:flee_sun",
        "minecraft:avoid_entity",
        "minecraft:water_avoiding_random_stroll");
  }

  @Test
  void copperKeepsItsVanillaGoals() {
    assertThat(GoalGolemController.removedGoals(GolemBody.COPPER)).isEmpty();
  }

  @Test
  void everyGoalThatWalksABoundBodyOnItsOwnIsRemoved() {
    assertThat(keys(GolemBody.IRON)).contains("minecraft:move_back_to_village", "minecraft:golem_random_stroll_in_village");
    assertThat(keys(GolemBody.FROST)).contains("minecraft:water_avoiding_random_stroll");
    assertThat(keys(GolemBody.BONE)).contains("minecraft:restrict_sun", "minecraft:flee_sun", "minecraft:avoid_entity", "minecraft:water_avoiding_random_stroll");
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
        "minecraft:iron_golem_defend_village", 1,
        "minecraft:iron_golem_offer_flower", 5,
        "minecraft:move_back_to_village", 2,
        "minecraft:golem_random_stroll_in_village", 4,
        "minecraft:nearest_attackable", 3));
    assertThat(priorities(GolemBody.FROST)).containsExactlyInAnyOrderEntriesOf(Map.of(
        "minecraft:nearest_attackable", 1,
        "minecraft:water_avoiding_random_stroll", 2));
    assertThat(priorities(GolemBody.BONE)).containsExactlyInAnyOrderEntriesOf(Map.of(
        "minecraft:restrict_sun", 2,
        "minecraft:flee_sun", 3,
        "minecraft:avoid_entity", 3,
        "minecraft:water_avoiding_random_stroll", 5));
  }

  @Test
  void boneAttachKeepsItsAttackAndLookGoals() {
    Mob golem = mock(Skeleton.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaBoneGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);

    new GoalGolemController(golem, GolemBody.BONE, goals).attach();

    for (Goal<Mob> goal : vanilla) {
      String key = goal.getKey().getNamespacedKey().getKey();
      if (List.of("restrict_sun", "flee_sun", "avoid_entity", "water_avoiding_random_stroll").contains(key)) {
        verify(goals).removeGoal(golem, goal);
      } else {
        verify(goals, never()).removeGoal(golem, goal);
      }
    }
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
    when(goals.getAllGoals(golem)).thenReturn(new HashSet<>(vanilla));
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
  void restorePrioritiesDoNotDependOnGoalIterationOrder() {
    for (int seed = 0; seed < 12; seed++) {
      assertRestoredInAnyOrder(GolemBody.IRON, vanillaIronGoals(), seed);
      assertRestoredInAnyOrder(GolemBody.FROST, vanillaFrostGoals(), seed);
      assertRestoredInAnyOrder(GolemBody.BONE, vanillaBoneGoals(), seed);
    }
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

  @Test
  void combatGoalsListEachBodysAttackAndRetaliationGoalsAtTheirVanillaPriorities() {
    assertThat(combatPriorities(GolemBody.IRON)).containsExactlyInAnyOrderEntriesOf(Map.of(
        "minecraft:melee_attack", 1,
        "minecraft:hurt_by", 2));
    assertThat(combatPriorities(GolemBody.FROST)).containsExactlyInAnyOrderEntriesOf(Map.of("minecraft:ranged_attack", 1));
    assertThat(combatPriorities(GolemBody.BONE)).containsExactlyInAnyOrderEntriesOf(Map.of(
        "minecraft:hurt_by", 1,
        "minecraft:nearest_attackable", 3,
        "minecraft:ranged_bow_attack", 4,
        "minecraft:skeleton_melee", 4));
    assertThat(GoalGolemController.combatGoals(GolemBody.COPPER)).isEmpty();
  }

  @Test
  void combatGoalsNeverOverlapTheGoalsAttachRemoves() {
    for (GolemBody body : GolemBody.values()) {
      for (GoalGolemController.VanillaGoal combat : GoalGolemController.combatGoals(body)) {
        assertThat(keys(body)).as(body.id()).doesNotContain(combat.key().toString());
      }
    }
  }

  @Test
  void combatOffRemovesTheTargetGoalAndIronsAttackAndRetaliationGoals() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    remaining(goals, golem, vanilla, "iron_golem_defend_village", "iron_golem_offer_flower", "move_back_to_village",
        "golem_random_stroll_in_village", "nearest_attackable");

    controller.setCombat(false);

    verify(goals).removeGoal(golem, target);
    verifyRemoved(goals, golem, goal(vanilla, "melee_attack", 0));
    verifyRemoved(goals, golem, goal(vanilla, "hurt_by", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "move_towards", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "look_at_player", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "random_look_around", 0));
  }

  @Test
  void combatOffRemovesFrostsRangedAttackGoal() {
    Mob golem = mock(Snowman.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaFrostGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.FROST, goals);
    controller.attach();
    remaining(goals, golem, vanilla, "nearest_attackable", "water_avoiding_random_stroll");

    controller.setCombat(false);

    verifyRemoved(goals, golem, goal(vanilla, "ranged_attack", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "look_at_player", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "random_look_around", 0));
  }

  @Test
  void combatOffRemovesTheBoneBowMeleeAndTargetingGoals() {
    Mob golem = mock(Skeleton.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaBoneGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.BONE, goals);
    controller.attach();
    remaining(goals, golem, vanilla, "restrict_sun", "flee_sun", "avoid_entity", "water_avoiding_random_stroll");

    controller.setCombat(false);

    verifyRemoved(goals, golem, goal(vanilla, "ranged_bow_attack", 0));
    verifyRemoved(goals, golem, goal(vanilla, "skeleton_melee", 0));
    verifyRemoved(goals, golem, goal(vanilla, "hurt_by", 0));
    verifyRemoved(goals, golem, goal(vanilla, "nearest_attackable", 0));
    verifyRemoved(goals, golem, goal(vanilla, "nearest_attackable", 1));
    verifyRemoved(goals, golem, goal(vanilla, "nearest_attackable", 2));
    verifyNotRemoved(goals, golem, goal(vanilla, "look_at_player", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "random_look_around", 0));
  }

  @Test
  void combatOnAgainRestoresEveryRemovedGoalAtItsVanillaPriority() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    remaining(goals, golem, vanilla, "iron_golem_defend_village", "iron_golem_offer_flower", "move_back_to_village",
        "golem_random_stroll_in_village", "nearest_attackable");
    controller.setCombat(false);
    clearInvocations(goals);

    controller.setCombat(true);

    verifyAdded(goals, golem, 1, goal(vanilla, "melee_attack", 0));
    verifyAdded(goals, golem, 2, goal(vanilla, "hurt_by", 0));
    verify(goals).addGoal(golem, GoalGolemController.TARGET_PRIORITY, target);
    verify(goals, times(3)).addGoal(eq(golem), anyInt(), any());
  }

  @Test
  void boneRestoresItsTargetingGoalsAtTheirListedPriorities() {
    Mob golem = mock(Skeleton.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaBoneGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.BONE, goals);
    controller.attach();
    remaining(goals, golem, vanilla, "restrict_sun", "flee_sun", "avoid_entity", "water_avoiding_random_stroll");
    controller.setCombat(false);
    clearInvocations(goals);

    controller.setCombat(true);

    verifyAdded(goals, golem, 4, goal(vanilla, "ranged_bow_attack", 0));
    verifyAdded(goals, golem, 4, goal(vanilla, "skeleton_melee", 0));
    verifyAdded(goals, golem, 1, goal(vanilla, "hurt_by", 0));
    verifyAdded(goals, golem, 3, goal(vanilla, "nearest_attackable", 0));
    verifyAdded(goals, golem, 3, goal(vanilla, "nearest_attackable", 1));
    verifyAdded(goals, golem, 3, goal(vanilla, "nearest_attackable", 2));
  }

  @Test
  void combatOffClearsTheTargetTheTargetGoalWasHolding() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    when(goals.getAllGoals(golem)).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    LivingEntity zombie = living();
    controller.setTarget(zombie);
    target.start();

    controller.setCombat(false);

    assertThat(target.shouldActivate()).isFalse();
    assertThat(target.shouldStayActive()).isFalse();
  }

  @Test
  void settingTheSameCombatStateTwiceChangesNothingTheSecondTime() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    clearInvocations(goals);

    controller.setCombat(true);
    verifyNoInteractions(goals);

    controller.setCombat(false);
    clearInvocations(goals);

    controller.setCombat(false);

    verifyNoInteractions(goals);
  }

  @Test
  void combatSettingsBeforeAttachTouchNoGoals() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);

    controller.setCombat(false);
    controller.setCombat(true);

    verify(goals, never()).addGoal(any(), anyInt(), any());
    verify(goals, never()).removeGoal(any(), any(Goal.class));
    verify(goals, never()).getAllGoals(any());
  }

  @Test
  void releaseWhileCombatIsOffRestoresTheAttackGoalsButNotTheTargetGoal() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    remaining(goals, golem, vanilla, "iron_golem_defend_village", "iron_golem_offer_flower", "move_back_to_village",
        "golem_random_stroll_in_village", "nearest_attackable");
    controller.setCombat(false);
    clearInvocations(goals);

    controller.release();

    verifyAdded(goals, golem, 1, goal(vanilla, "melee_attack", 0));
    verifyAdded(goals, golem, 2, goal(vanilla, "hurt_by", 0));
    verify(goals, never()).addGoal(eq(golem), eq(GoalGolemController.TARGET_PRIORITY), same(target));
  }

  @Test
  void aReleasedControllerStartsFightingAgainOnTheNextAttach() {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> vanilla = vanillaIronGoals();
    when(goals.getAllGoals(golem)).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    controller.setCombat(false);
    controller.release();
    controller.attach();
    clearInvocations(goals);

    controller.setCombat(false);

    verify(goals).removeGoal(eq(golem), any(GolemTargetGoal.class));
  }

  private static void verifyRemoved(MobGoals goals, Mob golem, Goal<Mob> goal) {
    verify(goals).removeGoal(golem, goal);
  }

  private static void verifyNotRemoved(MobGoals goals, Mob golem, Goal<Mob> goal) {
    verify(goals, never()).removeGoal(golem, goal);
  }

  private static void verifyAdded(MobGoals goals, Mob golem, int priority, Goal<Mob> goal) {
    verify(goals).addGoal(golem, priority, goal);
  }

  private static void remaining(MobGoals goals, Mob golem, List<Goal<Mob>> vanilla, String... removedAtAttach) {
    List<Goal<Mob>> left = vanillaWithout(vanilla, removedAtAttach);
    when(goals.getAllGoals(golem)).thenReturn(left);
  }

  private static List<Goal<Mob>> vanillaWithout(List<Goal<Mob>> vanilla, String... keys) {
    List<String> dropped = List.of(keys);
    List<Goal<Mob>> remaining = new ArrayList<>();
    for (Goal<Mob> goal : vanilla) {
      if (!dropped.contains(goal.getKey().getNamespacedKey().getKey())) {
        remaining.add(goal);
      }
    }
    return remaining;
  }

  private static Map<String, Integer> combatPriorities(GolemBody body) {
    Map<String, Integer> priorities = new LinkedHashMap<>();
    for (GoalGolemController.VanillaGoal goal : GoalGolemController.combatGoals(body)) {
      priorities.put(goal.key().toString(), goal.priority());
    }
    return priorities;
  }

  private static List<String> keys(GolemBody body) {
    List<String> keys = new ArrayList<>();
    for (GoalGolemController.VanillaGoal goal : GoalGolemController.removedGoals(body)) {
      keys.add(goal.key().toString());
    }
    return keys;
  }

  private static Map<String, Integer> priorities(GolemBody body) {
    Map<String, Integer> priorities = new LinkedHashMap<>();
    for (GoalGolemController.VanillaGoal goal : GoalGolemController.removedGoals(body)) {
      priorities.put(goal.key().toString(), goal.priority());
    }
    return priorities;
  }

  private static void assertRestoredInAnyOrder(GolemBody body, List<Goal<Mob>> vanilla, int seed) {
    Mob golem = mock(IronGolem.class);
    MobGoals goals = mock(MobGoals.class);
    List<Goal<Mob>> shuffled = new ArrayList<>(vanilla);
    Collections.shuffle(shuffled, new Random(seed));
    when(goals.getAllGoals(golem)).thenReturn(shuffled);
    Map<String, Integer> expected = priorities(body);
    Map<Goal<Mob>, Integer> restored = new LinkedHashMap<>();
    for (Goal<Mob> goal : vanilla) {
      Integer priority = expected.get(goal.getKey().getNamespacedKey().toString());
      if (priority != null) {
        restored.put(goal, priority);
      }
    }
    GoalGolemController controller = new GoalGolemController(golem, body, goals);
    controller.attach();

    controller.release();

    for (Map.Entry<Goal<Mob>, Integer> entry : restored.entrySet()) {
      verify(goals).addGoal(golem, entry.getValue().intValue(), entry.getKey());
    }
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

  private static List<Goal<Mob>> vanillaFrostGoals() {
    return List.of(
        vanillaGoal("ranged_attack", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("water_avoiding_random_stroll", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("look_at_player", EnumSet.of(GoalType.LOOK)),
        vanillaGoal("random_look_around", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("nearest_attackable", EnumSet.of(GoalType.TARGET)));
  }

  private static List<Goal<Mob>> vanillaBoneGoals() {
    return List.of(
        vanillaGoal("restrict_sun", EnumSet.noneOf(GoalType.class)),
        vanillaGoal("flee_sun", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("avoid_entity", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("ranged_bow_attack", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("skeleton_melee", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("water_avoiding_random_stroll", EnumSet.of(GoalType.MOVE)),
        vanillaGoal("look_at_player", EnumSet.of(GoalType.LOOK)),
        vanillaGoal("random_look_around", EnumSet.of(GoalType.MOVE, GoalType.LOOK)),
        vanillaGoal("hurt_by", EnumSet.of(GoalType.TARGET)),
        vanillaGoal("nearest_attackable", EnumSet.of(GoalType.TARGET)),
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
