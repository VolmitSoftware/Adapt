package com.volmit.adapt.content.item.multiItems;

import com.volmit.adapt.util.Form;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class OmniTool implements MultiItem {
    @Override
    public boolean supportsItem(ItemStack itemStack) {
        return true;
    }

    @Override
    public String getKey() {
        return "omnitool";
    }

    @Override
    public void onApplyMeta(ItemStack item, ItemMeta meta, List<ItemStack> otherItems) {
        List<String> lore = new ArrayList<>();
        lore.add("Omnitool (" + (otherItems.size() + 1) + " Items)");
        lore.add("-> " + Form.capitalizeWords(item.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));

        for(ItemStack i : otherItems) {
            lore.add("-  " + Form.capitalizeWords(i.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        }

        meta.setLore(lore);
    }

    public ItemStack nextPickaxe(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_PICKAXE"));
    }

    public ItemStack nextAxe(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_AXE"));
    }

    public ItemStack nextSword(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_SWORD"));
    }

    public ItemStack nextShovel(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_SHOVEL"));
    }

    public ItemStack nextHoe(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("_HOE"));
    }

    public ItemStack nextShears(ItemStack item) {
        return nextMatching(item, i -> i.getType().name().endsWith("SHEARS"));
    }

}
