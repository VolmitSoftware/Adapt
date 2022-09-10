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
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.Collection;

public class StealthGhostArmor extends SimpleAdaptation<StealthGhostArmor.Config> {
    public StealthGhostArmor() {
        super("stealth-ghost-armor");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Stealth","GhostArmor", "Description"));
        setDisplayName(Adapt.dLocalize("Stealth","GhostArmor", "Name"));
        setIcon(Material.NETHERITE_CHESTPLATE);
        setInterval(5353);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getMaxArmorPoints(getLevelPercent(level)), 0) + C.GRAY + " " +Adapt.dLocalize("Stealth","GhostArmor", "Lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getMaxArmorPerTick(getLevelPercent(level)), 1) + C.GRAY + " " +Adapt.dLocalize("Stealth","GhostArmor", "Lore2"));
    }

    public double getMaxArmorPoints(double factor) {
        return M.lerp(getConfig().minArmor, getConfig().maxArmor, factor);
    }

    public double getMaxArmorPerTick(double factor) {
        return M.lerp(getConfig().minArmorPerTick, getConfig().maxArmorPerTick, factor);
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if(!hasAdaptation(p)){
                Collection<AttributeModifier> c = p.getAttribute(Attribute.GENERIC_ARMOR).getModifiers();
                for (AttributeModifier i : new ArrayList<>(c)) {
                    if(i.getName().equals("adapt-ghost-armor")) {
                        p.getAttribute(Attribute.GENERIC_ARMOR).removeModifier(i);
                    }
                }
                continue;
            }
            double oldArmor = 0;
            double armor = getMaxArmorPoints(getLevelPercent(p));
            armor = Double.isNaN(armor) ? 0 : armor;



            if(oldArmor < armor)
            {Collection<AttributeModifier> c = p.getAttribute(Attribute.GENERIC_ARMOR).getModifiers();
                for (AttributeModifier i : new ArrayList<>(c)) {
                    if(i.getName().equals("adapt-ghost-armor")) {
                        oldArmor = i.getAmount();
                        oldArmor = Double.isNaN(oldArmor) ? 0 : oldArmor;
                        p.getAttribute(Attribute.GENERIC_ARMOR).removeModifier(i);
                    }
                }
                p.getAttribute(Attribute.GENERIC_ARMOR)
                    .addModifier(new AttributeModifier("adapt-ghost-armor", Math.min(armor, oldArmor+getMaxArmorPerTick(getLevelPercent(p))), AttributeModifier.Operation.ADD_NUMBER));
            }

            else if(oldArmor > armor)
            {Collection<AttributeModifier> c = p.getAttribute(Attribute.GENERIC_ARMOR).getModifiers();
                for (AttributeModifier i : new ArrayList<>(c)) {
                    if(i.getName().equals("adapt-ghost-armor")) {
                        oldArmor = i.getAmount();
                        oldArmor = Double.isNaN(oldArmor) ? 0 : oldArmor;
                        p.getAttribute(Attribute.GENERIC_ARMOR).removeModifier(i);
                    }
                }
                p.getAttribute(Attribute.GENERIC_ARMOR)
                    .addModifier(new AttributeModifier("adapt-ghost-armor", armor, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e)
    {
        if(e.getEntity() instanceof Player p && hasAdaptation(p) && !e.isCancelled() && e.getDamage() > 0)
        {
            xp(p, 2.5 * e.getDamage());
            J.s(() -> {
                Collection<AttributeModifier> c = p.getAttribute(Attribute.GENERIC_ARMOR).getModifiers();
                for (AttributeModifier i : new ArrayList<>(c)) {
                    if(i.getName().equals("adapt-ghost-armor")) {
                        p.getAttribute(Attribute.GENERIC_ARMOR).removeModifier(i);
                    }
                }
            });
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 3;
        int maxArmor = 16;
        int minArmor = 2;
        int maxArmorPerTick = 3;
        int minArmorPerTick = 1;
        int initialCost = 1;
        double costFactor = 0.335;
        int maxLevel = 7;
    }
}
