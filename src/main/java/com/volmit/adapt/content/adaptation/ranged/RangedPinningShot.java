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
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RangedPinningShot extends SimpleAdaptation<RangedPinningShot.Config> {
    private final Map<UUID, Long> targetProcTimes = new HashMap<>();

    public RangedPinningShot() {
        super("ranged-pinning-shot");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.pinning_shot.description"));
        setDisplayName(Localizer.dLocalize("ranged.pinning_shot.name"));
        setIcon(Material.TRIPWIRE_HOOK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2200);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ARROW)
                .key("challenge_ranged_pinning_300")
                .title(Localizer.dLocalize("advancement.challenge_ranged_pinning_300.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_pinning_300.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_ranged_pinning_300", "ranged.pinning-shot.targets-pinned", 300, 400);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getProcChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("ranged.pinning_shot.lore1"));
        v.addLore(C.GREEN + "+ " + Form.duration(getDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("ranged.pinning_shot.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getReapplyCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("ranged.pinning_shot.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player p) || !hasAdaptation(p) || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(p, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(p, target.getLocation())) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupExpired(now);
        int level = getLevel(p);
        long reapply = getReapplyCooldownMillis(level);
        Long last = targetProcTimes.get(target.getUniqueId());
        if (last != null && last + reapply > now) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
            return;
        }

        targetProcTimes.put(target.getUniqueId(), now);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getDurationTicks(level), getAmplifier(level), true, true, true), true);
        getPlayer(p).getData().addStat("ranged.pinning-shot.targets-pinned", 1);

        if (getConfig().dampenVelocityOnProc) {
            Vector v = target.getVelocity();
            target.setVelocity(new Vector(v.getX() * getConfig().horizontalVelocityFactor, v.getY(), v.getZ() * getConfig().horizontalVelocityFactor));
        }

        if (areParticlesEnabled()) {

            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 0.9, 0), 18, 0.3, 0.45, 0.3, 0.08);

        }
        if (areParticlesEnabled()) {
            target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1.0, 0), 28, 0.35, 0.5, 0.35, 0.35);

        }
        SoundPlayer sp = SoundPlayer.of(target.getWorld());
        sp.play(target.getLocation(), Sound.BLOCK_BELL_USE, 1.1f, 0.48f);
        sp.play(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.55f);
        xp(p, getConfig().xpOnProc);
    }

    private void cleanupExpired(long now) {
        if (targetProcTimes.size() < getConfig().cleanupThreshold) {
            return;
        }

        targetProcTimes.entrySet().removeIf(entry -> entry.getValue() + getConfig().entryTtlMillis < now);
    }

    private double getProcChance(int level) {
        return Math.min(getConfig().maxProcChance, getConfig().procChanceBase + (getLevelPercent(level) * getConfig().procChanceFactor));
    }

    private int getDurationTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
    }

    private int getAmplifier(int level) {
        return Math.max(0, (int) Math.floor(getConfig().amplifierBase + (getLevelPercent(level) * getConfig().amplifierFactor)));
    }

    private long getReapplyCooldownMillis(int level) {
        return Math.max(1000, (long) Math.round(getConfig().reapplyCooldownMillisBase - (getLevelPercent(level) * getConfig().reapplyCooldownMillisFactor)));
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
    @ConfigDescription("Projectiles can pin targets with heavy slowness.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dampen Velocity On Proc for the Ranged Pinning Shot adaptation.", impact = "True enables this behavior and false disables it.")
        boolean dampenVelocityOnProc = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.74;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double procChanceBase = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double procChanceFactor = 0.42;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Proc Chance for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxProcChance = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durationTicksBase = 30;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durationTicksFactor = 90;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Amplifier Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double amplifierBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Amplifier Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double amplifierFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reapply Cooldown Millis Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reapplyCooldownMillisBase = 5000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reapply Cooldown Millis Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reapplyCooldownMillisFactor = 2800;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Horizontal Velocity Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double horizontalVelocityFactor = 0.15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cleanup Threshold for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int cleanupThreshold = 128;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Entry Ttl Millis for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long entryTtlMillis = 60000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp On Proc for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnProc = 12;
    }
}
