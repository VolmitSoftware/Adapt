package art.arcane.adapt.content.adaptation.golemancy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class GatherDeathSites {
  static final int MAX_SITES = 256;

  private volatile List<Site> sites = List.of();

  synchronized void record(UUID world, double x, double y, double z, long nowMillis, long durationMillis) {
    if (durationMillis <= 0L) {
      return;
    }
    List<Site> next = new ArrayList<>(Math.min(sites.size() + 1, MAX_SITES));
    for (Site site : sites) {
      if (site.until() > nowMillis) {
        next.add(site);
      }
    }
    if (next.size() >= MAX_SITES) {
      next.subList(0, next.size() - MAX_SITES + 1).clear();
    }
    next.add(new Site(world, x, y, z, nowMillis + durationMillis));
    sites = List.copyOf(next);
  }

  boolean protects(UUID world, double x, double y, double z, double radius, long nowMillis) {
    double reach = radius * radius;
    for (Site site : sites) {
      if (site.until() > nowMillis && site.world().equals(world) && site.distanceSquared(x, y, z) <= reach) {
        return true;
      }
    }
    return false;
  }

  synchronized void sweep(long nowMillis) {
    List<Site> current = sites;
    if (current.isEmpty()) {
      return;
    }
    List<Site> next = new ArrayList<>(current.size());
    for (Site site : current) {
      if (site.until() > nowMillis) {
        next.add(site);
      }
    }
    if (next.size() != current.size()) {
      sites = List.copyOf(next);
    }
  }

  boolean isEmpty() {
    return sites.isEmpty();
  }

  int size() {
    return sites.size();
  }

  synchronized void clear() {
    sites = List.of();
  }

  private record Site(UUID world, double x, double y, double z, long until) {
    private double distanceSquared(double otherX, double otherY, double otherZ) {
      double dx = x - otherX;
      double dy = y - otherY;
      double dz = z - otherZ;
      return dx * dx + dy * dy + dz * dz;
    }
  }
}
