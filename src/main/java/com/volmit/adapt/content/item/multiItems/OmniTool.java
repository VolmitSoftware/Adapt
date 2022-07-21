package com.volmit.adapt.content.item.multiItems;

import org.bukkit.inventory.ItemStack;

public class OmniTool implements MultiItem {
    @Override
    public boolean supportsItem(ItemStack itemStack) {
        return true;
    }

    @Override
    public String getKey() {
        return "omnitool";
    }

    public ItemStack nextPickaxe(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_PICKAXE"));
    }
}
