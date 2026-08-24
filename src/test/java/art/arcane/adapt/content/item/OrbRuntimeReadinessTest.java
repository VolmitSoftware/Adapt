package art.arcane.adapt.content.item;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.notification.Notifier;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrbRuntimeReadinessTest extends AdaptTestBase {
  private AdaptServer server;
  private Player player;
  private AdaptPlayer adaptPlayer;
  private UUID playerId;

  @BeforeEach
  void setUpRuntime() {
    server = mock(AdaptServer.class);
    player = mock(Player.class);
    adaptPlayer = mock(AdaptPlayer.class);
    playerId = UUID.randomUUID();
    when(plugin.getAdaptServer()).thenReturn(server);
    when(player.getUniqueId()).thenReturn(playerId);
  }

  @Test
  void experienceOrbRemainsUnappliedWithoutReadyRuntime() {
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(false);

    boolean applied = new ExperienceOrb.Data("pickaxe", 25D).apply(player);

    assertThat(applied).isFalse();
    verify(adaptPlayer, never()).getSkillLine("pickaxe");
  }

  @Test
  void experienceOrbAppliesThroughReadyRuntime() {
    PlayerSkillLine skillLine = mock(PlayerSkillLine.class);
    Notifier notifier = mock(Notifier.class);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getSkillLine("pickaxe")).thenReturn(skillLine);
    when(adaptPlayer.getNot()).thenReturn(notifier);

    boolean applied = new ExperienceOrb.Data("pickaxe", 25D).apply(player);

    assertThat(applied).isTrue();
    verify(skillLine).giveXPFresh(notifier, 25D);
  }

  @Test
  void experienceOrbRejectsRuntimeFromReplacementSession() {
    Player replacement = mock(Player.class);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(replacement);

    boolean applied = new ExperienceOrb.Data("pickaxe", 25D).apply(player);

    assertThat(applied).isFalse();
    verify(adaptPlayer, never()).getSkillLine("pickaxe");
  }

  @Test
  void knowledgeOrbRemainsUnappliedWithoutReadyRuntime() {
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(false);

    boolean applied = new KnowledgeOrb.Data("pickaxe", 4).apply(player);

    assertThat(applied).isFalse();
    verify(adaptPlayer, never()).getSkillLine("pickaxe");
  }

  @Test
  void knowledgeOrbAppliesThroughReadyRuntime() {
    PlayerSkillLine skillLine = mock(PlayerSkillLine.class);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getSkillLine("pickaxe")).thenReturn(skillLine);

    boolean applied = new KnowledgeOrb.Data("pickaxe", 4).apply(player);

    assertThat(applied).isTrue();
    verify(skillLine).giveKnowledge(4);
  }
}
