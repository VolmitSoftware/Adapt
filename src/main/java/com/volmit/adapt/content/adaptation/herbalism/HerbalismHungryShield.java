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
import com.volmit.adapt.util.C;
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
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("herbalism.hungry_shield.lore1"));
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.925;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Effectiveness Base for the Herbalism Hungry Shield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double effectivenessBase = 0.15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Effectiveness for the Herbalism Hungry Shield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxEffectiveness = 0.95;
    }
}
