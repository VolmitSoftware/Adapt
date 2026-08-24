package art.arcane.adapt.api.skill;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillRegistryRuntimeReadinessTest extends AdaptTestBase {
  private AdaptServer server;
  private SkillRegistry registry;
  private Player player;
  private UUID playerId;

  @BeforeEach
  void setUpRuntime() {
    server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class, CALLS_REAL_METHODS);
    player = mock(Player.class);
    playerId = UUID.randomUUID();
    when(plugin.getAdaptServer()).thenReturn(server);
    when(player.getUniqueId()).thenReturn(playerId);
  }

  @Test
  void experienceChangeDoesNothingWithoutReadyRuntime() {
    PlayerExpChangeEvent event = mock(PlayerExpChangeEvent.class);
    when(event.getPlayer()).thenReturn(player);

    registry.on(event);

    verify(event, never()).getAmount();
    verify(server, never()).getPlayer(player);
  }

  @Test
  void experienceChangeUsesExistingReadyRuntime() {
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerExpChangeEvent event = mock(PlayerExpChangeEvent.class);
    when(event.getPlayer()).thenReturn(player);
    when(event.getAmount()).thenReturn(3);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);

    registry.on(event);

    verify(adaptPlayer).boostXPToRecents(0.03D, 10000L);
    verify(server, never()).getPlayer(player);
  }

  @Test
  void experienceChangeRejectsRuntimeFromReplacementSession() {
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    Player replacement = mock(Player.class);
    PlayerExpChangeEvent event = mock(PlayerExpChangeEvent.class);
    when(event.getPlayer()).thenReturn(player);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(replacement);

    registry.on(event);

    verify(event, never()).getAmount();
    verify(adaptPlayer, never()).boostXPToRecents(0.03D, 10000L);
  }

  @Test
  void interactionDoesNothingWithoutReadyRuntime() {
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    when(event.getPlayer()).thenReturn(player);

    registry.on(event);

    verify(event, never()).getAction();
    verify(event, never()).getClickedBlock();
    verify(server, never()).getPlayer(player);
  }
}
