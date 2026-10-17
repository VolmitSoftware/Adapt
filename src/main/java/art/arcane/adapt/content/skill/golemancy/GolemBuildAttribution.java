/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.skill.golemancy;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemBuildAttribution {
  public static final NamespacedKey BUILDER_KEY = NamespacedKey.fromString("adapt:golem_builder");
  private static final long NANOS_PER_MILLI = 1_000_000L;

  private final ConcurrentHashMap<BlockKey, HeadPlacement> placements = new ConcurrentHashMap<>();
  private final Set<GolemSpawn> spawns = ConcurrentHashMap.newKeySet();

  public static String bodyKey(SpawnReason reason) {
    if (reason == null) {
      return null;
    }
    return switch (reason) {
      case BUILD_SNOWMAN -> "snow";
      case BUILD_COPPERGOLEM -> "copper";
      case BUILD_IRONGOLEM -> "iron";
      default -> null;
    };
  }

  public static void stamp(Entity golem, UUID builder, long epochMillis) {
    golem.getPersistentDataContainer().set(BUILDER_KEY, PersistentDataType.STRING, builder + ";" + epochMillis);
  }

  public static Optional<UUID> builderOf(Entity entity) {
    if (entity == null) {
      return Optional.empty();
    }
    String stamp = entity.getPersistentDataContainer().get(BUILDER_KEY, PersistentDataType.STRING);
    int separator = stamp == null ? -1 : stamp.indexOf(';');
    if (separator < 0) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(stamp.substring(0, separator)));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  public Optional<Build> recordPlacement(HeadPlacement placement, double radius, long windowMillis) {
    expire(placement.atNanos(), windowMillis);
    while (true) {
      GolemSpawn nearest = nearestSpawn(placement, radius, windowMillis);
      if (nearest == null) {
        placements.put(BlockKey.of(placement), placement);
        return Optional.empty();
      }
      if (spawns.remove(nearest)) {
        return Optional.of(new Build(placement.builder(), nearest));
      }
    }
  }

  public Optional<Build> recordSpawn(GolemSpawn spawn, double radius, long windowMillis) {
    expire(spawn.atNanos(), windowMillis);
    while (true) {
      Map.Entry<BlockKey, HeadPlacement> nearest = nearestPlacement(spawn, radius, windowMillis);
      if (nearest == null) {
        spawns.add(spawn);
        return Optional.empty();
      }
      if (placements.remove(nearest.getKey(), nearest.getValue())) {
        return Optional.of(new Build(nearest.getValue().builder(), spawn));
      }
    }
  }

  public void expire(long nowNanos, long windowMillis) {
    long windowNanos = windowNanos(windowMillis);
    placements.values().removeIf(placement -> nowNanos - placement.atNanos() > windowNanos);
    spawns.removeIf(spawn -> nowNanos - spawn.atNanos() > windowNanos);
  }

  private GolemSpawn nearestSpawn(HeadPlacement placement, double radius, long windowMillis) {
    GolemSpawn nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (GolemSpawn spawn : spawns) {
      double distance = matchDistanceSquared(placement, spawn, radius, windowMillis);
      if (distance < nearestDistance) {
        nearest = spawn;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  private Map.Entry<BlockKey, HeadPlacement> nearestPlacement(GolemSpawn spawn, double radius, long windowMillis) {
    Map.Entry<BlockKey, HeadPlacement> nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Map.Entry<BlockKey, HeadPlacement> entry : placements.entrySet()) {
      double distance = matchDistanceSquared(entry.getValue(), spawn, radius, windowMillis);
      if (distance < nearestDistance) {
        nearest = Map.entry(entry.getKey(), entry.getValue());
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  private static double matchDistanceSquared(HeadPlacement placement, GolemSpawn spawn, double radius, long windowMillis) {
    if (!Double.isFinite(radius) || radius < 0D
        || !placement.world().equals(spawn.world())
        || Math.abs(spawn.atNanos() - placement.atNanos()) > windowNanos(windowMillis)) {
      return Double.MAX_VALUE;
    }
    double dx = placement.x() + 0.5D - spawn.x();
    double dy = placement.y() + 0.5D - spawn.y();
    double dz = placement.z() + 0.5D - spawn.z();
    double distanceSquared = dx * dx + dy * dy + dz * dz;
    return distanceSquared <= radius * radius ? distanceSquared : Double.MAX_VALUE;
  }

  private static long windowNanos(long windowMillis) {
    return Math.max(0L, windowMillis) * NANOS_PER_MILLI;
  }

  public record HeadPlacement(String world, int x, int y, int z, UUID builder, long atNanos) {
  }

  public record GolemSpawn(Entity golem, SpawnReason reason, String world, double x, double y, double z, long atNanos) {
  }

  public record Build(UUID builder, GolemSpawn spawn) {
  }

  private record BlockKey(String world, int x, int y, int z) {
    private static BlockKey of(HeadPlacement placement) {
      return new BlockKey(placement.world(), placement.x(), placement.y(), placement.z());
    }
  }
}
