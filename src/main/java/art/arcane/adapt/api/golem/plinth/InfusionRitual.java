package art.arcane.adapt.api.golem.plinth;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class InfusionRitual {
  public static final double MAX_PLAYER_DISTANCE = 8D;
  private static final double MAX_PLAYER_DISTANCE_SQUARED = MAX_PLAYER_DISTANCE * MAX_PLAYER_DISTANCE;
  private static final long STALE_MILLIS = 5_000L;

  private final Player player;
  private final PlinthSpot center;
  private final Infusion infusion;
  private final InfusionTint tint;
  private final Map<PlinthSlot, PlinthSlotItem> slots;
  private final AtomicBoolean finished = new AtomicBoolean();
  private volatile int elapsed;
  private volatile long lastPulse;

  public InfusionRitual(Player player, PlinthSpot center, Infusion infusion, Map<PlinthSlot, PlinthSlotItem> slots, long startedAt) {
    this.player = Objects.requireNonNull(player);
    this.center = Objects.requireNonNull(center);
    this.infusion = Objects.requireNonNull(infusion);
    this.tint = InfusionTint.of(infusion);
    this.slots = Collections.unmodifiableMap(new EnumMap<>(slots));
    this.lastPulse = startedAt;
  }

  public static int levelCost(Infusion infusion, boolean creative) {
    return creative ? 0 : infusion.essenceLevels();
  }

  public Player player() {
    return player;
  }

  public PlinthSpot center() {
    return center;
  }

  public Infusion infusion() {
    return infusion;
  }

  InfusionTint tint() {
    return tint;
  }

  public Map<PlinthSlot, PlinthSlotItem> slots() {
    return slots;
  }

  public Optional<Interruption> check(boolean online, boolean sameWorld, double distanceSquared, boolean structureValid,
                                      Function<PlinthSlot, PlinthSlotItem> current) {
    if (!online) {
      return Optional.of(Interruption.LOGGED_OUT);
    }
    if (!sameWorld || distanceSquared > MAX_PLAYER_DISTANCE_SQUARED) {
      return Optional.of(Interruption.PLAYER_AWAY);
    }
    if (!structureValid) {
      return Optional.of(Interruption.STRUCTURE_BROKEN);
    }
    for (PlinthSlot slot : PlinthSlot.values()) {
      if (!Objects.equals(slots.get(slot), current.apply(slot))) {
        return Optional.of(Interruption.SLOT_CHANGED);
      }
    }
    return Optional.empty();
  }

  public void advance() {
    if (elapsed < infusion.durationTicks()) {
      elapsed++;
    }
  }

  public int elapsed() {
    return elapsed;
  }

  public double progress() {
    return Math.min(1D, (double) elapsed / infusion.durationTicks());
  }

  public boolean due() {
    return elapsed >= infusion.durationTicks();
  }

  public boolean covers(PlinthSpot spot) {
    if (!spot.world().equals(center.world()) || spot.y() != center.y()) {
      return false;
    }
    for (PlinthSlot slot : PlinthSlot.values()) {
      if (center.slot(slot).equals(spot)) {
        return true;
      }
    }
    return false;
  }

  public void pulsed(long nowMillis) {
    lastPulse = nowMillis;
  }

  public boolean stale(long nowMillis) {
    return nowMillis - lastPulse > STALE_MILLIS;
  }

  public boolean isFinished() {
    return finished.get();
  }

  public boolean finish() {
    return finished.compareAndSet(false, true);
  }

  public enum Interruption {
    PLAYER_AWAY,
    LOGGED_OUT,
    STRUCTURE_BROKEN,
    SLOT_CHANGED
  }
}
