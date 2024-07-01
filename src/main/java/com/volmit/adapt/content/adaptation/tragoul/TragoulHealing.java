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
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;


public class TragoulHealing extends SimpleAdaptation<TragoulHealing.Config> {
    private final Map<Player, Long> cooldowns;
    private final Map<Player, Long> healingWindow;

    public TragoulHealing() {
        super("tragoul-healing");
        registerConfiguration(TragoulHealing.Config.class);
        setDescription(Localizer.dLocalize("tragoul", "healing", "description"));
        setDisplayName(Localizer.dLocalize("tragoul", "healing", "name"));
        setIcon(Material.REDSTONE);
        setInterval(25000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
        healingWindow = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("tragoul", "healing", "lore1"));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul", "healing", "lore2"));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul", "healing", "lore3") + (getConfig().minHealPercent + (getConfig().maxHealPercent - getConfig().minHealPercent) * (level - 1) / (getConfig().maxLevel - 1)) + "%");
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && hasAdaptation(p)) {
            if (isOnCooldown(p)) {
                return;
            }

            if (!healingWindow.containsKey(p)) {
                Adapt.verbose("Starting healing window for " + p.getName());
                startHealingWindow(p);
            }

            if (getConfig().showParticles) {
                vfxParticleLine(p.getLocation(), e.getEntity().getLocation(), 25, Particle.WHITE_ASH);
            }

            double healPercentage = getConfig().minHealPercent + (getConfig().maxHealPercent - getConfig().minHealPercent) * (getLevel(p) - 1) / (getConfig().maxLevel - 1);
            double healAmount = e.getDamage() * healPercentage;
            Adapt.verbose("Healing " + p.getName() + " for " + healAmount + " (" + healPercentage * 100 + "% of " + e.getDamage() + " damage)");
            p.setHealth(Math.min(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), p.getHealth() + healAmount));

        }
    }

    private boolean isOnCooldown(Player p) {
        Long cooldown = cooldowns.get(p);
        return cooldown != null && cooldown > System.currentTimeMillis();
    }

    private void startHealingWindow(Player p) {
        long currentTime = System.currentTimeMillis();
        healingWindow.put(p, currentTime + getConfig().windowDuration);
        Bukkit.getScheduler().runTaskLater(Adapt.instance, () -> {
            healingWindow.remove(p);
            cooldowns.put(p, currentTime + getConfig().windowDuration + getConfig().cooldownDuration);
        }, getConfig().windowDuration / 50);
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
        double costFactor = 1.10;
        double minHealPercent = 0.10; // 0.10%
        double maxHealPercent = 0.45; // 0.45%
        int cooldownDuration = 1000; // 1 second
        int windowDuration = 3000; // 3 seconds
    }
}
