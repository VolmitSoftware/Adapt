package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitRefsReset;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Loc;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitEntityRefTeleportTest {
  private static final UUID OVERWORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

  private MockedStatic<Bukkit> bukkit;
  private World overworld;

  @BeforeEach
  void setUp() {
    overworld = mock(World.class);
    lenient().when(overworld.getUID()).thenReturn(OVERWORLD);
    lenient().when(overworld.getName()).thenReturn("world");
    lenient().when(overworld.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getWorld(OVERWORLD)).thenReturn(overworld);
  }

  @AfterEach
  void tearDown() {
    bukkit.close();
    BukkitRefsReset.clear();
  }

  @Test
  void teleportWithoutTeleportFlagsRemountsEachPassenger() {
    IronGolem golem = golem();
    TextDisplay label = mock(TextDisplay.class);
    Location destination = new Location(overworld, 4D, 64D, 4D);
    when(golem.getPassengers()).thenReturn(List.of(label));
    when(golem.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)).thenReturn(true);

    assertThat(new BukkitEntityRef(golem).teleport(loc(), TeleportCause.PLUGIN, true)).isCompletedWithValue(true);

    InOrder order = inOrder(golem, label);
    order.verify(golem).removePassenger(label);
    order.verify(golem).teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    order.verify(label).teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    order.verify(golem).addPassenger(label);
  }

  @Test
  void teleportWithoutTeleportFlagsRemountsEvenWhenTheTeleportIsRefused() {
    IronGolem refused = golem();
    TextDisplay label = mock(TextDisplay.class);
    when(refused.getPassengers()).thenReturn(List.of(label));

    assertThat(new BukkitEntityRef(refused).teleport(loc(), TeleportCause.PLUGIN, true)).isCompletedWithValue(false);

    verify(label, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    verify(refused).addPassenger(label);
  }

  private Loc loc() {
    return new Loc(BukkitRefs.world(overworld), 4D, 64D, 4D);
  }

  private static IronGolem golem() {
    IronGolem golem = mock(IronGolem.class);
    when(golem.getUniqueId()).thenReturn(UUID.randomUUID());
    when(golem.getType()).thenReturn(EntityType.IRON_GOLEM);
    lenient().when(golem.teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class), any(TeleportFlag.class)))
        .thenThrow(new NoSuchMethodError("teleport flags absent"));
    return golem;
  }
}
