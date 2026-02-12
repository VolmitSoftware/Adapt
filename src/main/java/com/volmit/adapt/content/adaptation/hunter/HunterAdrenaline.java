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

package com.volmit.adapt.content.adaptation.hunter;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class HunterAdrenaline extends SimpleAdaptation<HunterAdrenaline.Config> {
    public HunterAdrenaline() {
        super("hunter-adrenaline");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("hunter.adrenaline.description"));
        setDisplayName(Localizer.dLocalize("hunter.adrenaline.name"));
        setIcon(Material.LEATHER_HELMET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1911);

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDamage(level), 0) + C.GRAY + " " + Localizer.dLocalize("hunter.adrenaline.lore1"));
    }

    private double getDamage(int level) {
        return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().damageBase);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && hasAdaptation(p) && getLevel((Player) e.getDamager()) > 0) {
            double damageMax = getDamage(getLevel(p));
            double hpp = ((Player) e.getDamager()).getHealth() / ((Player) e.getDamager()).getMaxHealth();

            if (hpp >= 1) {
                return;
            }

            damageMax *= (1D - hpp);
            e.setDamage(e.getDamage() * (damageMax + 1D));
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Base for the Hunter Adrenaline adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBase = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Hunter Adrenaline adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageFactor = 0.21;
    }
}
