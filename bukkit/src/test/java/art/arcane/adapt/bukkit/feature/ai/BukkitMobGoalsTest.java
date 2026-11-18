package art.arcane.adapt.bukkit.feature.ai;

import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalEntry;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.value.Id;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitMobGoalsTest {
  private static final GoalPriorities UNREADABLE = (Mob ignored) -> GoalPriorities.NONE;
  private final Mob mob = mock(Mob.class);
  private final MobGoals paper = mock(MobGoals.class);
  private final List<Goal<Mob>> installed = new ArrayList<>();

  BukkitMobGoalsTest() {
    when(paper.getAllGoals(mob)).thenAnswer(invocation -> List.copyOf(installed));
    doAnswer(invocation -> installed.add(invocation.getArgument(2))).when(paper).addGoal(eq(mob), anyInt(), org.mockito.ArgumentMatchers.<Goal<Mob>>any());
    doAnswer(invocation -> installed.remove(invocation.<Goal<Mob>>getArgument(1))).when(paper).removeGoal(eq(mob), org.mockito.ArgumentMatchers.<Goal<Mob>>any());
  }

  @Test
  void customGoalsUseThePaperKeyGolemancyRegisters() {
    GoalKey<Mob> key = PaperAdaptGoal.goalKey(Id.adapt("golem_work"));

    assertThat(key.getNamespacedKey()).isEqualTo(new NamespacedKey("adapt", "golem_work"));
    assertThat(key.getEntityClass()).isEqualTo(Mob.class);
    assertThat(PaperAdaptGoal.id(key)).isEqualTo(Id.adapt("golem_work"));
  }

  @Test
  void vanillaKeysMatchById() {
    Goal<Mob> melee = vanilla("melee_attack", EnumSet.of(GoalType.MOVE, GoalType.LOOK));
    installed.add(melee);
    BukkitMobGoals goals = goals(UNREADABLE);

    assertThat(goals.has(Id.minecraft("melee_attack"))).isTrue();
    assertThat(goals.has(Id.minecraft("random_stroll"))).isFalse();
    assertThat(goals.withKey(Id.minecraft("melee_attack"))).singleElement().satisfies((GoalEntry entry) -> {
      assertThat(entry.isCustom()).isFalse();
      assertThat(entry.flags()).containsExactlyInAnyOrder(GoalFlag.MOVE, GoalFlag.LOOK);
      assertThat(entry.unwrap()).isSameAs(melee);
    });
  }

  @Test
  void addInstallsAPaperGoalWithMappedTypes() {
    BukkitMobGoals goals = goals(UNREADABLE);

    GoalEntry entry = goals.add(2, goal(Id.adapt("golem_target"), Set.of(GoalFlag.TARGET)));

    ArgumentCaptor<Goal<Mob>> captor = goalCaptor();
    verify(paper).addGoal(eq(mob), eq(2), captor.capture());
    assertThat(captor.getValue().getTypes()).containsExactly(GoalType.TARGET);
    assertThat(captor.getValue().getKey()).isEqualTo(PaperAdaptGoal.goalKey(Id.adapt("golem_target")));
    assertThat(entry.isCustom()).isTrue();
    assertThat(entry.priority()).isEqualTo(2);
    assertThat(goals.has(Id.adapt("golem_target"))).isTrue();
  }

  @Test
  void removeAllReturnsEntriesThatRestoreAtTheirPriority() {
    Goal<Mob> stroll = vanilla("random_stroll", EnumSet.of(GoalType.MOVE));
    installed.add(stroll);
    BukkitMobGoals goals = goals((Mob ignored) -> (Goal<Mob> goal) -> goal == stroll ? 6 : GoalPriorities.UNKNOWN);

    List<GoalEntry> removed = goals.removeAll(Id.minecraft("random_stroll"));

    assertThat(installed).isEmpty();
    assertThat(removed).singleElement().extracting(GoalEntry::priority).isEqualTo(6);
    goals.restore(removed.getFirst());
    verify(paper).addGoal(mob, 6, stroll);
    assertThat(installed).containsExactly(stroll);
  }

  @Test
  void restoreSkipsGoalsThatAreStillInstalled() {
    Goal<Mob> stroll = vanilla("random_stroll", EnumSet.of(GoalType.MOVE));
    installed.add(stroll);
    BukkitMobGoals goals = goals(UNREADABLE);
    GoalEntry entry = goals.withKey(Id.minecraft("random_stroll")).getFirst();

    goals.restore(entry, 6);

    verify(paper, never()).addGoal(eq(mob), anyInt(), org.mockito.ArgumentMatchers.<Goal<Mob>>any());
  }

  @Test
  void unknownPrioritiesNeedAnExplicitPriority() {
    Goal<Mob> stroll = vanilla("random_stroll", EnumSet.of(GoalType.MOVE));
    installed.add(stroll);
    BukkitMobGoals goals = goals(UNREADABLE);
    GoalEntry entry = goals.removeAll(Id.minecraft("random_stroll")).getFirst();

    assertThatThrownBy(() -> goals.restore(entry)).isInstanceOf(IllegalStateException.class);
    goals.restore(entry, 5);

    verify(paper).addGoal(mob, 5, stroll);
  }

  @Test
  void allIsSortedByPriority() {
    Goal<Mob> late = vanilla("look_at_player", EnumSet.of(GoalType.LOOK));
    Goal<Mob> early = vanilla("melee_attack", EnumSet.of(GoalType.MOVE));
    installed.add(late);
    installed.add(early);
    BukkitMobGoals goals = goals((Mob ignored) -> (Goal<Mob> goal) -> goal == early ? 1 : 7);

    assertThat(goals.all()).extracting(GoalEntry::key)
        .containsExactly(Id.minecraft("melee_attack"), Id.minecraft("look_at_player"));
  }

  private BukkitMobGoals goals(GoalPriorities priorities) {
    return new BukkitMobGoals(mob, paper, priorities);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Goal<Mob>> goalCaptor() {
    return ArgumentCaptor.forClass((Class<Goal<Mob>>) (Class<?>) Goal.class);
  }

  @SuppressWarnings("unchecked")
  private static Goal<Mob> vanilla(String key, EnumSet<GoalType> types) {
    Goal<Mob> goal = mock(Goal.class);
    GoalKey<IronGolem> golemKey = GoalKey.of(IronGolem.class, NamespacedKey.minecraft(key));
    when(goal.getKey()).thenReturn((GoalKey<Mob>) (GoalKey<?>) golemKey);
    when(goal.getTypes()).thenReturn(types);
    return goal;
  }

  private static AdaptGoal goal(Id key, Set<GoalFlag> flags) {
    return new AdaptGoal() {
      @Override
      public Id key() {
        return key;
      }

      @Override
      public Set<GoalFlag> flags() {
        return flags;
      }

      @Override
      public boolean shouldActivate() {
        return true;
      }
    };
  }
}
