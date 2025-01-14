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
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.enums.Attributes;
import com.volmit.adapt.util.reflect.events.api.ReflectiveHandler;
import com.volmit.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import com.volmit.adapt.util.reflect.events.api.entity.EntityMountEvent;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
    private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-wind-up".getBytes());
    private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString( "adapt:wind-up");

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

    @ReflectiveHandler
    public void on(EntityMountEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER)
            return;
        ticksRunning.remove((Player) event.getEntity());
    }

    @ReflectiveHandler
    public void on(EntityDismountEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER)
            return;
        ticksRunning.remove((Player) event.getEntity());
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
            var attribute = Version.get().getAttribute(p, Attributes.GENERIC_MOVEMENT_SPEED);
            if (attribute == null) continue;

            try {
                attribute.removeModifier(MODIFIER, MODIFIER_KEY);
            } catch (Exception e) {
                Adapt.verbose("Failed to remove windup modifier: " + e.getMessage());
            }
            if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSneaking()) {
                ticksRunning.remove(p);
                continue;
            }
            if (p.isSprinting() && hasAdaptation(p)) {
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
                attribute.setModifier(MODIFIER, MODIFIER_KEY, speedIncrease, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            } else {
                ticksRunning.remove(p);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }


    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = false;
        final boolean enabled = true;
        final boolean showParticles = true;
        final int baseCost = 2;
        final double costFactor = 0.65;
        final int initialCost = 8;
        final double windupTicksSlowest = 180;
        final double windupTicksFastest = 60;
        final double windupSpeedBase = 0.22;
        final double windupSpeedLevelMultiplier = 0.225;
    }

}
