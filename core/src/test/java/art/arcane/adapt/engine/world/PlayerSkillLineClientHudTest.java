package art.arcane.adapt.engine.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.engine.notification.ActionBarNotification;
import art.arcane.adapt.engine.notification.Notification;
import art.arcane.adapt.engine.notification.Notifier;
import art.arcane.adapt.engine.notification.SoundNotification;
import art.arcane.adapt.engine.xp.XpEarnings;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.client.server.hud.ToastNotification;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.MockedHosts;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlayerSkillLineClientHudTest extends AdaptTestBase {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b8");

  private final RecordingHudLink link = new RecordingHudLink();
  private AdaptPlayer player;
  private Notifier notifications;
  private Notifier actionBarNotifications;
  private Skill<?> skill;
  private MockedHosts hosts;

  @BeforeEach
  void setUp() {
    ClientHud.install(link);
    PlayerRef ref = mock(PlayerRef.class);
    when(ref.uuid()).thenReturn(PLAYER_ID);
    notifications = mock(Notifier.class);
    actionBarNotifications = mock(Notifier.class);
    player = mock(AdaptPlayer.class);
    when(player.getPlayer()).thenReturn(ref);
    when(player.getNot()).thenReturn(notifications);
    when(player.getActionBarNotifier()).thenReturn(actionBarNotifications);
    skill = mock(Skill.class);
    lenient().when(skill.getId()).thenReturn(Id.adapt("mining"));
    when(skill.getIcon()).thenReturn(Icon.item(Id.minecraft("iron_pickaxe")));
    hosts = new MockedHosts();
    when(hosts.skill(skill).getModel()).thenReturn(new CustomModel(Id.minecraft("iron_pickaxe"), 0, CustomModel.EMPTY_KEY, null));
    when(skill.getShortName()).thenReturn("Mining");
    when(skill.getDisplayName(11)).thenReturn("Mining 11");
    when(skill.getLocalizedName()).thenReturn("Mining");
    when(skill.getColor()).thenReturn(Rgb.of(0x55FFFF));
  }

  @AfterEach
  void tearDown() {
    hosts.close();
    ClientHud.uninstall();
  }

  @Test
  void earnReachesTheNotifierWhileTheXpStaysPooled() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("adapt:mining");
    Notifier notifier = mock(Notifier.class);

    line.award(notifier, XpAward.gameplay(50D, "test:work").silent(), 50D, new XpEarnings());

    verify(notifier).xpEarned(eq(line), anyDouble());
    verify(notifier, never()).notifyXP(eq("mining"), anyDouble());
    assertThat(line.getXp()).isZero();
    assertThat(line.getPooledXp()).isPositive();
  }

  @Test
  void levelUpIsAToastForAClientWithToasts() throws Exception {
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);

    notifyLevel(11);

    ArgumentCaptor<Notification[]> queued = ArgumentCaptor.forClass(Notification[].class);
    verify(actionBarNotifications).queue(queued.capture());
    assertThat(queued.getValue()).hasSize(3);
    assertThat(queued.getValue()).filteredOn(SoundNotification.class::isInstance).hasSize(2);
    assertThat(queued.getValue()[2]).isInstanceOfSatisfying(ToastNotification.class,
        (ToastNotification toast) -> {
          assertThat(toast.toast().kind()).isEqualTo(ToastKind.LEVEL_UP);
          assertThat(C.stripColor(toast.toast().title())).isEqualTo("Mining 11");
          assertThat(toast.fallback()).isNotEmpty();
        });
    verifyNoInteractions(notifications);
  }

  @Test
  void milestoneToastNamesTheSkillInsteadOfItsMenuSymbol() throws Exception {
    link.grant(PLAYER_ID, ClientProtocol.TOASTS);
    when(skill.getShortName()).thenReturn(C.RESET + "" + C.BOLD + C.AQUA + "⇉");
    when(skill.getDisplayName(10)).thenReturn("Mining 10");

    notifyLevel(10);

    ArgumentCaptor<Notification[]> queued = ArgumentCaptor.forClass(Notification[].class);
    verify(notifications).queue(queued.capture());
    assertThat(queued.getValue()).filteredOn(ToastNotification.class::isInstance).singleElement()
        .isInstanceOfSatisfying(ToastNotification.class, (ToastNotification toast) -> {
          String body = C.stripColor(toast.toast().body());
          assertThat(body).contains("3").contains("Mining").doesNotContain("⇉");
          assertThat(body.codePoints().allMatch((int codePoint) -> codePoint < 0x7F)).isTrue();
        });
  }

  @Test
  void levelUpKeepsTheActionBarWithoutTheClient() throws Exception {
    notifyLevel(11);

    ArgumentCaptor<Notification[]> queued = ArgumentCaptor.forClass(Notification[].class);
    verify(actionBarNotifications).queue(queued.capture());
    assertThat(queued.getValue()[2]).isInstanceOfSatisfying(ActionBarNotification.class,
        (ActionBarNotification bar) -> assertThat(bar.getTitle()).isEqualTo("Mining 11"));
  }

  private void notifyLevel(int level) throws Exception {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("adapt:mining");
    Method notify = PlayerSkillLine.class.getDeclaredMethod("notifyLevel", AdaptPlayer.class, Skill.class, int.class,
        long.class);
    notify.setAccessible(true);
    notify.invoke(line, player, skill, level, 3L);
  }
}
