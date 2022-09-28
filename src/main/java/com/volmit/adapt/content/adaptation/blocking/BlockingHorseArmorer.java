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

package com.volmit.adapt.content.adaptation.blocking;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.recipe.MaterialChar;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BlockingHorseArmorer extends SimpleAdaptation<BlockingHorseArmorer.Config> {

    public BlockingHorseArmorer() {
        super("blocking-horsearmorer");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("blocking", "horsearmorer", "description"));
        setDisplayName(Adapt.dLocalize("blocking", "horsearmorer", "name"));
        setIcon(Material.GOLDEN_HORSE_ARMOR);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(17774);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmorerleather")
                .ingredient(new MaterialChar('I', Material.LEATHER))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.IRON_HORSE_ARMOR, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmoreriron")
                .ingredient(new MaterialChar('I', Material.IRON_INGOT))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.GOLDEN_HORSE_ARMOR, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmorergold")
                .ingredient(new MaterialChar('I', Material.GOLD_INGOT))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.GOLDEN_HORSE_ARMOR, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmorerdiamond")
                .ingredient(new MaterialChar('I', Material.DIAMOND))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.DIAMOND_HORSE_ARMOR, 1))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Adapt.dLocalize("blocking", "chainarmorer", "lore1"));
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
        int baseCost = 0;
        int maxLevel = 1;
        int initialCost = 1;
        double costFactor = 0;
    }
}
