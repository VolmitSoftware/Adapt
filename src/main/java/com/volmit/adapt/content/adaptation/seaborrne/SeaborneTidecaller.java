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

package com.volmit.adapt.content.adaptation.seaborrne;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

public class SeaborneTidecaller extends SimpleAdaptation<SeaborneTidecaller.Config> {
    public SeaborneTidecaller() {
        super("seaborne-tidecaller");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("seaborn.tidecaller.description"));
        setDisplayName(Localizer.dLocalize("seaborn.tidecaller.name"));
        setIcon(Material.HEART_OF_THE_SEA);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1600);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getDashDistance(level), 1) + C.GRAY + " " + Localizer.dLocalize("seaborn.tidecaller.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("seaborn.tidecaller.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking() || !hasAdaptation(p) || p.hasCooldown(Material.HEART_OF_THE_SEA) || !isRainingAt(p)) {
            return;
        }

        int level = getLevel(p);
        org.bukkit.Location target = findSafeDashTarget(p, getDashDistance(level));
        if (target == null) {
            return;
        }

        p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 20, 0.25, 0.35, 0.25, 0.08);
        p.teleport(target);
        p.getWorld().spawnParticle(Particle.SPLASH, target.clone().add(0, 1, 0), 30, 0.35, 0.45, 0.35, 0.08);
        p.getWorld().spawnParticle(Particle.BUBBLE, target.clone().add(0, 0.9, 0), 18, 0.4, 0.35, 0.4, 0.05);
        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 0.75f, 1.2f);
        sp.play(target, Sound.ENTITY_DOLPHIN_SPLASH, 0.65f, 1.15f);
        p.setCooldown(Material.HEART_OF_THE_SEA, getCooldownTicks(level));
        xp(p, getConfig().xpPerBurst);
    }

    private boolean isRainingAt(Player p) {
        if (!p.getWorld().hasStorm()) {
            return false;
        }

        int topY = p.getWorld().getHighestBlockYAt(p.getLocation());
        return p.getLocation().getY() >= topY - 1;
    }

    private org.bukkit.Location findSafeDashTarget(Player p, double maxDistance) {
        Vector direction = p.getLocation().getDirection().clone();
        if (direction.lengthSquared() <= 0.0001) {
            return null;
        }

        direction.normalize();
        for (double d = maxDistance; d >= 1.0; d -= 0.5) {
            org.bukkit.Location c = p.getLocation().clone().add(direction.clone().multiply(d));
            if (isSafe(c)) {
                return c;
            }
        }

        return null;
    }

    private boolean isSafe(org.bukkit.Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        Block floor = location.clone().subtract(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable() && (floor.getType().isSolid() || floor.isLiquid());
    }

    private double getDashDistance(int level) {
        return getConfig().dashDistanceBase + (getLevelPercent(level) * getConfig().dashDistanceFactor);
    }

    private int getCooldownTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
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
    @ConfigDescription("Sneak while it is raining to dash like a water blink through the storm.")
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
        double costFactor = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dash Distance Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dashDistanceBase = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dash Distance Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dashDistanceFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 140;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 80;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Burst for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerBurst = 11;
    }
}
