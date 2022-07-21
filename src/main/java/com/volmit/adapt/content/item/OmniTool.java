package com.volmit.adapt.content.item;

import org.bukkit.inventory.ItemStack;

public class OmniTool implements MultiItem {
    @Override
    public boolean supportsItem(ItemStack itemStack) {
        return itemStack.getAmount() <= 1 && itemStack.getMaxStackSize() <= 1 && itemStack.getType().getMaxDurability() > 0;
    }

    @Override
    public String getKey() {
        return "omnitool";
    }
}
