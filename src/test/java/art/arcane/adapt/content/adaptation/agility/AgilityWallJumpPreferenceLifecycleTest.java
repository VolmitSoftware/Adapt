package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.entity.Player;
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
    AgilityWallJump jump = spy(new AgilityWallJump());
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    when(player.getLocation()).thenReturn(new Location(null, 0D, 80D, 0D));
    doReturn(0).when(jump).getActiveInteractLevel(eq(player), any(Location.class));
    Map<UUID, Double> spent = spentJumps(jump);
    spent.put(id, 2D);
    Method update = AgilityWallJump.class.getDeclaredMethod("updatePlayer", Player.class);
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
