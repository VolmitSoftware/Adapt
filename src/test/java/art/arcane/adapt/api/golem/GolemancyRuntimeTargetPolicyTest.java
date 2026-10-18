package art.arcane.adapt.api.golem;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GolemancyRuntimeTargetPolicyTest {
  private final GolemTargetPolicy first = new NamedPolicy();
  private final GolemTargetPolicy second = new NamedPolicy();

  @AfterEach
  void restoreTheCompanion() {
    GolemancyRuntime.clearTargetPolicy(GolemCoreType.GUARDIAN, first);
    GolemancyRuntime.clearTargetPolicy(GolemCoreType.GUARDIAN, second);
  }

  @Test
  void clearingARolePolicyRestoresTheCompanionPolicy() {
    GolemancyRuntime.setTargetPolicy(GolemCoreType.GUARDIAN, first);
    assertThat(GolemancyRuntime.targetPolicy(List.of(GolemCoreType.GUARDIAN))).isSameAs(first);

    GolemancyRuntime.clearTargetPolicy(GolemCoreType.GUARDIAN, first);

    assertThat(GolemancyRuntime.targetPolicy(List.of(GolemCoreType.GUARDIAN))).isInstanceOf(CompanionTargetPolicy.class);
  }

  @Test
  void clearingOnlyRemovesThatExactPolicy() {
    GolemancyRuntime.setTargetPolicy(GolemCoreType.GUARDIAN, second);

    GolemancyRuntime.clearTargetPolicy(GolemCoreType.GUARDIAN, first);

    assertThat(GolemancyRuntime.targetPolicy(List.of(GolemCoreType.GUARDIAN))).isSameAs(second);
    assertThat(GolemancyRuntime.targetPolicy(List.of(GolemCoreType.GATHERER))).isInstanceOf(CompanionTargetPolicy.class);
  }

  @Test
  void aDualCoreGolemFightsWithTheFirstCoreThatHasACombatPolicy() {
    GolemancyRuntime.setTargetPolicy(GolemCoreType.GUARDIAN, first);

    assertThat(GolemancyRuntime.targetPolicy(List.of(GolemCoreType.HARVESTER, GolemCoreType.GUARDIAN))).isSameAs(first);
    assertThat(GolemancyRuntime.targetPolicy(List.of(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER))).isSameAs(first);
    assertThat(GolemancyRuntime.targetPolicy(List.of(GolemCoreType.HARVESTER, GolemCoreType.GATHERER))).isInstanceOf(CompanionTargetPolicy.class);
    assertThat(GolemancyRuntime.targetPolicy(List.of())).isInstanceOf(CompanionTargetPolicy.class);
  }

  private static final class NamedPolicy implements GolemTargetPolicy {
    @Override
    public LivingEntity select(GolemHandle golem, Player owner) {
      return null;
    }

    @Override
    public boolean holds(GolemHandle golem, Player owner, LivingEntity target) {
      return false;
    }
  }
}
