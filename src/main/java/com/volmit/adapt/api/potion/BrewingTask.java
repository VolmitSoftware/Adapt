package com.volmit.adapt.api.potion;

import com.volmit.adapt.Adapt;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class BrewingTask extends BukkitRunnable {

    private static final int DEFAULT_BREW_TIME = 400;

    @Getter private final BrewingRecipe recipe;

    private final Location location;
    private int brewTime;

    public BrewingTask(BrewingRecipe recipe, Location loc) {
        this.recipe = recipe;
        this.location = loc;
        this.brewTime = recipe.getBrewingTime();

        BrewingStand block = (BrewingStand) loc.getBlock().getState();
        if (block.getFuelLevel() > recipe.getFuelCost()) {
            block.setFuelLevel(block.getFuelLevel() - recipe.getFuelCost());
        } else {
            int rest = recipe.getFuelCost() - block.getFuelLevel();
            block.getInventory().setIngredient(decrease(block.getInventory().getFuel(), 1 + rest / 20));
            block.setFuelLevel(20 - rest % 20);
        }

        block.setBrewingTime(DEFAULT_BREW_TIME);
        block.update(true);

        runTaskTimer(Adapt.instance, 0L, 1L);
    }

    public static ItemStack decrease(ItemStack source, int amount) {
        if (source.getAmount() > amount) {
            source.setAmount(source.getAmount() - amount);
            return source;
        } else {
            return new ItemStack(Material.AIR);
        }
    }

    public static boolean isValid(BrewingRecipe recipe, Location loc) {
        BrewingStand block = (BrewingStand) loc.getBlock().getState();
        BrewerInventory inv = block.getInventory();
        if (!recipe.getIngredient().isSimilar(inv.getIngredient())) {
            return false;
        }

        int totalFuel = (inv.getFuel() != null && inv.getFuel().getType() != Material.AIR ? inv.getFuel().getAmount() * 20 : 0) + block.getFuelLevel();
        if (totalFuel < recipe.getFuelCost()) {
            return false;
        }

        for (int i = 0; i < 3; i++) {
            if (recipe.getBasePotion().isSimilar(inv.getItem(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void run() {
        BrewingStand block = (BrewingStand) this.location.getBlock().getState();
        BrewerInventory inventory = block.getInventory();
        if (brewTime <= 0) {
            inventory.setIngredient(decrease(inventory.getIngredient(), 1));

            for (int i = 0; i < 3; i++) {
                if (recipe.getBasePotion().equals(inventory.getItem(i))) {
                    inventory.setItem(i, recipe.getResult());
                }
            }

            inventory.getViewers().forEach(e -> {
                if (e instanceof Player p) {
                    p.playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1, 1);
                }
            });
            cancel();
            return;
        }
        brewTime--;
        block.setBrewingTime(getRemainingTime());
        block.update(true);
    }

    private int getRemainingTime() {
        return (int) (DEFAULT_BREW_TIME * (brewTime / (float) recipe.getBrewingTime()));
    }
}
