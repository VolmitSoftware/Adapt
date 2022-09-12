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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

public class HunterResistance extends SimpleAdaptation<HunterResistance.Config> {
    public HunterResistance() {
        super("hunter-resistance");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Hunter", "HunterResistance", "Description"));
        setDisplayName(Adapt.dLocalize("Hunter", "HunterResistance", "Name"));
        setIcon(Material.POWDER_SNOW_BUCKET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(9844);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("Hunter", "HunterResistance", "Lore1"));
        v.addLore(C.GREEN + "+ " + level + C.GRAY + Adapt.dLocalize("Hunter", "HunterResistance", "Lore2"));
        v.addLore(C.RED + "- " + 5 + level + C.GRAY + Adapt.dLocalize("Hunter", "HunterResistance", "Lore3"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " + Adapt.dLocalize("Hunter", "HunterResistance", "Lore4"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " + Adapt.dLocalize("Hunter", "HunterResistance", "Lore5"));
        v.addLore(C.GRAY + "- " + level + C.RED + " " + Adapt.dLocalize("Hunter", "GenericPenalty", "Lore1"));

    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Player  p && !e.getCause().equals(EntityDamageEvent.DamageCause.STARVATION) && hasAdaptation(p)) {
            if (p.getFoodLevel() == 0) {
                addPotionStacks(p, PotionEffectType.POISON, getLevel(p), 50, true);

            } else {
                addPotionStacks(p, PotionEffectType.HUNGER, 5 + getLevel(p), 100, true);
                addPotionStacks(p, PotionEffectType.DAMAGE_RESISTANCE, getLevel(p), 50, false);
            }
        }
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
        int baseCost = 4;
        int maxLevel = 5;
        int initialCost = 8;
        double costFactor = 0.4;
    }
}
