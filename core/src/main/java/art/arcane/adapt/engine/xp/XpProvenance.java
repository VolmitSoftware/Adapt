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

package art.arcane.adapt.engine.xp;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.data.WorldDataPort;
import art.arcane.adapt.engine.data.WorldDataPort.Placement;
import art.arcane.adapt.engine.telemetry.AdaptRuntimeTelemetry;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class XpProvenance {
  private static final Id SHROOMLIGHT = Id.minecraft("shroomlight");

  private XpProvenance() {
  }

  public static void recordPlacement(BlockRef block) {
    if (block == null) {
      return;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    WorldDataPort data = data();
    Placement stamp = data.placement(block);
    int brokenAt = stamp == null ? 0 : stamp.brokenAt();
    int bonemealedAt = stamp == null ? 0 : stamp.bonemealedAt();
    data.setPlacement(block, new Placement(nowSeconds(), brokenAt, bonemealedAt));
  }

  public static boolean isPlayerPlaced(BlockRef block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return false;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    Placement stamp = data().placement(block);
    return stamp != null && withinTtl(stamp.placedAt(), config.getPlacedBlockTtlMillis());
  }

  public static boolean wasPlaced(BlockRef block) {
    if (block == null) {
      return false;
    }
    return hasPlacement(data().placement(block));
  }

  public static boolean forgetPlacement(BlockRef block) {
    WorldDataPort data = data();
    Placement grown = grown(data.placement(block));
    if (grown == null) {
      return false;
    }
    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    data.setPlacement(block, grown);
    return true;
  }

  public static boolean hasPermanentPlayerModification(BlockRef block) {
    if (block == null) {
      return false;
    }
    return hasPermanentPlacementRecord(data().placement(block));
  }

  public static boolean hasPermanentPlacementRecord(Placement stamp) {
    return stamp != null && (stamp.placedAt() != 0 || stamp.brokenAt() != 0);
  }

  public static void transferBlockMovement(BlockRef source, BlockRef destination) {
    if (source == null || destination == null) {
      return;
    }
    WorldDataPort data = data();
    Placement stamp = data.placement(source);
    data.clearPlacement(source);
    if (stamp == null) {
      data.clearPlacement(destination);
      return;
    }
    data.setPlacement(destination, stamp);
  }

  public static boolean resetsOnGrowth(Id type) {
    String name = type.path().toUpperCase(Locale.ROOT);
    return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
        || name.endsWith("_LEAVES") || name.endsWith("_SAPLING") || name.endsWith("_PROPAGULE") || name.endsWith("_WART_BLOCK")
        || type.equals(SHROOMLIGHT);
  }

  public static void clearPlacementRecord(BlockRef block) {
    if (block == null) {
      return;
    }
    data().clearPlacement(block);
  }

  public static void transferPistonMovement(List<BlockRef> movedBlocks, BlockFace direction) {
    if (movedBlocks == null || movedBlocks.isEmpty() || direction == null) {
      return;
    }
    WorldDataPort data = data();
    int limit = Math.min(12, movedBlocks.size());
    ArrayList<PlacementMovement> movements = new ArrayList<>(limit);
    for (int index = 0; index < limit; index++) {
      BlockRef source = movedBlocks.get(index);
      if (source == null) {
        continue;
      }
      movements.add(new PlacementMovement(source, source.relative(direction), data.placement(source)));
    }
    for (PlacementMovement movement : movements) {
      data.clearPlacement(movement.source());
    }
    for (PlacementMovement movement : movements) {
      if (movement.stamp() == null) {
        data.clearPlacement(movement.destination());
      } else {
        data.setPlacement(movement.destination(), movement.stamp());
      }
    }
  }

  public static void recordBreak(BlockRef block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    WorldDataPort data = data();
    Placement stamp = data.placement(block);
    if (!hasPlacement(stamp)) {
      return;
    }
    int brokenAt = withinTtl(stamp.placedAt(), config.getPlacedBlockTtlMillis()) ? nowSeconds() : stamp.brokenAt();
    data.setPlacement(block, new Placement(0, brokenAt, stamp.bonemealedAt()));
  }

  public static boolean isReplaceDenied(BlockRef block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return false;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    Placement stamp = data().placement(block);
    return stamp != null && withinTtl(stamp.brokenAt(), config.getReplaceDenyTtlMillis());
  }

  public static void recordBonemeal(BlockRef block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isBonemealTrackingEnabled()) {
      return;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    WorldDataPort data = data();
    Placement stamp = data.placement(block);
    int placedAt = stamp == null ? 0 : stamp.placedAt();
    int brokenAt = stamp == null ? 0 : stamp.brokenAt();
    data.setPlacement(block, new Placement(placedAt, brokenAt, nowSeconds()));
  }

  public static boolean isBonemealed(BlockRef block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isBonemealTrackingEnabled()) {
      return false;
    }

    AdaptRuntimeTelemetry.recordProvenanceOp(System.currentTimeMillis());
    Placement stamp = data().placement(block);
    return stamp != null && withinTtl(stamp.bonemealedAt(), config.getBonemealTtlMillis());
  }

  public static double placeXpMultiplier(BlockRef block) {
    return isReplaceDenied(block) ? 0.0 : 1.0;
  }

  public static double breakXpMultiplier(BlockRef block) {
    return isPlayerPlaced(block) || isReplaceDenied(block) ? 0.0 : 1.0;
  }

  public static double harvestXpMultiplier(BlockRef block) {
    return isBonemealed(block) ? AdaptConfig.get().getXpIntegrity().getBonemealHarvestMultiplier() : 1.0;
  }

  private static WorldDataPort data() {
    return AdaptPorts.get(WorldDataPort.class, WorldDataPort.NONE);
  }

  private static boolean hasPlacement(Placement stamp) {
    return stamp != null && stamp.placedAt() != 0;
  }

  private static Placement grown(Placement stamp) {
    if (stamp == null || stamp.placedAt() == 0) {
      return null;
    }
    return new Placement(0, stamp.brokenAt(), stamp.bonemealedAt());
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

  private record PlacementMovement(BlockRef source, BlockRef destination, Placement stamp) {
  }
}
