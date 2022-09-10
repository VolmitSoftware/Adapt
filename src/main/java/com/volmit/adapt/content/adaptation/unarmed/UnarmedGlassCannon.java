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

package com.volmit.adapt.content.adaptation.unarmed;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class UnarmedGlassCannon extends SimpleAdaptation<UnarmedGlassCannon.Config> {
    public UnarmedGlassCannon() {
        super("unarmed-glass-cannon");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Unarmed", "GlassCannon", "Description"));
        setDisplayName(Adapt.dLocalize("Unarmed", "GlassCannon", "Name"));
        setIcon(Material.DISC_FRAGMENT_5);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(4544);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (getConfig().maxDamageFactor + (level * getConfig().maxDamagePerLevelMultiplier)) + C.GRAY + " " + Adapt.dLocalize("Unarmed", "GlassCannon", "Lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(level * getConfig().perLevelBonusMultiplier) + C.GRAY + " " + Adapt.dLocalize("Unarmed", "GlassCannon", "Lore2"));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (!hasAdaptation(p)) {
                return;
            }
            if (p.getInventory().getItemInMainHand().getType() != Material.AIR || p.getInventory().getItemInOffHand().getType() != Material.AIR) {
                return;
            }
            double armor = getArmorValue(p);
            double damage = e.getDamage();
            if (armor == 0) {
                e.setDamage(damage * getConfig().maxDamageFactor);
            } else {
                e.setDamage(damage - (damage * armor));
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
        int baseCost = 3;
        int maxLevel = 7;
        int initialCost = 6;
        double costFactor = 0.425;
        double perLevelBonusMultiplier = 0.25;
        double maxDamageFactor = 4.0;
        double maxDamagePerLevelMultiplier = 0.15;
    }

}
