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

import java.util.HashMap;
import java.util.Map;

public class AgilityArmorUp extends SimpleAdaptation<AgilityArmorUp.Config> {
    private final Map<Player, Integer> ticksRunning;


    public AgilityArmorUp() {
        super("agility-armor-up");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility", "armorup", "description"));
        setIcon(Material.IRON_CHESTPLATE);
        setDisplayName(Localizer.dLocalize("agility", "armorup", "name"));
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setInitialCost(getConfig().initialCost);
        setInterval(350);
        ticksRunning = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getWindupArmor(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("agility", "armorup", "lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + " " + C.GRAY + Localizer.dLocalize("agility", "armorup", "lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        ticksRunning.remove(p);
    }

    private double getWindupTicks(double factor) {
        return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
    }

    private double getWindupArmor(double factor) {
        return getConfig().windupArmorBase + (factor * getConfig().windupArmorLevelMultiplier);
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var attribute = p.getAttribute(Attribute.GENERIC_ARMOR);
            if (attribute == null) continue;

            for (AttributeModifier j : attribute.getModifiers()) {
                if (j.getName().equals("adapt-armor-up")) {
                    p.getAttribute(Attribute.GENERIC_ARMOR).removeModifier(j);
                }
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
                double armorInc = M.lerp(0, getWindupArmor(factor), progress);

                if (getConfig().showParticles) {
                    if (M.r(0.2 * progress)) {
                        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation(), 1);
                    }

                    if (M.r(0.25 * progress)) {
                        p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation(), 1, 0, 0, 0, 0);
                    }
                }
                p.getAttribute(Attribute.GENERIC_ARMOR).addModifier(new AttributeModifier("adapt-armor-up", armorInc * 10, AttributeModifier.Operation.ADD_NUMBER));

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
        double windupArmorBase = 0.22;
        double windupArmorLevelMultiplier = 0.525;
    }

}
