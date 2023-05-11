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
package com.volmit.adapt.content.adaptation.nether;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Random;

public class NetherFireResist extends SimpleAdaptation<NetherFireResist.Config> {
    private final Random random = new Random();

    public NetherFireResist() {
        super("nether-fire-resist");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether", "fireresist", "description"));
        setDisplayName(Localizer.dLocalize("nether", "fireresist", "name"));
        setIcon(Material.BLAZE_POWDER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4333);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.RED + "+ " + Form.pc(getFireResist(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether", "fireresist", "lore1"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }

        if (!(e.getEntity() instanceof Player p)) {
            return;
        }

        if (!hasAdaptation(p)) {
            return;
        }

        if (e.getCause() != EntityDamageEvent.DamageCause.FIRE && e.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) {
            return;
        }


        if (random.nextDouble() < getFireResist(getLevel(p))) {
            e.setCancelled(true);
        }
    }

    public double getFireResist(double level) {
        return getConfig().fireResistBase + (getConfig().fireResistFactor * level);
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

    @Data
    @NoArgsConstructor
    public static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 4;
        double costFactor = 0.75;
        int maxLevel = 3;
        int initialCost = 6;
        double fireResistBase = 0.0;
        double fireResistFactor = 0.3;
    }
}
