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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.BukkitGson;
import com.volmit.adapt.util.WindowResolution;
import lombok.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface MultiItem {
    boolean supportsItem(ItemStack itemStack);

    String getKey();

    default WindowResolution getWindowType() {
        return WindowResolution.W5_H1;
    }

    default ItemStack build(ItemStack... stacks) {
        ItemStack s = stacks[0];

        for (int i = 1; i < stacks.length; i++) {
            add(s, stacks[i]);
        }

        return s;
    }

    default boolean remove(ItemStack multi, ItemStack toRemove) {
        int ind = getItems(multi).indexOf(toRemove);
        if (ind == -1) {
            return false;
        }

        remove(multi, ind);
        return true;
    }

    default void remove(ItemStack multi, int index) {
        List<ItemStack> it = getItems(multi);
        it.remove(index);
        setItems(multi, it);
    }

    default void add(ItemStack multi, ItemStack item) {
        if (isMultiItem(item)) {
            explode(item).forEach(i -> add(multi, i));
        } else {
            setItems(multi, getItems(multi).qadd(item));
        }
    }

    default ItemStack nextMatching(ItemStack item, Predicate<ItemStack> predicate) {
        List<ItemStack> items = getItems(item);
        for (int i = 0; i < items.size(); i++) {
            if (predicate.test(items[i])) {
                return switchTo(item, i);
            }
        }

        return item;
    }

    default ItemStack nextTool(ItemStack multi) {
        return switchTo(multi, 0);
    }

    default ItemStack switchTo(ItemStack multi, int index) {
        List<ItemStack> items = getItems(multi);
        ItemStack next = items.remove(index);
        items.add(getRealItem(multi));
        setItems(next, items);
        return next;
    }

    default void setItems(ItemStack multi, List<ItemStack> itemStacks) {
        setMultiItemData(multi, MultiItemData.builder()
                .rawItems(itemStacks.stream().filter(this::supportsItem)
                        .map(NMS::serializeStack)
                        .collect(Collectors.toList()))
                .build());
    }

    default List<ItemStack> getItems(ItemStack multi) {
        MultiItemData d = getMultiItemData(multi);

        if (d == null) {
            return new ArrayList<>();
        }

        return d.getItems();
    }

    default List<ItemStack> explode(ItemStack multi) {
        List<ItemStack> it = new ArrayList<>();
        it.add(getRealItem(multi));
        it.add(getItems(multi));
        return it;
    }

    void onApplyMeta(ItemStack item, ItemMeta meta, List<ItemStack> otherItems);

    default boolean isMultiItem(ItemStack item) {
        return supportsItem(item) && getMultiItemData(item) != null;
    }

    default ItemStack getRealItem(ItemStack multi) {
        ItemStack c = multi.clone();
        if (c.hasItemMeta()) {
            ItemMeta meta = c.getItemMeta();
            Objects.requireNonNull(meta).getPersistentDataContainer().remove(new NamespacedKey(Adapt.instance, getKey()));
            c.setItemMeta(meta);
        }

        return c;
    }

    default MultiItemData getMultiItemData(ItemStack multi) {
        try {
            ItemMeta meta = multi.getItemMeta();
            String st = Objects.requireNonNull(meta).getPersistentDataContainer()
                    .get(new NamespacedKey(Adapt.instance, getKey()), PersistentDataType.STRING);
            return BukkitGson.gson.fromJson(st, MultiItemData.class);
        } catch (Throwable e) {
            return null;
        }
    }

    default void setMultiItemData(ItemStack multi, MultiItemData data) {
        String s = BukkitGson.gson.toJson(data);
        ItemMeta meta = multi.getItemMeta();
        Objects.requireNonNull(meta).getPersistentDataContainer()
                .set(new NamespacedKey(Adapt.instance, getKey()), PersistentDataType.STRING, s);
        multi.setItemMeta(meta);
        meta = multi.getItemMeta();
        onApplyMeta(multi, meta, getItems(multi));
        multi.setItemMeta(meta);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class MultiItemData {
        @Singular
        List<String> rawItems;

        List<ItemStack> getItems() {
            return rawItems.stream().map(NMS::deserializeStack).collect(Collectors.toList());
        }

        void setItems(List<ItemStack> is) {
            rawItems = is.stream().map(NMS::serializeStack).collect(Collectors.toList());
        }
    }
}
