package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosClaimsInteractionTest extends AdaptTestBase {
  private final UUID playerId = UUID.randomUUID();
  private final World world = mock(World.class);
  private final Player player = mock(Player.class);
  private final ClaimsService claims = mock(ClaimsService.class);

  @BeforeEach
  void preparePlayer() {
    when(plugin.getClaimsService()).thenReturn(claims);
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(location(0));
    when(claims.canTeleport(eq(player), any(Location.class))).thenReturn(true);
  }

  @AfterEach
  void forgetFreeze() {
    TemporalFreezeRegistry.forget(playerId);
  }

  @Test
  void recallPreflightRejectsAnyClaimedIntermediateDestinationAndClaimedStart() {
    ChronosInstantRecall recall = mock(ChronosInstantRecall.class, CALLS_REAL_METHODS);
    List<Snapshot> path = List.of(snapshot(0), snapshot(20), snapshot(40));
    try (MockedStatic<WorldIdentity> worlds = mockStatic(WorldIdentity.class)) {
      worlds.when(() -> WorldIdentity.resolve("world")).thenReturn(Optional.of(world));
      assertThat(recall.recallPathAllowed(player, path)).isTrue();
      when(claims.canTeleport(player, location(20))).thenReturn(false);
      assertThat(recall.recallPathAllowed(player, path)).isFalse();
      when(claims.canTeleport(player, location(20))).thenReturn(true);
      when(claims.canTeleport(player, location(0))).thenReturn(false);
      assertThat(recall.recallPathAllowed(player, path)).isFalse();
    }
  }

  @Test
  void claimCreatedDuringAnimationAbortsItsOwnedRewindAndRestoresVisualStateOnce() throws Exception {
    ChronosInstantRecall recall = mock(ChronosInstantRecall.class, CALLS_REAL_METHODS);
    UUID operation = UUID.randomUUID();
    Map<UUID, UUID> rewinding = new ConcurrentHashMap<>();
    rewinding.put(playerId, operation);
    Runnable cleanup = mock(Runnable.class);
    Map<UUID, ChronosInstantRecall.RewindVisualState> cleanups = new ConcurrentHashMap<>();
    cleanups.put(playerId, new ChronosInstantRecall.RewindVisualState(player, cleanup));
    setField(recall, "rewinding", rewinding);
    setField(recall, "rewindCleanups", cleanups);

    assertThat(recall.continueRecall(player, operation, location(20))).isTrue();
    when(claims.canTeleport(player, location(20))).thenReturn(false);
    assertThat(recall.continueRecall(player, operation, location(20))).isFalse();
    assertThat(recall.continueRecall(player, operation, location(20))).isFalse();
    assertThat(rewinding).isEmpty();
    assertThat(cleanups).isEmpty();
    verify(cleanup, times(1)).run();
    verify(player, never()).setSpectatorTarget(any());
    verify(player, never()).setHealth(anyDouble());
  }

  @Test
  void deniedOldRewindCannotCleanUpANewerOperation() throws Exception {
    ChronosInstantRecall recall = mock(ChronosInstantRecall.class, CALLS_REAL_METHODS);
    UUID active = UUID.randomUUID();
    Map<UUID, UUID> rewinding = new ConcurrentHashMap<>();
    rewinding.put(playerId, active);
    setField(recall, "rewinding", rewinding);
    when(claims.canTeleport(player, location(20))).thenReturn(false);
    assertThat(recall.continueRecall(player, UUID.randomUUID(), location(20))).isFalse();
    assertThat(rewinding.get(playerId)).isEqualTo(active);
  }

  @Test
  void recallCleanupRestoresTheGameModeWithoutFlightInANewClaim() {
    when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
    when(claims.canTeleport(player, location(0))).thenReturn(false);
    assertThat(ChronosInstantRecall.restoreOwnedPlayerState(player, GameMode.CREATIVE, true, true)).isTrue();
    verify(player).setGameMode(GameMode.CREATIVE);
    verify(player).setAllowFlight(true);
    verify(player).setFlying(false);
    verify(player, never()).setFlying(true);
  }

  @Test
  void frozenPlayerInsideClaimUsesGravityAndVelocityWithoutFlight() {
    ChronosTimeBomb bomb = timeBomb();
    when(claims.canTeleport(player, location(0))).thenReturn(false);
    bomb.maintainFrozenPlayer(player);
    verify(player).setFlying(false);
    verify(player, never()).setAllowFlight(true);
    verify(player, never()).setFlying(true);
    verify(player).setGravity(false);
    verify(player).setVelocity(new Vector());
    verify(player).setFallDistance(0);
  }

  @Test
  void permittedFrozenPlayerKeepsTheExistingAirFreezeBehavior() {
    timeBomb().maintainFrozenPlayer(player);
    verify(player).setAllowFlight(true);
    verify(player).setFlying(true);
    verify(player).setGravity(false);
    verify(player).setVelocity(new Vector());
  }

  @Test
  void sharedFreezeReleaseDoesNotRestoreFlightInANewClaim() {
    when(player.getAllowFlight()).thenReturn(true);
    when(player.isFlying()).thenReturn(true);
    when(player.hasGravity()).thenReturn(true);
    TemporalFreezeRegistry.Lease lease = TemporalFreezeRegistry.acquire(player);
    when(claims.canTeleport(player, location(0))).thenReturn(false);
    TemporalFreezeRegistry.release(player, lease, null);
    verify(player).setAllowFlight(true);
    verify(player).setFlying(false);
    verify(player, never()).setFlying(true);
    verify(player).setGravity(true);
  }

  @Test
  void permittedFreezeReleaseRestoresExistingFlight() {
    when(player.getAllowFlight()).thenReturn(true);
    when(player.isFlying()).thenReturn(true);
    TemporalFreezeRegistry.Lease lease = TemporalFreezeRegistry.acquire(player);
    TemporalFreezeRegistry.release(player, lease, null);
    verify(player).setAllowFlight(true);
    verify(player).setFlying(true);
  }

  private ChronosTimeBomb timeBomb() {
    ChronosTimeBomb bomb = mock(ChronosTimeBomb.class, CALLS_REAL_METHODS);
    ChronosTimeBomb.Config config = new ChronosTimeBomb.Config();
    config.freezePlayersInAir = true;
    doReturn(config).when(bomb).getConfig();
    return bomb;
  }

  private Snapshot snapshot(int x) {
    return new Snapshot(0, "world", x, 100, 0, 0, 0, 20, 20, 20, 0, 0);
  }

  private Location location(int x) {
    return new Location(world, x, 100, 0);
  }

  private void setField(ChronosInstantRecall recall, String name, Object value) throws Exception {
    Field field = ChronosInstantRecall.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(recall, value);
  }
}
