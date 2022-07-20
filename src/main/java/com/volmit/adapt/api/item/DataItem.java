package com.volmit.adapt.api.item;

import com.volmit.adapt.nms.NMS;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        if(stack != null && stack.getType().equals(getMaterial()) && stack.getItemMeta() != null) {
            return NMS.get().readItemData(stack, getType());
        }
        return null;
    }

    default ItemStack setData(ItemStack item, T t) {
        return NMS.get().writeItemData(item, t);
    }


    default ItemStack withData(T t) {
        ItemStack item = blank();
        ItemMeta meta = item.getItemMeta();

        if(meta == null) {
            return null;
        }

        applyMeta(t, meta);
        List<String> lore = new ArrayList<>();
        applyLore(t, lore);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return setData(item, t);
    }
}
