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
import com.volmit.adapt.api.recipe.MaterialChar;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CraftingBackpacks extends SimpleAdaptation<CraftingBackpacks.Config> {

    public CraftingBackpacks() {
        super("crafting-backpacks");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("crafting", "backpacks", "description"));
        setDisplayName(Localizer.dLocalize("crafting", "backpacks", "name"));
        setIcon(Material.BUNDLE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(17779);
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-backpacks")
                .ingredient(new MaterialChar('I', Material.LEATHER))
                .ingredient(new MaterialChar('L', Material.LEAD))
                .ingredient(new MaterialChar('C', Material.CHEST))
                .ingredient(new MaterialChar('X', Material.BARREL))
                .shapes(List.of(
                        "ILI",
                        "IXI",
                        "ICI"))
                .result(new ItemStack(Material.BUNDLE, 1))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting", "backpacks", "lore1"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting", "backpacks", "lore2"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting", "backpacks", "lore3"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting", "backpacks", "lore4"));

    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = true;
        final boolean enabled = true;
        final int baseCost = 5;
        final int maxLevel = 1;
        final int initialCost = 2;
        final double costFactor = 1;
    }
}
