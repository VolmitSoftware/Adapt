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

package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DiscoveryXpResist extends SimpleAdaptation<DiscoveryXpResist.Config> {
    private static final long COOLDOWN_MILLIS = 15000L;
    private final Map<UUID, Long> cooldowns;

    public DiscoveryXpResist() {
        super("discovery-xp-resist");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery", "resist", "description"));
        setDisplayName(Localizer.dLocalize("discovery", "resist", "name"));
        setIcon(Material.EMERALD);
        setInterval(5215);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        cooldowns = new HashMap<>();
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("discovery", "resist", "lore0"));
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + Localizer.dLocalize("discovery", "resist", "lore1"));
        v.addLore(C.GREEN + "+ " + getXpTaken(level) + " " + C.GRAY + Localizer.dLocalize("discovery", "resist", "lore2"));
    }

    private double getEffectiveness(double factor) {
        return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
    }

    private int getXpTaken(double level) {
        double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
        return Math.max(1, (int) Math.round(d));
    }

    @EventHandler
    public void on(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Player p && hasAdaptation(p) && p.getLevel() > 1) {
            if (!isCriticalHealthDamage(p, e)) {
                return;
            }

            SoundPlayer sp = SoundPlayer.of(p);
            int xpCost = getXpTaken(getLevel(p));
            if (p.getLevel() < xpCost) {
                vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.RED);
                sp.play(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 15, 0.01f);
                return;
            }
            UUID id = p.getUniqueId();
            Long cooldown = cooldowns.get(id);
            if (cooldown == null || M.ms() - cooldown > COOLDOWN_MILLIS) {
                e.setDamage(e.getDamage() - (e.getDamage() * (getEffectiveness(getLevelPercent(getLevel(p))))));
                xp(p, 5);
                cooldowns.put(id, M.ms());
                p.setLevel(p.getLevel() - xpCost);
                vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.LIME);
                sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 3, 0.01f);
                sp.play(p.getLocation(), Sound.BLOCK_SHROOMLIGHT_HIT, 15, 0.01f);
            } else {
                vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.RED);
                sp.play(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 15, 0.01f);
            }
        }
    }

    private boolean isCriticalHealthDamage(Player p, EntityDamageEvent e) {
        double predictedHealth = p.getHealth() - e.getFinalDamage();
        return predictedHealth <= 0 || predictedHealth <= getConfig().triggerHealthThreshold;
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

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 5;
        int initialCost = 3;
        double costFactor = 0.8;
        int maxLevel = 5;
        double effectivenessBase = 0.15;
        double maxEffectiveness = 0.95;
        int levelDrain = 2;
        int levelCostAdd = 12;
        double amplifier = 1.0;
        double triggerHealthThreshold = 10.0;
    }
}
