/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.content.adaptation.crafting;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;


public class CraftingReconstruction extends SimpleAdaptation<CraftingReconstruction.Config> {
    public CraftingReconstruction() {
        super("crafting-reconstruction");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("crafting", "reconstruction", "description"));
        setDisplayName(Localizer.dLocalize("crafting", "reconstruction", "name"));
        setIcon(Material.COAL_ORE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(80248);
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-iron-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .result(new ItemStack(Material.IRON_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-gold-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .result(new ItemStack(Material.GOLD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-copper-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .result(new ItemStack(Material.COPPER_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-lapis-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .result(new ItemStack(Material.LAPIS_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-redstone-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .result(new ItemStack(Material.REDSTONE_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-emerald-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .result(new ItemStack(Material.EMERALD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-diamond-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .result(new ItemStack(Material.DIAMOND_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-coal-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .result(new ItemStack(Material.COAL_ORE))
                .build());

        // Use Deepslate
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-iron-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .result(new ItemStack(Material.DEEPSLATE_IRON_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-gold-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .result(new ItemStack(Material.DEEPSLATE_GOLD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-copper-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .result(new ItemStack(Material.DEEPSLATE_COPPER_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-lapis-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .result(new ItemStack(Material.DEEPSLATE_LAPIS_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-redstone-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .result(new ItemStack(Material.DEEPSLATE_REDSTONE_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-emerald-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .result(new ItemStack(Material.DEEPSLATE_EMERALD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-diamond-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .result(new ItemStack(Material.DEEPSLATE_DIAMOND_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-deepslate-coal-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .result(new ItemStack(Material.DEEPSLATE_COAL_ORE))
                .build());

// Use Nether Bricks
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-nether-gold-ore")
                .ingredient(Material.NETHER_BRICKS)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .result(new ItemStack(Material.NETHER_GOLD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-nether-quartz-ore")
                .ingredient(Material.NETHER_BRICKS)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .result(new ItemStack(Material.NETHER_QUARTZ_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("reconstruction-ancient-debris")
                .ingredient(Material.NETHER_BRICKS)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .result(new ItemStack(Material.ANCIENT_DEBRIS))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("crafting", "reconstruction", "lore1"));
        v.addLore(C.UNDERLINE + Localizer.dLocalize("crafting", "reconstruction", "lore2"));
        v.addLore(C.YELLOW + Localizer.dLocalize("crafting", "reconstruction", "lore3"));
        v.addLore(C.YELLOW + Localizer.dLocalize("crafting", "reconstruction", "lore4"));
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {

    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = true;
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 1;
        int initialCost = 2;
        double costFactor = 1;
    }
}