package art.arcane.adapt.util.common.math;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.port.BukkitTextPorts;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Paper before 26.x does not report movement keys, so input is inferred from the movement the client reported (the
 * server's own velocity of a player never includes walking).
 */
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
  void clientMovementAlongTheLookDirectionReadsAsForward() {
    PlayerRef player = moving(new Vec3(0, 0.3, 0.8));

    assertThat(VelocitySpeed.readInput(player, 0.000001, Vec3.ZERO).isForwardOnly()).isTrue();
  }

  @Test
  void movementBelowTheThresholdReadsAsNoInput() {
    PlayerRef player = moving(new Vec3(0.0001, 0.3, 0));

    assertThat(VelocitySpeed.readInput(player, 0.01, Vec3.ZERO).hasHorizontal()).isFalse();
  }

  /** The legacy jar reads movement through its server natives, which unit tests do not load, so it is stubbed. */
  private static PlayerRef moving(Vec3 movement) {
    PlayerRef player = spy(BukkitRefs.player(facingSouth(new Vector(0, 0, 0))));
    doReturn(movement).when(player).movement();
    return player;
  }

  private static Player facingSouth(Vector velocity) {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(world.getName()).thenReturn("world");
    when(world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getVelocity()).thenReturn(velocity);
    when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0, 0F, 0F));
    return player;
  }
}
