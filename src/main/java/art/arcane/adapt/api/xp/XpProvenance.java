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
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.telemetry.AdaptRuntimeTelemetry;
import art.arcane.volmlib.util.math.M;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

public final class XpProvenance {
  private XpProvenance() {
  }

  public static void recordPlacement(Block block) {
    if (block == null) {
      return;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    WorldData data = WorldData.of(block.getWorld());
    PlacementStamp stamp = data.get(block, PlacementStamp.class);
    int brokenAt = stamp == null ? 0 : stamp.getBrokenAt();
    int bonemealedAt = stamp == null ? 0 : stamp.getBonemealedAt();
    data.set(block, new PlacementStamp(nowSeconds(), brokenAt, bonemealedAt));
  }

  public static boolean isPlayerPlaced(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return false;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    PlacementStamp stamp = WorldData.of(block.getWorld()).get(block, PlacementStamp.class);
    return stamp != null && withinTtl(stamp.getPlacedAt(), config.getPlacedBlockTtlMillis());
  }

  public static boolean hasPermanentPlayerModification(Block block) {
    if (block == null) {
      return false;
    }
    PlacementStamp stamp = WorldData.of(block.getWorld()).get(block, PlacementStamp.class);
    return hasPermanentPlacementRecord(stamp);
  }

  public static boolean hasPermanentPlacementRecord(PlacementStamp stamp) {
    return stamp != null && (stamp.getPlacedAt() != 0 || stamp.getBrokenAt() != 0);
  }

  public static void transferBlockMovement(Block source, Block destination) {
    if (source == null || destination == null) {
      return;
    }
    WorldData sourceData = WorldData.of(source.getWorld());
    PlacementStamp stamp = sourceData.get(source, PlacementStamp.class);
    sourceData.remove(source, PlacementStamp.class);
    WorldData destinationData = WorldData.of(destination.getWorld());
    if (stamp == null) {
      destinationData.remove(destination, PlacementStamp.class);
      return;
    }
    destinationData.set(destination, stamp);
  }

  public static void clearPlacementRecord(Block block) {
    if (block == null) {
      return;
    }
    WorldData.of(block.getWorld()).remove(block, PlacementStamp.class);
  }

  public static void transferPistonMovement(List<Block> movedBlocks, BlockFace direction) {
    if (movedBlocks == null || movedBlocks.isEmpty() || direction == null) {
      return;
    }
    int limit = Math.min(12, movedBlocks.size());
    ArrayList<PlacementMovement> movements = new ArrayList<>(limit);
    for (int index = 0; index < limit; index++) {
      Block source = movedBlocks.get(index);
      if (source == null) {
        continue;
      }
      WorldData data = WorldData.of(source.getWorld());
      movements.add(new PlacementMovement(
          source,
          source.getRelative(direction),
          data.get(source, PlacementStamp.class)
      ));
    }
    for (PlacementMovement movement : movements) {
      WorldData.of(movement.source().getWorld()).remove(movement.source(), PlacementStamp.class);
    }
    for (PlacementMovement movement : movements) {
      WorldData data = WorldData.of(movement.destination().getWorld());
      if (movement.stamp() == null) {
        data.remove(movement.destination(), PlacementStamp.class);
      } else {
        data.set(movement.destination(), movement.stamp());
      }
    }
  }

  public static void recordPlayerPlacedBreak(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    WorldData data = WorldData.of(block.getWorld());
    PlacementStamp stamp = data.get(block, PlacementStamp.class);
    int bonemealedAt = stamp == null ? 0 : stamp.getBonemealedAt();
    data.set(block, new PlacementStamp(0, nowSeconds(), bonemealedAt));
  }

  public static boolean isReplaceDenied(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return false;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    PlacementStamp stamp = WorldData.of(block.getWorld()).get(block, PlacementStamp.class);
    return stamp != null && withinTtl(stamp.getBrokenAt(), config.getReplaceDenyTtlMillis());
  }

  public static void recordBonemeal(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isBonemealTrackingEnabled()) {
      return;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    WorldData data = WorldData.of(block.getWorld());
    PlacementStamp stamp = data.get(block, PlacementStamp.class);
    int placedAt = stamp == null ? 0 : stamp.getPlacedAt();
    int brokenAt = stamp == null ? 0 : stamp.getBrokenAt();
    data.set(block, new PlacementStamp(placedAt, brokenAt, nowSeconds()));
  }

  public static boolean isBonemealed(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isBonemealTrackingEnabled()) {
      return false;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    PlacementStamp stamp = WorldData.of(block.getWorld()).get(block, PlacementStamp.class);
    return stamp != null && withinTtl(stamp.getBonemealedAt(), config.getBonemealTtlMillis());
  }

  public static double placeXpMultiplier(Block block) {
    return isReplaceDenied(block) ? 0.0 : 1.0;
  }

  public static double breakXpMultiplier(Block block) {
    return isPlayerPlaced(block) || isReplaceDenied(block) ? 0.0 : 1.0;
  }

  public static double harvestXpMultiplier(Block block) {
    return isBonemealed(block) ? AdaptConfig.get().getXpIntegrity().getBonemealHarvestMultiplier() : 1.0;
  }

  private static boolean withinTtl(int stampSeconds, long ttlMillis) {
    if (stampSeconds == 0) {
      return false;
    }

    if (ttlMillis <= 0) {
      return true;
    }

    long ageMillis = (nowSeconds() - (long) stampSeconds) * 1000L;
    return ageMillis <= ttlMillis;
  }

  private static int nowSeconds() {
    return (int) (M.ms() / 1000L);
  }

  private record PlacementMovement(Block source, Block destination, PlacementStamp stamp) {
  }
}
