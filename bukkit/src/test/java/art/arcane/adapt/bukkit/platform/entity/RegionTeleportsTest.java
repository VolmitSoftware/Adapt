package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionTeleportsTest {
  private final WorldRef world = mock(WorldRef.class);
  private final Loc origin = new Loc(world, 0D, 100D, 0D);
  private final Loc destination = new Loc(world, 9D, 100D, 0D);
  private final PlayerRef player = mock(PlayerRef.class);
  private final AdaptScheduler scheduler = mock(AdaptScheduler.class);
  private final AdaptEventBus bus = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) -> {
    throw new AssertionError(error);
  });
  private final List<Loc> moves = new ArrayList<>();

  @Test
  void anOwnedPlayerTeleportIsPostedBeforeTheMoveAndKeepsItsCause() {
    when(scheduler.owns(player)).thenReturn(true);
    List<PlayerTeleportEvent> seen = new ArrayList<>();
    bus.register(this, PlayerTeleportEvent.class, EventPriority.MONITOR, false, seen::add);

    CompletableFuture<Boolean> result = RegionTeleports.announce(scheduler, bus, player, () -> origin, destination, TeleportCause.PLUGIN, this::move);

    assertThat(result).isCompletedWithValue(true);
    assertThat(seen).singleElement().satisfies(event -> {
      assertThat(event.from()).isEqualTo(origin);
      assertThat(event.to()).isEqualTo(destination);
      assertThat(event.cause()).isEqualTo(TeleportCause.PLUGIN);
    });
    assertThat(moves).containsExactly(destination);
  }

  @Test
  void aCancelledTeleportNeverMovesThePlayer() {
    when(scheduler.owns(player)).thenReturn(true);
    bus.register(this, PlayerTeleportEvent.class, EventPriority.HIGHEST, true, (PlayerTeleportEvent event) -> event.setCancelled(true));

    CompletableFuture<Boolean> result = RegionTeleports.announce(scheduler, bus, player, () -> origin, destination, TeleportCause.PLUGIN, this::move);

    assertThat(result).isCompletedWithValue(false);
    assertThat(moves).isEmpty();
  }

  @Test
  void aRedirectedTeleportMovesToTheNewDestination() {
    Loc redirected = new Loc(world, 2D, 100D, 2D);
    when(scheduler.owns(player)).thenReturn(true);
    bus.register(this, PlayerTeleportEvent.class, EventPriority.HIGHEST, true, (PlayerTeleportEvent event) -> event.setTo(redirected));

    RegionTeleports.announce(scheduler, bus, player, () -> origin, destination, TeleportCause.PLUGIN, this::move);

    assertThat(moves).containsExactly(redirected);
  }

  @Test
  void anUnownedPlayerIsAnnouncedOnItsOwnRegion() {
    AtomicReference<Runnable> scheduled = new AtomicReference<>();
    when(scheduler.owns(player)).thenReturn(false);
    when(scheduler.runEntity(same(player), any(Runnable.class), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
      scheduled.set(invocation.getArgument(1));
      return true;
    });

    CompletableFuture<Boolean> result = RegionTeleports.announce(scheduler, bus, player, () -> origin, destination, TeleportCause.PLUGIN, this::move);

    assertThat(result).isNotDone();
    assertThat(moves).isEmpty();
    scheduled.get().run();
    assertThat(result).isCompletedWithValue(true);
    assertThat(moves).containsExactly(destination);
  }

  private CompletableFuture<Boolean> move(Loc arrival) {
    moves.add(arrival);
    return CompletableFuture.completedFuture(true);
  }
}
