package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemWorkGateTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

  @Test
  void aGolemInColossusFormTakesNoWorkUntilItLeavesTheForm() {
    GolemHandle handle = ironGolem();

    assertThat(GolemWorkGate.canWork(handle)).isTrue();

    handle.setColossus(true);
    assertThat(GolemWorkGate.canWork(handle)).isFalse();

    handle.setColossus(false);
    assertThat(GolemWorkGate.canWork(handle)).isTrue();
  }

  private static GolemHandle ironGolem() {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    lenient().when(golem.location()).thenReturn(new Loc(world, 0, 64, 0));
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.BLANK,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    return new GolemHandle(golem, state, new FakeGolemPlatform());
  }
}
