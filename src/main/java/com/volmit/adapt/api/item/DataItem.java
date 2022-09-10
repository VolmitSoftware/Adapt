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

package com.volmit.adapt.api.item;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.BukkitGson;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public interface DataItem<T> {
    Material getMaterial();

    Class<T> getType();

    void applyLore(T data, List<String> lore);

    void applyMeta(T data, ItemMeta meta);

    default ItemStack blank() {
        return new ItemStack(getMaterial());
    }

    default T getData(ItemStack stack) {
        if (stack != null
                && stack.getType().equals(getMaterial())
                && stack.getItemMeta() != null) {
            String r = stack.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(Adapt.instance, getType().getCanonicalName().hashCode() + ""), PersistentDataType.STRING);
            if (r != null) {
                return BukkitGson.gson.fromJson(r, getType());
            }
        }

        return null;
    }

    default void setData(ItemStack item, T t) {
        item.setItemMeta(withData(t).getItemMeta());
    }

    default ItemStack withData(T t) {
        ItemStack item = blank();
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return null;
        }

        applyMeta(t, meta);
        List<String> lore = new ArrayList<>();
        applyLore(t, lore);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(Adapt.instance, getType().getCanonicalName().hashCode() + ""), PersistentDataType.STRING, BukkitGson.gson.toJson(t));
        item.setItemMeta(meta);
        return item;
    }
}
