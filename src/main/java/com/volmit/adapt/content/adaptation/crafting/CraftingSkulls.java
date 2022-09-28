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

public class CraftingSkulls extends SimpleAdaptation<CraftingSkulls.Config> {

    public CraftingSkulls() {
        super("crafting-skulls");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("crafting", "skulls", "description"));
        setDisplayName(Adapt.dLocalize("crafting", "skulls", "name"));
        setIcon(Material.LEATHER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(17776);
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-skeletonskull")
                .ingredient(new MaterialChar('I', Material.BONE))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.SKELETON_SKULL, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-witherskeletonskull")
                .ingredient(new MaterialChar('I', Material.NETHER_BRICK))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.WITHER_SKELETON_SKULL, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-zombieskull")
                .ingredient(new MaterialChar('I', Material.ROTTEN_FLESH))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.ZOMBIE_HEAD, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-creeperhead")
                .ingredient(new MaterialChar('I', Material.GUNPOWDER))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.CREEPER_HEAD, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-dragonhead")
                .ingredient(new MaterialChar('I', Material.DRAGON_BREATH))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.DRAGON_HEAD, 1))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Adapt.dLocalize("crafting", "skulls", "lore1"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Adapt.dLocalize("crafting", "skulls", "lore2"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Adapt.dLocalize("crafting", "skulls", "lore3"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Adapt.dLocalize("crafting", "skulls", "lore4"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Adapt.dLocalize("crafting", "skulls", "lore5"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Adapt.dLocalize("crafting", "skulls", "lore6"));
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