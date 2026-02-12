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

package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class RangedLungeShot extends SimpleAdaptation<RangedLungeShot.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public RangedLungeShot() {
        super("ranged-lunge-shot");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged", "lungeshot", "description"));
        setDisplayName(Localizer.dLocalize("ranged", "lungeshot", "name"));
        setIcon(Material.FEATHER);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(4859);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private double getSpeed(double factor) {
        return (factor * getConfig().factor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("ranged", "lungeshot", "lore1"));
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity().getShooter() instanceof Player p) {
            if (e.getEntity() instanceof AbstractArrow a) {
                if (hasAdaptation(p)) {
                    if (!p.isOnGround()) {
                        Vector velocity = p.getPlayer().getLocation().getDirection().normalize().multiply(getSpeed(getLevelPercent(p)));
                        p.setVelocity(p.getVelocity().subtract(velocity));
                        SoundPlayer spw = SoundPlayer.of(p.getWorld());
                        spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1f, 0.75f);
                        spw.play(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.95f);

                        for (int i = 0; i < 9; i++) {
                            Vector v = velocity.clone().add(Vector.getRandom().subtract(Vector.getRandom()).multiply(0.3)).normalize();
                            if (getConfig().showParticles) {

                                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().clone().add(0, 1, 0), 0, v.getX(), v.getY(), v.getZ(), 0.2);
                            }
                        }
                    }
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Ranged Lunge Shot adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Factor for the Ranged Lunge Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double factor = 0.935;
    }
}
