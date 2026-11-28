package art.arcane.adapt.pack.hud;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.ClientHudPort;
import art.arcane.adapt.client.server.hud.RecordingHudLink;
import art.arcane.adapt.client.server.hud.RecordingHudPort;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.engine.notification.HudPriority;
import art.arcane.adapt.engine.notification.HudSegment;
import art.arcane.adapt.engine.notification.HudSlot;
import art.arcane.adapt.engine.notification.HudStatusSpec;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackHudPortTest {
  private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final IconRef ICON = IconRef.item("minecraft:feather");
  private static final HudSegment NOTICE = new HudSegment("adapt:notice", HudPriority.NOTICE, 2_500L,
      List.of(HudSlot.CENTER), "§aSaved");

  private final AtomicLong clock = new AtomicLong(10_000L);
  private final AtomicReference<HudPlacement> placement = new AtomicReference<>(HudPlacement.HOTBAR);
  private final RecordingHudPort platform = new RecordingHudPort();
  private final List<String> sent = new ArrayList<>();
  private final List<Scheduled> scheduled = new ArrayList<>();
  private final Set<UUID> book = new HashSet<>();
  private final RecordingHudLink link = new RecordingHudLink();
  private PackHudPort port;
  private PlayerRef viewer;
  private PlayerRef other;

  private record Scheduled(int ticks, Runnable task) {
  }

  @BeforeEach
  void setUp() {
    port = new PackHudPort(platform, (PlayerRef player) -> book.contains(player.uuid()), placement::get,
        (String spriteId) -> null, (String legacy) -> "<green>" + legacy.substring(2), (int level) -> "Lv " + level, clock::get,
        (PlayerRef player, Runnable task, int delayTicks, Runnable retired) -> scheduled.add(new Scheduled(delayTicks, task)),
        (PlayerRef player, String markup) -> sent.add(player.uuid() == VIEWER_ID ? markup : "other " + markup));
    viewer = mock(PlayerRef.class);
    when(viewer.uuid()).thenReturn(VIEWER_ID);
    other = mock(PlayerRef.class);
    when(other.uuid()).thenReturn(OTHER_ID);
    book.add(VIEWER_ID);
    ClientHud.install(link);
  }

  @AfterEach
  void tearDown() {
    ClientHud.uninstall();
  }

  @Test
  void playersWithoutThePackGoToTheDelegateUnchanged() {
    port.publish(other, NOTICE);
    port.clear(other, "adapt:notice");

    assertThat(platform.calls()).containsExactly("publish adapt:notice", "clear adapt:notice");
    assertThat(platform.published()).containsExactly(NOTICE);
    assertThat(port.status(other, "adapt:status:x", HudStatusSpec.toggle("s", ICON, "", true))).isFalse();
    assertThat(sent).isEmpty();
  }

  @Test
  void packViewersGetTheStripAndTheDelegateNothing() {
    port.publish(viewer, NOTICE);

    assertThat(platform.calls()).isEmpty();
    assertThat(sent).containsExactly("<reset><green>Saved");
  }

  @Test
  void modClientsKeepNativeFramesAndNeverReachTheStrip() {
    ClientHudPort chain = new ClientHudPort(port);
    link.grant(VIEWER_ID, ClientProtocol.HUD);

    chain.publish(viewer, NOTICE);

    assertThat(link.frames()).containsExactly(new ServerFrame.HudPublish("adapt:notice", HudPriority.NOTICE, 2_500L,
        List.of(HudSlot.CENTER), "§aSaved"));
    assertThat(sent).isEmpty();
    assertThat(platform.calls()).isEmpty();
  }

  @Test
  void changesInsideTheBudgetAreHeldAndSentOnce() {
    port.status(viewer, "adapt:status:a", HudStatusSpec.toggle("s", ICON, "", true));
    clock.addAndGet(50L);
    port.status(viewer, "adapt:status:b", HudStatusSpec.toggle("s", ICON, "", false));
    clock.addAndGet(50L);
    port.status(viewer, "adapt:status:c", HudStatusSpec.toggle("s", ICON, "", true));

    assertThat(sent).hasSize(1);
    Scheduled held = scheduled.get(1);
    assertThat(held.ticks()).isEqualTo(4);
    clock.addAndGet(150L);
    held.task().run();

    assertThat(sent).hasSize(2);
    assertThat(sent.get(1).chars().filter((int c) -> c == HudGlyphs.WELL).count()).isEqualTo(3);
  }

  @Test
  void unchangedStripIsOnlyResentAsKeepAlive() {
    port.publish(viewer, new HudSegment("adapt:status:x", HudPriority.STATUS, 60_000L, List.of(HudSlot.LEFT), "§aOn"));
    port.publish(viewer, new HudSegment("adapt:status:x", HudPriority.STATUS, 60_000L, List.of(HudSlot.LEFT), "§aOn"));
    clock.addAndGet(1_000L);
    port.publish(viewer, new HudSegment("adapt:status:x", HudPriority.STATUS, 60_000L, List.of(HudSlot.LEFT), "§aOn"));
    assertThat(sent).hasSize(1);

    clock.addAndGet(PackHudPort.KEEP_ALIVE_MILLIS);
    scheduled.get(scheduled.size() - 1).task().run();

    assertThat(sent).hasSize(2).allMatch("<reset><green>On"::equals);
  }

  @Test
  void cooldownWipeStepsAreSentAtMostFourTimesASecondAndClearWhenDone() {
    long start = clock.get();
    port.status(viewer, "adapt:status:dash.cooldown", HudStatusSpec.cooldown("s", ICON, "", start, 1_000L));
    for (int index = 0; index < 40 && clock.get() < start + 1_200L; index++) {
      clock.addAndGet(50L);
      scheduled.get(scheduled.size() - 1).task().run();
    }

    assertThat(sent.size()).isBetween(4, 6);
    assertThat(sent.get(sent.size() - 1)).isEqualTo(" ");
  }

  @Test
  void xpAwardsForTheShownSkillAddUpAndExpire() {
    port.xp(viewer, new HudXp("herbalism", "Herbalism", "adapt:gui/skill/herbalism", 0x4DB344, 3, 0.1, 10.0), 2_000L);
    clock.addAndGet(300L);
    port.xp(viewer, new HudXp("herbalism", "Herbalism", "adapt:gui/skill/herbalism", 0x4DB344, 3, 0.2, 5.0), 2_000L);

    assertThat(sent.get(sent.size() - 1)).contains("Herbalism 3").contains(" +15");

    clock.addAndGet(2_000L);
    scheduled.get(scheduled.size() - 1).task().run();
    assertThat(sent.get(sent.size() - 1)).isEqualTo(" ");
  }

  @Test
  void offPlacementSendsPackViewersToTheDelegate() {
    placement.set(HudPlacement.OFF);

    port.publish(viewer, NOTICE);

    assertThat(platform.calls()).containsExactly("publish adapt:notice");
    assertThat(port.status(viewer, "adapt:status:x", HudStatusSpec.toggle("s", ICON, "", true))).isFalse();
    assertThat(sent).isEmpty();
  }

  @Test
  void cornerShowsOneRowPerSkillInTheOrderTheyStarted() {
    placement.set(HudPlacement.CORNER);
    port.xp(viewer, new HudXp("mining", "Mining", "adapt:gui/skill/mining", 0x808080, 7, 0.4, 3.0), 3_000L);
    clock.addAndGet(300L);
    port.xp(viewer, new HudXp("herbalism", "Herbalism", "adapt:gui/skill/herbalism", 0x4DB344, 3, 0.1, 10.0), 3_000L);
    clock.addAndGet(300L);
    port.xp(viewer, new HudXp("mining", "Mining", "adapt:gui/skill/mining", 0x808080, 7, 0.5, 2.0), 3_000L);

    String line = sent.get(sent.size() - 1);
    assertThat(line.indexOf("Mining")).isLessThan(line.indexOf("Herbalism"));
    assertThat(line).contains("+5").contains("+10").contains("Lv 7").contains("Lv 3");
    assertThat(line.chars().filter((int c) -> c == HudGlyphs.PLATE_LEFT).count()).isEqualTo(2);
  }

  @Test
  void cornerInksALevelUpGoldUntilTheFlourishEnds() {
    placement.set(HudPlacement.CORNER);
    port.xp(viewer, new HudXp("mining", "Mining", "adapt:gui/skill/mining", 0x808080, 7, 0.9, 3.0), 10_000L);
    clock.addAndGet(300L);
    port.xp(viewer, new HudXp("mining", "Mining", "adapt:gui/skill/mining", 0x808080, 8, 0.0, 3.0), 10_000L);
    assertThat(sent.get(sent.size() - 1)).contains("<color:#6b440c>Lv 8");

    clock.addAndGet(PackHudPort.LEVEL_MILLIS);
    scheduled.get(scheduled.size() - 1).task().run();
    assertThat(sent.get(sent.size() - 1)).contains("<color:#5a4430>Lv 8");
  }

  @Test
  void clearedStatusLeavesTheStripAndLosingThePackDropsTheViewer() {
    port.status(viewer, "adapt:status:a", HudStatusSpec.toggle("s", ICON, "", true));
    clock.addAndGet(300L);
    port.clearStatus(viewer, "adapt:status:a");
    assertThat(sent).containsExactly(sent.get(0), " ");

    book.remove(VIEWER_ID);
    port.publish(viewer, NOTICE);
    assertThat(platform.calls()).containsExactly("publish adapt:notice");
  }
}
