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

package com.volmit.adapt.content.adaptation.taming;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import xyz.xenondevs.particle.ParticleEffect;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TamingHealthRegeneration extends SimpleAdaptation<TamingHealthRegeneration.Config> {
    private final Map<UUID, Long> lastDamage = new HashMap<>();

    public TamingHealthRegeneration() {
        super("tame-health-regeneration");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming", "regeneration", "description"));
        setDisplayName(Localizer.dLocalize("taming", "regeneration", "name"));
        setIcon(Material.GOLDEN_APPLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(1033);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRegenSpeed(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming", "regeneration", "lore1"));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Tameable) {
            lastDamage.put(e.getEntity().getUniqueId(), M.ms());
        }

        if (e.getEntity() instanceof Tameable) {
            lastDamage.put(e.getDamager().getUniqueId(), M.ms());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDeathEvent e) {
        lastDamage.remove(e.getEntity().getUniqueId());
    }


    private double getRegenSpeed(int level) {
        return ((getLevelPercent(level) * (getLevelPercent(level)) * getConfig().regenFactor) + getConfig().regenBase);
    }

    @Override
    public void onTick() {
        for (UUID i : lastDamage.k()) {
            if (M.ms() - lastDamage.get(i) > 10000) {
                lastDamage.remove(i);
            }
        }

        for (World i : Bukkit.getServer().getWorlds()) {
            J.s(() -> {
                Collection<Tameable> gl = i.getEntitiesByClass(Tameable.class);

                J.a(() -> {
                    for (Tameable j : gl) {
                        if (lastDamage.containsKey(j.getUniqueId())) {
                            continue;
                        }

                        double mh = j.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        if (j.isTamed() && j.getOwner() instanceof Player && j.getHealth() < mh) {
                            Player p = (Player) j.getOwner();
                            int level = getLevel(p);

                            if (level > 0) {
                                J.s(() -> j.setHealth(Math.min(j.getHealth() + getRegenSpeed(level), mh)));
                                if (getConfig().showParticles) {

                                    ParticleEffect.HEART.display(j.getLocation().clone().add(0, 1, 0), 0.55f, 0.37f, 0.55f, 0.3f, level, null);
                                }
                            }
                        }
                    }
                });
            });
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
        boolean permanent = false;
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 7;
        int maxLevel = 3;
        int initialCost = 8;
        double costFactor = 0.4;
        double regenFactor = 5;
        double regenBase = 1;
    }
}
