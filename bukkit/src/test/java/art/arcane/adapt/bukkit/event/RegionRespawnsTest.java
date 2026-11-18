package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionRespawnsTest {
  private final WorldRef world = mock(WorldRef.class);
  private final Loc spawn = new Loc(world, 0D, 64D, 0D);
  private final UUID playerId = UUID.randomUUID();
  private final PlayerRef player = mock(PlayerRef.class);
  private final AdaptEventBus bus = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) -> {
    throw new AssertionError(error);
  });
  private final RegionRespawns respawns = new RegionRespawns(bus);
  private final List<PlayerRespawnEvent> seen = new ArrayList<>();
  private final List<Loc> relocations = new ArrayList<>();

  RegionRespawnsTest() {
    when(player.uuid()).thenReturn(playerId);
    bus.register(this, PlayerRespawnEvent.class, EventPriority.MONITOR, false, seen::add);
  }

  @Test
  void aPlayerPlacedBackAliveAfterDyingPostsOneRespawnAtItsLocation() {
    respawns.died(playerId);

    respawns.placed(player, true, spawn, relocations::add);
    respawns.placed(player, true, spawn, relocations::add);

    assertThat(seen).singleElement().satisfies(event -> {
      assertThat(event.player()).isSameAs(player);
      assertThat(event.respawnLocation()).isEqualTo(spawn);
      assertThat(event.isBedSpawn()).isFalse();
      assertThat(event.isAnchorSpawn()).isFalse();
    });
    assertThat(relocations).isEmpty();
  }

  @Test
  void placementsWithoutAPriorDeathAreNotRespawns() {
    respawns.placed(player, true, spawn, relocations::add);

    assertThat(seen).isEmpty();
  }

  @Test
  void aStillDeadPlacementWaitsForTheLivingOne() {
    respawns.died(playerId);

    respawns.placed(player, false, spawn, relocations::add);
    assertThat(seen).isEmpty();

    respawns.placed(player, true, spawn, relocations::add);
    assertThat(seen).hasSize(1);
  }

  @Test
  void aQuitForgetsThePendingRespawn() {
    respawns.died(playerId);
    respawns.forget(playerId);

    respawns.placed(player, true, spawn, relocations::add);

    assertThat(seen).isEmpty();
  }

  @Test
  void aHandlerChangingTheRespawnLocationMovesThePlayerThere() {
    Loc chosen = new Loc(world, 5D, 70D, 5D);
    bus.register(this, PlayerRespawnEvent.class, EventPriority.NORMAL, false,
        (PlayerRespawnEvent event) -> event.setRespawnLocation(chosen));
    respawns.died(playerId);

    respawns.placed(player, true, spawn, relocations::add);

    assertThat(relocations).containsExactly(chosen);
  }
}
