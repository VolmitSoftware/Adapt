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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.Particle.HEART;

public class TamingHealthRegeneration extends SimpleAdaptation<TamingHealthRegeneration.Config> {
    private final Map<UUID, Long> lastDamage = new HashMap<>();

    public TamingHealthRegeneration() {
        super("tame-health-regeneration");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming.regeneration.description"));
        setDisplayName(Localizer.dLocalize("taming.regeneration.name"));
        setIcon(Material.GOLDEN_APPLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(1033);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("taming.regeneration.lore", Form.f(getRegenSpeed(level), 0)));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Tameable tam
                && tam.getOwner() instanceof Player p
                && hasAdaptation(p)) {
            if (lastDamage.containsKey(tam.getUniqueId())) {
                Adapt.verbose("Tamed Entity " + tam.getUniqueId() + " last damaged " + (M.ms() - lastDamage.get(tam.getUniqueId())) + "ms ago");
                return;
            }
            var attribute = Version.get().getAttribute(tam, Attributes.GENERIC_MAX_HEALTH);
            double mh = attribute == null ? tam.getHealth() : attribute.getValue();
            if (tam.isTamed() && tam.getOwner() instanceof Player && tam.getHealth() < mh) {
                Adapt.verbose("Successfully healed tamed entity " + tam.getUniqueId());
                int level = getLevel(p);
                if (level > 0) {
                    Adapt.verbose("[PRE] Current Health: " + tam.getHealth() + " Max Health: " + mh);
                    tam.addPotionEffect(PotionEffectType.REGENERATION.createEffect(25 * getLevel(p), 3));
                    J.a(() -> {
                        try {
                            Thread.sleep(getLevel(p) * 2000L);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
//                        Adapt.verbose("[POST] Current Health: " + tam.getHealth() + " Max Health: " + mh);

                    });

                    if (getConfig().showParticles) {
                        Adapt.verbose("Healing tamed entity " + tam.getUniqueId() + " with particles");
                        tam.getWorld().spawnParticle(HEART, tam.getLocation().add(0, 1, 0), 2 * p.getLevel());
                    } else {
                        Adapt.verbose("Healing tamed entity " + tam.getUniqueId() + " without particles");
                    }
                }
            }
            lastDamage.put(e.getEntity().getUniqueId(), M.ms());
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
        for (UUID i : lastDamage.keySet()) {
            if (M.ms() - lastDamage.get(i) > 8000) {
                lastDamage.remove(i);
            }
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
