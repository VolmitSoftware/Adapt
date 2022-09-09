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

package com.volmit.adapt.content.adaptation.tragoul;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import de.slikey.effectlib.effect.BleedEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;


public class TragoulThorns extends SimpleAdaptation<TragoulThorns.Config> {

    public TragoulThorns() {
        super("tragoul-thorns");
        registerConfiguration(TragoulThorns.Config.class);
        setDescription(Adapt.dLocalize("TragOul", "Thorns", "Description"));
        setDisplayName(Adapt.dLocalize("TragOul", "Thorns", "Name"));
        setIcon(Material.ECHO_SHARD);
        setInterval(25000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "" + getConfig().damageMultiplierPerLevel * level + "x "+ Adapt.dLocalize("TragOul", "Thorns", "Lore1"));
    }


    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && hasAdaptation(p)) {
            if (e.getDamager() instanceof LivingEntity le) {
                BleedEffect blood = new BleedEffect(Adapt.instance.adaptEffectManager);  // Enemy gets blood
                blood.setEntity(le);
                blood.height = -1;
                blood.iterations = 1;
                blood.start();
                le.damage(getConfig().damageMultiplierPerLevel * getLevel(p), p);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }


    @Override
    public void onTick() {
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 5;
        int initialCost = 5;
        int damageMultiplierPerLevel = 1;
        double costFactor = 1.10;
    }
}
