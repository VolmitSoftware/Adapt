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

package com.volmit.adapt.content.adaptation.seaborrne;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SeaborneOxygen extends SimpleAdaptation<SeaborneOxygen.Config> {

    public SeaborneOxygen() {
        super("seaborne-oxygen");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("seaborn.oxygen.description"));
        setDisplayName(Localizer.dLocalize("seaborn.oxygen.name"));
        setIcon(Material.GLASS_PANE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(3750);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("seaborn.oxygen.lore", Form.pc(getAirBoost(level), 0)));
    }

    public double getAirBoost(int level) {
        return getLevelPercent(level);
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (i.getLocation().getBlock().getType() == Material.WATER && hasAdaptation(i)) {
                J.s(() -> i.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, getLevel(i) * getConfig().airPerLevelTics, getLevel(i))));
            }
        }
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
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 3;
        int maxLevel = 5;
        int initialCost = 5;
        double costFactor = 0.525;
        int airPerLevelTics = 15;
    }
}
