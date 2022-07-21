package com.volmit.adapt.content.item.multiItems;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.BukkitGson;
import com.volmit.adapt.util.WindowResolution;
import lombok.*;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface MultiItem {
    boolean supportsItem(ItemStack itemStack);

    String getKey();

    default WindowResolution getWindowType() {
        return WindowResolution.W5_H1;
    }

    default ItemStack build(ItemStack... stacks) {
        ItemStack s = stacks[0];

        for(int i = 1; i < stacks.length; i++) {
            add(s, stacks[i]);
        }

        return s;
    }

    default boolean remove(ItemStack multi, ItemStack toRemove) {
        int ind = getItems(multi).indexOf(toRemove);
        if(ind == -1) {
            return false;
        }

        remove(multi, ind);
        return true;
    }

    default void remove(ItemStack multi, int index){
        List<ItemStack> it = getItems(multi);
        it.remove(index);
        setItems(multi, it);
    }

    default void add(ItemStack multi, ItemStack item){
        if(isMultiItem(item)) {
            explode(item).forEach(i -> add(multi, i));
        }

        else {
            setItems(multi, getItems(multi).qadd(item));
        }
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
                .rawItems(itemStacks.stream().filter(this::supportsItem).map(i -> NMS.get().serializeStack(i)).collect(Collectors.toList()))
                .build());
    }

    default List<ItemStack> getItems(ItemStack multi) {
        MultiItemData d =  getMultiItemData(multi);

        if(d == null) {
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

    default boolean isMultiItem(ItemStack item) {
        return supportsItem(item) && getMultiItemData(item) != null;
    }

    default ItemStack getRealItem(ItemStack multi) {
        ItemStack c = multi.clone();
        if(c.hasItemMeta()) {
            ItemMeta meta = c.getItemMeta();
            meta.getPersistentDataContainer().remove(new NamespacedKey(Adapt.instance, getKey()));
            c.setItemMeta(meta);
        }

        return c;
    }

    default MultiItemData getMultiItemData(ItemStack multi) {
        try {
            if(multi.hasItemMeta()) {
                ItemMeta meta = multi.getItemMeta();
                return BukkitGson.gson.fromJson(meta.getPersistentDataContainer()
                        .get(new NamespacedKey(Adapt.instance, getKey()), PersistentDataType.STRING), MultiItemData.class);
            }
        }

        catch(Throwable e) {
            return null;
        }

        return null;
    }

    default void setMultiItemData(ItemStack multi, MultiItemData data) {
        if(multi.hasItemMeta()) {
            ItemMeta meta = multi.getItemMeta();
            meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.setDisplayName("T.O.O.L");
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey(Adapt.instance, getKey()), PersistentDataType.STRING, BukkitGson.gson.toJson(data));
            multi.setItemMeta(meta);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class MultiItemData {
        @Singular
        List<String> rawItems;

        void setItems(List<ItemStack> is) {
            rawItems = is.stream().map(i -> NMS.get().serializeStack(i)).collect(Collectors.toList());
        }

        List<ItemStack> getItems() {
            return rawItems.stream().map(i -> NMS.get().deserializeStack(i)).collect(Collectors.toList());
        }
    }
}
