package art.arcane.adapt.api.advancement;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvancementClientToastTest {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f6");
  private static final ServerFrame.Toast TOAST = new ServerFrame.Toast(ToastKind.UNLOCK,
      IconRef.item("minecraft:diamond_pickaxe"), "Veinminer", "Break ore veins at once");

  private final RecordingHudLink link = new RecordingHudLink();
  private final AdvancementHost host = mock(AdvancementHost.class);
  private PlayerRef player;

  @BeforeEach
  void setUp() {
    ClientHud.install(link);
    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(PLAYER_ID);
  }

  @AfterEach
  void tearDown() {
    ClientHud.uninstall();
  }

  @Test
  void capablePlayerGetsTheFrameAndTheGrantWithoutPopup() {
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);

    AdvancementManager.renderGrant(host, player, "adaptation_pickaxe-veinminer", true, TOAST);

    assertThat(link.frames()).containsExactly(TOAST);
    verify(host).grant(player, AdaptAdvancement.advancementId("adaptation_pickaxe-veinminer"), false);
  }

  @Test
  void playerWithoutTheClientKeepsTheVanillaPopup() {
    AdvancementManager.renderGrant(host, player, "adaptation_pickaxe-veinminer", true, TOAST);

    assertThat(link.frames()).isEmpty();
    verify(host).grant(player, AdaptAdvancement.advancementId("adaptation_pickaxe-veinminer"), true);
  }

  @Test
  void silentGrantSendsNoToast() {
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);

    AdvancementManager.renderGrant(host, player, "skill_pickaxe", false, TOAST);

    assertThat(link.frames()).isEmpty();
    verify(host).grant(player, AdaptAdvancement.advancementId("skill_pickaxe"), false);
  }

  @Test
  void closedChannelFallsBackToThePopup() {
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);
    link.close();

    AdvancementManager.renderGrant(host, player, "skill_pickaxe", true, TOAST);

    verify(host).grant(player, AdaptAdvancement.advancementId("skill_pickaxe"), true);
  }

  @Test
  void toastKindFollowsTheKeyFamily() {
    assertThat(AdaptAdvancement.toastKind("skill_pickaxe", AdaptAdvancementFrame.TASK)).isEqualTo(ToastKind.DISCOVERY);
    assertThat(AdaptAdvancement.toastKind("adaptation_pickaxe-veinminer", AdaptAdvancementFrame.GOAL))
        .isEqualTo(ToastKind.UNLOCK);
    assertThat(AdaptAdvancement.toastKind("challenge_pickaxe_veinminer_20", AdaptAdvancementFrame.TASK))
        .isEqualTo(ToastKind.GENERIC);
    assertThat(AdaptAdvancement.toastKind("challenge_pickaxe_1k", AdaptAdvancementFrame.CHALLENGE))
        .isEqualTo(ToastKind.CHALLENGE);
  }

  @Test
  void aToastTitleDropsTheCentringFrameOfTheAdvancementTab() {
    assertThat(AdaptAdvancement.toastTitle("\u00a7f[     \u00a7aAir Dash\u00a7f     ]")).isEqualTo("\u00a7f\u00a7aAir Dash");
    assertThat(AdaptAdvancement.toastTitle("[ Not padded ]")).isEqualTo("[ Not padded ]");
    assertThat(AdaptAdvancement.toastTitle("Veinminer")).isEqualTo("Veinminer");
  }
}
