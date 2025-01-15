package com.volmit.adapt.api.potion;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BrewingManager implements Listener {

    private static final Map<BrewingRecipe, List<String>> recipes = Maps.newHashMap();
    private static final Map<Location, BrewingTask> activeTasks = Maps.newHashMap();

    public static void registerRecipe(String adaptation, BrewingRecipe recipe) {
        recipes.putIfAbsent(recipe, Lists.newArrayList(adaptation));
        recipes.computeIfPresent(recipe, (k, v) -> {
            if (!v.contains(adaptation))
                v.add(adaptation);
            return v;
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() != InventoryType.BREWING || e.getView().getTopInventory().getHolder() == null) {
            return;
        }
        Adapt.verbose("Brewing click: " + e.getRawSlot());
        BrewerInventory inv = (BrewerInventory) e.getInventory();
        boolean doTheThing = inv.getIngredient() == null
                && e.getCursor() != null
                && e.getRawSlot() == 3
                && e.getClickedInventory() != null
                && e.getClickedInventory().getType().equals(InventoryType.BREWING)
                && (e.getClick() == ClickType.LEFT);
        if (doTheThing) {
            Adapt.verbose("Brewing Stand Ingredient Clicked");
            e.setCancelled(true);
        }
        J.s(() -> {
            if (doTheThing) {
                inv.setIngredient(e.getCursor());
                e.setCursor(null);
            }
            BrewingStand stand = inv.getHolder();
            AdaptPlayer p = Adapt.instance.getAdaptServer().getPlayer((Player) e.getWhoClicked());
            Optional<BrewingRecipe> recipe = recipes.keySet().stream().filter(r -> BrewingTask.isValid(r, Objects.requireNonNull(stand).getLocation())).findFirst();
            recipe.ifPresent(r -> {
                if (activeTasks.containsKey(stand.getLocation())) {
                    BrewingTask t = activeTasks.get(stand.getLocation());
                    if (!t.getRecipe().getId().equals(r.getId())) {
                        activeTasks.remove(stand.getLocation()).cancel();
                        if (recipes.get(r).stream().noneMatch(p::hasAdaptation)) {
                            return;
                        }
                        activeTasks.put(stand.getLocation(), new BrewingTask(r, stand.getLocation()));
                    }
                } else {
                    if (recipes.get(r).stream().noneMatch(p::hasAdaptation)) {
                        return;
                    }
                    activeTasks.put(stand.getLocation(), new BrewingTask(r, stand.getLocation()));
                }
            });
            if (recipe.isEmpty() && activeTasks.containsKey(Objects.requireNonNull(stand).getLocation())) {
                activeTasks.remove(stand.getLocation()).cancel();
            }
        });
    }

    @EventHandler
    public void onBrew(BrewEvent e) {
        Material m = Objects.requireNonNull(e.getContents().getIngredient()).getType();
        if (m != Material.GUNPOWDER && m != Material.DRAGON_BREATH) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            ItemStack s = e.getContents().getItem(i);
            if (s == null) continue;
            PotionMeta meta = (PotionMeta) s.getItemMeta();
            var opt = Reflect.getEnum(PotionType.class, "UNCRAFTABLE");
            if (opt.isEmpty()) {
                Objects.requireNonNull(meta).getBasePotionData();
                continue;
            }
            if (Objects.requireNonNull(meta).getBasePotionData().getType() == opt.get())
                continue;
            ItemStack newStack = s.clone();
            if (m == Material.GUNPOWDER) {
                newStack.setType(Material.SPLASH_POTION);
            } else {
                newStack.setType(Material.LINGERING_POTION);
            }
            e.getResults().set(i, newStack);
        }
    }
}
