package com.volmit.adapt.api.potion;

import lombok.Builder;
import lombok.Data;
import org.bukkit.inventory.ItemStack;

@Data
@Builder
public class BrewingRecipe {
    private final String id;
    private final ItemStack basePotion, ingredient, result;
    private final int brewingTime, fuelCost;
}
