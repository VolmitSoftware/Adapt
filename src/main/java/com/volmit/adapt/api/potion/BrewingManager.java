package com.volmit.adapt.api.potion;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BrewingManager implements Listener {

    private static final Map<BrewingRecipe, List<String>> recipes = Maps.newHashMap();
    private static final Map<Location, BrewingTask> activeTasks = Maps.newHashMap();

    public static void registerRecipe(String adaptation, BrewingRecipe recipe) {
        recipes.putIfAbsent(recipe, Lists.newArrayList(adaptation));
        recipes.computeIfPresent(recipe, (k, v) -> {
            if(!v.contains(adaptation))
                v.add(adaptation);
            return v;
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(e.getView().getTopInventory().getType() != InventoryType.BREWING || e.getView().getTopInventory().getHolder() == null)
            return;

        BrewerInventory inv = (BrewerInventory)e.getView().getTopInventory();
        boolean doTheThing = inv.getIngredient() == null && e.getCursor() != null && e.getSlot() == 3 && e.getClick() == ClickType.LEFT;
        if(doTheThing)
            e.setCancelled(true);

        J.s(() -> {
            if(doTheThing) {
                inv.setIngredient(e.getCursor());
                e.setCursor(null);
            }
            BrewingStand stand = inv.getHolder();
            AdaptPlayer p = Adapt.instance.getAdaptServer().getPlayer((Player)e.getWhoClicked());
            Optional<BrewingRecipe> recipe = recipes.keySet().stream().filter(r -> BrewingTask.isValid(r, stand)).findFirst();
            recipe.ifPresent(r -> {
                if(activeTasks.containsKey(stand.getLocation())) {
                    BrewingTask t = activeTasks.get(stand.getLocation());
                    if(!t.getRecipe().getId().equals(r.getId())) {
                        activeTasks.remove(stand.getLocation()).cancel();
                        if(recipes.get(r).stream().noneMatch(p::hasAdaptation))
                            return;
                        activeTasks.put(stand.getLocation(), new BrewingTask(r, inv, stand));
                    }
                } else {
                    if(recipes.get(r).stream().noneMatch(p::hasAdaptation))
                        return;
                    activeTasks.put(stand.getLocation(), new BrewingTask(r, inv, stand));
                }
            });
            if(recipe.isEmpty() && activeTasks.containsKey(stand.getLocation())) {
                activeTasks.remove(stand.getLocation()).cancel();
            }
        });
    }
}
