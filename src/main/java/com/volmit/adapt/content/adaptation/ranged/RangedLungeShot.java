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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
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
                        for (Player players : p.getWorld().getPlayers()) {
                            players.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1f, 0.75f);
                            players.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.95f);
                        }

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
        boolean permanent = false;
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 3;
        int maxLevel = 3;
        int initialCost = 8;
        double costFactor = 0.5;
        double factor = 0.935;
    }
}
