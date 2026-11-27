package art.arcane.adapt.engine.notification;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.client.server.hud.RecordingHudPort;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptHudStatusTest {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
  private static final HudStatusSpec SPEC = HudStatusSpec.duration("rift", IconRef.item("minecraft:ender_eye"),
      "§dRift Gate", 10_000L, 4_250L, 0, 0);

  private final RecordingHudLink link = new RecordingHudLink();
  private final RecordingHudPort platformPort = new RecordingHudPort();
  private PlayerRef player;

  @BeforeEach
  void start() {
    AdaptPorts.install(HudPort.class, platformPort);
    ClientHud.install(link);
    AdaptHud.start();
    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(PLAYER_ID);
  }

  @AfterEach
  void stop() {
    AdaptHud.stop();
    ClientHud.uninstall();
    AdaptPorts.uninstall(HudPort.class);
  }

  @Test
  void typedStatusReportsFalseWithoutTheClient() {
    assertThat(AdaptHud.status(PLAYER_ID, "blocking-counter-guard", SPEC)).isFalse();
    AdaptHud.clearStatus(PLAYER_ID, "blocking-counter-guard");
    assertThat(platformPort.calls()).isEmpty();
    assertThat(link.frames()).isEmpty();
  }

  @Test
  void capablePlayerGetsAStatusFrameAndAClearAndNoText() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    assertThat(AdaptHud.status(PLAYER_ID, "rift-gate", SPEC)).isTrue();
    AdaptHud.clearStatus(PLAYER_ID, "rift-gate");

    assertThat(link.frames()).containsExactly(
        new ServerFrame.HudStatus("adapt:status:rift-gate", HudGlyph.DURATION, "rift", SPEC.icon(),
            MarkupPort.current().legacy("§dRift Gate"), 10_000L, 4_250L, 0, 0, false),
        new ServerFrame.HudClear("adapt:status:rift-gate"));
    assertThat(platformPort.calls()).isEmpty();
  }

  @Test
  void textOutputOfCapablePlayersBecomesFrames() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    AdaptHud.actionBar(player, "§aSaved");

    assertThat(link.frames()).hasSize(1);
    assertThat(link.frames().get(0)).isInstanceOf(ServerFrame.HudPublish.class);
    assertThat(platformPort.calls()).isEmpty();
  }

  @Test
  void withoutAPlatformPortTheCompositorSitsBehindTheClientPort() {
    AdaptHud.stop();
    AdaptPorts.uninstall(HudPort.class);
    AdaptHud.start();
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    AdaptHud.ambientStatus(player, "stealth-core", "§aHidden");

    assertThat(link.frames()).containsExactly(new ServerFrame.HudPublish("adapt:status:stealth-core",
        HudPriority.STATUS, 2500L, List.of(HudSlot.CENTER, HudSlot.LEFT),
        MarkupPort.current().legacy("§aHidden")));
  }
}
