package art.arcane.adapt.api.xp;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MovementXpTest {
  @Test
  void movementRequiresMeaningfulDistanceWithinTheSameWorld() {
    WorldRef world = mock(WorldRef.class);
    Loc origin = new Loc(world, 0D, 64D, 0D);
    assertThat(MovementXp.moved(origin, new Loc(world, 0D, 64D, 0D))).isFalse();
    assertThat(MovementXp.moved(origin, new Loc(world, 0.1D, 64D, 0D))).isFalse();
    assertThat(MovementXp.moved(origin, new Loc(world, 1D, 64D, 0D))).isTrue();
    assertThat(MovementXp.moved(origin, new Loc(world, 100D, 64D, 0D))).isFalse();
    assertThat(MovementXp.moved(origin, new Loc(mock(WorldRef.class), 1D, 64D, 0D))).isFalse();
    assertThat(MovementXp.moved(null, origin)).isFalse();
  }
}
