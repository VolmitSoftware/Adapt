package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.engine.notification.HudPort;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.client.server.hud.RecordingHudPort;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.MockedHosts;
import art.arcane.adapt.util.common.misc.CustomModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CooldownGlyphTest {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a7");
  private static final String COOLDOWN_PURPOSE = "adapt:status:architect-foundation.cooldown";

  private final RecordingHudLink link = new RecordingHudLink();
  private final RecordingHudPort port = new RecordingHudPort();
  private Adaptation<?> foundation;
  private PlayerRef player;
  private MockedHosts hosts;

  @BeforeEach
  void start() {
    AdaptPorts.install(HudPort.class, port);
    ClientHud.install(link);
    AdaptHud.start();
    foundation = mock(Adaptation.class);
    Skill<?> architect = mock(Skill.class);
    lenient().when(architect.getId()).thenReturn(Id.adapt("architect"));
    lenient().when(foundation.getId()).thenReturn(Id.adapt("architect/foundation"));
    when(foundation.getDisplayName()).thenReturn("Foundation");
    when(foundation.getIcon()).thenReturn(Icon.item(Id.minecraft("bricks")));
    hosts = new MockedHosts();
    when(hosts.adaptation(foundation).getModel()).thenReturn(new CustomModel(Id.minecraft("bricks"), 0, CustomModel.EMPTY_KEY, null));
    when(foundation.getSkill()).thenAnswer(invocation -> architect);
    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(PLAYER_ID);
  }

  @AfterEach
  void stop() {
    hosts.close();
    AdaptHud.stop();
    ClientHud.uninstall();
    AdaptPorts.uninstall(HudPort.class);
  }

  @Test
  void markingAnOwnedGateWithItsLengthShowsTheCooldownGlyph() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);
    Cooldowns cooldowns = new PlayerCooldowns(new HashMap<>(), foundation);

    long before = System.currentTimeMillis();
    cooldowns.mark(PLAYER_ID, 5000L);

    assertThat(link.frames()).hasSize(1);
    ServerFrame.HudStatus status = (ServerFrame.HudStatus) link.frames().get(0);
    assertThat(status.purpose()).isEqualTo(COOLDOWN_PURPOSE);
    assertThat(status.glyph()).isEqualTo(HudGlyph.COOLDOWN);
    assertThat(status.skillId()).isEqualTo("adapt:architect");
    assertThat(status.icon().itemId()).isEqualTo("minecraft:bricks");
    assertThat(status.icon().spriteId()).isNotNull();
    assertThat(status.label()).contains("Foundation");
    assertThat(status.startedAtMillis()).isBetween(before, System.currentTimeMillis());
    assertThat(status.durationMillis()).isEqualTo(5000L);
    assertThat(cooldowns.isReady(PLAYER_ID, 5000L)).isFalse();
  }

  @Test
  void silentMarksShortGatesAndUnownedGatesSendNothing() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    new PlayerCooldowns(new HashMap<>(), foundation).mark(PLAYER_ID);
    new PlayerCooldowns(new HashMap<>(), foundation).mark(PLAYER_ID, AbilityGlyphs.MIN_COOLDOWN_MILLIS - 1L);
    new PlayerCooldowns(new HashMap<>(), null).mark(PLAYER_ID, 5000L);

    assertThat(link.frames()).isEmpty();
  }

  @Test
  void clearingAMarkedGateRemovesItsGlyph() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);
    Cooldowns cooldowns = new PlayerCooldowns(new HashMap<>(), foundation);
    cooldowns.mark(PLAYER_ID, 5000L);

    cooldowns.clear(PLAYER_ID);
    cooldowns.clear(PLAYER_ID);

    assertThat(link.frames()).hasSize(2);
    assertThat(link.frames().get(1)).isEqualTo(new ServerFrame.HudClear(COOLDOWN_PURPOSE));
  }

  @Test
  void playersWithoutTheClientGetNoFramesAndNoHudOutput() {
    Cooldowns cooldowns = new PlayerCooldowns(new HashMap<>(), foundation);

    cooldowns.mark(PLAYER_ID, 5000L);
    cooldowns.clear(PLAYER_ID);
    assertThat(AbilityGlyphs.charges(PLAYER_ID, foundation, 2, 3)).isFalse();
    assertThat(AbilityGlyphs.duration(PLAYER_ID, foundation, 0L, 4000L)).isFalse();
    assertThat(AbilityGlyphs.toggle(PLAYER_ID, foundation, true)).isFalse();
    AbilityGlyphs.clear(PLAYER_ID, foundation, HudGlyph.CHARGES);

    assertThat(link.frames()).isEmpty();
    assertThat(port.calls()).isEmpty();
  }

  @Test
  void aGlyphCallerFallingBackPublishesExactlyTheTextItPublishedBefore() {
    if (!AbilityGlyphs.duration(PLAYER_ID, foundation, 0L, 4250L)) {
      AdaptHud.ambientStatus(player, "adapt:rift/gate", "§7Teleporting in 3");
    }
    AdaptHud.ambientStatus(player, "adapt:rift/gate", "§7Teleporting in 3");

    assertThat(port.published()).hasSize(2);
    assertThat(port.published().get(0)).isEqualTo(port.published().get(1));
    assertThat(link.frames()).isEmpty();
  }

  @Test
  void chargesClampToTheirCapAndUncappedCountsPassThrough() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    AbilityGlyphs.charges(PLAYER_ID, foundation, 5, 3);
    AbilityGlyphs.charges(PLAYER_ID, foundation, 7, 0);
    AbilityGlyphs.toggle(PLAYER_ID, foundation, true);

    ServerFrame.HudStatus capped = (ServerFrame.HudStatus) link.frames().get(0);
    ServerFrame.HudStatus uncapped = (ServerFrame.HudStatus) link.frames().get(1);
    ServerFrame.HudStatus toggle = (ServerFrame.HudStatus) link.frames().get(2);
    assertThat(capped.purpose()).isEqualTo("adapt:status:architect-foundation.charges");
    assertThat(capped.stacks()).isEqualTo(3);
    assertThat(capped.maxStacks()).isEqualTo(3);
    assertThat(uncapped.stacks()).isEqualTo(7);
    assertThat(uncapped.maxStacks()).isZero();
    assertThat(toggle.glyph()).isEqualTo(HudGlyph.TOGGLE);
    assertThat(toggle.lit()).isTrue();
    assertThat(toggle.purpose()).isEqualTo("adapt:status:architect-foundation.toggle");
  }
}
