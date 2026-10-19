package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.reveal.RevealService;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemTeleportTest {
  private static final UUID OVERWORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private MockedStatic<GolemancyRuntime> runtime;
  private RevealService reveal;
  private World overworld;
  private World nether;
  private IronGolem golem;
  private GolemHandle handle;

  @BeforeEach
  void setUp() {
    reveal = mock(RevealService.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.of(reveal));
    overworld = world(OVERWORLD);
    nether = world(NETHER);
    golem = mock(IronGolem.class);
    lenient().when(golem.getWorld()).thenReturn(overworld);
    handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(golem);
  }

  @AfterEach
  void tearDown() {
    runtime.close();
  }

  @Test
  void sameWorldTeleportOnPaperKeepsTheLabelMounted() {
    Location destination = new Location(overworld, 4D, 64D, 4D);
    when(golem.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS))
        .thenReturn(CompletableFuture.completedFuture(true));

    assertThat(GolemTeleport.teleport(handle, destination)).isCompletedWithValue(true);

    verify(golem).teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    verify(golem, never()).eject();
    verify(reveal, never()).forget(any(GolemHandle.class));
  }

  @Test
  void crossWorldTeleportForgetsTheLabelBeforeTeleporting() {
    Location destination = new Location(nether, 4D, 64D, 4D);
    when(golem.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS))
        .thenReturn(CompletableFuture.completedFuture(true));

    assertThat(GolemTeleport.teleport(handle, destination)).isCompletedWithValue(true);

    InOrder order = inOrder(reveal, golem);
    order.verify(reveal).forget(handle);
    order.verify(golem).teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS);
  }

  @Test
  void crossWorldTeleportStillRunsWithoutTheRevealService() {
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.empty());
    Location destination = new Location(nether, 4D, 64D, 4D);
    when(golem.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS))
        .thenReturn(CompletableFuture.completedFuture(true));

    assertThat(GolemTeleport.teleport(handle, destination)).isCompletedWithValue(true);
  }

  @Test
  void teleportWithoutTeleportFlagsRemountsEachPassenger() {
    TextDisplay label = mock(TextDisplay.class);
    Location destination = new Location(overworld, 4D, 64D, 4D);
    when(golem.getPassengers()).thenReturn(List.of(label));
    when(golem.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)).thenReturn(true);

    assertThat(GolemTeleport.teleportRemountingPassengers(golem, destination)).isCompletedWithValue(true);

    InOrder order = inOrder(golem, label);
    order.verify(golem).removePassenger(label);
    order.verify(golem).teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    order.verify(label).teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    order.verify(golem).addPassenger(label);
  }

  @Test
  void teleportWithoutTeleportFlagsRemountsEvenWhenTheTeleportIsRefused() {
    Entity refused = mock(IronGolem.class);
    TextDisplay label = mock(TextDisplay.class);
    Location destination = new Location(overworld, 4D, 64D, 4D);
    when(refused.getPassengers()).thenReturn(List.of(label));

    assertThat(GolemTeleport.teleportRemountingPassengers(refused, destination)).isCompletedWithValue(false);

    verify(label, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    verify(refused).addPassenger(label);
  }

  private static World world(UUID id) {
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(id);
    return world;
  }
}
