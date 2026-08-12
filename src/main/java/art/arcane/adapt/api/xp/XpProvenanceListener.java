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
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

public class XpProvenanceListener implements Listener {
  private static final int FERTILIZE_BLOCK_CAP = 64;

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    if (ProtectionEventProbe.isActive(e)) {
      return;
    }
    XpProvenance.recordPlacement(e.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (ProtectionEventProbe.isActive(e)) {
      return;
    }
    if (!AdaptConfig.get().getXpIntegrity().isProvenanceEnabled()) {
      return;
    }

    if (XpProvenance.isPlayerPlaced(e.getBlock())) {
      XpProvenance.recordPlayerPlacedBreak(e.getBlock());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFertilizeEvent e) {
    if (!AdaptConfig.get().getXpIntegrity().isBonemealTrackingEnabled()) {
      return;
    }

    List<BlockState> blocks = e.getBlocks();
    int limit = Math.min(blocks.size(), FERTILIZE_BLOCK_CAP);
    for (int i = 0; i < limit; i++) {
      XpProvenance.recordBonemeal(blocks.get(i).getBlock());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent e) {
    XpProvenance.transferPistonMovement(e.getBlocks(), e.getDirection());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent e) {
    XpProvenance.transferPistonMovement(e.getBlocks(), e.getDirection().getOppositeFace());
  }
}
