package com.volmit.adapt.api.potion;


import org.bukkit.inventory.ItemStack;

public record BrewingRecipe(ItemStack basePotion, ItemStack ingredient, ItemStack result, int brewingTime, int fuelCost) { }
