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

package com.volmit.adapt.content.adaptation.axe;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class AxeCraftLogSwap extends SimpleAdaptation<AxeCraftLogSwap.Config> {

    public AxeCraftLogSwap() {
        super("axe-logswap");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("axe", "logswap", "description"));
        setDisplayName(Adapt.dLocalize("axe", "logswap", "name"));
        setIcon(Material.MUDDY_MANGROVE_ROOTS);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(17773);

        //Birch -> Types
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapbirchoak")
                .ingredient(Material.BIRCH_LOG)
                .ingredient(Material.OAK_SAPLING)
                .result(new ItemStack(Material.OAK_PLANKS, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapbirchacacia")
                .ingredient(Material.BIRCH_LOG)
                .ingredient(Material.ACACIA_SAPLING)
                .result(new ItemStack(Material.ACACIA_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapbirchdarkoak")
                .ingredient(Material.BIRCH_LOG)
                .ingredient(Material.DARK_OAK_SAPLING)
                .result(new ItemStack(Material.DARK_OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapbirchjungle")
                .ingredient(Material.BIRCH_LOG)
                .ingredient(Material.JUNGLE_SAPLING)
                .result(new ItemStack(Material.JUNGLE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapbirchspruce")
                .ingredient(Material.BIRCH_LOG)
                .ingredient(Material.SPRUCE_SAPLING)
                .result(new ItemStack(Material.SPRUCE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapbirchmangrove")
                .ingredient(Material.BIRCH_LOG)
                .ingredient(Material.MANGROVE_PROPAGULE)
                .result(new ItemStack(Material.MANGROVE_LOG, 1))
                .build());

        //Oak -> Types
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapoakbirch")
                .ingredient(Material.OAK_LOG)
                .ingredient(Material.BIRCH_SAPLING)
                .result(new ItemStack(Material.BIRCH_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapoakacacia")
                .ingredient(Material.OAK_LOG)
                .ingredient(Material.ACACIA_SAPLING)
                .result(new ItemStack(Material.ACACIA_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapoakdarkoak")
                .ingredient(Material.OAK_LOG)
                .ingredient(Material.DARK_OAK_SAPLING)
                .result(new ItemStack(Material.DARK_OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapoakjungle")
                .ingredient(Material.OAK_LOG)
                .ingredient(Material.JUNGLE_SAPLING)
                .result(new ItemStack(Material.JUNGLE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapoakspruce")
                .ingredient(Material.OAK_LOG)
                .ingredient(Material.SPRUCE_SAPLING)
                .result(new ItemStack(Material.SPRUCE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapoakmangrove")
                .ingredient(Material.OAK_LOG)
                .ingredient(Material.MANGROVE_PROPAGULE)
                .result(new ItemStack(Material.MANGROVE_LOG, 1))
                .build());

        //Acacia -> Types
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapacaciabirch")
                .ingredient(Material.ACACIA_LOG)
                .ingredient(Material.BIRCH_SAPLING)
                .result(new ItemStack(Material.BIRCH_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapacaciaoak")
                .ingredient(Material.ACACIA_LOG)
                .ingredient(Material.OAK_SAPLING)
                .result(new ItemStack(Material.OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapacaciadarkoak")
                .ingredient(Material.ACACIA_LOG)
                .ingredient(Material.DARK_OAK_SAPLING)
                .result(new ItemStack(Material.DARK_OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapacaciajungle")
                .ingredient(Material.ACACIA_LOG)
                .ingredient(Material.JUNGLE_SAPLING)
                .result(new ItemStack(Material.JUNGLE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapacaciaspruce")
                .ingredient(Material.ACACIA_LOG)
                .ingredient(Material.SPRUCE_SAPLING)
                .result(new ItemStack(Material.SPRUCE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapacaciamangrove")
                .ingredient(Material.ACACIA_LOG)
                .ingredient(Material.MANGROVE_PROPAGULE)
                .result(new ItemStack(Material.MANGROVE_LOG, 1))
                .build());

        //Dark Oak -> Types
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapdarkoakbirch")
                .ingredient(Material.DARK_OAK_LOG)
                .ingredient(Material.BIRCH_SAPLING)
                .result(new ItemStack(Material.BIRCH_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapdarkoakoak")
                .ingredient(Material.DARK_OAK_LOG)
                .ingredient(Material.OAK_SAPLING)
                .result(new ItemStack(Material.OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapdarkoakacacia")
                .ingredient(Material.DARK_OAK_LOG)
                .ingredient(Material.ACACIA_SAPLING)
                .result(new ItemStack(Material.ACACIA_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapdarkoakjungle")
                .ingredient(Material.DARK_OAK_LOG)
                .ingredient(Material.JUNGLE_SAPLING)
                .result(new ItemStack(Material.JUNGLE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapdarkoakspruce")
                .ingredient(Material.DARK_OAK_LOG)
                .ingredient(Material.SPRUCE_SAPLING)
                .result(new ItemStack(Material.SPRUCE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapdarkoakmangrove")
                .ingredient(Material.DARK_OAK_LOG)
                .ingredient(Material.MANGROVE_PROPAGULE)
                .result(new ItemStack(Material.MANGROVE_LOG, 1))
                .build());

        //Jungle -> Types
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapjunglebirch")
                .ingredient(Material.JUNGLE_LOG)
                .ingredient(Material.BIRCH_SAPLING)
                .result(new ItemStack(Material.BIRCH_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapjungleoak")
                .ingredient(Material.JUNGLE_LOG)
                .ingredient(Material.OAK_SAPLING)
                .result(new ItemStack(Material.OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapjungleacacia")
                .ingredient(Material.JUNGLE_LOG)
                .ingredient(Material.ACACIA_SAPLING)
                .result(new ItemStack(Material.ACACIA_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapjungledarkoak")
                .ingredient(Material.JUNGLE_LOG)
                .ingredient(Material.DARK_OAK_SAPLING)
                .result(new ItemStack(Material.DARK_OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapjunglespruce")
                .ingredient(Material.JUNGLE_LOG)
                .ingredient(Material.SPRUCE_SAPLING)
                .result(new ItemStack(Material.SPRUCE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapjunglemangrove")
                .ingredient(Material.JUNGLE_LOG)
                .ingredient(Material.MANGROVE_PROPAGULE)
                .result(new ItemStack(Material.MANGROVE_LOG, 1))
                .build());

        //Spruce -> Types
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapsprucebirch")
                .ingredient(Material.SPRUCE_LOG)
                .ingredient(Material.BIRCH_SAPLING)
                .result(new ItemStack(Material.BIRCH_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapspruceoak")
                .ingredient(Material.SPRUCE_LOG)
                .ingredient(Material.OAK_SAPLING)
                .result(new ItemStack(Material.OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapspruceacacia")
                .ingredient(Material.SPRUCE_LOG)
                .ingredient(Material.ACACIA_SAPLING)
                .result(new ItemStack(Material.ACACIA_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapsprucedarkoak")
                .ingredient(Material.SPRUCE_LOG)
                .ingredient(Material.DARK_OAK_SAPLING)
                .result(new ItemStack(Material.DARK_OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapsprucejungle")
                .ingredient(Material.SPRUCE_LOG)
                .ingredient(Material.JUNGLE_SAPLING)
                .result(new ItemStack(Material.JUNGLE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapsprucemangrove")
                .ingredient(Material.SPRUCE_LOG)
                .ingredient(Material.MANGROVE_PROPAGULE)
                .result(new ItemStack(Material.MANGROVE_LOG, 1))
                .build());

        //Mangrove -> Types
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapmangrovebirch")
                .ingredient(Material.MANGROVE_LOG)
                .ingredient(Material.BIRCH_SAPLING)
                .result(new ItemStack(Material.BIRCH_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapmangroveoak")
                .ingredient(Material.MANGROVE_LOG)
                .ingredient(Material.OAK_SAPLING)
                .result(new ItemStack(Material.OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapmangroveacacia")
                .ingredient(Material.MANGROVE_LOG)
                .ingredient(Material.ACACIA_SAPLING)
                .result(new ItemStack(Material.ACACIA_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapmangrovedarkoak")
                .ingredient(Material.MANGROVE_LOG)
                .ingredient(Material.DARK_OAK_SAPLING)
                .result(new ItemStack(Material.DARK_OAK_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapmangrovejungle")
                .ingredient(Material.MANGROVE_LOG)
                .ingredient(Material.JUNGLE_SAPLING)
                .result(new ItemStack(Material.JUNGLE_LOG, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("axe-swapmangrovespruce")
                .ingredient(Material.MANGROVE_LOG)
                .ingredient(Material.SPRUCE_SAPLING)
                .result(new ItemStack(Material.SPRUCE_LOG, 1))
                .build());

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Adapt.dLocalize("axe", "logswap", "lore1"));
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
        int baseCost = 2;
        int maxLevel = 1;
        int initialCost = 2;
        double costFactor = 1;
    }
}