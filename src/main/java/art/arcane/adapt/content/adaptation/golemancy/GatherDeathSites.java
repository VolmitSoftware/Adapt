package art.arcane.adapt.content.adaptation.golemancy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class GatherDeathSites {
  static final int MAX_SITES_PER_WORLD = 256;

  private final Map<UUID, List<Site>> worlds = new ConcurrentHashMap<>();

  void record(UUID world, double x, double y, double z, long nowMillis, long durationMillis) {
    if (durationMillis <= 0L) {
      return;
    }
    Site site = new Site(x, y, z, nowMillis + durationMillis);
    worlds.compute(world, (ignored, current) -> withSite(current == null ? List.of() : current, site, nowMillis));
  }

  boolean protects(UUID world, double x, double y, double z, double radius, long nowMillis) {
    List<Site> sites = worlds.get(world);
    if (sites == null) {
      return false;
    }
    double reach = radius * radius;
    for (Site site : sites) {
      if (site.until() > nowMillis && site.distanceSquared(x, y, z) <= reach) {
        return true;
      }
    }
    return false;
  }

  void sweep(long nowMillis) {
    for (UUID world : List.copyOf(worlds.keySet())) {
      worlds.computeIfPresent(world, (ignored, current) -> {
        List<Site> kept = live(current, nowMillis);
        return kept.isEmpty() ? null : kept;
      });
    }
  }

  boolean isEmpty() {
    return worlds.isEmpty();
  }

  int size(UUID world) {
    List<Site> sites = worlds.get(world);
    return sites == null ? 0 : sites.size();
  }

  void clear() {
    worlds.clear();
  }

  private static List<Site> withSite(List<Site> current, Site site, long nowMillis) {
    List<Site> next = new ArrayList<>(live(current, nowMillis));
    if (next.size() >= MAX_SITES_PER_WORLD) {
      next.subList(0, next.size() - MAX_SITES_PER_WORLD + 1).clear();
    }
    next.add(site);
    return List.copyOf(next);
  }

  private static List<Site> live(List<Site> sites, long nowMillis) {
    List<Site> kept = new ArrayList<>(sites.size());
    for (Site site : sites) {
      if (site.until() > nowMillis) {
        kept.add(site);
      }
    }
    return kept.size() == sites.size() ? sites : List.copyOf(kept);
  }

  private record Site(double x, double y, double z, long until) {
    private double distanceSquared(double otherX, double otherY, double otherZ) {
      double dx = x - otherX;
      double dy = y - otherY;
      double dz = z - otherZ;
      return dx * dx + dy * dy + dz * dz;
    }
  }
}
