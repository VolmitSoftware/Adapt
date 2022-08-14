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

package com.volmit.adapt.content.item.multiItems;

import com.volmit.adapt.util.Form;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class OmniTool implements MultiItem {
    @Override
    public boolean supportsItem(ItemStack itemStack) {
        return true;
    }

    @Override
    public String getKey() {
        return "omnitool";
    }

    @Override
    public void onApplyMeta(ItemStack item, ItemMeta meta, List<ItemStack> otherItems) {
        List<String> lore = new ArrayList<>();
        lore.add("Leatherman (" + (otherItems.size() + 1) + " Items)");
        lore.add("-> " + Form.capitalizeWords(item.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));

        for(ItemStack i : otherItems) {
            lore.add("-  " + Form.capitalizeWords(i.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        }

        meta.setLore(lore);
    }

    public ItemStack nextPickaxe(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_PICKAXE"));
    }

    public ItemStack nextAxe(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_AXE"));
    }

    public ItemStack nextSword(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_SWORD"));
    }

    public ItemStack nextShovel(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_SHOVEL"));
    }

    public ItemStack nextHoe(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_HOE"));
    }

    public ItemStack nextShears(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("SHEARS"));
    }

    public ItemStack nextFnS(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("FLINT_AND_STEEL"));
    }

    public ItemStack nextItem(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_PICKAXE") || i.getType().name().endsWith("_AXE") || i.getType().name().endsWith("_SWORD") || i.getType().name().endsWith("_SHOVEL") || i.getType().name().endsWith("_HOE") || i.getType().name().endsWith("SHEARS"));
    }

    public ItemStack nextNonMatchingItem(ItemStack item, Material material) {
        if (material.toString().contains("_PICKAXE")) {
            return nextAxe(item);
        } else if (material.toString().contains("_AXE")) {
            return nextSword(item);
        } else if (material.toString().contains("_SWORD")) {
            return nextShovel(item);
        } else if (material.toString().contains("_SHOVEL")) {
            return nextHoe(item);
        } else if (material.toString().contains("_HOE")) {
            return nextShears(item);
        } else if (material.toString().contains("SHEARS")) {
            return nextPickaxe(item);
        } else if (material.toString().contains("FLINT_AND_STEEL")) {
            return nextFnS(item);
        } else {
            return nextItem(item);
        }
    }
}
