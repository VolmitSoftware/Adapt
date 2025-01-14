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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SeaborneTurtlesVision extends SimpleAdaptation<SeaborneTurtlesVision.Config> {

    public SeaborneTurtlesVision() {
        super("seaborne-turtles-vision");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("seaborn", "nightvision", "description"));
        setDisplayName(Localizer.dLocalize("seaborn", "nightvision", "name"));
        setIcon(Material.DIAMOND_HORSE_ARMOR);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(3000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("seaborn", "nightvision", "lore1"));
    }


    @Override
    public void onTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isInWater() && hasAdaptation(player)) {
                if (player.getLocation().getBlock().isLiquid()) {
                    J.s(() -> player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 62, 0, false, false)));
                }
            }
        }
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
        final boolean permanent = false;
        final boolean enabled = true;
        final int baseCost = 5;
        final int maxLevel = 1;
        final int initialCost = 3;
        final double costFactor = 1;
    }
}
