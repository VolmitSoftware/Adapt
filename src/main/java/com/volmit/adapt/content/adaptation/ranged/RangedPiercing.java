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

package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.ArrayList;
import java.util.List;

public class RangedPiercing extends SimpleAdaptation<RangedPiercing.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public RangedPiercing() {
        super("ranged-piercing");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged", "arrowpiercing", "description"));
        setDisplayName(Localizer.dLocalize("ranged", "arrowpiercing", "name"));
        setIcon(Material.SHEARS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(4791);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + level + C.GRAY + " " + Localizer.dLocalize("ranged", "arrowpiercing", "lore1"));
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity().getShooter() instanceof Player p) {
            if (e.getEntity() instanceof AbstractArrow a) {
                xp(p, 5);
                if (hasAdaptation(p)) {
                    a.setPierceLevel(((AbstractArrow) e.getEntity()).getPierceLevel() + getLevel(p));
                }
            }
        }
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
        final boolean permanent = false;
        final boolean enabled = true;
        final int baseCost = 3;
        final int maxLevel = 5;
        final int initialCost = 8;
        final double costFactor = 0.5;
    }
}
