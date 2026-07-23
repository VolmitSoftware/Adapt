package art.arcane.adapt.api.fx;

import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicInteger;

public final class FxBudget {
  public static final int GLOBAL_PACKET_BUDGET = 10_000;
  public static final int PER_EMITTER_PARTICLE_CAP = 256;
  public static final int PER_VIEWER_EMISSION_CAP = 64;
  private static final long TPS_SAMPLE_INTERVAL_MS = 1_000L;
  private static final long BAND_UPGRADE_HOLD_MS = 1_000L;
  private static final Object SAMPLE_LOCK = new Object();
  private static final AtomicInteger USED_PACKETS = new AtomicInteger();
  private static volatile double cachedTps = 20.0D;
  private static volatile int appliedBand = 0;
  private static volatile long lastSampleAt = 0L;
  private static volatile boolean tpsUnavailable = false;
  private static int candidateBand = 0;
  private static long candidateSince = 0L;

  private FxBudget() {
  }

  public static double densityScalar(FxPriority priority) {
    sampleIfDue();
    int band = appliedBand;
    return switch (band) {
      case 0 -> 1.0D;
      case 1 -> taperScalar();
      case 2 -> priority == FxPriority.GAMEPLAY || priority == FxPriority.COMBAT ? 0.25D : 0.0D;
      case 3 -> priority == FxPriority.GAMEPLAY ? 0.10D : 0.0D;
      default -> priority == FxPriority.GAMEPLAY ? 0.05D : 0.0D;
    };
  }

  public static int tryConsume(FxPriority priority, int particleTimesViewers) {
    if (priority == null || particleTimesViewers <= 0) {
      return 0;
    }

    int ceiling = shedCeiling(priority);
    while (true) {
      int used = USED_PACKETS.get();
      int available = ceiling - used;
      if (available <= 0) {
        return 0;
      }

      int granted = Math.min(particleTimesViewers, available);
      if (USED_PACKETS.compareAndSet(used, used + granted)) {
        return granted;
      }
    }
  }

  static void resetTick() {
    USED_PACKETS.set(0);
  }

  public static int usedPackets() {
    return USED_PACKETS.get();
  }

  public static int shedBand() {
    return appliedBand;
  }

  private static int shedCeiling(FxPriority priority) {
    return switch (priority) {
      case GAMEPLAY -> GLOBAL_PACKET_BUDGET;
      case COMBAT -> (GLOBAL_PACKET_BUDGET * 95) / 100;
      case TRANSITION -> (GLOBAL_PACKET_BUDGET * 80) / 100;
      case TRAIL -> (GLOBAL_PACKET_BUDGET * 65) / 100;
      case AMBIENT -> GLOBAL_PACKET_BUDGET / 2;
    };
  }

  private static double taperScalar() {
    double tps = cachedTps;
    double clamped = Math.max(17.0D, Math.min(19.0D, tps));
    return 0.5D + (0.25D * (clamped - 17.0D));
  }

  private static void sampleIfDue() {
    long now = M.ms();
    if (now - lastSampleAt < TPS_SAMPLE_INTERVAL_MS) {
      return;
    }

    synchronized (SAMPLE_LOCK) {
      if (now - lastSampleAt < TPS_SAMPLE_INTERVAL_MS) {
        return;
      }

      lastSampleAt = now;
      double tps = sampleTps();
      cachedTps = tps;
      int measured = bandOf(tps);
      int applied = appliedBand;
      if (measured >= applied) {
        appliedBand = measured;
        candidateBand = measured;
        candidateSince = now;
        return;
      }

      if (measured != candidateBand) {
        candidateBand = measured;
        candidateSince = now;
        return;
      }

      if (now - candidateSince >= BAND_UPGRADE_HOLD_MS) {
        appliedBand = measured;
      }
    }
  }

  private static int bandOf(double tps) {
    if (tps >= 19.0D) {
      return 0;
    }
    if (tps >= 17.0D) {
      return 1;
    }
    if (tps >= 15.0D) {
      return 2;
    }
    if (tps >= 12.0D) {
      return 3;
    }
    return 4;
  }

  private static double sampleTps() {
    if (tpsUnavailable) {
      return 20.0D;
    }

    try {
      double[] tps = Bukkit.getTPS();
      if (tps == null || tps.length == 0) {
        return 20.0D;
      }
      return tps[0];
    } catch (Throwable ignored) {
      tpsUnavailable = true;
      return 20.0D;
    }
  }
}
