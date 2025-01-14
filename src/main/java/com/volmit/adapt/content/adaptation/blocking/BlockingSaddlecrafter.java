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

public class BlockingSaddlecrafter extends SimpleAdaptation<BlockingSaddlecrafter.Config> {

    public BlockingSaddlecrafter() {
        super("blocking-saddlecrafter");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("blocking", "saddlecrafter", "description"));
        setDisplayName(Localizer.dLocalize("blocking", "saddlecrafter", "name"));
        setIcon(Material.SADDLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(17774);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-saddlecrafter")
                .ingredient(new MaterialChar('I', Material.LEATHER))
                .shapes(List.of(
                        "I I",
                        "III"))
                .result(new ItemStack(Material.SADDLE, 1))
                .build());

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("blocking", "saddlecrafter", "lore1"));
        v.addLore("X-X");
        v.addLore("XXX");
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
        final int initialCost = 1;
        final double costFactor = 0;
    }
}
