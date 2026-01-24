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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class HerbalismHungryShield extends SimpleAdaptation<HerbalismHungryShield.Config> {

    public HerbalismHungryShield() {
        super("herbalism-hungry-shield");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.hungry_shield.description"));
        setDisplayName(Localizer.dLocalize("herbalism.hungry_shield.name"));
        setIcon(Material.APPLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(875);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("herbalism.hungry_shield.lore", Form.pc(getEffectiveness(getLevelPercent(level)), 0)));
    }


    @Override
    public void onTick() {

    }

    private double getEffectiveness(double factor) {
        return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Player p && hasAdaptation(p)) {
            double f = getEffectiveness(getLevelPercent(p));
            double h = e.getDamage() * f;
            double d = e.getDamage() - h;

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

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 7;
        int maxLevel = 5;
        int initialCost = 14;
        double costFactor = 0.925;
        double effectivenessBase = 0.15;
        double maxEffectiveness = 0.95;
    }
}
