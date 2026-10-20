package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.Bukkit;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class GolemControllerSelectionTest {
  private final FakeGolemPlatform platform = new FakeGolemPlatform();

  @Test
  void boneBodiesGetTheGoalController() {
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getMobGoals).thenReturn(mock(MobGoals.class));

      assertThat(GolemancyRuntime.controllerFor(handle(GolemTestEntities.entity(Skeleton.class), GolemBody.BONE)))
          .hasValueSatisfying(controller -> assertThat(controller).isInstanceOf(GoalGolemController.class));
    }
  }

  @Test
  void copperBodiesGetTheRepathingController() {
    assertThat(GolemancyRuntime.controllerFor(handle(GolemTestEntities.entity(CopperGolem.class), GolemBody.COPPER)))
        .hasValueSatisfying(controller -> assertThat(controller).isInstanceOf(CopperGolemController.class));
  }

  private GolemHandle handle(Mob golem, GolemBody body) {
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), body, GolemCoreType.BLANK,
        GolemState.Anchor.of(UUID.randomUUID(), 0, 64, 0)));
    return new GolemHandle(golem, state, platform);
  }
}
