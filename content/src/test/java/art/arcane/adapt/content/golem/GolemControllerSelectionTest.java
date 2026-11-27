package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemControllerSelectionTest {
  private final FakeGolemPlatform platform = new FakeGolemPlatform();

  @Test
  void boneBodiesGetTheGoalController() {
    MobRef skeleton = GolemTestEntities.golem(GolemBody.BONE);
    lenient().when(skeleton.goals()).thenReturn(mock(MobGoalsRef.class));

    assertThat(GolemancyRuntime.controllerFor(handle(skeleton, GolemBody.BONE)))
        .hasValueSatisfying(controller -> assertThat(controller).isInstanceOf(GoalGolemController.class));
  }

  @Test
  void copperBodiesGetTheRepathingController() {
    assertThat(GolemancyRuntime.controllerFor(handle(GolemTestEntities.golem(GolemBody.COPPER), GolemBody.COPPER)))
        .hasValueSatisfying(controller -> assertThat(controller).isInstanceOf(CopperGolemController.class));
  }

  private GolemHandle handle(MobRef golem, GolemBody body) {
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), body, GolemCoreType.BLANK,
        GolemAnchor.of(UUID.randomUUID(), 0, 64, 0)));
    return new GolemHandle(golem, state, platform);
  }
}
