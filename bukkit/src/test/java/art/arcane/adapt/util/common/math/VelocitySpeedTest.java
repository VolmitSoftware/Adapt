package art.arcane.adapt.util.common.math;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.port.BukkitTextPorts;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocitySpeedTest {
  @BeforeEach
  void installPorts() {
    BukkitTextPorts.install(mock(Adapt.class));
  }

  @AfterEach
  void uninstallPorts() {
    BukkitTextPorts.uninstall();
  }

  @Test
  void neutralInputDoesNotTreatKnockbackAsMovement() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getCurrentInput()).thenReturn(mock(Input.class));
    when(player.getVelocity()).thenReturn(new Vector(0.8, 0.3, 0));
    when(player.getLocation()).thenReturn(new Location(null, 0, 0, 0));

    assertThat(VelocitySpeed.readInput(BukkitRefs.player(player), 0.000001, Vec3.ZERO).hasHorizontal()).isFalse();
    verify(player, never()).getVelocity();
  }

  @Test
  void pressedMovementInputIsPreserved() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Input input = mock(Input.class);
    when(player.getCurrentInput()).thenReturn(input);
    when(input.isForward()).thenReturn(true);

    assertThat(VelocitySpeed.readInput(BukkitRefs.player(player), 0.000001, Vec3.ZERO).isForwardOnly()).isTrue();
  }
}
