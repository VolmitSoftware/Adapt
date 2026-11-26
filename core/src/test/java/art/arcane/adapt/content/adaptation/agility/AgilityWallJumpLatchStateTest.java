package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgilityWallJumpLatchStateTest {
  @Test
  void unregisterClearsAllLatchTrackingState() throws Exception {
    AgilityWallJump adaptation = new AgilityWallJump();
    UUID id = UUID.randomUUID();
    Map<UUID, Double> airjumps = field(adaptation, "airjumps");
    Map<UUID, Vec3> horizontalIntent = field(adaptation, "horizontalIntent");
    Map<UUID, Long> horizontalIntentTime = field(adaptation, "horizontalIntentTime");
    Map<UUID, Boolean> sneakState = field(adaptation, "sneakState");
    Map<UUID, BlockRef> latchedWalls = field(adaptation, "latchedWalls");
    airjumps.put(id, 1.5D);
    horizontalIntent.put(id, new Vec3(1, 0, 0));
    horizontalIntentTime.put(id, 1_000L);
    sneakState.put(id, true);
    latchedWalls.put(id, mock(BlockRef.class));

    adaptation.unregister();

    assertThat(airjumps).isEmpty();
    assertThat(horizontalIntent).isEmpty();
    assertThat(horizontalIntentTime).isEmpty();
    assertThat(sneakState).isEmpty();
    assertThat(latchedWalls).isEmpty();
  }

  @Test
  void latchStateDrivesJumpReleaseDecision() {
    assertThat(AgilityWallJump.shouldReleaseJump(false, true)).isTrue();
    assertThat(AgilityWallJump.shouldReleaseJump(true, true)).isFalse();
    assertThat(AgilityWallJump.shouldReleaseJump(false, false)).isFalse();
    assertThat(AgilityWallJump.shouldReleaseJump(true, false)).isFalse();
  }

  @Test
  void latchFallProtectionClearsAccumulatedFallDistance() {
    PlayerRef player = mock(PlayerRef.class);

    AgilityWallLatch.clearLatchedFallDistance(player);

    verify(player).setFallDistance(0F);
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(target);
  }
}
