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
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.registries.ItemFlags;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;

@AllArgsConstructor
@Data
public class ChronoTimeBottle implements DataItem<ChronoTimeBottle.Data> {
    public static ChronoTimeBottle io = new ChronoTimeBottle();

    public static boolean isBindableItem(ItemStack stack) {
        return stack != null && stack.getType() == Material.POTION && io.hasData(stack);
    }

    public static double getStoredSeconds(ItemStack stack) {
        Data data = io.getData(stack);
        return data == null ? 0 : Math.max(0, data.getStoredSeconds());
    }

    public static void setStoredSeconds(ItemStack stack, double seconds) {
        io.setData(stack, new Data(Math.max(0, seconds)));
    }

    public static ItemStack withStoredSeconds(double seconds) {
        return io.withData(new Data(Math.max(0, seconds)));
    }

    @Override
    public Material getMaterial() {
        return Material.POTION;
    }

    @Override
    public Class<Data> getType() {
        return Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.WHITE + Localizer.dLocalize("items.chrono_time_bottle.name"));
        lore.add(C.GRAY + Localizer.dLocalize("items.chrono_time_bottle.usage1"));
        lore.add(C.GRAY + Localizer.dLocalize("items.chrono_time_bottle.usage2"));
        lore.add(C.AQUA + Localizer.dLocalize("items.chrono_time_bottle.stored") + ": " + C.WHITE + Form.duration((long) (Math.max(0, data.getStoredSeconds()) * 1000D), 1));
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(PotionType.WATER);
            potionMeta.setColor(Color.fromRGB(235, 245, 255));
            meta = potionMeta;
        }

        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlags.HIDE_POTION_EFFECTS);
        meta.setDisplayName(Localizer.dLocalize("items.chrono_time_bottle.name"));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private double storedSeconds;
    }
}
