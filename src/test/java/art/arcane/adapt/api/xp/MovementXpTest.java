package art.arcane.adapt.api.xp;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MovementXpTest {
  @Test
  void movementRequiresMeaningfulDistanceWithinTheSameWorld() {
    World world = mock(World.class);
    Location origin = new Location(world, 0D, 64D, 0D);
    assertThat(MovementXp.moved(origin, origin.clone())).isFalse();
    assertThat(MovementXp.moved(origin, new Location(world, 0.1D, 64D, 0D))).isFalse();
    assertThat(MovementXp.moved(origin, new Location(world, 1D, 64D, 0D))).isTrue();
    assertThat(MovementXp.moved(origin, new Location(world, 100D, 64D, 0D))).isFalse();
    assertThat(MovementXp.moved(origin, new Location(mock(World.class), 1D, 64D, 0D))).isFalse();
    assertThat(MovementXp.moved(null, origin)).isFalse();
  }
}
