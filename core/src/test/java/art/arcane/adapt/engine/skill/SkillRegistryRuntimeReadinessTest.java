package art.arcane.adapt.engine.skill;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillRegistryRuntimeReadinessTest {
  private AdaptServer server;
  private SkillRegistry registry;
  private PlayerRef player;
  private UUID playerId;

  @BeforeEach
  void setUpRuntime() {
    server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class, CALLS_REAL_METHODS);
    player = mock(PlayerRef.class);
    playerId = UUID.randomUUID();
    AdaptCore core = TestCores.install();
    TestCores.setField(core, "adaptServer", server);
    when(player.uuid()).thenReturn(playerId);
    when(player.isOnline()).thenReturn(true);
  }

  @AfterEach
  void clearRuntime() {
    TestCores.clear();
  }

  @Test
  void experienceChangeDoesNothingWithoutReadyRuntime() {
    PlayerExpChangeEvent event = mock(PlayerExpChangeEvent.class);
    when(event.player()).thenReturn(player);

    registry.on(event);

    verify(event, never()).amount();
    verify(server, never()).getPlayer(player);
  }

  @Test
  void experienceChangeUsesExistingReadyRuntime() {
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerExpChangeEvent event = mock(PlayerExpChangeEvent.class);
    when(event.player()).thenReturn(player);
    when(event.amount()).thenReturn(3);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);

    registry.on(event);

    verify(adaptPlayer).refreshRecentXpBoost("vanilla-xp", 0.03D, 10000L);
    verify(server, never()).getPlayer(player);
  }

  @Test
  void experienceChangeRejectsRuntimeFromReplacementSession() {
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerRef replacement = mock(PlayerRef.class);
    PlayerExpChangeEvent event = mock(PlayerExpChangeEvent.class);
    when(event.player()).thenReturn(player);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(replacement);

    registry.on(event);

    verify(event, never()).amount();
    verify(adaptPlayer, never()).refreshRecentXpBoost("vanilla-xp", 0.03D, 10000L);
  }

  @Test
  void interactionDoesNothingWithoutReadyRuntime() {
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    when(event.player()).thenReturn(player);

    registry.on(event);

    verify(event, never()).action();
    verify(event, never()).clickedBlock();
    verify(server, never()).getPlayer(player);
  }
}
