package art.arcane.adapt.pack.hud;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.notification.HudPort;
import art.arcane.adapt.engine.notification.HudSegment;
import art.arcane.adapt.engine.notification.HudStatusSpec;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.link.PackAudience;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Sits between {@code ClientHudPort} and the platform port. Pack viewers get the HUD the {@link HudPlacement} names,
 * composed from the same segments, statuses and xp awards native clients receive as frames: the {@link HudCorner} in the
 * top left or the {@link HudStrip} above the hotbar; with {@link HudPlacement#OFF}, and for everyone else, they go to the
 * delegate unchanged. The line is sent only when it changes, at most every {@link #MIN_INTERVAL_MILLIS}, and resent
 * every {@link #KEEP_ALIVE_MILLIS} while it has content so the client does not fade it out.
 */
public final class PackHudPort implements HudPort {
  public static final long MIN_INTERVAL_MILLIS = 250L;
  public static final long KEEP_ALIVE_MILLIS = 1_500L;
  public static final int MAX_NOTICES = 2;
  /** How long a level-up inks the corner's level gold, as the client HUD's level flourish lasts. */
  public static final long LEVEL_MILLIS = 2_500L;
  private static final long TICK_MILLIS = 50L;
  private static final Comparator<Notice> NOTICE_ORDER = Comparator
      .comparingInt((Notice notice) -> -notice.segment().priority())
      .thenComparingLong(Notice::sinceMillis);

  /** Runs {@code task} on the player's thread after {@code delayTicks}; {@code retired} runs instead if they left. */
  @FunctionalInterface
  public interface Scheduler {
    boolean later(PlayerRef player, Runnable task, int delayTicks, Runnable retired);
  }

  /** Sends one action bar line of Adapt markup. */
  @FunctionalInterface
  public interface Sender {
    void send(PlayerRef player, String markup);
  }

  private final HudPort delegate;
  private final PackAudience audience;
  private final Supplier<HudPlacement> placement;
  private final Function<String, String> icons;
  private final UnaryOperator<String> noticeMarkup;
  private final IntFunction<String> levelLabel;
  private final LongSupplier clock;
  private final Scheduler scheduler;
  private final Sender sender;
  private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();

  /**
   * @param placement    where viewers see the HUD, read on every send
   * @param icons        the full-size {@code adapt:hud} glyph of a sprite id, or null when the pack has none
   * @param noticeMarkup turns a segment's legacy section text into Adapt markup
   * @param levelLabel   the plain text naming a level
   */
  public PackHudPort(HudPort delegate, PackAudience audience, Supplier<HudPlacement> placement, Function<String, String> icons,
                     UnaryOperator<String> noticeMarkup, IntFunction<String> levelLabel, LongSupplier clock, Scheduler scheduler,
                     Sender sender) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.audience = Objects.requireNonNull(audience, "audience");
    this.placement = Objects.requireNonNull(placement, "placement");
    this.icons = Objects.requireNonNull(icons, "icons");
    this.noticeMarkup = Objects.requireNonNull(noticeMarkup, "noticeMarkup");
    this.levelLabel = Objects.requireNonNull(levelLabel, "levelLabel");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.sender = Objects.requireNonNull(sender, "sender");
  }

  /**
   * The running server's port over {@code delegate}: viewers are the {@link PackAudience#DELIVERED} audience, the
   * placement is {@code pack.hud}, icon glyphs come from the {@link PackHudManifest}, all read on each call, so the pack
   * can arrive after the HUD starts and the config can change.
   */
  public static PackHudPort over(HudPort delegate) {
    return new PackHudPort(delegate, PackAudience.DELIVERED, () -> AdaptConfig.get().getPack().hudPlacement(),
        (String spriteId) -> PackHudManifest.current().icon(spriteId),
        (String legacy) -> MarkupPort.current().normalize(legacy),
        (int level) -> MarkupPort.current().plain(AdaptLanguage.text(GuiMessages.PACK_LV, trusted("level", level))),
        System::currentTimeMillis,
        (PlayerRef player, Runnable task, int delayTicks, Runnable retired) ->
            Tasks.runEntity(player, task, delayTicks, retired),
        PlayerRef::sendActionBar);
  }

  /** True when the player sees the pack HUD. Must stay cheap: callers check it before building a status. */
  public boolean viewing(PlayerRef player) {
    if (player != null && placement.get() != HudPlacement.OFF && audience.book(player)) {
      return true;
    }
    if (player != null) {
      viewers.remove(player.uuid());
    }
    return false;
  }

  @Override
  public void publish(PlayerRef player, HudSegment segment) {
    if (!viewing(player)) {
      delegate.publish(player, segment);
      return;
    }
    Viewer viewer = viewer(player);
    synchronized (viewer) {
      long now = clock.getAsLong();
      Notice previous = viewer.notices.get(segment.purpose());
      viewer.notices.put(segment.purpose(), new Notice(segment, previous == null ? now : previous.sinceMillis(),
          now + segment.ttlMillis()));
    }
    pump(viewer);
  }

  @Override
  public void clear(PlayerRef player, String purpose) {
    if (!viewing(player)) {
      delegate.clear(player, purpose);
      return;
    }
    Viewer viewer = viewer(player);
    synchronized (viewer) {
      viewer.notices.remove(purpose);
    }
    pump(viewer);
  }

  @Override
  public void clearAll(PlayerRef player) {
    viewers.remove(player.uuid());
    delegate.clearAll(player);
  }

  @Override
  public void shutdown() {
    viewers.clear();
    delegate.shutdown();
  }

  /** Shows an ability status on the pack HUD; false when the player is not a pack viewer. */
  public boolean status(PlayerRef player, String purpose, HudStatusSpec spec) {
    if (!viewing(player)) {
      return false;
    }
    Viewer viewer = viewer(player);
    synchronized (viewer) {
      viewer.abilities.put(purpose, spec);
    }
    pump(viewer);
    return true;
  }

  public void clearStatus(PlayerRef player, String purpose) {
    Viewer viewer = viewers.get(player.uuid());
    if (viewer == null) {
      return;
    }
    synchronized (viewer) {
      if (viewer.abilities.remove(purpose) == null) {
        return;
      }
    }
    pump(viewer);
  }

  /**
   * Shows an xp award on its skill's xp line for {@code showMillis}; an award for a skill already shown adds to its gain,
   * and a higher level marks the line levelled up. False when the player is not a pack viewer.
   */
  public boolean xp(PlayerRef player, HudXp award, long showMillis) {
    if (!viewing(player)) {
      return false;
    }
    Viewer viewer = viewer(player);
    synchronized (viewer) {
      long now = clock.getAsLong();
      XpLine shown = viewer.xp.get(award.skillId());
      long until = now + Math.max(1L, showMillis);
      if (shown != null && now < shown.untilMillis()) {
        long levelledAt = award.level() > shown.xp().level() ? now : shown.levelledAtMillis();
        viewer.xp.put(award.skillId(), new XpLine(shown.xp().add(award.level(), award.progress(), award.gained()), now, until,
            levelledAt));
      } else {
        viewer.xp.remove(award.skillId());
        viewer.xp.put(award.skillId(), new XpLine(award, now, until, Long.MIN_VALUE));
      }
    }
    pump(viewer);
    return true;
  }

  private Viewer viewer(PlayerRef player) {
    return viewers.computeIfAbsent(player.uuid(), (UUID id) -> new Viewer(player));
  }

  private void pump(Viewer viewer) {
    String line = null;
    long wake;
    synchronized (viewer) {
      long now = clock.getAsLong();
      String composed = compose(viewer, now);
      boolean changed = !composed.equals(viewer.sent);
      boolean due = now - viewer.sentAt >= MIN_INTERVAL_MILLIS;
      if (changed && due || !changed && !composed.isEmpty() && now - viewer.sentAt >= KEEP_ALIVE_MILLIS) {
        line = composed;
        viewer.sent = composed;
        viewer.sentAt = now;
      }
      wake = nextWake(viewer, composed, line == null && changed, now);
      if (wake != Long.MAX_VALUE && (viewer.wakeAt <= now || wake < viewer.wakeAt)) {
        viewer.wakeAt = wake;
      } else {
        wake = Long.MAX_VALUE;
      }
    }
    if (line != null) {
      sender.send(viewer.player, line.isEmpty() ? " " : line);
    }
    if (wake != Long.MAX_VALUE) {
      long delayMillis = Math.max(1L, wake - clock.getAsLong());
      int ticks = (int) Math.max(1L, (delayMillis + TICK_MILLIS - 1L) / TICK_MILLIS);
      UUID id = viewer.player.uuid();
      scheduler.later(viewer.player, () -> wake(viewer), ticks, () -> viewers.remove(id, viewer));
    }
  }

  private void wake(Viewer viewer) {
    synchronized (viewer) {
      viewer.wakeAt = Long.MIN_VALUE;
    }
    if (viewers.get(viewer.player.uuid()) == viewer) {
      pump(viewer);
    }
  }

  private String compose(Viewer viewer, long now) {
    viewer.notices.values().removeIf((Notice notice) -> now >= notice.untilMillis());
    viewer.abilities.values().removeIf((HudStatusSpec spec) -> HudStrip.expired(spec, now));
    viewer.xp.values().removeIf((XpLine line) -> now >= line.untilMillis());
    List<Notice> live = new ArrayList<>(viewer.notices.values());
    live.sort(NOTICE_ORDER);
    List<HudCorner.Notice> notices = new ArrayList<>(Math.min(MAX_NOTICES, live.size()));
    for (Notice notice : live) {
      if (notices.size() == MAX_NOTICES) {
        break;
      }
      String legacy = notice.segment().text();
      if (!legacy.isBlank()) {
        notices.add(new HudCorner.Notice(noticeMarkup.apply(legacy), PackFontMetrics.vanilla().width(legacy)));
      }
    }
    List<HudStatusSpec> abilities = new ArrayList<>(viewer.abilities.values());
    if (placement.get() == HudPlacement.HOTBAR) {
      XpLine newest = null;
      for (XpLine line : viewer.xp.values()) {
        if (newest == null || line.updatedMillis() >= newest.updatedMillis()) {
          newest = line;
        }
      }
      return HudStrip.compose(abilities, newest == null ? null : newest.xp(),
          notices.stream().map(HudCorner.Notice::markup).toList(), icons, now);
    }
    List<HudCorner.Skill> skills = new ArrayList<>(viewer.xp.size());
    for (XpLine line : viewer.xp.values()) {
      skills.add(new HudCorner.Skill(line.xp(), line.levelledAtMillis() != Long.MIN_VALUE && now - line.levelledAtMillis() < LEVEL_MILLIS));
    }
    return HudCorner.compose(skills, abilities, notices, icons, levelLabel, now);
  }

  private static long nextWake(Viewer viewer, String composed, boolean held, long now) {
    long wake = HudStrip.nextChange(new ArrayList<>(viewer.abilities.values()), now);
    if (held) {
      wake = Math.min(wake, viewer.sentAt + MIN_INTERVAL_MILLIS);
    }
    if (!composed.isEmpty()) {
      wake = Math.min(wake, viewer.sentAt + KEEP_ALIVE_MILLIS);
    }
    for (Notice notice : viewer.notices.values()) {
      wake = Math.min(wake, notice.untilMillis());
    }
    for (XpLine line : viewer.xp.values()) {
      wake = Math.min(wake, line.untilMillis());
      long levelFades = line.levelledAtMillis() + LEVEL_MILLIS;
      if (line.levelledAtMillis() != Long.MIN_VALUE && levelFades > now) {
        wake = Math.min(wake, levelFades);
      }
    }
    return wake;
  }

  private record Notice(HudSegment segment, long sinceMillis, long untilMillis) {
  }

  private record XpLine(HudXp xp, long updatedMillis, long untilMillis, long levelledAtMillis) {
  }

  private static final class Viewer {
    private final PlayerRef player;
    private final Map<String, HudStatusSpec> abilities = new LinkedHashMap<>();
    private final Map<String, Notice> notices = new LinkedHashMap<>();
    private final Map<String, XpLine> xp = new LinkedHashMap<>();
    private String sent = "";
    private long sentAt = Long.MIN_VALUE / 2;
    private long wakeAt = Long.MIN_VALUE;

    private Viewer(PlayerRef player) {
      this.player = player;
    }
  }
}
