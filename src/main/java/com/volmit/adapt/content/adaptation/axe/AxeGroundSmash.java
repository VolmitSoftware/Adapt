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

package com.volmit.adapt.content.adaptation.axe;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.List;

public class AxeGroundSmash extends SimpleAdaptation<AxeGroundSmash.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public AxeGroundSmash() {
        super("axe-ground-smash");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("axe", "groundsmash", "description"));
        setDisplayName(Localizer.dLocalize("axe", "groundsmash", "name"));
        setIcon(Material.NETHERITE_AXE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4333);
    }

    @Override
    public void addStats(int level, Element v) {
        double f = getLevelPercent(level);
        v.addLore(C.RED + "+ " + Form.f(getFalloffDamage(f), 1) + " - " + Form.f(getDamage(f), 1) + C.GRAY + " " + Localizer.dLocalize("axe", "groundsmash", "lore1"));
        v.addLore(C.RED + "+ " + Form.f(getRadius(f), 1) + C.GRAY + " " + Localizer.dLocalize("axe", "groundsmash", "lore2"));
        v.addLore(C.RED + "+ " + Form.pc(getForce(f), 0) + C.GRAY + " " + Localizer.dLocalize("axe", "groundsmash", "lore3"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("axe", "groundsmash", "lore4"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && hasAdaptation(p) && p.isSneaking()) {
            if (!isAxe(p.getInventory().getItemInMainHand())) {
                return;
            }

            double f = getLevelPercent(p);

            if (p.hasCooldown(p.getInventory().getItemInMainHand().getType())) {
                return;
            }

            p.setCooldown(p.getInventory().getItemInMainHand().getType(), getCooldownTime(f));
            new Impulse(getRadius(f))
                    .damage(getDamage(f), getFalloffDamage(f))
                    .force(getForce(f))
                    .punch(e.getEntity().getLocation());
            for (Player players : e.getEntity().getWorld().getPlayers()) {
                players.playSound(e.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 0.6f, 0.4f);
                players.playSound(e.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 0.5f, 0.1f);
               players.playSound(e.getEntity().getLocation(), Sound.ENTITY_TURTLE_EGG_CRACK, SoundCategory.HOSTILE, 1f, 0.4f);
            }

        }
    }


    public int getCooldownTime(double factor) {
        return (int) (((1D - factor) * getConfig().cooldownTicksInverseLevelMultiplier) + getConfig().cooldownTicksBase);
    }

    public double getRadius(double factor) {
        return getConfig().radiusLevelFactorMultiplier * factor;
    }

    public double getDamage(double factor) {
        return getConfig().damageLevelFactorMultiplier * factor;
    }

    public double getForce(double factor) {
        return (getConfig().forceFactorMultiplier * factor) + getConfig().forceBase;
    }

    public double getFalloffDamage(double factor) {
        return getConfig().falloffFactor * factor;
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
        int baseCost = 6;
        double costFactor = 0.75;
        int maxLevel = 5;
        int initialCost = 8;
        double falloffFactor = 3;
        double radiusLevelFactorMultiplier = 8;
        double damageLevelFactorMultiplier = 8;
        double forceFactorMultiplier = 1.15;
        double forceBase = 0.27;
        double cooldownTicksBase = 80;
        double cooldownTicksInverseLevelMultiplier = 225;
    }
}
