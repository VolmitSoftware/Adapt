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

package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.recipe.MaterialChar;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class HerbalismCraftableMushroomBlocks extends SimpleAdaptation<HerbalismCraftableMushroomBlocks.Config> {

    public HerbalismCraftableMushroomBlocks() {
        super("herbalism-mushroom-blocks");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism", "mushroomblocks", "description"));
        setDisplayName(Localizer.dLocalize("herbalism", "mushroomblocks", "name"));
        setIcon(Material.CRIMSON_FUNGUS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(17772);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shaped()
                .key("herbalism-redmushblock")
                .ingredient(new MaterialChar('I', Material.RED_MUSHROOM))
                .shapes(List.of(
                        "II",
                        "II"))
                .result(new ItemStack(Material.RED_MUSHROOM_BLOCK, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("herbalism-brownmushblock")
                .ingredient(new MaterialChar('I', Material.BROWN_MUSHROOM))
                .shapes(List.of(
                        "II",
                        "II"))
                .result(new ItemStack(Material.BROWN_MUSHROOM_BLOCK, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("herbalism-mushstemred")
                .ingredient(Material.RED_MUSHROOM_BLOCK)
                .result(new ItemStack(Material.MUSHROOM_STEM, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("herbalism-mushstembrown")
                .ingredient(Material.BROWN_MUSHROOM_BLOCK)
                .result(new ItemStack(Material.MUSHROOM_STEM, 1))
                .build());

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("herbalism", "mushroomblocks", "lore1"));
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
        int baseCost = 4;
        int maxLevel = 1;
        int initialCost = 2;
        double costFactor = 1;
    }
}
