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

package com.volmit.adapt.content.adaptation.tragoul;

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
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TragoulBloodPact extends SimpleAdaptation<TragoulBloodPact.Config> {
    private static final PotionEffectType[] EFFECT_POOL = {
            PotionEffectType.SPEED,
            PotionEffectType.REGENERATION,
            PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.ABSORPTION,
            PotionEffectType.JUMP_BOOST,
            PotionEffectType.NIGHT_VISION
    };

    private final Map<UUID, Long> procCooldowns = new HashMap<>();
    private final Map<UUID, Boolean> lowHealthProcs = new HashMap<>();

    public TragoulBloodPact() {
        super("tragoul-blood-pact");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("tragoul.blood_pact.description"));
        setDisplayName(Localizer.dLocalize("tragoul.blood_pact.name"));
        setIcon(Material.REDSTONE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(20);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.REDSTONE)
                .key("challenge_tragoul_pact_200")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_pact_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_pact_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.NETHERITE_SWORD)
                .key("challenge_tragoul_pact_kills_500")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_pact_kills_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_pact_kills_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_pact_200").goal(200).stat("tragoul.blood-pact.health-sacrificed").reward(400).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_pact_kills_500").goal(500).stat("tragoul.blood-pact.empowered-kills").reward(1000).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.REDSTONE)
                .key("challenge_tragoul_pact_all_in")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_pact_all_in.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_pact_all_in.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getProcChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.blood_pact.lore1"));
        v.addLore(C.GREEN + "+ " + Form.duration(getEffectDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.blood_pact.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getProcCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.blood_pact.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        procCooldowns.remove(e.getPlayer().getUniqueId());
        lowHealthProcs.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        int level = getLevel(p);
        if (level <= 0 || e.getFinalDamage() < getMinTriggerDamage()) {
            return;
        }

        long now = System.currentTimeMillis();
        long until = procCooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < until) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
            return;
        }

        procCooldowns.put(p.getUniqueId(), now + getProcCooldownMillis(level));
        getPlayer(p).getData().addStat("tragoul.blood-pact.health-sacrificed", (int) e.getFinalDamage());
        if (p.getHealth() - e.getFinalDamage() <= 6.0) {
            lowHealthProcs.put(p.getUniqueId(), true);
        }
        applyRandomBuffs(p, level, e.getFinalDamage());
        p.getWorld().spawnParticle(Particle.CRIMSON_SPORE, p.getLocation().add(0, 1.0, 0), 22, 0.28, 0.42, 0.28, 0.02);
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.62f, 1.25f);
        xp(p, getConfig().xpPerProc);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmgEvent) {
            if (dmgEvent.getDamager() instanceof Player p && hasAdaptation(p)) {
                if (p.hasPotionEffect(PotionEffectType.ABSORPTION) || p.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                    getPlayer(p).getData().addStat("tragoul.blood-pact.empowered-kills", 1);
                    if (lowHealthProcs.getOrDefault(p.getUniqueId(), false) && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_tragoul_pact_all_in")) {
                        getPlayer(p).getAdvancementHandler().grant("challenge_tragoul_pact_all_in");
                    }
                }
            }
        }
    }

    private void applyRandomBuffs(Player p, int level, double takenDamage) {
        int count = getBuffCount(level);
        if (takenDamage >= (getMinTriggerDamage() * 1.6)) {
            count++;
        }
        if (ThreadLocalRandom.current().nextDouble() <= getBonusBuffChance(level)) {
            count++;
        }

        List<PotionEffectType> pool = new ArrayList<>(List.of(EFFECT_POOL));
        Collections.shuffle(pool);
        count = Math.min(count, pool.size());

        int duration = getEffectDurationTicks(level);
        for (int i = 0; i < count; i++) {
            PotionEffectType type = pool.get(i);
            int amplifier = getEffectAmplifier(type, level);
            int d = type == PotionEffectType.ABSORPTION ? Math.max(40, duration - 20) : duration;
            p.addPotionEffect(new PotionEffect(type, d, amplifier, false, true, true), true);
        }
    }

    private double getProcChance(int level) {
        return Math.min(getConfig().maxProcChance,
                Math.max(0, getConfig().procChanceBase + (getLevelPercent(level) * getConfig().procChanceFactor)));
    }

    private long getProcCooldownMillis(int level) {
        return Math.max(500L, (long) Math.round(getConfig().procCooldownMillisBase - (getLevelPercent(level) * getConfig().procCooldownMillisFactor)));
    }

    private int getEffectDurationTicks(int level) {
        return Math.max(40, (int) Math.round(getConfig().effectDurationTicksBase + (getLevelPercent(level) * getConfig().effectDurationTicksFactor)));
    }

    private int getBuffCount(int level) {
        return Math.max(1, (int) Math.round(getConfig().buffCountBase + (getLevelPercent(level) * getConfig().buffCountFactor)));
    }

    private double getBonusBuffChance(int level) {
        return Math.min(0.9, Math.max(0, getConfig().bonusBuffChanceBase + (getLevelPercent(level) * getConfig().bonusBuffChanceFactor)));
    }

    private int getEffectAmplifier(PotionEffectType type, int level) {
        double progress = getLevelPercent(level);
        if (type == PotionEffectType.ABSORPTION || type == PotionEffectType.RESISTANCE || type == PotionEffectType.REGENERATION) {
            return progress >= 0.85 ? 1 : 0;
        }
        return progress >= 0.7 ? 1 : 0;
    }

    private double getMinTriggerDamage() {
        return Math.max(1, getConfig().minDamageTriggerHearts * 2D);
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        procCooldowns.entrySet().removeIf(i -> i.getValue() <= now);
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
    @ConfigDescription("Taking at least 5 hearts of damage can trigger temporary beneficial effects.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.78;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Damage Trigger Hearts for the Tragoul Blood Pact adaptation.", impact = "Minimum damage taken in hearts required before the proc roll happens.")
        double minDamageTriggerHearts = 5.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double procChanceBase = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double procChanceFactor = 0.38;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Proc Chance for the Tragoul Blood Pact adaptation.", impact = "Caps chance at the requested maximum.")
        double maxProcChance = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Proc Cooldown Millis Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double procCooldownMillisBase = 22000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Proc Cooldown Millis Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double procCooldownMillisFactor = 14000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Effect Duration Ticks Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double effectDurationTicksBase = 100;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Effect Duration Ticks Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double effectDurationTicksFactor = 150;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Buff Count Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double buffCountBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Buff Count Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double buffCountFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Buff Chance Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusBuffChanceBase = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Buff Chance Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusBuffChanceFactor = 0.34;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls XP Per Proc for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerProc = 14;
    }
}
