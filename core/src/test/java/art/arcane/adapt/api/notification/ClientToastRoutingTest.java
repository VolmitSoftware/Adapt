package art.arcane.adapt.api.notification;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.client.server.hud.ToastNotification;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientToastRoutingTest {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e5");

  private final RecordingHudLink link = new RecordingHudLink();
  private AdaptPlatform platform;
  private PlayerRef player;
  private AdaptPlayer target;

  @BeforeEach
  void setUp() {
    platform = TestCores.mockPlatform();
    TestCores.install(platform);
    ClientHud.install(link);
    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(PLAYER_ID);
    target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
  }

  @AfterEach
  void tearDown() {
    ClientHud.uninstall();
    TestCores.clear();
  }

  @Test
  void capablePlayerGetsAToastFrameInsteadOfTheAdvancementPopup() {
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);

    knowledgeNotification().play(target);

    assertThat(link.frames()).containsExactly(new ServerFrame.Toast(ToastKind.KNOWLEDGE, IconRef.item("minecraft:book"),
        MarkupPort.current().legacy("§7+3 Mining Knowledge"), "", ""));
    verify(platform.advancements(), never()).toast(any(), any(), anyString(), any());
  }

  @Test
  void onlyAClientThatReadsSkillToastsGetsTheSkill() {
    link.grant(PLAYER_ID, ClientProtocol.TOASTS | ClientProtocol.SKILL_TOASTS);
    knowledgeNotification().play(target);
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);
    knowledgeNotification().play(target);

    String title = MarkupPort.current().legacy("§7+3 Mining Knowledge");
    assertThat(link.frames()).containsExactly(
        new ServerFrame.Toast(ToastKind.KNOWLEDGE, IconRef.item("minecraft:book"), title, "", "pickaxe"),
        new ServerFrame.Toast(ToastKind.KNOWLEDGE, IconRef.item("minecraft:book"), title, "", ""));
  }

  @Test
  void aSignatureReachesAnOlderClientAsAnUnlock() {
    ServerFrame.Toast signature = ClientToasts.toast(ToastKind.SIGNATURE, IconRef.item("minecraft:bow"), "Called Shot", "",
        "mutated-ranged-stealth");
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);

    ClientToasts.send(PLAYER_ID, signature);

    assertThat(link.frames()).containsExactly(new ServerFrame.Toast(ToastKind.UNLOCK, IconRef.item("minecraft:bow"),
        "Called Shot", "", ""));
  }

  @Test
  void hudOnlyClientStillGetsTheAdvancementPopup() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);
    ItemRef icon = mock(ItemRef.class);
    ItemOps items = mock(ItemOps.class);
    when(platform.host().items()).thenReturn(items);
    when(items.create(Id.minecraft("book"))).thenReturn(icon);

    knowledgeNotification().play(target);

    assertThat(link.frames()).isEmpty();
    verify(platform.advancements()).toast(player, icon, "§7+3 Mining Knowledge",
        AdaptAdvancementFrame.TASK.platform());
  }

  @Test
  void queuedToastFallsBackToVanillaForPlayersWithoutToasts() {
    ServerFrame.Toast toast = ClientToasts.toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:iron_pickaxe"),
        "§bMining 12", "", "");
    List<AdaptPlayer> fallbackPlays = new ArrayList<>();
    Notification fallback = new Notification() {
      @Override
      public long getTotalDuration() {
        return 0L;
      }

      @Override
      public String getGroup() {
        return "lvlmining";
      }

      @Override
      public void play(AdaptPlayer p) {
        fallbackPlays.add(p);
      }
    };
    ToastNotification notification = new ToastNotification(toast, "lvlmining", List.of(fallback));

    notification.play(target);
    assertThat(link.frames()).isEmpty();
    assertThat(fallbackPlays).containsExactly(target);

    link.grant(PLAYER_ID, ClientProtocol.TOASTS);
    notification.play(target);
    assertThat(link.frames()).containsExactly(toast);
  }

  private static AdvancementNotification knowledgeNotification() {
    return AdvancementNotification.builder()
        .kind(ToastKind.KNOWLEDGE)
        .skill("pickaxe")
        .icon(Id.minecraft("book"))
        .title("§7+3 Mining Knowledge")
        .build();
  }
}
