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

package com.volmit.adapt.content.item;

import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundEyeOfEnder implements DataItem<BoundEyeOfEnder.Data> {
    public static BoundEyeOfEnder io = new BoundEyeOfEnder();

    public static Location getLocation(ItemStack stack) {
        if (io.getData(stack) != null) {
            return io.getData(stack).getLocation();
        }

        return null;
    }

    public static void setData(ItemStack item, Location t) {
        io.setData(item, new Data(t));
    }

    public static ItemStack withData(Location t) {
        return io.withData(new Data(t));
    }

    @Override
    public Material getMaterial() {
        return Material.ENDER_EYE;
    }

    @Override
    public Class<Data> getType() {
        return BoundEyeOfEnder.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.WHITE + "Ocular Anchor");
        lore.add(C.LIGHT_PURPLE + "Right Click " + C.GRAY + "to consume and teleport");
        lore.add(C.LIGHT_PURPLE + "Shift + Left Click " + C.GRAY + "to bind/unbind");
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
        meta.setDisplayName("Ocular Anchor");
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private Location location;

        public static BoundEyeOfEnder.Data at(Location l) {
            return new BoundEyeOfEnder.Data(l);
        }
    }
}
