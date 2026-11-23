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
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.event.block.BlockFertilizeEvent;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.block.BlockFromToEvent;
import art.arcane.adapt.platform.event.block.BlockGrowEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.StructureGrowEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Set;

/**
 * Keeps block placement provenance current. Register one instance on the core event bus.
 */
public class XpProvenanceListener {
  private static final int FERTILIZE_BLOCK_CAP = 64;
  private static final Id AIR = Id.minecraft("air");
  private static final Id DRAGON_EGG = Id.minecraft("dragon_egg");
  private static final Id FALLING_BLOCK = Id.minecraft("falling_block");
  private static final Set<Id> FLUID_FORMED = Set.of(Id.minecraft("cobblestone"), Id.minecraft("stone"),
      Id.minecraft("basalt"), Id.minecraft("obsidian"));
  private static final DataKey<Byte> PLAYER_MODIFIED_FALLING_BLOCK_KEY =
      DataKey.adapt("xp-provenance-player-modified-falling-block", DataCodecs.BYTE);

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    XpProvenance.recordPlacement(e.block());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    XpProvenance.recordBreak(e.block());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockFormEvent e) {
    if (FLUID_FORMED.contains(e.newState().type())) {
      XpProvenance.recordPlacement(e.block());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CreatureSpawnEvent e) {
    MobOrigin.record(e.entity(), e.reason());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockFertilizeEvent e) {
    if (!AdaptConfig.get().getXpIntegrity().isBonemealTrackingEnabled()) {
      return;
    }

    List<BlockChange> blocks = e.blocks();
    int limit = Math.min(blocks.size(), FERTILIZE_BLOCK_CAP);
    for (int i = 0; i < limit; i++) {
      XpProvenance.recordBonemeal(blocks.get(i).block());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(StructureGrowEvent e) {
    BlockRef sapling = e.origin();
    for (BlockChange grown : e.blocks()) {
      if (XpProvenance.resetsOnGrowth(grown.state().type()) || atSapling(grown.block(), sapling)) {
        XpProvenance.forgetPlacement(grown.block());
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockGrowEvent e) {
    if (!e.block().type().equals(e.newState().type())) {
      XpProvenance.forgetPlacement(e.block());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPistonEvent e) {
    XpProvenance.transferPistonMovement(e.blocks(), e.movement());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockFromToEvent e) {
    if (!e.block().type().equals(DRAGON_EGG)) {
      return;
    }
    XpProvenance.transferBlockMovement(e.block(), e.toBlock());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityChangeBlockEvent e) {
    if (!e.entity().type().equals(FALLING_BLOCK)) {
      return;
    }
    DataHolder data = e.entity().data();
    if (e.to().type().equals(AIR)) {
      if (XpProvenance.hasPermanentPlayerModification(e.block())) {
        data.set(PLAYER_MODIFIED_FALLING_BLOCK_KEY, (byte) 1);
      } else {
        data.remove(PLAYER_MODIFIED_FALLING_BLOCK_KEY);
      }
      XpProvenance.clearPlacementRecord(e.block());
      return;
    }
    if (data.has(PLAYER_MODIFIED_FALLING_BLOCK_KEY)) {
      XpProvenance.recordPlacement(e.block());
    } else {
      XpProvenance.clearPlacementRecord(e.block());
    }
  }

  private static boolean atSapling(BlockRef grown, BlockRef sapling) {
    return grown.x() == sapling.x() && grown.y() == sapling.y() && grown.z() == sapling.z();
  }
}
