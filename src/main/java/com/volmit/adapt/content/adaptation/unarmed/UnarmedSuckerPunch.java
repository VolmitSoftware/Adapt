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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class UnarmedSuckerPunch extends SimpleAdaptation<UnarmedSuckerPunch.Config> {
    public UnarmedSuckerPunch() {
        super("unarmed-sucker-punch");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("unarmed", "suckerpunch", "description"));
        setDisplayName(Localizer.dLocalize("unarmed", "suckerpunch", "name"));
        setIcon(Material.OBSIDIAN);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(4944);
    }


    @Override
    public void addStats(int level, Element v) {
        double f = getLevelPercent(level);
        double d = getDamage(f);
        v.addLore(C.GREEN + "+ " + Form.pc(d, 0) + C.GRAY + " " + Localizer.dLocalize("unarmed", "suckerpunch", "lore1"));
        v.addLore(C.GRAY + Localizer.dLocalize("unarmed", "suckerpunch", "lore2"));
    }

    private double getDamage(double f) {
        return getConfig().baseDamage + (f * getConfig().damageFactor);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }

        if (e.getDamager() instanceof Player p) {
            if (!hasAdaptation(p)) {
                return;
            }
            if (p.getInventory().getItemInMainHand().getType() != Material.AIR && p.getInventory().getItemInOffHand().getType() != Material.AIR) {
                return;
            }
            double factor = getLevelPercent(p);

            if (!p.isSprinting()) {
                return;
            }

            if (factor <= 0) {
                return;
            }

            if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
                return;
            }

            e.setDamage(e.getDamage() * getDamage(factor));
            for (Player players : e.getEntity().getWorld().getPlayers()) {
                players.playSound(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.8f);
                players.playSound(e.getEntity().getLocation(), Sound.BLOCK_BASALT_BREAK, 1f, 0.6f);
            }
            getSkill().xp(p, 6.221 * e.getDamage());
            if (e.getDamage() > 5) {
                getSkill().xp(p, 0.42 * e.getDamage());
                if (getConfig().showParticles) {
                    e.getEntity().getWorld().spawnParticle(Particle.FLASH, e.getEntity().getLocation(), 1);
                }
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

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 2;
        int initialCost = 4;
        double costFactor = 0.225;
        double baseDamage = 0.2;
        double damageFactor = 0.55;
    }
}
