package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TragoulThornsAuthorizationTest {
  @Test
  void rotationsAndMovementInsideTheAuthorizedBlockKeepTheSameProtectionLocation() {
    WorldRef world = mock(WorldRef.class);
    assertThat(TragoulReflection.sameTargetBlock(new Loc(world, 2.8D, 100.4D, -0.2D, 90F, 30F),
        new Loc(world, 2.1D, 100.8D, -0.8D))).isTrue();
  }

  @Test
  void crossingABlockOrWorldRequiresNewProtectionAuthorization() {
    WorldRef world = mock(WorldRef.class);
    Loc authorized = new Loc(world, 2.5D, 100D, 0.5D);
    assertThat(TragoulReflection.sameTargetBlock(new Loc(world, 3D, 100D, 0.5D), authorized)).isFalse();
    assertThat(TragoulReflection.sameTargetBlock(new Loc(mock(WorldRef.class), 2.5D, 100D, 0.5D), authorized)).isFalse();
  }
}
