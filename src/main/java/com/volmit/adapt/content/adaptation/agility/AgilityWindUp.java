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

package com.volmit.adapt.content.adaptation.agility;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import java.util.HashMap;
import java.util.Map;

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
    private final Map<Player, Integer> ticksRunning;

    public AgilityWindUp() {
        super("agility-wind-up");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility", "windup", "description"));
        setDisplayName(Localizer.dLocalize("agility", "windup", "name"));
        setIcon(Material.POWERED_RAIL);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setInitialCost(getConfig().initialCost);
        setInterval(120);
        ticksRunning = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getWindupSpeed(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("agility", "windup", "lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("agility", "windup", "lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        ticksRunning.remove(p);
    }

    @EventHandler
    public void on(EntityMountEvent e) {
        if(e.getEntity() instanceof Player p) {
            ticksRunning.remove(p);
        }
    }

    @EventHandler
    public void on(EntityDismountEvent e) {
        if(e.getEntity() instanceof Player p) {
            ticksRunning.remove(p);
        }
    }

    private double getWindupTicks(double factor) {
        return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
    }

    private double getWindupSpeed(double factor) {
        return getConfig().windupSpeedBase + (factor * getConfig().windupSpeedLevelMultiplier);
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) == null) {
                return;
            }
            try {
                for (AttributeModifier j : p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers()) {
                    if (j.getName().equals("adapt-wind-up")) {
                        p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(j);
                    }
                }
            } catch (Exception e) {
                Adapt.verbose("Failed to remove windup modifier: " + e.getMessage());
            }
            if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSneaking()) {
                ticksRunning.remove(p);
                return;
            }
            if (p.isSprinting() && getLevel(p) > 0) {
                ticksRunning.compute(p, (k, v) -> {
                    if (v == null) {
                        return 1;
                    }
                    return v + 1;
                });
                Integer tr = ticksRunning.get(p);
                if (tr == null || tr <= 0) {
                    continue;
                }
                double factor = getLevelPercent(p);
                double ticksToMax = getWindupTicks(factor);
                double progress = Math.min(M.lerpInverse(0, ticksToMax, tr), 1);
                double speedIncrease = M.lerp(0, getWindupSpeed(factor), progress);

                if (getConfig().showParticles) {

                    if (M.r(0.2 * progress)) {
                        p.getWorld().spawnParticle(Particle.LAVA, p.getLocation(), 1);
                    }

                    if (M.r(0.25 * progress)) {
                        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 1, 0, 0, 0, 0);
                    }
                }
                p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(new AttributeModifier("adapt-wind-up", speedIncrease, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            } else {
                ticksRunning.remove(p);
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
        int baseCost = 2;
        double costFactor = 0.65;
        int initialCost = 8;
        double windupTicksSlowest = 180;
        double windupTicksFastest = 60;
        double windupSpeedBase = 0.22;
        double windupSpeedLevelMultiplier = 0.225;
    }

}
