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

package com.volmit.adapt.content.adaptation.chronos;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChronosAberrantTouch extends SimpleAdaptation<ChronosAberrantTouch.Config> {
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, StackState> targetStacks;

    public ChronosAberrantTouch() {
        super("chronos-aberrant-touch");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("chronos.aberrant_touch.description"));
        setDisplayName(Localizer.dLocalize("chronos.aberrant_touch.name"));
        setIcon(Material.SPIDER_EYE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1000);
        cooldowns = new HashMap<>();
        targetStacks = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("chronos.aberrant_touch.lore1"));
        v.addLore(C.YELLOW + "+ " + getPvEDurationCapTicks(level) / 20D + "s " + Localizer.dLocalize("chronos.aberrant_touch.lore2"));
        v.addLore(C.RED + "* " + getConfig().playerAmplifierCap + " " + Localizer.dLocalize("chronos.aberrant_touch.lore3"));
        v.addLore(C.AQUA + "* " + getConfig().rootAtStacks + " stacks roots for " + (getConfig().rootDurationTicks / 20D) + "s");
    }

    private int getPvEAmplifierCap(int level) {
        return Math.max(0, Math.min(getConfig().entityAmplifierCap, level));
    }

    private int getPvEDurationCapTicks(int level) {
        return getConfig().entityDurationCapTicks + (level * getConfig().entityDurationCapPerLevelTicks);
    }

    private int getDurationAddedTicks(int level) {
        return getConfig().durationAddTicks + (level * getConfig().durationPerLevelTicks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !hasAdaptation(attacker)) {
            return;
        }

        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = cooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        if (cooldownUntil > now) {
            return;
        }

        if (target instanceof Player playerTarget) {
            if (!canPVP(attacker, playerTarget.getLocation())) {
                return;
            }
        } else {
            if (!canPVE(attacker, target.getLocation())) {
                return;
            }
        }

        if (!getPlayer(attacker).consumeFood(getConfig().hungerCost, getConfig().minimumFoodLevel)) {
            return;
        }

        int level = getLevel(attacker);
        int amplifierCap = target instanceof Player ? getConfig().playerAmplifierCap : getPvEAmplifierCap(level);
        int durationCap = target instanceof Player ? getConfig().playerDurationCapTicks : getPvEDurationCapTicks(level);

        PotionEffect existing = target.getPotionEffect(PotionEffectType.SLOWNESS);
        int currentAmplifier = existing == null ? -1 : existing.getAmplifier();
        int currentDuration = existing == null ? 0 : existing.getDuration();

        int newAmplifier = Math.min(amplifierCap, currentAmplifier + 1);
        int newDuration = Math.min(durationCap, currentDuration + getDurationAddedTicks(level));

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Math.max(20, newDuration), Math.max(0, newAmplifier), true, true, true), true);

        StackState state = targetStacks.getOrDefault(target.getUniqueId(), new StackState(0, 0L));
        int stacks = (now - state.lastHitMillis() > getConfig().stackResetMillis) ? 1 : state.stacks() + 1;
        if (stacks >= getConfig().rootAtStacks) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getConfig().rootDurationTicks, getConfig().rootAmplifier, true, true, true), true);
            target.setVelocity(new Vector());
            stacks = 0;
        }
        targetStacks.put(target.getUniqueId(), new StackState(stacks, now));

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playTouchProc(attacker, target.getLocation());
        }
        xp(attacker, attacker.getLocation(), getConfig().xpPerProc + (getConfig().xpPerLevel * level));
        cooldowns.put(attacker.getUniqueId(), now + getConfig().cooldownMillis);
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        targetStacks.entrySet().removeIf(entry -> now - entry.getValue().lastHitMillis() > getConfig().stackResetMillis);
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Aberrant Touch adaptation.", impact = "True enables this behavior and false disables it.")
        boolean playClockSounds = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.38;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Add Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int durationAddTicks = 30;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Per Level Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int durationPerLevelTicks = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Player Duration Cap Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int playerDurationCapTicks = 80;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Player Amplifier Cap for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int playerAmplifierCap = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Entity Duration Cap Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int entityDurationCapTicks = 120;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Entity Duration Cap Per Level Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int entityDurationCapPerLevelTicks = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Entity Amplifier Cap for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int entityAmplifierCap = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hungerCost = 1.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Food Level for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int minimumFoodLevel = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Root At Stacks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int rootAtStacks = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Root Duration Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int rootDurationTicks = 20;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Root Amplifier for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int rootAmplifier = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stack Reset Millis for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long stackResetMillis = 2500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownMillis = 250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Proc for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerProc = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Level for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerLevel = 1.25;
    }

    private record StackState(int stacks, long lastHitMillis) {
    }
}
