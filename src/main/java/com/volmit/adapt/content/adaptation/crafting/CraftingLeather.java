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

public class CraftingLeather extends SimpleAdaptation<CraftingLeather.Config> {

    public CraftingLeather() {
        super("crafting-leather");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("crafting", "leather", "description"));
        setDisplayName(Localizer.dLocalize("crafting", "leather", "name"));
        setIcon(Material.LEATHER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(17776);
        registerRecipe(AdaptRecipe.campfire()
                .key("crafting-leather")
                .ingredient(Material.ROTTEN_FLESH)
                .cookTime(100)
                .experience(1)
                .result(new ItemStack(Material.LEATHER, 1))
                .build());

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting", "leather", "lore1"));
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.ROTTEN_FLESH && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CAMPFIRE) {
            if (!hasAdaptation(e.getPlayer())) {
                e.setCancelled(true);
            }
        }
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
        int baseCost = 3;
        int maxLevel = 1;
        int initialCost = 2;
        double costFactor = 1;
    }
}