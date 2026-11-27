package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalEntry;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
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
  private static final Map<MobGoalsRef, Map<AdaptGoal, GoalEntry>> ENTRIES = new IdentityHashMap<>();
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
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaBoneGoals();
    when(goals.all()).thenReturn(vanilla);

    new GoalGolemController(golem, GolemBody.BONE, goals).attach();

    for (GoalEntry goal : vanilla) {
      String key = goal.key().path();
      if (List.of("restrict_sun", "flee_sun", "avoid_entity", "water_avoiding_random_stroll").contains(key)) {
        verify(goals).remove(goal);
      } else {
        verify(goals, never()).remove(goal);
      }
    }
  }

  @Test
  void attachRemovesOnlyListedGoalsAndAddsWorkAndTargetGoals() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(vanilla);

    new GoalGolemController(golem, GolemBody.IRON, goals).attach();

    verify(goals).removeAll(GolemWorkGoal.KEY);
    verify(goals).removeAll(GolemTargetGoal.KEY);
    for (GoalEntry goal : vanilla) {
      String key = goal.key().toString();
      if (keys(GolemBody.IRON).contains(key)) {
        verify(goals).remove(goal);
      } else {
        verify(goals, never()).remove(goal);
      }
    }
    verify(goals).add(eq(GoalGolemController.WORK_PRIORITY), any(GolemWorkGoal.class));
    verify(goals).add(eq(GoalGolemController.TARGET_PRIORITY), any(GolemTargetGoal.class));
  }

  @Test
  void attachTwiceChangesNothingTheSecondTime() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);

    controller.attach();
    controller.attach();

    verify(goals, times(1)).all();
    verify(goals, times(2)).add(anyInt(), any());
  }

  @Test
  void releaseRestoresRemovedGoalsAtTheirVanillaPriorities() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(new ArrayList<>(new HashSet<>(vanilla)));
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemWorkGoal work = added(goals, golem, GoalGolemController.WORK_PRIORITY, GolemWorkGoal.class);
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);

    GoalEntry defend = goal(vanilla, "iron_golem_defend_village", 0);
    GoalEntry flower = goal(vanilla, "iron_golem_offer_flower", 0);
    GoalEntry village = goal(vanilla, "move_back_to_village", 0);
    GoalEntry stroll = goal(vanilla, "golem_random_stroll_in_village", 0);
    GoalEntry players = goal(vanilla, "nearest_attackable", 0);
    GoalEntry monsters = goal(vanilla, "nearest_attackable", 1);
    GoalEntry melee = goal(vanilla, "melee_attack", 0);

    controller.release();

    verify(goals).remove(entryOf(goals, work));
    verify(goals).remove(entryOf(goals, target));
    verify(goals).restore(defend, 1);
    verify(goals).restore(flower, 5);
    verify(goals).restore(village, 2);
    verify(goals).restore(stroll, 4);
    verify(goals).restore(players, 3);
    verify(goals).restore(monsters, 3);
    verify(goals, never()).restore(same(melee), anyInt());
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
    MobRef golem = golem();
    MobGoalsRef goals = goals();

    new GoalGolemController(golem, GolemBody.IRON, goals).release();

    verify(goals, never()).add(anyInt(), any());
    verify(goals, never()).restore(any(), anyInt());
    verify(goals, never()).remove(any());
  }

  @Test
  void workGoalRunsOnlyWhileTheBrainHasAMoveTarget() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    when(goals.all()).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemWorkGoal work = added(goals, golem, GoalGolemController.WORK_PRIORITY, GolemWorkGoal.class);

    assertThat(work.shouldActivate()).isFalse();
    assertThat(work.flags()).containsExactly(GoalFlag.MOVE);
    controller.moveTo(new Loc(mock(WorldRef.class), 1, 64, 1), 1D);
    assertThat(work.shouldActivate()).isTrue();
    assertThat(work.shouldStayActive()).isTrue();
    controller.stop();
    assertThat(work.shouldStayActive()).isFalse();
  }

  @Test
  void targetGoalAppliesTheBrainTargetAndClearsOnlyItsOwnTarget() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    when(goals.all()).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    LivingRef zombie = living();

    assertThat(target.flags()).containsExactly(GoalFlag.TARGET);
    assertThat(target.shouldActivate()).isFalse();
    controller.setTarget(zombie);
    assertThat(target.shouldActivate()).isTrue();
    target.start();
    verify(golem).setTarget(zombie);

    when(golem.target()).thenReturn(zombie);
    controller.setTarget(null);
    assertThat(target.shouldStayActive()).isFalse();
    target.stop();
    verify(golem).setTarget(null);
  }

  @Test
  void targetGoalLeavesAnotherTargetInPlaceWhenItStops() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    when(goals.all()).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    LivingRef zombie = living();
    LivingRef other = living();
    controller.setTarget(zombie);
    target.start();

    when(golem.target()).thenReturn(other);
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
        assertThat(keys(body)).as(body.id().toString()).doesNotContain(combat.key().toString());
      }
    }
  }

  @Test
  void combatOffRemovesTheTargetGoalAndIronsAttackAndRetaliationGoals() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    remaining(goals, golem, vanilla, "iron_golem_defend_village", "iron_golem_offer_flower", "move_back_to_village",
        "golem_random_stroll_in_village", "nearest_attackable");

    controller.setCombat(false);

    verify(goals).remove(entryOf(goals, target));
    verifyRemoved(goals, golem, goal(vanilla, "melee_attack", 0));
    verifyRemoved(goals, golem, goal(vanilla, "hurt_by", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "move_towards", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "look_at_player", 0));
    verifyNotRemoved(goals, golem, goal(vanilla, "random_look_around", 0));
  }

  @Test
  void combatOffRemovesFrostsRangedAttackGoal() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaFrostGoals();
    when(goals.all()).thenReturn(vanilla);
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
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaBoneGoals();
    when(goals.all()).thenReturn(vanilla);
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
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(vanilla);
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
    verify(goals).add(GoalGolemController.TARGET_PRIORITY, target);
    verify(goals, times(2)).restore(any(), anyInt());
    verify(goals, times(1)).add(anyInt(), any());
  }

  @Test
  void boneRestoresItsTargetingGoalsAtTheirListedPriorities() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaBoneGoals();
    when(goals.all()).thenReturn(vanilla);
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
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    when(goals.all()).thenReturn(List.of());
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    GolemTargetGoal target = added(goals, golem, GoalGolemController.TARGET_PRIORITY, GolemTargetGoal.class);
    LivingRef zombie = living();
    controller.setTarget(zombie);
    target.start();

    controller.setCombat(false);

    assertThat(target.shouldActivate()).isFalse();
    assertThat(target.shouldStayActive()).isFalse();
  }

  @Test
  void settingTheSameCombatStateTwiceChangesNothingTheSecondTime() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(vanilla);
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
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);

    controller.setCombat(false);
    controller.setCombat(true);

    verify(goals, never()).add(anyInt(), any());
    verify(goals, never()).remove(any());
    verify(goals, never()).all();
  }

  @Test
  void releaseWhileCombatIsOffRestoresTheAttackGoalsButNotTheTargetGoal() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(vanilla);
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
    verify(goals, never()).add(eq(GoalGolemController.TARGET_PRIORITY), same(target));
  }

  @Test
  void aReleasedControllerStartsFightingAgainOnTheNextAttach() {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> vanilla = vanillaIronGoals();
    when(goals.all()).thenReturn(vanilla);
    GoalGolemController controller = new GoalGolemController(golem, GolemBody.IRON, goals);
    controller.attach();
    controller.setCombat(false);
    controller.release();
    controller.attach();
    clearInvocations(goals);

    controller.setCombat(false);

    verify(goals).remove(argThat(entry -> entry != null && entry.unwrap() instanceof GolemTargetGoal));
  }

  private static void verifyRemoved(MobGoalsRef goals, MobRef golem, GoalEntry goal) {
    verify(goals).remove(goal);
  }

  private static void verifyNotRemoved(MobGoalsRef goals, MobRef golem, GoalEntry goal) {
    verify(goals, never()).remove(goal);
  }

  private static void verifyAdded(MobGoalsRef goals, MobRef golem, int priority, GoalEntry goal) {
    verify(goals).restore(goal, priority);
  }

  private static void remaining(MobGoalsRef goals, MobRef golem, List<GoalEntry> vanilla, String... removedAtAttach) {
    List<GoalEntry> left = vanillaWithout(vanilla, removedAtAttach);
    when(goals.all()).thenReturn(left);
  }

  private static List<GoalEntry> vanillaWithout(List<GoalEntry> vanilla, String... keys) {
    List<String> dropped = List.of(keys);
    List<GoalEntry> remaining = new ArrayList<>();
    for (GoalEntry goal : vanilla) {
      if (!dropped.contains(goal.key().path())) {
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

  private static void assertRestoredInAnyOrder(GolemBody body, List<GoalEntry> vanilla, int seed) {
    MobRef golem = golem();
    MobGoalsRef goals = goals();
    List<GoalEntry> shuffled = new ArrayList<>(vanilla);
    Collections.shuffle(shuffled, new Random(seed));
    when(goals.all()).thenReturn(shuffled);
    Map<String, Integer> expected = priorities(body);
    Map<GoalEntry, Integer> restored = new LinkedHashMap<>();
    for (GoalEntry goal : vanilla) {
      Integer priority = expected.get(goal.key().toString());
      if (priority != null) {
        restored.put(goal, priority);
      }
    }
    GoalGolemController controller = new GoalGolemController(golem, body, goals);
    controller.attach();

    controller.release();

    for (Map.Entry<GoalEntry, Integer> entry : restored.entrySet()) {
      verify(goals).restore(entry.getKey(), entry.getValue().intValue());
    }
  }

  private static List<GoalEntry> vanillaIronGoals() {
    return List.of(
        vanillaGoal("melee_attack", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("move_towards", Set.of(GoalFlag.MOVE)),
        vanillaGoal("move_back_to_village", Set.of(GoalFlag.MOVE)),
        vanillaGoal("golem_random_stroll_in_village", Set.of(GoalFlag.MOVE)),
        vanillaGoal("iron_golem_offer_flower", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("look_at_player", Set.of(GoalFlag.LOOK)),
        vanillaGoal("random_look_around", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("iron_golem_defend_village", Set.of(GoalFlag.TARGET)),
        vanillaGoal("hurt_by", Set.of(GoalFlag.TARGET)),
        vanillaGoal("nearest_attackable", Set.of(GoalFlag.TARGET)),
        vanillaGoal("nearest_attackable", Set.of(GoalFlag.TARGET)));
  }

  private static List<GoalEntry> vanillaFrostGoals() {
    return List.of(
        vanillaGoal("ranged_attack", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("water_avoiding_random_stroll", Set.of(GoalFlag.MOVE)),
        vanillaGoal("look_at_player", Set.of(GoalFlag.LOOK)),
        vanillaGoal("random_look_around", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("nearest_attackable", Set.of(GoalFlag.TARGET)));
  }

  private static List<GoalEntry> vanillaBoneGoals() {
    return List.of(
        vanillaGoal("restrict_sun", Set.of()),
        vanillaGoal("flee_sun", Set.of(GoalFlag.MOVE)),
        vanillaGoal("avoid_entity", Set.of(GoalFlag.MOVE)),
        vanillaGoal("ranged_bow_attack", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("skeleton_melee", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("water_avoiding_random_stroll", Set.of(GoalFlag.MOVE)),
        vanillaGoal("look_at_player", Set.of(GoalFlag.LOOK)),
        vanillaGoal("random_look_around", Set.of(GoalFlag.MOVE, GoalFlag.LOOK)),
        vanillaGoal("hurt_by", Set.of(GoalFlag.TARGET)),
        vanillaGoal("nearest_attackable", Set.of(GoalFlag.TARGET)),
        vanillaGoal("nearest_attackable", Set.of(GoalFlag.TARGET)),
        vanillaGoal("nearest_attackable", Set.of(GoalFlag.TARGET)));
  }

  private static GoalEntry vanillaGoal(String key, Set<GoalFlag> types) {
    GoalEntry goal = mock(GoalEntry.class);
    when(goal.key()).thenReturn(Id.minecraft(key));
    when(goal.flags()).thenReturn(types);
    when(goal.isCustom()).thenReturn(false);
    return goal;
  }

  private static GoalEntry goal(List<GoalEntry> goals, String key, int occurrence) {
    int seen = 0;
    for (GoalEntry goal : goals) {
      if (goal.key().path().equals(key) && seen++ == occurrence) {
        return goal;
      }
    }
    throw new AssertionError("no goal " + key + "#" + occurrence);
  }

  private static <G extends AdaptGoal> G added(MobGoalsRef goals, MobRef golem, int priority, Class<G> type) {
    ArgumentCaptor<AdaptGoal> captor = ArgumentCaptor.forClass(AdaptGoal.class);
    verify(goals, atLeastOnce()).add(eq(priority), captor.capture());
    for (AdaptGoal goal : captor.getAllValues()) {
      if (type.isInstance(goal)) {
        return type.cast(goal);
      }
    }
    throw new AssertionError("no " + type.getSimpleName() + " added at priority " + priority);
  }

  private static MobRef golem() {
    MobRef golem = mock(MobRef.class);
    when(golem.uuid()).thenReturn(UUID.randomUUID());
    return golem;
  }

  private static MobGoalsRef goals() {
    MobGoalsRef goals = mock(MobGoalsRef.class);
    Map<AdaptGoal, GoalEntry> entries = new IdentityHashMap<>();
    when(goals.add(anyInt(), any(AdaptGoal.class))).thenAnswer(invocation -> {
      AdaptGoal goal = invocation.getArgument(1);
      int priority = invocation.getArgument(0);
      return entries.computeIfAbsent(goal, ignored -> customEntry(goal, priority));
    });
    ENTRIES.put(goals, entries);
    return goals;
  }

  private static GoalEntry customEntry(AdaptGoal goal, int priority) {
    GoalEntry entry = mock(GoalEntry.class);
    when(entry.key()).thenReturn(goal.key());
    when(entry.priority()).thenReturn(priority);
    when(entry.isCustom()).thenReturn(true);
    when(entry.unwrap()).thenReturn(goal);
    return entry;
  }

  private static GoalEntry entryOf(MobGoalsRef goals, AdaptGoal goal) {
    GoalEntry entry = ENTRIES.get(goals).get(goal);
    if (entry == null) {
      throw new AssertionError("no entry for " + goal);
    }
    return entry;
  }

  private static LivingRef living() {
    LivingRef entity = mock(LivingRef.class);
    when(entity.isValid()).thenReturn(true);
    when(entity.isDead()).thenReturn(false);
    when(entity.uuid()).thenReturn(UUID.randomUUID());
    return entity;
  }
}
