package com.volmit.adapt.api.potion;

import lombok.Builder;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Data
@Builder
public class BrewingRecipe {
    private final String id;
    private final Material ingredient;
    private final ItemStack basePotion, result;
    private final int brewingTime, fuelCost;
}
