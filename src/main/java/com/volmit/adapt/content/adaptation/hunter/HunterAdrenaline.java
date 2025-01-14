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
        setDescription(Localizer.dLocalize("hunter", "adrenaline", "description"));
        setDisplayName(Localizer.dLocalize("hunter", "adrenaline", "name"));
        setIcon(Material.LEATHER_HELMET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1911);

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDamage(level), 0) + C.GRAY + " " + Localizer.dLocalize("hunter", "adrenaline", "lore1"));
    }

    private double getDamage(int level) {
        return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().damageBase);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && hasAdaptation(p) && getLevel(p) > 0) {
            double damageMax = getDamage(getLevel(p));
            double hpp = p.getHealth() / p.getMaxHealth();

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
        final int baseCost = 4;
        final int maxLevel = 5;
        final int initialCost = 8;
        final double costFactor = 0.4;
        final double damageBase = 0.12;
        final double damageFactor = 0.21;
    }
}
