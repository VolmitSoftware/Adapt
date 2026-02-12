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

package com.volmit.adapt.content.adaptation.blocking;

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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockingBulwarkBash extends SimpleAdaptation<BlockingBulwarkBash.Config> {
    private final Map<UUID, Long> lastSprintMillis = new HashMap<>();

    public BlockingBulwarkBash() {
        super("blocking-bulwark-bash");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("blocking.bulwark_bash.description"));
        setDisplayName(Localizer.dLocalize("blocking.bulwark_bash.name"));
        setIcon(Material.SHIELD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRange(level)) + C.GRAY + " " + Localizer.dLocalize("blocking.bulwark_bash.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getDamageBonus(level), 0) + C.GRAY + " " + Localizer.dLocalize("blocking.bulwark_bash.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("blocking.bulwark_bash.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerToggleSprintEvent e) {
        if (e.isSprinting()) {
            lastSprintMillis.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        lastSprintMillis.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target) || !hasAdaptation(p)) {
            return;
        }

        if (p.getInventory().getItemInOffHand().getType() != Material.SHIELD || p.hasCooldown(Material.SHIELD)) {
            return;
        }

        if (!isJumpCrit(p) || !wasRecentlySprinting(p)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(p, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(p, target.getLocation())) {
            return;
        }

        int level = getLevel(p);
        int affected = 0;
        double radius = getRange(level);
        for (org.bukkit.entity.Entity nearby : target.getWorld().getNearbyEntities(target.getLocation(), radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity hit) || hit == p) {
                continue;
            }

            if (hit instanceof Player victim) {
                if (!canPVP(p, victim.getLocation())) {
                    continue;
                }
            } else if (!canPVE(p, hit.getLocation())) {
                continue;
            }

            applyImpact(p, hit, level);
            affected++;
        }

        if (affected <= 0) {
            return;
        }

        e.setDamage(e.getDamage() + getBaseDamage(level));
        p.setCooldown(Material.SHIELD, getCooldownTicks(level));
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.3, 0), 18, 0.35, 0.1, 0.35, 0.06);
        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.85f);
        sp.play(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
        xp(p, getConfig().xpPerTargetHit * affected);
    }

    private void applyImpact(Player p, LivingEntity target, int level) {
        Vector kb = target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (kb.lengthSquared() <= 0.0001) {
            kb = p.getLocation().getDirection().setY(0);
        }
        if (kb.lengthSquared() <= 0.0001) {
            return;
        }

        kb.normalize();
        target.setVelocity(target.getVelocity().multiply(0.25).add(kb.multiply(getKnockback(level)).setY(getUpwardKnockback(level))));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getStunTicks(level), getStunAmplifier(level), false, false, true), true);
    }

    private boolean wasRecentlySprinting(Player p) {
        long last = lastSprintMillis.getOrDefault(p.getUniqueId(), 0L);
        return p.isSprinting() || (System.currentTimeMillis() - last) <= getConfig().recentSprintWindowMillis;
    }

    private boolean isJumpCrit(Player p) {
        return p.getFallDistance() > getConfig().minFallDistanceForCrit
                && !p.isOnGround()
                && !p.isInWater()
                && !p.isInsideVehicle()
                && !p.isClimbing();
    }

    private double getRange(int level) {
        return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
    }

    private double getDamageBonus(int level) {
        return getConfig().damageBonusBase + (getLevelPercent(level) * getConfig().damageBonusFactor);
    }

    private double getBaseDamage(int level) {
        return getConfig().baseDamage + getDamageBonus(level);
    }

    private double getKnockback(int level) {
        return getConfig().knockbackBase + (getLevelPercent(level) * getConfig().knockbackFactor);
    }

    private double getUpwardKnockback(int level) {
        return getConfig().upwardKnockbackBase + (getLevelPercent(level) * getConfig().upwardKnockbackFactor);
    }

    private int getStunTicks(int level) {
        return Math.max(10, (int) Math.round(getConfig().stunTicksBase + (getLevelPercent(level) * getConfig().stunTicksFactor)));
    }

    private int getStunAmplifier(int level) {
        return Math.max(0, (int) Math.round(getConfig().stunAmplifierBase + (getLevelPercent(level) * getConfig().stunAmplifierFactor)));
    }

    private int getCooldownTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
    }

    @Override
    public void onTick() {
        long cutoff = System.currentTimeMillis() - Math.max(1000L, getConfig().recentSprintWindowMillis * 3L);
        lastSprintMillis.entrySet().removeIf(i -> i.getValue() < cutoff);
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
    @ConfigDescription("Sprint, jump, and land a shielded crit to unleash a bash shockwave.")
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseDamage = 1.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBonusBase = 0.3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBonusFactor = 2.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rangeBase = 2.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rangeFactor = 1.8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Knockback Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double knockbackBase = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Knockback Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double knockbackFactor = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Upward Knockback Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double upwardKnockbackBase = 0.18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Upward Knockback Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double upwardKnockbackFactor = 0.14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stun Ticks Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double stunTicksBase = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stun Ticks Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double stunTicksFactor = 24;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stun Amplifier Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double stunAmplifierBase = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stun Amplifier Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double stunAmplifierFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 220;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 120;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Fall Distance For Crit for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        float minFallDistanceForCrit = 0.08f;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Recent Sprint Window Millis for the Blocking Bulwark Bash adaptation.", impact = "Allows crit bash to trigger shortly after sprint momentum, even if sprint toggles off mid-air.")
        long recentSprintWindowMillis = 900L;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Hit for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerTargetHit = 8;
    }
}
