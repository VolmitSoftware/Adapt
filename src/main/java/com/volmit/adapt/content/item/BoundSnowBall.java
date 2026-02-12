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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundSnowBall implements DataItem<BoundSnowBall.Data> {
    public static BoundSnowBall io = new BoundSnowBall();

    public static Player getPlayer(ItemStack stack) {
        if (io.getData(stack) != null) {
            return io.getData(stack).getPlayer();
        }

        return null;
    }

    public static void setData(ItemStack item, Player t) {
        io.setData(item, new Data(t));
    }

    public static ItemStack withData(Player t) {
        return io.withData(new Data(t));
    }

    public static boolean isBindableItem(ItemStack t) {
        if (t.getType().equals(Material.SNOWBALL)) {
            if (t.getItemMeta() != null && t.getItemMeta().getLore() != null) {
                if (t.getItemMeta().getLore().get(0).contains(Localizer.dLocalize("items.bound_snowball.name"))) {
                    Adapt.verbose("Snowball is bindable: " + t.getType().name());
                    return true;
                }
            }
        }
        return false;
    }


    @Override
    public Material getMaterial() {
        return Material.SNOWBALL;
    }

    @Override
    public Class<Data> getType() {
        return BoundSnowBall.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.WHITE + Localizer.dLocalize("items.bound_snowball.name"));
        lore.add(C.GRAY + Localizer.dLocalize("items.bound_snowball.usage1"));
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
        meta.setDisplayName(Localizer.dLocalize("items.bound_snowball.name"));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private Player player;

        public static BoundSnowBall.Data at(Player p) {
            return new BoundSnowBall.Data(p);
        }
    }
}
