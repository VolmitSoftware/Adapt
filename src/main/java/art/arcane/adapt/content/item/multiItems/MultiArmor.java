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

package art.arcane.adapt.content.item.multiItems;

import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import art.arcane.adapt.util.common.inventorygui.Items;

public class MultiArmor implements MultiItem {
    @Override
    public boolean supportsItem(ItemStack itemStack) {
        return true;
    }

    @Override
    public String getKey() {
        return "multiarmor";
    }

    @Override
    public void onApplyMeta(ItemStack item, ItemMeta meta, List<ItemStack> otherItems) {
        List<String> lore = new ArrayList<>();
        lore.add("MultiArmor (" + (otherItems.size() + 1) + " Items)");
        lore.add("-> " + Form.capitalizeWords(item.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));

        for (ItemStack i : otherItems) {
            lore.add("-  " + Form.capitalizeWords(i.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        }

        meta.setLore(lore);
    }

    public ItemStack nextElytra(ItemStack item) {
        return nextMatching(item, i -> i.getType().equals(Material.ELYTRA));
    }

    public ItemStack nextChestplate(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_CHESTPLATE"));
    }


}
