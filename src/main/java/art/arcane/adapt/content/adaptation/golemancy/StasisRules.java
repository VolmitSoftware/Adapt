package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class StasisRules {
  static final double MAX_CHANNEL_SECONDS = 30D;
  private static final Set<Material> WATER = Set.of(Material.WATER, Material.BUBBLE_COLUMN, Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP,
      Material.KELP_PLANT);
  private static final int TICKS_PER_SECOND = 20;
  private static final long MILLIS_PER_SECOND = 1_000L;

  private StasisRules() {
  }

  static int channelTicks(List<Double> secondsByLevel, int level) {
    double seconds = AnimusBindingRules.atLevel(secondsByLevel, level, 0D);
    return Double.isFinite(seconds) && seconds > 0D ? (int) Math.round(seconds * TICKS_PER_SECOND) : 0;
  }

  static List<Double> clampChannelSeconds(List<Double> secondsByLevel) {
    List<Double> clamped = new ArrayList<>(secondsByLevel == null ? 0 : secondsByLevel.size());
    if (secondsByLevel == null) {
      return clamped;
    }
    for (Double seconds : secondsByLevel) {
      clamped.add(seconds == null || seconds.isNaN() ? 0D : Math.max(0D, Math.min(MAX_CHANNEL_SECONDS, seconds)));
    }
    return clamped;
  }

  static boolean packable(GolemBody body) {
    return body != GolemBody.BONE;
  }

  static double packedHealth(double stored, double maximum, long takenAtMillis, long nowMillis, int intervalSeconds, double amount) {
    double base = Double.isFinite(stored) && stored > 0D ? stored : maximum;
    long intervals = Math.max(0L, nowMillis - takenAtMillis) / (Math.max(1, intervalSeconds) * MILLIS_PER_SECOND);
    double healed = base + intervals * (Double.isFinite(amount) ? Math.max(0D, amount) : 0D);
    return Math.min(maximum, healed);
  }

  static boolean hazardous(Material replaced, boolean waterlogged, GolemBody body) {
    if (replaced == Material.LAVA || replaced == Material.FIRE || replaced == Material.SOUL_FIRE) {
      return true;
    }
    return body == GolemBody.FROST && (waterlogged || (replaced != null && WATER.contains(replaced)));
  }

  static AwakenVerdict awakenVerdict(AwakenCheck check) {
    if (!check.readable()) {
      return AwakenVerdict.UNREADABLE;
    }
    if (!check.owner()) {
      return AwakenVerdict.NOT_OWNER;
    }
    if (!check.worldEnabled()) {
      return AwakenVerdict.PROTECTED;
    }
    if (check.level() < 1) {
      return AwakenVerdict.LOCKED;
    }
    if (!check.rosterKnown()) {
      return AwakenVerdict.UNAVAILABLE;
    }
    if (check.status() != GolemRoster.Status.PACKED || !check.sealed()) {
      return AwakenVerdict.VOID;
    }
    if (!check.bodyUnlocked()) {
      return AwakenVerdict.BODY_LOCKED;
    }
    GolemRegistry.BindBlock block = check.registryBlock();
    if (block == GolemRegistry.BindBlock.CORE_NOT_ALLOWED) {
      return AwakenVerdict.CORE_NOT_ALLOWED;
    }
    if (block == GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE) {
      return AwakenVerdict.UNAVAILABLE;
    }
    if (!check.spaceClear() || hazardous(check.replaced(), check.waterlogged(), check.body())) {
      return AwakenVerdict.NO_ROOM;
    }
    if (block == GolemRegistry.BindBlock.CHUNK_CAP) {
      return AwakenVerdict.CHUNK_CAP;
    }
    if (block == GolemRegistry.BindBlock.SERVER_CAP) {
      return AwakenVerdict.SERVER_CAP;
    }
    return check.allowed() ? AwakenVerdict.READY : AwakenVerdict.PROTECTED;
  }
}
