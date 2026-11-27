package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AgilityWallJumpPreferenceLifecycleTest extends AdaptTestBase {
  @Test
  void disablingMidairDoesNotRefundSpentWallJumps() throws ReflectiveOperationException {
    AgilityWallJump jump = spy(TestContent.adaptation(Id.adapt("agility"), AgilityWallJump::new));
    PlayerRef player = mock(PlayerRef.class);
    UUID id = UUID.randomUUID();
    when(player.uuid()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    when(player.location()).thenReturn(new Loc(mock(WorldRef.class), 0D, 80D, 0D));
    doReturn(0).when(jump).getActiveInteractLevel(eq(player), any(Loc.class));
    Map<UUID, Double> spent = spentJumps(jump);
    spent.put(id, 2D);
    Method update = AgilityWallJump.class.getDeclaredMethod("updatePlayer", PlayerRef.class);
    update.setAccessible(true);
    update.invoke(jump, player);
    assertThat(spent).containsEntry(id, 2D);
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Double> spentJumps(AgilityWallJump adaptation) throws ReflectiveOperationException {
    Field field = AgilityWallJump.class.getDeclaredField("airjumps");
    field.setAccessible(true);
    return (Map<UUID, Double>) field.get(adaptation);
  }
}
