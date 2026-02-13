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

package com.volmit.adapt.content.adaptation.sword;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import de.slikey.effectlib.effect.BleedEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class SwordsCrimsonCyclone extends SimpleAdaptation<SwordsCrimsonCyclone.Config> {
    public SwordsCrimsonCyclone() {
        super("sword-crimson-cyclone");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("sword.crimson_cyclone.description"));
        setDisplayName(Localizer.dLocalize("sword.crimson_cyclone.name"));
        setIcon(Material.NETHERITE_SWORD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD)
                .key("challenge_swords_cyclone_500")
                .title(Localizer.dLocalize("advancement.challenge_swords_cyclone_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_swords_cyclone_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_SWORD)
                        .key("challenge_swords_cyclone_5k")
                        .title(Localizer.dLocalize("advancement.challenge_swords_cyclone_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_swords_cyclone_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_swords_cyclone_500", "swords.crimson-cyclone.mobs-hit", 500, 400);
        registerMilestone("challenge_swords_cyclone_5k", "swords.crimson-cyclone.mobs-hit", 5000, 1500);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.NETHERITE_SWORD)
                .key("challenge_swords_cyclone_6")
                .title(Localizer.dLocalize("advancement.challenge_swords_cyclone_6.title"))
                .description(Localizer.dLocalize("advancement.challenge_swords_cyclone_6.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + C.GRAY + " " + Localizer.dLocalize("sword.crimson_cyclone.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getBaseDamage(level), 2) + C.GRAY + " " + Localizer.dLocalize("sword.crimson_cyclone.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("sword.crimson_cyclone.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity primaryTarget)) {
            return;
        }

        if (!hasAdaptation(p)) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isSword(hand) || p.hasCooldown(hand.getType()) || !isCritTrigger(p)) {
            return;
        }

        if (primaryTarget instanceof Player victim) {
            if (!canPVP(p, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(p, primaryTarget.getLocation())) {
            return;
        }

        int level = getLevel(p);
        int hungerCost = getHungerCost(level);
        if (p.getFoodLevel() < hungerCost) {
            return;
        }

        if (!applyDurabilityCost(hand, getDurabilityCost(level))) {
            return;
        }

        int hits = 0;
        double radius = getRadius(level);
        double damage = getBaseDamage(level);
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
        p.setCooldown(hand.getType(), getCooldownTicks(level));

        e.setDamage(e.getDamage() + damage);
        applyBleed(primaryTarget, level);
        if (areParticlesEnabled()) {
            primaryTarget.getWorld().spawnParticle(Particle.CRIMSON_SPORE, primaryTarget.getLocation().add(0, 0.8, 0), 8, 0.2, 0.35, 0.2, 0.01);
        }
        hits++;

        for (Entity entity : primaryTarget.getWorld().getNearbyEntities(primaryTarget.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }

            if (target == p || target == primaryTarget) {
                continue;
            }

            if (target instanceof Player victim) {
                if (!canPVP(p, victim.getLocation())) {
                    continue;
                }
            } else if (!canPVE(p, target.getLocation())) {
                continue;
            }

            target.damage(damage, p);
            applyBleed(target, level);
            if (areParticlesEnabled()) {
                target.getWorld().spawnParticle(Particle.CRIMSON_SPORE, target.getLocation().add(0, 0.8, 0), 8, 0.2, 0.35, 0.2, 0.01);
            }
            hits++;
        }

        if (hits <= 0) {
            return;
        }

        if (areParticlesEnabled()) {

            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, primaryTarget.getLocation().add(0, 1, 0), 2, 0.4, 0.1, 0.4, 0.02);

        }
        if (areParticlesEnabled()) {
            p.getWorld().spawnParticle(Particle.CRIMSON_SPORE, primaryTarget.getLocation().add(0, 1, 0), 36, 0.8, 0.4, 0.8, 0.02);
        }
        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(primaryTarget.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        sp.play(primaryTarget.getLocation(), Sound.ENTITY_WITHER_HURT, 0.65f, 1.45f);
        xp(p, hits * getConfig().xpPerTargetHit);
        getPlayer(p).getData().addStat("swords.crimson-cyclone.mobs-hit", hits);

        // Special achievement: hit 6+ mobs with one activation
        if (hits >= 6 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_swords_cyclone_6")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_swords_cyclone_6");
        }
    }

    private boolean isCritTrigger(Player p) {
        return p.getFallDistance() >= getConfig().minFallDistanceForCrit
                && !p.isOnGround()
                && !p.isInWater()
                && !p.isInsideVehicle()
                && !p.isClimbing();
    }

    private boolean applyDurabilityCost(ItemStack hand, int durabilityCost) {
        if (!(hand.getItemMeta() instanceof Damageable damageable) || hand.getType().getMaxDurability() <= 0) {
            return true;
        }

        int max = hand.getType().getMaxDurability();
        int next = damageable.getDamage() + durabilityCost;
        if (next >= max) {
            return false;
        }

        damageable.setDamage(next);
        hand.setItemMeta(damageable);
        return true;
    }

    private double getRadius(int level) {
        return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    }

    private double getBaseDamage(int level) {
        return getConfig().baseDamage + (getLevelPercent(level) * getConfig().damageFactor);
    }

    private void applyBleed(LivingEntity target, int level) {
        BleedEffect bleed = new DamagingBleedEffect(Adapt.instance.adaptEffectManager, getBleedDamagePerProc(level), target);
        bleed.setEntity(target);
        bleed.material = getConfig().showBleedParticles ? Material.CRIMSON_ROOTS : Material.VOID_AIR;
        bleed.height = -1;
        bleed.period = 5;
        bleed.hurt = false;
        bleed.iterations = Math.max(4, (int) Math.ceil(getBleedTicks(level) / 5D));
        bleed.start();
    }

    private int getBleedTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().bleedTicksBase + (getLevelPercent(level) * getConfig().bleedTicksFactor)));
    }

    private double getBleedDamagePerProc(int level) {
        return Math.max(0.01, getConfig().bleedDamagePerProcBase + (getLevelPercent(level) * getConfig().bleedDamagePerProcFactor));
    }

    private int getHungerCost(int level) {
        return Math.max(1, (int) Math.round(getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostFactor)));
    }

    private int getDurabilityCost(int level) {
        return Math.max(1, (int) Math.round(getConfig().durabilityCostBase - (getLevelPercent(level) * getConfig().durabilityCostFactor)));
    }

    private int getCooldownTicks(int level) {
        return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
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
    @ConfigDescription("Land a sword crit while falling to unleash a bleeding crimson cyclone around your target.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Bleed Particles for the Swords Crimson Cyclone adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showBleedParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.76;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 2.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 2.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseDamage = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageFactor = 4.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bleedTicksBase = 40;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bleedTicksFactor = 90;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bleedDamagePerProcBase = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bleedDamagePerProcFactor = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hungerCostBase = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hungerCostFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durabilityCostBase = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durabilityCostFactor = 1.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 320;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 160;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Fall Distance For Crit for the Swords Crimson Cyclone adaptation.", impact = "Minimum fall distance required to trigger the cyclone on hit.")
        float minFallDistanceForCrit = 0.08f;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Hit for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerTargetHit = 10;
    }
}
