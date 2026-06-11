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

package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptConfig;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class XpNovelty {
  private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
  private static final String NULL_KEY = "null";
  private static final int STILLNESS_RING = 32;
  private static final float STILLNESS_YAW_RANGE = 10.0f;
  private static final double ENTROPY_SATURATION_KEYS = 3.0;

  private XpNovelty() {
  }

  public static double noveltyMultiplier(Player p, Location at, String rewardKey) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isNoveltyEnabled() || p == null) {
      return 1.0;
    }

    State state = stateOf(p.getUniqueId(), config);
    long now = M.ms();
    synchronized (state) {
      double spatial = 1.0;
      if (at != null && at.getWorld() != null) {
        long key = cellKey(at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), config.getSpatialCellShift());
        spatial = state.spatialVisit(key, now, config);
      }

      String entropyKey = rewardKey == null ? NULL_KEY : rewardKey;
      double entropy = state.entropySample(entropyKey, config);
      double combined = spatial * entropy;

      if (config.isStillnessEnabled()) {
        Location pos = p.getLocation(state.scratch);
        if (state.stillnessSample(pos.getX(), pos.getY(), pos.getZ(), pos.getYaw(), now, config)) {
          combined = Math.min(combined, config.getStillnessFloorMultiplier());
        }
      }

      double floor = config.getSpatialFloorMultiplier() * config.getEntropyFloorMultiplier() * config.getStillnessFloorMultiplier();
      return Math.max(combined, floor);
    }
  }

  public static double adjacencyBonusMultiplier(Player p, Block placed) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isAdjacencyBonusEnabled() || p == null || placed == null) {
      return 1.0;
    }

    boolean touching = XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.DOWN))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.UP))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.NORTH))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.SOUTH))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.EAST))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.WEST));
    State state = stateOf(p.getUniqueId(), config);
    double perStreak = config.getAdjacencyBonusPerStreak();
    double max = config.getAdjacencyBonusMax();
    synchronized (state) {
      if (touching) {
        long key = cellKey(placed.getWorld(), placed.getX(), placed.getY(), placed.getZ(), config.getSpatialCellShift());
        int visits = state.spatialPeek(key, M.ms(), config);
        if (perStreak > 0.0 && visits * config.getSpatialRepeatDecay() <= 1.0 && state.adjacencyStreak * perStreak < max) {
          state.adjacencyStreak++;
        }
      } else {
        state.adjacencyStreak >>= 1;
      }

      double bonus = Math.min(max, state.adjacencyStreak * perStreak);
      return 1.0 + Math.max(0.0, bonus);
    }
  }

  public static double fieldCycleMultiplier(Player p, Block crop) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isNoveltyEnabled() || p == null || crop == null) {
      return 1.0;
    }

    State state = stateOf(p.getUniqueId(), config);
    long now = M.ms();
    long key = cellKey(crop.getWorld(), crop.getX(), crop.getY(), crop.getZ(), config.getSpatialCellShift());
    synchronized (state) {
      return state.fieldCycle(key, now, config);
    }
  }

  public static void clear(UUID id) {
    STATES.remove(id);
  }

  private static State stateOf(UUID id, AdaptConfig.XpIntegrity config) {
    State state = STATES.get(id);
    if (state != null) {
      return state;
    }

    State created = new State(config);
    State prior = STATES.putIfAbsent(id, created);
    return prior == null ? created : prior;
  }

  private static long cellKey(World world, int x, int y, int z, int shift) {
    long cx = x >> shift;
    long cy = y >> shift;
    long cz = z >> shift;
    long key = ((cx & 0x3FFFFFFL) << 38) | ((cy & 0xFFFL) << 26) | (cz & 0x3FFFFFFL);
    return key ^ ((long) world.hashCode() * 0x9E3779B97F4A7C15L);
  }

  private static final class Cell {
    private int count;
    private long touch;
  }

  private static final class LruMap extends LinkedHashMap<Long, Cell> {
    private final int cap;

    private LruMap(int cap) {
      super(16, 0.75f, true);
      this.cap = Math.max(1, cap);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, Cell> eldest) {
      return size() > cap;
    }
  }

  private static final class State {
    private final LruMap spatialCells;
    private final LruMap fieldCells;
    private final String[] window;
    private final HashMap<String, Integer> counts;
    private final long[] times;
    private final Location scratch;
    private int entropyHead;
    private int entropyFilled;
    private int distinct;
    private int stillHead;
    private int stillCount;
    private int runEvents;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private double minZ;
    private double maxZ;
    private float minYaw;
    private float maxYaw;
    private int adjacencyStreak;

    private State(AdaptConfig.XpIntegrity config) {
      this.spatialCells = new LruMap(config.getSpatialCellCap());
      this.fieldCells = new LruMap(config.getSpatialCellCap());
      int windowSize = config.getEntropyWindow();
      this.window = windowSize > 1 ? new String[windowSize] : null;
      this.counts = new HashMap<>();
      this.times = new long[STILLNESS_RING];
      this.scratch = new Location(null, 0.0, 0.0, 0.0);
    }

    private double spatialVisit(long key, long now, AdaptConfig.XpIntegrity config) {
      Cell cell = spatialCells.get(key);
      if (cell == null) {
        cell = new Cell();
        spatialCells.put(key, cell);
      }

      long ttl = config.getSpatialCellTtlMillis();
      if (ttl > 0 && now - cell.touch > ttl) {
        cell.count = 0;
      }

      int n = cell.count;
      cell.count = n + 1;
      cell.touch = now;
      double m = 1.0 / (1.0 + config.getSpatialRepeatDecay() * n);
      return Math.max(config.getSpatialFloorMultiplier(), m);
    }

    private int spatialPeek(long key, long now, AdaptConfig.XpIntegrity config) {
      Cell cell = spatialCells.get(key);
      if (cell == null) {
        return 0;
      }

      long ttl = config.getSpatialCellTtlMillis();
      if (ttl > 0 && now - cell.touch > ttl) {
        return 0;
      }

      return cell.count;
    }

    private double entropySample(String key, AdaptConfig.XpIntegrity config) {
      if (window == null) {
        return 1.0;
      }

      if (entropyFilled == window.length) {
        String old = window[entropyHead];
        Integer oldCount = counts.get(old);
        if (oldCount != null) {
          if (oldCount.intValue() <= 1) {
            counts.remove(old);
            distinct--;
          } else {
            counts.put(old, oldCount.intValue() - 1);
          }
        }
      }

      window[entropyHead] = key;
      entropyHead = entropyHead + 1 == window.length ? 0 : entropyHead + 1;
      if (entropyFilled < window.length) {
        entropyFilled++;
      }

      Integer newCount = counts.get(key);
      if (newCount == null) {
        counts.put(key, 1);
        distinct++;
      } else {
        counts.put(key, newCount.intValue() + 1);
      }

      if (entropyFilled < window.length) {
        return 1.0;
      }

      double floor = config.getEntropyFloorMultiplier();
      double normalized = (distinct - 1) / (ENTROPY_SATURATION_KEYS - 1.0);
      double smooth = normalized >= 1.0 ? 1.0 : Math.sqrt(Math.max(0.0, normalized));
      return floor + (1.0 - floor) * smooth;
    }

    private boolean stillnessSample(double x, double y, double z, float yaw, long now, AdaptConfig.XpIntegrity config) {
      double eps = config.getStillnessEpsilon();
      if (runEvents == 0) {
        beginRun(x, y, z, yaw, now);
        return false;
      }

      double nMinX = Math.min(minX, x);
      double nMaxX = Math.max(maxX, x);
      double nMinY = Math.min(minY, y);
      double nMaxY = Math.max(maxY, y);
      double nMinZ = Math.min(minZ, z);
      double nMaxZ = Math.max(maxZ, z);
      float nMinYaw = Math.min(minYaw, yaw);
      float nMaxYaw = Math.max(maxYaw, yaw);
      if (nMaxX - nMinX >= eps || nMaxY - nMinY >= eps || nMaxZ - nMinZ >= eps || nMaxYaw - nMinYaw >= STILLNESS_YAW_RANGE) {
        beginRun(x, y, z, yaw, now);
        return false;
      }

      minX = nMinX;
      maxX = nMaxX;
      minY = nMinY;
      maxY = nMaxY;
      minZ = nMinZ;
      maxZ = nMaxZ;
      minYaw = nMinYaw;
      maxYaw = nMaxYaw;
      times[stillHead] = now;
      stillHead = stillHead + 1 == STILLNESS_RING ? 0 : stillHead + 1;
      if (stillCount < STILLNESS_RING) {
        stillCount++;
      }
      runEvents++;

      if (runEvents < config.getStillnessMinEvents()) {
        return false;
      }

      long oldest = stillCount == STILLNESS_RING ? times[stillHead] : times[0];
      return now - oldest >= config.getStillnessWindowMillis();
    }

    private void beginRun(double x, double y, double z, float yaw, long now) {
      minX = x;
      maxX = x;
      minY = y;
      maxY = y;
      minZ = z;
      maxZ = z;
      minYaw = yaw;
      maxYaw = yaw;
      times[0] = now;
      stillHead = 1;
      stillCount = 1;
      runEvents = 1;
    }

    private double fieldCycle(long key, long now, AdaptConfig.XpIntegrity config) {
      Cell cell = fieldCells.get(key);
      double result = 1.0;
      if (cell == null) {
        cell = new Cell();
        fieldCells.put(key, cell);
      } else {
        long cycle = config.getFieldCycleMillis();
        if (cycle > 0) {
          long elapsed = now - cell.touch;
          if (elapsed < cycle) {
            double floor = config.getFieldCycleFloorMultiplier();
            result = floor + (1.0 - floor) * ((double) elapsed / (double) cycle);
          }
        }
      }

      cell.touch = now;
      return result;
    }
  }
}
