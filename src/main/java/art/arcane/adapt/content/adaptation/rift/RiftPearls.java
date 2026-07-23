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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.content.item.BoundEnderPearl;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

final class RiftPearls {
  private RiftPearls() {
  }

  static boolean isPlainPearl(ItemStack stack) {
    if (stack == null || stack.getType() != Material.ENDER_PEARL || stack.getAmount() <= 0) {
      return false;
    }
    return !isReservedPearl(stack);
  }

  private static boolean isReservedPearl(ItemStack stack) {
    if (BoundEnderPearl.isBindableItem(stack)) {
      return true;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return false;
    }
    PersistentDataContainer data = meta.getPersistentDataContainer();
    return data.has(new NamespacedKey(Adapt.instance, RiftConduit.TAGLOCK_LOC_KEY_NAME), PersistentDataType.STRING)
        || data.has(new NamespacedKey(Adapt.instance, RiftConduit.TAGLOCK_ID_KEY_NAME), PersistentDataType.STRING)
        || data.has(new NamespacedKey(Adapt.instance, RiftEnderTaglock.TARGET_KEY_NAME), PersistentDataType.STRING);
  }
}
