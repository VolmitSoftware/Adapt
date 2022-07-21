package com.volmit.adapt.content.item;

import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.WindowResolution;
import lombok.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public interface MultiItem {
    boolean supportsItem(ItemStack itemStack);

    default WindowResolution getWindowType() {
        return WindowResolution.W5_H1;
    }

    default void remove(ItemStack multi, int index){
        List<ItemStack> it = getItems(multi);
        it.remove(index);
        setItems(multi, it);
    }

    default void add(ItemStack multi, ItemStack item){
        setItems(multi, getItems(multi).qadd(item));
    }

    default void switchTo(ItemStack multi, int index) {

    }

    default List<ItemStack> setItems(ItemStack multi, List<ItemStack> itemStacks) {

    }

    default List<ItemStack> getItems(ItemStack multi) {

    }

    default List<ItemStack> explode(ItemStack multi) {

    }

    default ItemStack getRealItem(ItemStack multi) {

    }

    default MultiItemData getMultiItemData(ItemStack multi) {
        if(multi.hasItemMeta())
        {
            ItemMeta meta = multi.getItemMeta();
            meta.
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class MultiItemData {
        @Singular
        List<String> rawItems;
    }
}
