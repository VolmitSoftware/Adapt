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

package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class StealthSpeed extends SimpleAdaptation<StealthSpeed.Config> {
    public StealthSpeed() {
        super("stealth-speed");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Stealth", "SneakSpeed", "Description"));
        setDisplayName(Adapt.dLocalize("Stealth", "SneakSpeed", "Name"));
        setIcon(Material.MUSHROOM_STEW);
        setBaseCost(getConfig().baseCost);
        setInterval(2000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + Adapt.dLocalize("Stealth", "SneakSpeed", "Lore1"));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        double factor = getLevelPercent(p);
        if (!hasAdaptation(p)) {
            return;
        }

        if (factor == 0) {
            return;
        }
        AttributeModifier mod = new AttributeModifier("adapt-sneak-speed", getSpeed(factor), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        if (e.isSneaking()) {
            p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(mod);
        } else {
            for (AttributeModifier i : p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers()) {
                if (i.getName().equals("adapt-sneak-speed")) {
                    p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(i);
                }
            }
        }
    }

    private double getSpeed(double factor) {
        return factor * getConfig().factor;
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 2;
        int initialCost = 5;
        double costFactor = 0.6;
        double factor = 1.25;
    }
}
