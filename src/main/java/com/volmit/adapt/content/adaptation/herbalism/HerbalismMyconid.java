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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class HerbalismMyconid extends SimpleAdaptation<HerbalismMyconid.Config> {

    public HerbalismMyconid() {
        super("herbalism-myconid");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Herbalism", "Myconid", "Description"));
        setDisplayName(Adapt.dLocalize("Herbalism", "Myconid", "Name"));
        setIcon(Material.MYCELIUM);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(10101);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shapeless()
                .key("herbalism-dirt-myconid")
                .ingredient(Material.DIRT)
                .ingredient(Material.RED_MUSHROOM)
                .ingredient(Material.BROWN_MUSHROOM)
                .result(new ItemStack(Material.MYCELIUM, 1))
                .build());

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Adapt.dLocalize("Herbalism", "Myconid", "Lore1"));
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 8;
        int maxLevel = 1;
        int initialCost = 3;
        double costFactor = 0.75;
    }
}
