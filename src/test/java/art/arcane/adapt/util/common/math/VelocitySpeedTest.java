package art.arcane.adapt.util.common.math;

import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocitySpeedTest {
  @Test
  void neutralInputDoesNotTreatKnockbackAsMovement() {
    Player player = mock(Player.class);
    when(player.getCurrentInput()).thenReturn(mock(Input.class));
    when(player.getVelocity()).thenReturn(new Vector(0.8, 0.3, 0));
    when(player.getLocation()).thenReturn(new Location(null, 0, 0, 0));

    assertThat(VelocitySpeed.readInput(player, 0.000001).hasHorizontal()).isFalse();
    verify(player, never()).getVelocity();
  }

  @Test
  void pressedMovementInputIsPreserved() {
    Player player = mock(Player.class);
    Input input = mock(Input.class);
    when(player.getCurrentInput()).thenReturn(input);
    when(input.isForward()).thenReturn(true);

    assertThat(VelocitySpeed.readInput(player, 0.000001).isForwardOnly()).isTrue();
  }
}
