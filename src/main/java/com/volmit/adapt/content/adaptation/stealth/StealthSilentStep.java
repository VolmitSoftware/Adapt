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

package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StealthSilentStep extends SimpleAdaptation<StealthSilentStep.Config> {
    private final Map<UUID, Boolean> dimmed = new HashMap<>();
    private final Map<UUID, List<Long>> recentBackstabs = new HashMap<>();

    public StealthSilentStep() {
        super("stealth-silent-step");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("stealth.silent_step.description"));
        setDisplayName(Localizer.dLocalize("stealth.silent_step.name"));
        setIcon(Material.WHITE_WOOL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(10);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD)
                .key("challenge_stealth_silent_200")
                .title(Localizer.dLocalize("advancement.challenge_stealth_silent_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_silent_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DIAMOND_SWORD)
                .key("challenge_stealth_silent_5in10")
                .title(Localizer.dLocalize("advancement.challenge_stealth_silent_5in10.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_silent_5in10.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_silent_200").goal(200).stat("stealth.silent-step.backstabs").reward(400).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getStealthRadius(level)) + C.GRAY + " " + Localizer.dLocalize("stealth.silent_step.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getMobBackstabMultiplier(level) - 1D, 0) + C.GRAY + " " + Localizer.dLocalize("stealth.silent_step.lore2"));
        v.addLore(C.GREEN + "+ " + Form.pc(getPlayerBackstabMultiplier(level) - 1D, 0) + C.GRAY + " " + Localizer.dLocalize("stealth.silent_step.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        clearDimming(e.getPlayer());
        recentBackstabs.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Mob) || !(e.getTarget() instanceof Player p)) {
            return;
        }

        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        double radius = getStealthRadius(getLevel(p));
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            if (mob.getTarget() == p) {
                mob.setTarget(null);
                xp(p, getConfig().xpPerTargetDrop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player attacker) || !hasAdaptation(attacker)) {
            return;
        }

        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(attacker, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(attacker, target.getLocation())) {
            return;
        }

        boolean unseen = attacker.hasPotionEffect(PotionEffectType.INVISIBILITY) || !isLookingAt(target, attacker);
        if (target == attacker || !unseen) {
            return;
        }

        int level = getLevel(attacker);
        double multiplier = (target instanceof Player) ? getPlayerBackstabMultiplier(level) : getMobBackstabMultiplier(level);
        e.setDamage(e.getDamage() * multiplier);
        xp(attacker, e.getDamage() * getConfig().xpPerBonusDamage);
        getPlayer(attacker).getData().addStat("stealth.silent-step.backstabs", 1);

        long now = System.currentTimeMillis();
        UUID uid = attacker.getUniqueId();
        recentBackstabs.computeIfAbsent(uid, k -> new ArrayList<>()).add(now);
        recentBackstabs.get(uid).removeIf(t -> now - t > 10000);
        if (recentBackstabs.get(uid).size() >= 5
                && AdaptConfig.get().isAdvancements()
                && !getPlayer(attacker).getData().isGranted("challenge_stealth_silent_5in10")) {
            getPlayer(attacker).getAdvancementHandler().grant("challenge_stealth_silent_5in10");
        }
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(p) || !p.isSneaking()) {
                clearDimming(p);
                continue;
            }

            int level = getLevel(p);
            p.setFallDistance(Math.min(p.getFallDistance(), getConfig().maxSilentFallDistance));
            if (isUndetected(p, level)) {
                applyDimming(p, level);
            } else {
                clearDimming(p);
            }
        }
    }

    private boolean isUndetected(Player p, int level) {
        double mobRadius = getStealthRadius(level);
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), mobRadius, mobRadius, mobRadius)) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            if (mob.hasLineOfSight(p) && isLookingAt(mob, p)) {
                return false;
            }
        }

        double playerRadius = getPlayerDetectionRadius(level);
        double rs = playerRadius * playerRadius;
        for (Player other : p.getWorld().getPlayers()) {
            if (other == p || other.isDead()) {
                continue;
            }

            if (other.getLocation().distanceSquared(p.getLocation()) > rs) {
                continue;
            }

            if (other.hasLineOfSight(p) && isLookingAt(other, p)) {
                return false;
            }
        }

        return true;
    }

    private void applyDimming(Player p, int level) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, getDimDurationTicks(level), getConfig().dimAmplifier, false, false, false), true);
        dimmed.put(p.getUniqueId(), true);
    }

    private void clearDimming(Player p) {
        if (dimmed.remove(p.getUniqueId()) != null) {
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    private boolean isLookingAt(LivingEntity observer, LivingEntity target) {
        Vector look = observer.getEyeLocation().getDirection().normalize();
        Vector toTarget = target.getEyeLocation().toVector().subtract(observer.getEyeLocation().toVector());
        if (toTarget.lengthSquared() <= 0.0001) {
            return true;
        }

        toTarget.normalize();
        return look.dot(toTarget) >= getConfig().lookDotThreshold;
    }

    private double getStealthRadius(int level) {
        return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    }

    private double getPlayerDetectionRadius(int level) {
        return getConfig().playerDetectionRadiusBase + (getLevelPercent(level) * getConfig().playerDetectionRadiusFactor);
    }

    private int getDimDurationTicks(int level) {
        return Math.max(10, (int) Math.round(getConfig().dimDurationTicksBase + (getLevelPercent(level) * getConfig().dimDurationTicksFactor)));
    }

    private double getMobBackstabMultiplier(int level) {
        return getConfig().mobBackstabBase + (getLevelPercent(level) * getConfig().mobBackstabFactor);
    }

    private double getPlayerBackstabMultiplier(int level) {
        return getConfig().playerBackstabBase + (getLevelPercent(level) * getConfig().playerBackstabFactor);
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
    @ConfigDescription("Sneaking prevents hostile mob detection, and unseen hits deal backstab damage.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerDetectionRadiusBase = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerDetectionRadiusFactor = 14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dimDurationTicksBase = 20;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dimDurationTicksFactor = 20;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dim Amplifier for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int dimAmplifier = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mobBackstabBase = 1.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mobBackstabFactor = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerBackstabBase = 1.25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerBackstabFactor = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Look Dot Threshold for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double lookDotThreshold = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Drop for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerTargetDrop = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Damage for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerBonusDamage = 3.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Silent Fall Distance for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        float maxSilentFallDistance = 1.6f;
    }
}
