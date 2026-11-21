package art.arcane.adapt.api.notification;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.tick.Ticker;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifierXpEarnedTest {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

  private final RecordingHudLink link = new RecordingHudLink();
  private AdaptPlayer target;
  private PlayerSkillLine line;
  private Notifier notifier;

  @BeforeEach
  void setUp() {
    AdaptCore core = TestCores.install();
    TestCores.setField(core, "ticker", mock(Ticker.class));
    ClientHud.install(link);
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(PLAYER_ID);
    line = new PlayerSkillLine();
    line.setLine("mining");
    line.setXp(1_000D);
    line.setPooledXp(40D);
    PlayerData data = mock(PlayerData.class);
    when(data.getSkillLineNullable("mining")).thenReturn(line);
    target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    when(target.getData()).thenReturn(data);
    notifier = new Notifier(target);
  }

  @AfterEach
  void tearDown() {
    ClientHud.uninstall();
    TestCores.clear();
  }

  @Test
  void capablePlayerGetsTheEarnAtOnceWithTheProjectedTotal() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    notifier.xpEarned(line, 5D);

    double absolute = XP.getLevelForXp(1_040D);
    int level = (int) Math.floor(absolute);
    assertThat(link.frames()).containsExactly(new ServerFrame.XpEarned("mining", 5D, 1_040D, level, absolute - level));
  }

  @Test
  void rapidEarnsAreCoalescedAndDrainedByTheTick() throws InterruptedException {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    notifier.xpEarned(line, 5D);
    notifier.xpEarned(line, 2D);
    notifier.xpEarned(line, 1D);
    assertThat(link.frames()).hasSize(1);

    long deadline = System.currentTimeMillis() + 2_000L;
    while (link.frames().size() < 2 && System.currentTimeMillis() < deadline) {
      notifier.onTick();
      Thread.sleep(10L);
    }

    assertThat(link.frames()).hasSize(2);
    assertThat(((ServerFrame.XpEarned) link.frames().get(1)).amount()).isEqualTo(3D);
  }

  @Test
  void capablePlayerGetsNoXpTickerText() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    notifier.notifyXP("mining", 5D);
    notifier.onTick();

    verify(target, never()).getActionBarNotifier();
    assertThat(notifier.getInterval()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void playerWithoutTheClientGetsNoFramesAndKeepsTheTicker() {
    notifier.xpEarned(line, 5D);
    assertThat(link.frames()).isEmpty();
    assertThat(notifier.getInterval()).isEqualTo(Long.MAX_VALUE);

    notifier.notifyXP("mining", 5D);
    assertThat(notifier.getInterval()).isNotEqualTo(Long.MAX_VALUE);
  }
}
