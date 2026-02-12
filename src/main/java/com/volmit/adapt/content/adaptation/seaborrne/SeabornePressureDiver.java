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
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SeabornePressureDiver extends SimpleAdaptation<SeabornePressureDiver.Config> {
    private final Map<UUID, Long> xpCooldowns = new HashMap<>();

    public SeabornePressureDiver() {
        super("seaborne-pressure-diver");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("seaborn.pressure_diver.description"));
        setDisplayName(Localizer.dLocalize("seaborn.pressure_diver.name"));
        setIcon(Material.NAUTILUS_SHELL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(20);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getDepthThreshold(level), 1) + C.GRAY + " " + Localizer.dLocalize("seaborn.pressure_diver.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getDamageReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("seaborn.pressure_diver.lore2"));
        v.addLore(C.GREEN + "+ " + Form.pc(getFatigueTrimChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("seaborn.pressure_diver.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        xpCooldowns.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !hasAdaptation(p) || !p.isInWater()) {
            return;
        }

        int level = getLevel(p);
        if (!isDeepEnough(p, level)) {
            return;
        }

        e.setDamage(e.getDamage() * (1D - getDamageReduction(level)));
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(p) || !p.isInWater()) {
                continue;
            }

            int level = getLevel(p);
            if (!isDeepEnough(p, level)) {
                continue;
            }

            applyDepthBuffs(p, level);
            awardDepthXp(p, now);
        }
    }

    private void applyDepthBuffs(Player p, int level) {
        int resistanceAmp = getResistanceAmplifier(level, p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, getConfig().effectTicks, resistanceAmp, false, false, true), true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, getConfig().effectTicks, 0, false, false, true), true);

        PotionEffect fatigue = p.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (fatigue == null) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > getFatigueTrimChance(level)) {
            return;
        }

        int reducedAmp = Math.max(0, fatigue.getAmplifier() - getFatigueTrimAmount(level));
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                Math.max(20, Math.min(fatigue.getDuration(), getConfig().fatigueReplaceTicks)),
                reducedAmp,
                false,
                true,
                true), true);
    }

    private void awardDepthXp(Player p, long now) {
        UUID id = p.getUniqueId();
        long next = xpCooldowns.getOrDefault(id, 0L);
        if (now < next) {
            return;
        }

        xp(p, getConfig().xpPerDepthPulse);
        xpCooldowns.put(id, now + getConfig().xpPulseCooldownMillis);
    }

    private boolean isDeepEnough(Player p, int level) {
        double seaLevel = p.getWorld().getSeaLevel();
        double depth = seaLevel - p.getEyeLocation().getY();
        return depth >= getDepthThreshold(level);
    }

    private int getResistanceAmplifier(int level, Player p) {
        double seaLevel = p.getWorld().getSeaLevel();
        double depth = seaLevel - p.getEyeLocation().getY();
        if (depth >= getDeepThreshold(level)) {
            return 1;
        }

        return 0;
    }

    private double getDepthThreshold(int level) {
        return Math.max(2, getConfig().depthThresholdBase - (getLevelPercent(level) * getConfig().depthThresholdFactor));
    }

    private double getDeepThreshold(int level) {
        return Math.max(4, getConfig().deepThresholdBase - (getLevelPercent(level) * getConfig().deepThresholdFactor));
    }

    private double getDamageReduction(int level) {
        return Math.min(getConfig().maxDamageReduction, getConfig().damageReductionBase + (getLevelPercent(level) * getConfig().damageReductionFactor));
    }

    private double getFatigueTrimChance(int level) {
        return Math.min(1.0, getConfig().fatigueTrimChanceBase + (getLevelPercent(level) * getConfig().fatigueTrimChanceFactor));
    }

    private int getFatigueTrimAmount(int level) {
        return Math.max(1, (int) Math.round(getConfig().fatigueTrimAmountBase + (getLevelPercent(level) * getConfig().fatigueTrimAmountFactor)));
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
    @ConfigDescription("Gain depth scaling protection underwater and partially counter mining fatigue in deep ocean play.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Depth Threshold Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double depthThresholdBase = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Depth Threshold Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double depthThresholdFactor = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Deep Threshold Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double deepThresholdBase = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Deep Threshold Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double deepThresholdFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageReductionBase = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageReductionFactor = 0.26;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Maximum Damage Reduction for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxDamageReduction = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Chance Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fatigueTrimChanceBase = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Chance Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fatigueTrimChanceFactor = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Amount Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fatigueTrimAmountBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Amount Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fatigueTrimAmountFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Effect Ticks for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int effectTicks = 60;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fatigue Replace Ticks for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int fatigueReplaceTicks = 80;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls XP Per Depth Pulse for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerDepthPulse = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls XP Pulse Cooldown Millis for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long xpPulseCooldownMillis = 3000;
    }
}
