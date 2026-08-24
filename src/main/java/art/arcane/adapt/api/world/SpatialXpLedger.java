package art.arcane.adapt.api.world;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.xp.SpatialXP;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

final class SpatialXpLedger {
  private static final int CHUNK_SIZE = 16;
  private static final long MAX_INDEX_CELLS_PER_TICKET = 1_089L;

  private final Map<CellKey, ConcurrentLinkedDeque<Ticket>> cells = new ConcurrentHashMap<>();
  private final Map<UUID, ConcurrentLinkedDeque<Ticket>> overflowByWorld = new ConcurrentHashMap<>();
  private final AtomicInteger ticketCount = new AtomicInteger();

  boolean offer(SpatialXP spatialXp, long now) {
    if (spatialXp == null || spatialXp.getSkill() == null || spatialXp.getLocation() == null) {
      return false;
    }

    Location location = spatialXp.getLocation();
    World world = location.getWorld();
    double radius = spatialXp.getRadius();
    double xp = spatialXp.getXp();
    if (world == null
        || !Double.isFinite(location.getX())
        || !Double.isFinite(location.getY())
        || !Double.isFinite(location.getZ())
        || !Double.isFinite(radius)
        || !Double.isFinite(xp)
        || radius <= 0D
        || xp <= 0D
        || spatialXp.getMs() <= now) {
      return false;
    }

    UUID worldId = world.getUID();
    if (worldId == null) {
      return false;
    }

    Ticket ticket = new Ticket(
        location.getX(),
        location.getY(),
        location.getZ(),
        radius,
        spatialXp.getSkill(),
        xp,
        spatialXp.getMs(),
        ticketCount
    );
    int minChunkX = chunk(location.getX() - radius);
    int maxChunkX = chunk(location.getX() + radius);
    int minChunkZ = chunk(location.getZ() - radius);
    int maxChunkZ = chunk(location.getZ() + radius);
    long width = (long) maxChunkX - minChunkX + 1L;
    long depth = (long) maxChunkZ - minChunkZ + 1L;
    ticketCount.incrementAndGet();
    if (width <= 0L
        || depth <= 0L
        || width > MAX_INDEX_CELLS_PER_TICKET
        || depth > MAX_INDEX_CELLS_PER_TICKET
        || width * depth > MAX_INDEX_CELLS_PER_TICKET) {
      overflowByWorld.computeIfAbsent(worldId, ignored -> new ConcurrentLinkedDeque<>()).addLast(ticket);
      return true;
    }

    for (long chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (long chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        CellKey key = new CellKey(worldId, (int) chunkX, (int) chunkZ);
        cells.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>()).addLast(ticket);
      }
    }
    return true;
  }

  Claim claim(Location playerLocation, long now) {
    if (playerLocation == null || ticketCount.get() == 0) {
      return null;
    }

    World world = playerLocation.getWorld();
    if (world == null || world.getUID() == null) {
      return null;
    }

    UUID worldId = world.getUID();
    CellKey key = new CellKey(worldId, chunk(playerLocation.getX()), chunk(playerLocation.getZ()));
    Claim indexed = claimFrom(cells.get(key), playerLocation, now);
    return indexed == null ? claimFrom(overflowByWorld.get(worldId), playerLocation, now) : indexed;
  }

  int size() {
    return ticketCount.get();
  }

  void purgeExpired(long now) {
    cells.entrySet().removeIf(entry -> purgeDeque(entry.getValue(), now));
    overflowByWorld.entrySet().removeIf(entry -> purgeDeque(entry.getValue(), now));
  }

  private Claim claimFrom(ConcurrentLinkedDeque<Ticket> tickets, Location playerLocation, long now) {
    if (tickets == null) {
      return null;
    }

    Iterator<Ticket> iterator = tickets.descendingIterator();
    while (iterator.hasNext()) {
      Ticket ticket = iterator.next();
      if (!ticket.isActive(now)) {
        tickets.remove(ticket);
        continue;
      }

      Claim claim = ticket.claim(playerLocation.getX(), playerLocation.getY(), playerLocation.getZ(), now);
      if (claim != null) {
        return claim;
      }
    }
    return null;
  }

  private boolean purgeDeque(ConcurrentLinkedDeque<Ticket> tickets, long now) {
    tickets.removeIf(ticket -> !ticket.isActive(now));
    return tickets.isEmpty();
  }

  private static int chunk(double coordinate) {
    return (int) Math.floor(coordinate / CHUNK_SIZE);
  }

  record Claim(Skill<?> skill, double xp) {
  }

  private record CellKey(UUID worldId, int chunkX, int chunkZ) {
  }

  private static final class Ticket {
    private final double x;
    private final double y;
    private final double z;
    private final double radiusSquared;
    private final Skill<?> skill;
    private final long expiresAt;
    private final AtomicInteger ticketCount;
    private double xp;
    private boolean active = true;

    private Ticket(double x, double y, double z, double radius, Skill<?> skill, double xp,
                   long expiresAt, AtomicInteger ticketCount) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.radiusSquared = radius * radius;
      this.skill = skill;
      this.xp = xp;
      this.expiresAt = expiresAt;
      this.ticketCount = ticketCount;
    }

    private synchronized boolean isActive(long now) {
      expire(now);
      return active;
    }

    private synchronized Claim claim(double playerX, double playerY, double playerZ, long now) {
      expire(now);
      if (!active) {
        return null;
      }

      double deltaX = playerX - x;
      double deltaY = playerY - y;
      double deltaZ = playerZ - z;
      double distanceSquared = (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ);
      if (distanceSquared >= radiusSquared) {
        return null;
      }

      double distanceRatio = distanceSquared / radiusSquared;
      double granted = xp / (1.5D * ((distanceRatio * 9D) + 1D));
      xp -= granted;
      if (xp < 10D) {
        granted += xp;
        deactivate();
      }
      return new Claim(skill, granted);
    }

    private void expire(long now) {
      if (active && now > expiresAt) {
        deactivate();
      }
    }

    private void deactivate() {
      if (!active) {
        return;
      }
      active = false;
      ticketCount.decrementAndGet();
    }
  }
}
