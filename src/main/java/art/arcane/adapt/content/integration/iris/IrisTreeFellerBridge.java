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

import art.arcane.iris.api.tree.IrisTreeFellerService;
import art.arcane.iris.api.tree.TreeFellerOptions;
import art.arcane.iris.api.tree.TreeFellerRunHooks;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;

final class IrisTreeFellerBridge {
  private IrisTreeFellerBridge() {
  }

  static boolean tryFell(BlockBreakEvent event, int durabilityPreservationChance,
                         IrisTreeFellerLink.RunHooks runHooks) {
    return tryFell(service(), event, durabilityPreservationChance, runHooks);
  }

  static boolean isManagedBreak(BlockBreakEvent event) {
    return isManagedBreak(service(), event);
  }

  static boolean isTreeBlock(Block block) {
    return isTreeBlock(service(), block);
  }

  static boolean tryFell(IrisTreeFellerService service, BlockBreakEvent event,
                         int durabilityPreservationChance, IrisTreeFellerLink.RunHooks runHooks) {
    return service != null
        && service.tryFell(event,
            TreeFellerOptions.integrationOverride(durabilityPreservationChance, adapt(runHooks)));
  }

  static boolean isManagedBreak(IrisTreeFellerService service, BlockBreakEvent event) {
    return service != null && service.isManagedBreak(event);
  }

  static boolean isTreeBlock(IrisTreeFellerService service, Block block) {
    return service != null && service.isTreeBlock(block);
  }

  private static IrisTreeFellerService service() {
    return Bukkit.getServicesManager().load(IrisTreeFellerService.class);
  }

  private static TreeFellerRunHooks adapt(IrisTreeFellerLink.RunHooks runHooks) {
    return new TreeFellerRunHooks() {
      @Override
      public void onActivationAccepted() {
        runHooks.onActivationAccepted();
      }

      @Override
      public boolean reserveLogCost() {
        return runHooks.reserveLogCost();
      }

      @Override
      public void commitLogCost() {
        runHooks.commitLogCost();
      }

      @Override
      public void refundLogCost() {
        runHooks.refundLogCost();
      }
    };
  }
}
