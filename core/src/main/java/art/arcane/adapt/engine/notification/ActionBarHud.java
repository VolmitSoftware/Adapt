package art.arcane.adapt.engine.notification;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Adapt-only action bar compositor used when the platform installs no {@link HudPort}. Same slot, priority, budget
 * and refresh rules as VolmLib's shared HUD, without segments from other plugins.
 */
final class ActionBarHud implements HudPort {
  static final int VISIBLE_BUDGET = 150;
  static final String SEPARATOR = "§r  ";
  private static final long CLIENT_REFRESH_MILLIS = 2_000L;
  private static final long TICK_MILLIS = 50L;
  private static final Comparator<Stamped> ORDER = Comparator
      .comparingInt((Stamped stamped) -> -stamped.segment().priority())
      .thenComparingLong(Stamped::sinceMillis)
      .thenComparing(stamped -> stamped.segment().purpose());

  private final LongSupplier clock;
  private final Map<UUID, ConcurrentHashMap<String, Stamped>> segments = new ConcurrentHashMap<>();

  ActionBarHud() {
    this(System::currentTimeMillis);
  }

  ActionBarHud(LongSupplier clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public void publish(PlayerRef player, HudSegment segment) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(segment, "segment");
    long now = clock.getAsLong();
    ConcurrentHashMap<String, Stamped> mine = segments.computeIfAbsent(player.uuid(), key -> new ConcurrentHashMap<>());
    Stamped previous = mine.get(segment.purpose());
    Stamped stamped = new Stamped(segment, previous == null ? now : previous.sinceMillis(), now);
    mine.put(segment.purpose(), stamped);
    send(player, compose(mine, now));
    scheduleLifecycle(player, stamped, now);
  }

  @Override
  public void clear(PlayerRef player, String purpose) {
    Objects.requireNonNull(player, "player");
    ConcurrentHashMap<String, Stamped> mine = segments.get(player.uuid());
    if (mine == null || mine.remove(purpose) == null) {
      return;
    }
    if (mine.isEmpty()) {
      segments.remove(player.uuid());
    }
    send(player, compose(mine, clock.getAsLong()));
  }

  @Override
  public void clearAll(PlayerRef player) {
    segments.remove(player.uuid());
  }

  @Override
  public void shutdown() {
    segments.clear();
  }

  private void scheduleLifecycle(PlayerRef player, Stamped expected, long nowMillis) {
    long elapsedMillis = Math.max(0L, nowMillis - expected.assertedMillis());
    long remainingMillis = Math.max(1L, expected.segment().ttlMillis() - elapsedMillis);
    long delayMillis = Math.min(CLIENT_REFRESH_MILLIS, remainingMillis);
    int delayTicks = (int) Math.max(1L, (delayMillis + TICK_MILLIS - 1L) / TICK_MILLIS);
    UUID playerId = player.uuid();
    Tasks.runEntity(player, () -> refresh(player, expected), delayTicks, () -> retire(playerId, expected));
  }

  private void refresh(PlayerRef player, Stamped expected) {
    ConcurrentHashMap<String, Stamped> mine = segments.get(player.uuid());
    if (mine == null || mine.get(expected.segment().purpose()) != expected) {
      return;
    }
    long now = clock.getAsLong();
    if (expected.isExpired(now)) {
      clear(player, expected.segment().purpose());
      return;
    }
    send(player, compose(mine, now));
    scheduleLifecycle(player, expected, now);
  }

  private void retire(UUID playerId, Stamped expected) {
    segments.computeIfPresent(playerId, (key, mine) -> {
      mine.remove(expected.segment().purpose(), expected);
      return mine.isEmpty() ? null : mine;
    });
  }

  static String compose(Map<String, Stamped> mine, long nowMillis) {
    mine.values().removeIf(stamped -> stamped.isExpired(nowMillis));
    List<Stamped> live = new ArrayList<>(mine.size());
    for (Stamped stamped : mine.values()) {
      if (!stamped.segment().text().isBlank()) {
        live.add(stamped);
      }
    }
    live.sort(ORDER);
    EnumMap<HudSlot, List<Placed>> placedBySlot = new EnumMap<>(HudSlot.class);
    int visible = 0;
    int index = 0;
    for (Stamped stamped : live) {
      String text = stamped.segment().text();
      int cost = visibleLength(text);
      if (visible > 0 && visible + cost > VISIBLE_BUDGET) {
        continue;
      }
      List<HudSlot> preferences = stamped.segment().slots();
      HudSlot target = preferences.get(preferences.size() - 1);
      int rank = preferences.size() - 1;
      for (int i = 0; i < preferences.size(); i++) {
        if (!placedBySlot.containsKey(preferences.get(i))) {
          target = preferences.get(i);
          rank = i;
          break;
        }
      }
      placedBySlot.computeIfAbsent(target, key -> new ArrayList<>(2)).add(new Placed(text, rank, index++));
      visible += cost;
    }
    StringBuilder line = new StringBuilder(visible + 8);
    for (HudSlot slot : HudSlot.values()) {
      List<Placed> placed = placedBySlot.get(slot);
      if (placed == null) {
        continue;
      }
      placed.sort(Comparator.comparingInt(Placed::rank).thenComparingInt(Placed::order));
      for (Placed entry : placed) {
        if (!line.isEmpty()) {
          line.append(SEPARATOR);
        }
        line.append(entry.text());
      }
    }
    return line.toString();
  }

  static int visibleLength(String text) {
    int length = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '§' && i + 1 < text.length()) {
        i++;
        continue;
      }
      length++;
    }
    return length;
  }

  private static void send(PlayerRef player, String line) {
    try {
      player.sendActionBar(line.isEmpty() ? " " : line);
    } catch (RuntimeException ignored) {
    }
  }

  record Stamped(HudSegment segment, long sinceMillis, long assertedMillis) {
    boolean isExpired(long nowMillis) {
      return nowMillis - assertedMillis > segment.ttlMillis();
    }
  }

  private record Placed(String text, int rank, int order) {
  }
}
