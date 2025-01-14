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

package com.volmit.adapt.content.adaptation.brewing;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.potion.BrewingRecipe;
import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;


public class BrewingDecay extends SimpleAdaptation<BrewingDecay.Config> {
    public BrewingDecay() {
        super("brewing-decay");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("brewing", "decay", "description"));
        setDisplayName(Localizer.dLocalize("brewing", "decay", "name"));
        setIcon(Material.WITHER_ROSE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(1334);
        registerBrewingRecipe(BrewingRecipe.builder()
                .id("brewing-decay-1")
                .brewingTime(320)
                .fuelCost(16)
                .ingredient(Material.POISONOUS_POTATO)
                .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.WEAKNESS, false, false))
                .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
                        .setName("Bottled Decay")
                        .setColor(Color.MAROON)
                        .addEffect(PotionEffectType.WITHER, 300, 1, true, true, true)
                        .build())
                .build());
        registerBrewingRecipe(BrewingRecipe.builder()
                .id("brewing-decay-2")
                .brewingTime(320)
                .fuelCost(32)
                .ingredient(Material.CRIMSON_ROOTS)
                .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.WEAKNESS, false, false))
                .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
                        .setName("Bottled Decay 2")
                        .setColor(Color.MAROON)
                        .addEffect(PotionEffectType.WITHER, 150, 2, true, true, true)
                        .build())
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing", "decay", "lore1"));
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing", "decay", "lore2"));
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
        final int baseCost = 3;
        final double costFactor = 1;
        final int maxLevel = 1;
        final int initialCost = 2;
    }
}
