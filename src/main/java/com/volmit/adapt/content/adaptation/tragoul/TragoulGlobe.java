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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TragoulGlobe extends SimpleAdaptation<TragoulGlobe.Config> {
    private final Map<Player, Long> cooldowns;

    public TragoulGlobe() {
        super("tragoul-globe");
        registerConfiguration(TragoulGlobe.Config.class);
        setDescription(Localizer.dLocalize("tragoul", "globe", "description"));
        setDisplayName(Localizer.dLocalize("tragoul", "globe", "name"));
        setIcon(Material.ENDER_PEARL);
        setInterval(25000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("tragoul", "globe", "lore1"));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul", "globe", "lore2") + ((getConfig().rangePerLevel * level) + getConfig().initalRange));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul", "globe", "lore3") + (getConfig().bonusDamagePerLevel * level));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && hasAdaptation(p)) {
            if (cooldowns.containsKey(p)) {
                if (cooldowns.get(p) + (1000 * getConfig().cooldown) > System.currentTimeMillis()) {
                    return;
                } else {
                    cooldowns.remove(p);
                }
            }

            cooldowns.put(p, System.currentTimeMillis());
            double range = (getConfig().rangePerLevel * getLevel(p)) + getConfig().initalRange;
            List<Entity> entitiesInRange = p.getNearbyEntities(range, range, range).stream()
                    .filter(entity -> entity instanceof LivingEntity && !entity.equals(p))
                    .toList();

            if (entitiesInRange.size() <=1) {
                return;
            }

            double damagePerEntity = e.getDamage() / entitiesInRange.size() + (getConfig().bonusDamagePerLevel * getLevel(p));
            e.setDamage(damagePerEntity);

            for (Entity entity : entitiesInRange) {
                ((LivingEntity) entity).damage(damagePerEntity, p);
            }

            if (getConfig().showParticles) {
                J.s(() -> {
                    if (getConfig().showParticles) {
                        vfxFastSphere(p.getLocation(), range, Color.BLACK, 100);
                        vfxDome(p.getLocation(), range, Color.BLACK, 300);
                        vfxLoadingRing(p.getLocation(), range, Particle.DRIP_LAVA, 20, 300);
                    }
                });
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

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }


    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 5;
        int maxLevel = 5;
        int initialCost = 5;
        double cooldown = 1;
        double rangePerLevel = 3.0;
        double initalRange = 5.0;
        double costFactor = 1.10;
        double bonusDamagePerLevel = 1;
    }
}
