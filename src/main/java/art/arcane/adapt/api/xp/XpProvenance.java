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
import art.arcane.volmlib.util.math.M;
import org.bukkit.block.Block;

public final class XpProvenance {
  private XpProvenance() {
  }

  public static void recordPlacement(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return;
    }

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

    PlacementStamp stamp = WorldData.of(block.getWorld()).get(block, PlacementStamp.class);
    return stamp != null && withinTtl(stamp.getPlacedAt(), config.getPlacedBlockTtlMillis());
  }

  public static void recordPlayerPlacedBreak(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isProvenanceEnabled()) {
      return;
    }

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

    PlacementStamp stamp = WorldData.of(block.getWorld()).get(block, PlacementStamp.class);
    return stamp != null && withinTtl(stamp.getBrokenAt(), config.getReplaceDenyTtlMillis());
  }

  public static void recordBonemeal(Block block) {
    AdaptConfig.XpIntegrity config = AdaptConfig.get().getXpIntegrity();
    if (!config.isBonemealTrackingEnabled()) {
      return;
    }

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
}
