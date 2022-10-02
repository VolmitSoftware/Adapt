package com.volmit.adapt.api.potion;

import com.volmit.adapt.Adapt;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class BrewingTask extends BukkitRunnable {

    private final BrewingRecipe recipe;
    private final BrewerInventory inventory;
    private final BrewingStand block;

    private int brewTime;

    public BrewingTask(BrewingRecipe recipe, BrewerInventory inventory, BrewingStand block) {
        this.recipe = recipe;
        this.inventory = inventory;
        this.block = block;

        this.brewTime = recipe.brewingTime();

        if(block.getFuelLevel() > recipe.fuelCost()) {
            block.setFuelLevel(block.getFuelLevel() - recipe.fuelCost());
        } else {
            int rest = recipe.fuelCost() - block.getFuelLevel();
            inventory.setFuel(decrease(inventory.getFuel(), rest / 20));
            block.setFuelLevel(20 - rest % 20);
        }

        runTaskTimer(Adapt.instance, 0L, 1L);
    }

    @Override
    public void run() {
        if(brewTime <= 0) {
            inventory.setIngredient(decrease(inventory.getIngredient(), 1));

            for(int i = 0; i < 3; i++) {
                if(recipe.basePotion().equals(inventory.getItem(i)))
                    inventory.setItem(i, recipe.result());
            }

            inventory.getViewers().forEach(e -> {
                if(e instanceof Player p)
                    p.playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1, 1);
            });
            cancel();
            return;
        }

        brewTime--;
        block.setBrewingTime(brewTime);
        block.update(true);
    }

    private ItemStack decrease(ItemStack source, int amount) {
        if(source.getAmount() > amount) {
            source.setAmount(source.getAmount() - amount);
            return source;
        } else
            return new ItemStack(Material.AIR);
    }

    public static boolean isValid(BrewingRecipe recipe, BrewingStand block) {
        BrewerInventory inv = block.getInventory();
        if(!recipe.ingredient().equals(inv.getIngredient()))
            return false;

        int totalFuel = (inv.getFuel() != null && inv.getFuel().getType() != Material.AIR ? inv.getFuel().getAmount() * 20 : 0) + block.getFuelLevel();
        if(totalFuel < recipe.fuelCost())
            return false;

        for(int i = 0; i < 3; i++) {
            if(recipe.basePotion().equals(inv.getItem(i)))
                return true;
        }

        return false;
    }
}
