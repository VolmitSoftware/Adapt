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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.List;

public class HerbalismHungryShield extends SimpleAdaptation<HerbalismHungryShield.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public HerbalismHungryShield() {
        super("herbalism-hungry-shield");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Herbalism", "HungryShield", "Description"));
        setDisplayName(Adapt.dLocalize("Herbalism", "HungryShield", "Name"));
        setIcon(Material.APPLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(875);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + " " +Adapt.dLocalize("Herbalism", "HungryShield", "Lore1"));
    }


    @Override
    public void onTick() {

    }

    private double getEffectiveness(double factor) {
        return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && getLevel((Player) e.getEntity()) > 0) {
            double f = getEffectiveness(getLevelPercent((Player) e.getEntity()));
            double h = e.getDamage() * f;
            double d = e.getDamage() - h;
            Player p = (Player) e.getEntity();

            if (getPlayer(p).consumeFood(h, 6)) {
                d += h;
                e.setDamage(d);
                xp(p, d);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 7;
        int maxLevel = 5;
        int initialCost = 14;
        double costFactor = 0.925;
        double effectivenessBase = 0.15;
        double maxEffectiveness = 0.95;
    }
}
