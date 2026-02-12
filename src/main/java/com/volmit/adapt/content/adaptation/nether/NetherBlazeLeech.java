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

package com.volmit.adapt.content.adaptation.nether;

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
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public class NetherBlazeLeech extends SimpleAdaptation<NetherBlazeLeech.Config> {
    public NetherBlazeLeech() {
        super("nether-blaze-leech");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether.blaze_leech.description"));
        setDisplayName(Localizer.dLocalize("nether.blaze_leech.name"));
        setIcon(Material.BLAZE_POWDER);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(900);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BLAZE_ROD)
                .key("challenge_nether_blaze_200")
                .title(Localizer.dLocalize("advancement.challenge_nether_blaze_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_nether_blaze_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BLAZE_POWDER)
                        .key("challenge_nether_blaze_2500")
                        .title(Localizer.dLocalize("advancement.challenge_nether_blaze_2500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_nether_blaze_2500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_nether_blaze_200").goal(200).stat("nether.blaze-leech.health-from-fire").reward(300).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_nether_blaze_2500").goal(2500).stat("nether.blaze-leech.health-from-fire").reward(1000).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getTriggerChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.blaze_leech.lore1"));
        v.addLore(C.GREEN + "+ " + Form.duration(getRegenTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("nether.blaze_leech.lore2"));
        v.addLore(C.GREEN + "+ " + Form.f(getFoodRestore(level)) + C.GRAY + " " + Localizer.dLocalize("nether.blaze_leech.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        if (!isFireCause(e.getCause()) || !isReady(p, getLevel(p))) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > getTriggerChance(getLevel(p))) {
            return;
        }

        applyLeech(p, getLevel(p), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player p) || !hasAdaptation(p) || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(p, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(p, target.getLocation())) {
            return;
        }

        if (target.getFireTicks() <= 0 || !isReady(p, getLevel(p))) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > getTriggerChance(getLevel(p))) {
            return;
        }

        applyLeech(p, getLevel(p), false);
    }

    private boolean isFireCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private boolean isReady(Player p, int level) {
        long now = System.currentTimeMillis();
        long next = getStorageLong(p, "blazeLeechNext", 0L);
        if (next > now) {
            return false;
        }

        setStorage(p, "blazeLeechNext", now + getCooldownMillis(level));
        return true;
    }

    private void applyLeech(Player p, int level, boolean defensive) {
        int newFood = Math.min(20, p.getFoodLevel() + (int) Math.round(getFoodRestore(level)));
        p.setFoodLevel(newFood);
        float sat = Math.min(20f, p.getSaturation() + (float) getConfig().saturationRestore);
        p.setSaturation(sat);

        int amp = Math.max(0, (int) Math.floor(getRegenAmplifier(level)));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, getRegenTicks(level), amp, true, false, true), true);

        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 0.45f, defensive ? 1.4f : 1.7f);
        xp(p, defensive ? getConfig().xpOnDefensiveProc : getConfig().xpOnOffensiveProc);
        getPlayer(p).getData().addStat("nether.blaze-leech.health-from-fire", 1);
    }

    private double getTriggerChance(int level) {
        return Math.min(getConfig().maxTriggerChance, getConfig().triggerChanceBase + (getLevelPercent(level) * getConfig().triggerChanceFactor));
    }

    private int getRegenTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().regenTicksBase + (getLevelPercent(level) * getConfig().regenTicksFactor)));
    }

    private double getRegenAmplifier(int level) {
        return getConfig().regenAmplifierBase + (getLevelPercent(level) * getConfig().regenAmplifierFactor);
    }

    private double getFoodRestore(int level) {
        return getConfig().foodRestoreBase + (getLevelPercent(level) * getConfig().foodRestoreFactor);
    }

    private long getCooldownMillis(int level) {
        return Math.max(100L, Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
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
    @ConfigDescription("Fire interactions can grant hunger and regeneration in short bursts.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.62;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Trigger Chance Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double triggerChanceBase = 0.16;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Trigger Chance Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double triggerChanceFactor = 0.34;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Trigger Chance for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxTriggerChance = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Regen Ticks Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double regenTicksBase = 28;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Regen Ticks Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double regenTicksFactor = 42;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Regen Amplifier Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double regenAmplifierBase = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Regen Amplifier Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double regenAmplifierFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Restore Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodRestoreBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Restore Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodRestoreFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Saturation Restore for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double saturationRestore = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 1400;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 900;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp On Defensive Proc for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnDefensiveProc = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp On Offensive Proc for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnOffensiveProc = 5;
    }
}
