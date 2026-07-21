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

package art.arcane.adapt.content.integration.iris;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.PluginManager;

public final class IrisTreeFellerLink {
  private IrisTreeFellerLink() {
  }

  public static boolean isAvailable() {
    return isAvailable(Bukkit.getPluginManager());
  }

  public static boolean isAvailable(PluginManager pluginManager) {
    return pluginManager.isPluginEnabled("Iris");
  }

  public static boolean tryFell(BlockBreakEvent event, int durabilityPreservationChance,
                                RunHooks runHooks) {
    return isAvailable()
        && IrisTreeFellerBridge.tryFell(event, durabilityPreservationChance, runHooks);
  }

  public static boolean isManagedBreak(BlockBreakEvent event) {
    return isAvailable() && IrisTreeFellerBridge.isManagedBreak(event);
  }

  public static boolean isTreeBlock(Block block) {
    return isAvailable() && IrisTreeFellerBridge.isTreeBlock(block);
  }

  public interface RunHooks {
    void onActivationAccepted();

    boolean reserveLogCost();

    void commitLogCost();

    void refundLogCost();
  }
}
