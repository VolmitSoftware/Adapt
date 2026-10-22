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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class XpNovelty {
  private static final String NULL_KEY = "null";
  private static final double ENTROPY_SATURATION_KEYS = 3.0;
  private static final int MAX_HISTORY_SIZE = 4096;

  private XpNovelty() {
  }

  public static double noveltyMultiplier(Player p, Location at, String rewardKey) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isNoveltyEnabled() || p == null) {
      return 1.0;
    }
    State state = stateOf(p);
    if (state == null) {
      return 1.0;
    }
    long now = M.ms();
    synchronized (state) {
      state.prepare(config);
      double spatial = at == null || at.getWorld() == null ? 1.0
          : state.spatialVisit(cellKey(at, config.getSpatialCellShift()), now, config);
      double entropy = state.entropySample(rewardKey == null ? NULL_KEY : rewardKey, now, config);
      double combined = spatial * entropy;
      if (config.isStillnessEnabled()) {
        Location position = p.getLocation(state.scratch);
        if (state.stillnessSample(position, now, config)) {
          combined *= config.getStillnessFloorMultiplier();
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
    State state = stateOf(p);
    if (state == null) {
      return 1.0;
    }
    boolean touching = XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.DOWN))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.UP))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.NORTH))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.SOUTH))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.EAST))
        || XpProvenance.isPlayerPlaced(placed.getRelative(BlockFace.WEST));
    double perStreak = config.getAdjacencyBonusPerStreak();
    double max = config.getAdjacencyBonusMax();
    synchronized (state) {
      state.prepare(config);
      if (touching) {
        int visits = state.spatialPeek(cellKey(placed.getLocation(), config.getSpatialCellShift()), M.ms(), config);
        if (perStreak > 0.0 && visits * config.getSpatialRepeatDecay() <= 1.0 && state.adjacencyStreak * perStreak < max) {
          state.adjacencyStreak++;
        }
      } else {
        state.adjacencyStreak >>= 1;
      }
      return 1.0 + Math.max(0.0, Math.min(max, state.adjacencyStreak * perStreak));
    }
  }

  public static double fieldCycleMultiplier(Player p, Block crop) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isNoveltyEnabled() || p == null || crop == null) {
      return 1.0;
    }
    State state = stateOf(p);
    if (state == null) {
      return 1.0;
    }
    synchronized (state) {
      state.prepare(config);
      return state.fieldCycle(cellKey(crop.getLocation(), config.getSpatialCellShift()), M.ms(), config);
    }
  }

  private static State stateOf(Player player) {
    Adapt plugin = Adapt.instance;
    if (plugin == null || plugin.getAdaptServer() == null) {
      return null;
    }
    AdaptPlayer runtime = plugin.getAdaptServer().getOnlineAdaptPlayer(player.getUniqueId());
    return runtime == null || !runtime.isRuntimeReady() || runtime.getPlayer() != player
        ? null : runtime.getData().getXpNovelty();
  }

  private static String cellKey(Location at, int shift) {
    int boundedShift = Math.clamp(shift, 0, 30);
    return at.getWorld().getUID() + ":" + boundedShift + ":" + (at.getBlockX() >> boundedShift)
        + ":" + (at.getBlockY() >> boundedShift) + ":" + (at.getBlockZ() >> boundedShift);
  }

  private static final class Cell {
    private int count;
    private long touch;
  }

  public static final class State {
    private LinkedHashMap<String, Cell> spatialCells = new LinkedHashMap<>();
    private LinkedHashMap<String, Cell> fieldCells = new LinkedHashMap<>();
    private String[] window = new String[0];
    private int entropyHead;
    private int entropyFilled;
    private long entropyTouchedAt;
    private long stillStartedAt;
    private long stillTouchedAt;
    private UUID stillWorld;
    private int runEvents;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private double minZ;
    private double maxZ;
    private int adjacencyStreak;
    private transient HashMap<String, Integer> counts;
    private transient long frequencySquares;
    private transient Location scratch;

    public State() {
    }

    private void prepare(AdaptConfig.XpIntegrity config) {
      if (spatialCells == null) {
        spatialCells = new LinkedHashMap<>();
      }
      if (fieldCells == null) {
        fieldCells = new LinkedHashMap<>();
      }
      int cap = Math.clamp(config.getSpatialCellCap(), 1, MAX_HISTORY_SIZE);
      trim(spatialCells, cap);
      trim(fieldCells, cap);
      int size = Math.clamp(config.getEntropyWindow(), 0, MAX_HISTORY_SIZE);
      if (window == null || window.length != size) {
        resizeWindow(size);
      }
      entropyFilled = Math.clamp(entropyFilled, 0, window.length);
      entropyHead = window.length == 0 ? 0 : Math.floorMod(entropyHead, window.length);
      if (counts == null) {
        rebuildCounts();
      }
      if (scratch == null) {
        scratch = new Location(null, 0.0, 0.0, 0.0);
      }
    }

    private void resizeWindow(int size) {
      String[] resized = new String[size];
      int available = window == null ? 0 : Math.clamp(entropyFilled, 0, window.length);
      int retained = Math.min(size, available);
      if (retained > 0) {
        int start = Math.floorMod(entropyHead - retained, window.length);
        for (int index = 0; index < retained; index++) {
          resized[index] = window[(start + index) % window.length];
        }
      }
      window = resized;
      entropyFilled = retained;
      entropyHead = size == 0 ? 0 : retained % size;
      counts = null;
    }

    private void rebuildCounts() {
      counts = new HashMap<>();
      frequencySquares = 0L;
      for (int index = 0; index < entropyFilled; index++) {
        String key = window[index] == null ? NULL_KEY : window[index];
        window[index] = key;
        int previous = counts.getOrDefault(key, 0);
        counts.put(key, previous + 1);
        frequencySquares += 2L * previous + 1L;
      }
    }

    private double spatialVisit(String key, long now, AdaptConfig.XpIntegrity config) {
      Cell cell = touch(spatialCells, key, config.getSpatialCellCap());
      long ttl = config.getSpatialCellTtlMillis();
      if (ttl > 0 && now - cell.touch > ttl) {
        cell.count = 0;
      }
      int repeats = Math.max(0, cell.count);
      cell.count = repeats == Integer.MAX_VALUE ? repeats : repeats + 1;
      cell.touch = now;
      return Math.max(config.getSpatialFloorMultiplier(), 1.0 / (1.0 + config.getSpatialRepeatDecay() * repeats));
    }

    private int spatialPeek(String key, long now, AdaptConfig.XpIntegrity config) {
      Cell cell = spatialCells.get(key);
      long ttl = config.getSpatialCellTtlMillis();
      return cell == null || (ttl > 0 && now - cell.touch > ttl) ? 0 : cell.count;
    }

    private double entropySample(String key, long now, AdaptConfig.XpIntegrity config) {
      if (window.length <= 1) {
        return 1.0;
      }
      long ttl = config.getSpatialCellTtlMillis();
      if (ttl > 0 && now - entropyTouchedAt > ttl) {
        entropyHead = 0;
        entropyFilled = 0;
        counts.clear();
        frequencySquares = 0L;
      }
      entropyTouchedAt = now;
      if (entropyFilled == window.length) {
        String old = window[entropyHead];
        int oldCount = counts.getOrDefault(old, 0);
        frequencySquares -= 2L * oldCount - 1L;
        if (oldCount <= 1) {
          counts.remove(old);
        } else {
          counts.put(old, oldCount - 1);
        }
      } else {
        entropyFilled++;
      }
      window[entropyHead] = key;
      entropyHead = (entropyHead + 1) % window.length;
      int previous = counts.getOrDefault(key, 0);
      counts.put(key, previous + 1);
      frequencySquares += 2L * previous + 1L;
      if (entropyFilled < window.length) {
        return 1.0;
      }
      double effectiveActivities = (double) entropyFilled * entropyFilled / frequencySquares;
      double normalized = (effectiveActivities - 1.0) / (ENTROPY_SATURATION_KEYS - 1.0);
      double smooth = Math.sqrt(Math.clamp(normalized, 0.0, 1.0));
      return config.getEntropyFloorMultiplier() + (1.0 - config.getEntropyFloorMultiplier()) * smooth;
    }

    private boolean stillnessSample(Location position, long now, AdaptConfig.XpIntegrity config) {
      World world = position.getWorld();
      UUID worldId = world == null ? null : world.getUID();
      double eps = Math.max(0.0, config.getStillnessEpsilon());
      double x = position.getX();
      double y = position.getY();
      double z = position.getZ();
      if (runEvents == 0 || !Objects.equals(stillWorld, worldId)
          || now < stillTouchedAt || now - stillTouchedAt > config.getStillnessWindowMillis()) {
        beginRun(position, now);
        return false;
      }
      double nMinX = Math.min(minX, x);
      double nMaxX = Math.max(maxX, x);
      double nMinY = Math.min(minY, y);
      double nMaxY = Math.max(maxY, y);
      double nMinZ = Math.min(minZ, z);
      double nMaxZ = Math.max(maxZ, z);
      if (nMaxX - nMinX >= eps || nMaxY - nMinY >= eps || nMaxZ - nMinZ >= eps) {
        beginRun(position, now);
        return false;
      }
      minX = nMinX;
      maxX = nMaxX;
      minY = nMinY;
      maxY = nMaxY;
      minZ = nMinZ;
      maxZ = nMaxZ;
      stillTouchedAt = now;
      runEvents = runEvents == Integer.MAX_VALUE ? runEvents : runEvents + 1;
      return runEvents >= config.getStillnessMinEvents() && now - stillStartedAt >= config.getStillnessWindowMillis();
    }

    private void beginRun(Location position, long now) {
      minX = position.getX();
      maxX = minX;
      minY = position.getY();
      maxY = minY;
      minZ = position.getZ();
      maxZ = minZ;
      stillWorld = position.getWorld() == null ? null : position.getWorld().getUID();
      stillStartedAt = now;
      stillTouchedAt = now;
      runEvents = 1;
    }

    private double fieldCycle(String key, long now, AdaptConfig.XpIntegrity config) {
      Cell cell = touch(fieldCells, key, config.getSpatialCellCap());
      double result = 1.0;
      long cycle = config.getFieldCycleMillis();
      if (cell.count > 0 && cycle > 0) {
        long elapsed = Math.max(0L, now - cell.touch);
        if (elapsed < cycle) {
          double floor = config.getFieldCycleFloorMultiplier();
          result = floor + (1.0 - floor) * ((double) elapsed / cycle);
        }
      }
      cell.count = 1;
      cell.touch = now;
      return result;
    }

    private static Cell touch(LinkedHashMap<String, Cell> cells, String key, int configuredCap) {
      Cell cell = cells.remove(key);
      if (cell == null) {
        cell = new Cell();
      }
      cells.put(key, cell);
      trim(cells, Math.clamp(configuredCap, 1, MAX_HISTORY_SIZE));
      return cell;
    }

    private static void trim(LinkedHashMap<String, Cell> cells, int cap) {
      if (cells.size() <= cap) {
        return;
      }
      Iterator<Map.Entry<String, Cell>> iterator = cells.entrySet().iterator();
      while (cells.size() > cap && iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
  }
}
