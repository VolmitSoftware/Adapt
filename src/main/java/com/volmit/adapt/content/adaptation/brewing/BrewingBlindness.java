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
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;


public class BrewingBlindness extends SimpleAdaptation<BrewingBlindness.Config> {
    public BrewingBlindness() {
        super("brewing-blindness");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("brewing.blindness.description"));
        setDisplayName(Localizer.dLocalize("brewing.blindness.name"));
        setIcon(Material.INK_SAC);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(1333);
        registerBrewingRecipe(BrewingRecipe.builder()
                .id("brewing-blindness-1")
                .brewingTime(320)
                .fuelCost(16)
                .ingredient(Material.INK_SAC)
                .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.AWKWARD))
                .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
                        .setName("Bottled Blindness")
                        .setColor(Color.OLIVE)
                        .addEffect(PotionEffectType.BLINDNESS, 600, 1, true, true, true)
                        .build())
                .build());
        registerBrewingRecipe(BrewingRecipe.builder()
                .id("brewing-blindness-2")
                .brewingTime(320)
                .fuelCost(32)
                .ingredient(Material.GLOW_INK_SAC)
                .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.AWKWARD))
                .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
                        .setName("Bottled Blindness 2")
                        .setColor(Color.OLIVE)
                        .addEffect(PotionEffectType.BLINDNESS, 300, 3, true, true, true)
                        .build())
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("brewing.blindness.lore"));
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
        double costFactor = 1;
        int maxLevel = 1;
        int initialCost = 2;
    }
}
