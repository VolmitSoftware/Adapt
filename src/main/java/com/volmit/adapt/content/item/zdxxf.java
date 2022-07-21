package com.volmit.adapt.content.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class zdxxf {
    public void go()
    {
        OmniTool tool = new OmniTool();

        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack shov = new ItemStack(Material.DIAMOND_SHOVEL);
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);

        ItemStack item = pick;
        // Set pickaxe to a new multitool containing a shovel and an axe
        tool.setItems(item, List.of(shov, axe));

        // switch to shovel
        item = tool.switchTo(item, 0);

        // Switch the shovel to whatever index the axe is at
        item = tool.switchTo(item, tool.getItems(item).indexOf(axe));

        // Get inner stored items, WITHOUT the current item
        List<ItemStack> innerItems = tool.getItems(item);

        // Get all stored items, WITH the current item
        List<ItemStack> allItems = tool.explode(item);

        // Get the current item WITHOUT its custom data that makes it a multitool
        ItemStack realActualItem = tool.getRealItem(item);

        // Add another tool
        tool.add(item, new ItemStack(Material.NETHERITE_SWORD));
    }
}
