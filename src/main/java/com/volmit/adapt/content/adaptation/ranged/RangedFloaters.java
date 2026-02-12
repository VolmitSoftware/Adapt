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

import java.util.concurrent.ThreadLocalRandom;

public class RangedFloaters extends SimpleAdaptation<RangedFloaters.Config> {
    public RangedFloaters() {
        super("ranged-floaters");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.floaters.description"));
        setDisplayName(Localizer.dLocalize("ranged.floaters.name"));
        setIcon(Material.SHULKER_SHELL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHULKER_SHELL)
                .key("challenge_ranged_floaters_200")
                .title(Localizer.dLocalize("advancement.challenge_ranged_floaters_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_floaters_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_floaters_200").goal(200).stat("ranged.floaters.targets-levitated").reward(300).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getProcChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("ranged.floaters.lore1"));
        v.addLore(C.GREEN + "+ " + Form.duration(getDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("ranged.floaters.lore2"));
        v.addLore(C.GREEN + "+ " + (1 + getAmplifier(level)) + C.GRAY + " " + Localizer.dLocalize("ranged.floaters.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }

        if (!(e.getDamager() instanceof Projectile projectile)) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        int level = getLevel(p);
        if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
            return;
        }

        target.addPotionEffect(new PotionEffect(
                PotionEffectType.LEVITATION,
                getDurationTicks(level),
                getAmplifier(level),
                true,
                true,
                true
        ), true);
        getPlayer(p).getData().addStat("ranged.floaters.targets-levitated", 1);

        if (getConfig().showParticles) {
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 10, 0.2, 0.5, 0.2, 0.02);
        }

        SoundPlayer.of(target.getWorld()).play(target.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 0.6f, 1.45f);
        xp(p, getConfig().skillXpOnProc);
    }

    private double getProcChance(int level) {
        return Math.min(getConfig().maxChance, getConfig().chanceBase + (getLevelPercent(level) * getConfig().chanceFactor));
    }

    private int getDurationTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
    }

    private int getAmplifier(int level) {
        return Math.max(0, (int) Math.floor(getLevelPercent(level) * getConfig().maxAmplifier));
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
    @ConfigDescription("Projectiles have a chance to apply levitation to targets.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Ranged Floaters adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.78;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Chance Base for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double chanceBase = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Chance Factor for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double chanceFactor = 0.58;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Chance for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxChance = 0.8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durationTicksBase = 26.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durationTicksFactor = 110.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Amplifier for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxAmplifier = 1.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Proc for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double skillXpOnProc = 8.0;
    }
}
