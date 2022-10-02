package com.volmit.adapt.api.potion;

import com.google.common.collect.Maps;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;

import java.util.Map;

public class BrewingListener implements Listener {

    private static final Map<String, BrewingRecipe> recipes = Maps.newHashMap();
    private static final Map<Location, BrewingTask> activeTasks = Maps.newHashMap();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(e.getClickedInventory().getType() != InventoryType.BREWING)
            return;

        BrewerInventory inv = (BrewerInventory)e.getClickedInventory();
    }
}
