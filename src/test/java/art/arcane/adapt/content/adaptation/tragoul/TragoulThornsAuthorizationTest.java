package art.arcane.adapt.content.adaptation.tragoul;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TragoulThornsAuthorizationTest {
  @Test
  void rotationsAndMovementInsideTheAuthorizedBlockKeepTheSameProtectionLocation() {
    World world = mock(World.class);
    assertThat(TragoulThorns.sameTargetBlock(new Location(world, 2.8D, 100.4D, -0.2D, 90F, 30F),
        new Location(world, 2.1D, 100.8D, -0.8D))).isTrue();
  }

  @Test
  void crossingABlockOrWorldRequiresNewProtectionAuthorization() {
    World world = mock(World.class);
    Location authorized = new Location(world, 2.5D, 100D, 0.5D);
    assertThat(TragoulThorns.sameTargetBlock(new Location(world, 3D, 100D, 0.5D), authorized)).isFalse();
    assertThat(TragoulThorns.sameTargetBlock(new Location(mock(World.class), 2.5D, 100D, 0.5D), authorized)).isFalse();
  }
}
