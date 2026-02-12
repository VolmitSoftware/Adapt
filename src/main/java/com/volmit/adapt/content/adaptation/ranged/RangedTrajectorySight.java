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
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RangedTrajectorySight extends SimpleAdaptation<RangedTrajectorySight.Config> {
    private final Map<UUID, Long> drawStartedMillis = new HashMap<>();

    public RangedTrajectorySight() {
        super("ranged-trajectory-sight");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.trajectory_sight.description"));
        setDisplayName(Localizer.dLocalize("ranged.trajectory_sight.name"));
        setIcon(Material.SPYGLASS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(4);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getVelocityMultiplier(level)) + C.GRAY + " " + Localizer.dLocalize("ranged.trajectory_sight.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getSegments(level)) + C.GRAY + " " + Localizer.dLocalize("ranged.trajectory_sight.lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        drawStartedMillis.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isRanged(hand)) {
            return;
        }

        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> drawStartedMillis.put(p.getUniqueId(), System.currentTimeMillis());
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p) {
            drawStartedMillis.remove(p.getUniqueId());
        }
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(p)) {
                drawStartedMillis.remove(p.getUniqueId());
                continue;
            }

            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!isRanged(hand)) {
                drawStartedMillis.remove(p.getUniqueId());
                continue;
            }

            if (!p.isSneaking() && !p.isHandRaised()) {
                drawStartedMillis.remove(p.getUniqueId());
                continue;
            }

            double launchVelocity = getLaunchVelocity(p, hand, getLevel(p));
            if (launchVelocity <= 0.01) {
                continue;
            }

            renderTrajectory(p, getSegments(getLevel(p)), launchVelocity);
        }
    }

    private double getLaunchVelocity(Player p, ItemStack hand, int level) {
        double multiplier = getVelocityMultiplier(level);
        Material type = hand.getType();

        if (type == Material.BOW) {
            double force = getBowForce(p);
            if (force <= 0) {
                return 0;
            }

            return (force * 3.0) * multiplier;
        }

        if (type == Material.CROSSBOW) {
            return getConfig().crossbowVelocity * multiplier;
        }

        return getConfig().fallbackVelocity * multiplier;
    }

    private double getBowForce(Player p) {
        UUID id = p.getUniqueId();
        long start = drawStartedMillis.getOrDefault(id, System.currentTimeMillis());
        double chargeTicks = Math.max(0, (System.currentTimeMillis() - start) / 50.0);

        if (!p.isHandRaised() && p.isSneaking()) {
            chargeTicks = getConfig().sneakPreviewChargeTicks;
        }

        double force = chargeTicks / 20.0;
        force = (force * force + force * 2.0) / 3.0;
        return Math.min(1.0, force);
    }

    private void renderTrajectory(Player p, int segments, double velocityScale) {
        Location current = p.getEyeLocation().clone().add(p.getEyeLocation().getDirection().normalize().multiply(0.35));
        Vector velocity = current.getDirection().normalize().multiply(velocityScale);

        Particle.DustOptions trail = new Particle.DustOptions(Color.fromRGB(110, 235, 190), (float) getConfig().particleSize);
        Particle.DustOptions impact = new Particle.DustOptions(Color.fromRGB(250, 220, 120), (float) Math.max(0.1, getConfig().particleSize * 1.2));
        int every = Math.max(1, getConfig().trailParticleEvery);

        for (int i = 0; i < segments; i++) {
            current.add(velocity.clone().multiply(getConfig().stepScale));
            velocity.multiply(getConfig().dragFactor);
            velocity.setY(velocity.getY() - getConfig().gravityStep);

            if (i % every == 0) {
                p.spawnParticle(Particle.DUST, current, Math.max(1, getConfig().trailParticleCount), 0, 0, 0, 0, trail);
            }

            if (current.getBlock().getType().isSolid()) {
                p.spawnParticle(Particle.DUST, current, Math.max(1, getConfig().impactParticleCount), 0.1, 0.1, 0.1, 0.01, impact);
                break;
            }
        }
    }

    private int getSegments(int level) {
        return Math.max(10, (int) Math.round(getConfig().segmentsBase + (getLevelPercent(level) * getConfig().segmentsFactor)));
    }

    private double getVelocityMultiplier(int level) {
        return Math.max(0.1, getConfig().velocityBase + (getLevelPercent(level) * getConfig().velocityFactor));
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
    @ConfigDescription("Preview ranged projectile flight while sneaking or drawing your shot.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Segments Base for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double segmentsBase = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Segments Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double segmentsFactor = 26;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Velocity Base for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double velocityBase = 1.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Velocity Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double velocityFactor = 0.18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Gravity Step for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double gravityStep = 0.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Step Scale for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double stepScale = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Drag Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dragFactor = 0.99;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Crossbow Velocity for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double crossbowVelocity = 3.15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fallback Velocity for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fallbackVelocity = 1.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Sneak Preview Charge Ticks for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sneakPreviewChargeTicks = 16;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Particle Size for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double particleSize = 0.3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Trail Particle Count for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int trailParticleCount = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Impact Particle Count for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int impactParticleCount = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Trail Particle Every for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int trailParticleEvery = 2;
    }
}
