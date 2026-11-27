package art.arcane.adapt.engine.item;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.engine.notification.Notifier;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.platform.entity.PlayerRef;
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
  private PlayerRef player;
  private AdaptPlayer adaptPlayer;
  private UUID playerId;

  @BeforeEach
  void setUpRuntime() {
    server = mock(AdaptServer.class);
    player = mock(PlayerRef.class);
    adaptPlayer = mock(AdaptPlayer.class);
    playerId = UUID.randomUUID();
    when(core.getAdaptServer()).thenReturn(server);
    when(player.uuid()).thenReturn(playerId);
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
    when(adaptPlayer.getSkillLine("adapt:pickaxe")).thenReturn(skillLine);
    when(adaptPlayer.getNot()).thenReturn(notifier);
    PlayerData data = new PlayerData();
    when(adaptPlayer.getData()).thenReturn(data);

    boolean applied = new ExperienceOrb.Data("pickaxe", 25D).apply(player);

    assertThat(applied).isTrue();
    verify(skillLine).award(notifier,
        XpAward.consumable(25D, "consumable:experience-orb"), 25D, data.getXpEarnings());
  }

  @Test
  void experienceOrbRejectsRuntimeFromReplacementSession() {
    PlayerRef replacement = mock(PlayerRef.class);
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
    when(adaptPlayer.getSkillLine("adapt:pickaxe")).thenReturn(skillLine);

    boolean applied = new KnowledgeOrb.Data("pickaxe", 4).apply(player);

    assertThat(applied).isTrue();
    verify(skillLine).grantKnowledge(4);
  }
}
