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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
    private final Map<Player, Integer> ticksRunning = new HashMap<>();

    public AgilityWindUp() {
        super("agility-wind-up");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("agility", "windup", "description"));
        setDisplayName(Adapt.dLocalize("agility", "windup", "name"));
        setIcon(Material.POWERED_RAIL);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setInitialCost(getConfig().initialCost);
        setInterval(120);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getWindupSpeed(getLevelPercent(level)), 0) + C.GRAY + Adapt.dLocalize("agility", "windup", "lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + C.GRAY + Adapt.dLocalize("agility", "windup", "lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        ticksRunning.remove(p);
    }

    private double getWindupTicks(double factor) {
        return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
    }

    private double getWindupSpeed(double factor) {
        return getConfig().windupSpeedBase + (factor * getConfig().windupSpeedLevelMultiplier);
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) == null) {
                return;
            }
            for (AttributeModifier j : i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers()) {
                if (j.getName().equals("adapt-wind-up")) {
                    i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(j);
                }
            }
            if (i.isSwimming() || i.isFlying() || i.isGliding() || i.isSneaking()) {
                ticksRunning.remove(i);
                return;
            }
            if (i.isSprinting() && getLevel(i) > 0) {
                ticksRunning.compute(i, (k, v) -> {
                    if (v == null) {
                        return 1;
                    }

                    return v + 1;
                });

                Integer tr = ticksRunning.get(i);

                if (tr == null || tr <= 0) {
                    continue;
                }
                double factor = getLevelPercent(i);
                double ticksToMax = getWindupTicks(factor);
                double progress = Math.min(M.lerpInverse(0, ticksToMax, tr), 1);
                double speedIncrease = M.lerp(0, getWindupSpeed(factor), progress);
                if (getConfig().showParticles) {

                    if (M.r(0.2 * progress)) {
                        i.getWorld().spawnParticle(Particle.LAVA, i.getLocation(), 1);
                    }

                    if (M.r(0.25 * progress)) {
                        i.getWorld().spawnParticle(Particle.FLAME, i.getLocation(), 1, 0, 0, 0, 0);
                    }
                }
                i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(new AttributeModifier("adapt-wind-up", speedIncrease, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            } else {
                ticksRunning.remove(i);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
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
