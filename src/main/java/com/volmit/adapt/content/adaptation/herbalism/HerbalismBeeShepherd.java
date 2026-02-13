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

package com.volmit.adapt.content.adaptation.herbalism;

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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HerbalismBeeShepherd extends SimpleAdaptation<HerbalismBeeShepherd.Config> {
    private final Map<UUID, Long> lastPulse = new HashMap<>();

    public HerbalismBeeShepherd() {
        super("herbalism-bee-shepherd");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.bee_shepherd.description"));
        setDisplayName(Localizer.dLocalize("herbalism.bee_shepherd.name"));
        setIcon(Material.BEE_NEST);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(10);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.HONEYCOMB)
                .key("challenge_herbalism_bee_100")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_bee_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_bee_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_herbalism_bee_100", "herbalism.bee-shepherd.bees-attracted", 100, 300);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + C.GRAY + " " + Localizer.dLocalize("herbalism.bee_shepherd.lore1"));
        v.addLore(C.GREEN + "+ " + getGrowthAttempts(level) + C.GRAY + " " + Localizer.dLocalize("herbalism.bee_shepherd.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getPulseMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("herbalism.bee_shepherd.lore3"));
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            if (!hasAdaptation(p) || !isHoldingFlower(p)) {
                continue;
            }

            int level = getLevel(p);
            if (now - lastPulse.getOrDefault(p.getUniqueId(), 0L) < getPulseMillis(level)) {
                continue;
            }

            int foodCost = getFoodCost(level);
            if (p.getFoodLevel() < foodCost) {
                continue;
            }

            int grown = pulseGrowth(p, level);
            int attracted = pullNearbyBees(p, level);
            lastPulse.put(p.getUniqueId(), now);
            if (attracted > 0) {
                getPlayer(p).getData().addStat("herbalism.bee-shepherd.bees-attracted", attracted);
            }
            if (grown <= 0) {
                continue;
            }

            p.setFoodLevel(Math.max(0, p.getFoodLevel() - foodCost));
            if (areParticlesEnabled()) {
                p.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 12, 0.5, 0.4, 0.5, 0.1);
            }
            SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ENTITY_BEE_POLLINATE, 0.85f, 1.25f);
            xp(p, grown * getConfig().xpPerGrowth);
        }
    }

    private int pulseGrowth(Player p, int level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = Math.max(1, (int) Math.round(getRadius(level)));
        int grown = 0;
        int attempts = getGrowthAttempts(level);
        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(-radius, radius + 1);
            int dz = random.nextInt(-radius, radius + 1);
            int dy = random.nextInt(-1, 2);
            Block block = p.getLocation().getBlock().getRelative(dx, dy, dz);
            if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
                continue;
            }

            int increase = Math.max(1, getGrowthStep(level));
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + increase));
            block.setBlockData(ageable, true);
            grown++;
            if (getConfig().showGrowthParticles) {
                if (areParticlesEnabled()) {
                    p.getWorld().spawnParticle(Particle.COMPOSTER, block.getLocation().add(0.5, 0.5, 0.5), 3, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }

        return grown;
    }

    private int pullNearbyBees(Player p, int level) {
        double radius = getRadius(level);
        int count = 0;
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Bee bee)) {
                continue;
            }

            Vector toward = p.getLocation().add(0, 0.75, 0).toVector().subtract(bee.getLocation().toVector());
            if (toward.lengthSquared() <= 0.001) {
                continue;
            }

            toward.normalize().multiply(getBeePullStrength(level));
            bee.setVelocity(bee.getVelocity().multiply(0.6).add(toward));
            bee.setTarget(null);
            count++;
        }
        return count;
    }

    private boolean isHoldingFlower(Player p) {
        return isFlower(p.getInventory().getItemInMainHand()) || isFlower(p.getInventory().getItemInOffHand());
    }

    private boolean isFlower(ItemStack item) {
        if (!isItem(item)) {
            return false;
        }

        Material type = item.getType();
        return type.name().endsWith("_TULIP")
                || type == Material.DANDELION
                || type == Material.POPPY
                || type == Material.BLUE_ORCHID
                || type == Material.ALLIUM
                || type == Material.AZURE_BLUET
                || type == Material.OXEYE_DAISY
                || type == Material.CORNFLOWER
                || type == Material.LILY_OF_THE_VALLEY
                || type == Material.WITHER_ROSE
                || type == Material.SUNFLOWER
                || type == Material.LILAC
                || type == Material.ROSE_BUSH
                || type == Material.PEONY
                || type == Material.TORCHFLOWER
                || type == Material.PINK_PETALS;
    }

    private double getRadius(int level) {
        return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    }

    private int getGrowthAttempts(int level) {
        return Math.max(1, (int) Math.round(getConfig().growthAttemptsBase + (getLevelPercent(level) * getConfig().growthAttemptsFactor)));
    }

    private int getGrowthStep(int level) {
        return Math.max(1, (int) Math.round(getConfig().growthStepBase + (getLevelPercent(level) * getConfig().growthStepFactor)));
    }

    private int getFoodCost(int level) {
        return Math.max(1, (int) Math.round(getConfig().foodCostBase - (getLevelPercent(level) * getConfig().foodCostFactor)));
    }

    private long getPulseMillis(int level) {
        return Math.max(250L, (long) Math.round(getConfig().pulseMillisBase - (getLevelPercent(level) * getConfig().pulseMillisFactor)));
    }

    private double getBeePullStrength(int level) {
        return Math.max(0.01, getConfig().beePullStrengthBase + (getLevelPercent(level) * getConfig().beePullStrengthFactor));
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
    @ConfigDescription("Holding flowers near crops emits growth pulses and draws nearby bees toward you.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Growth Particles for the Herbalism Bee Shepherd adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showGrowthParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.64;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Growth Attempts Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double growthAttemptsBase = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Growth Attempts Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double growthAttemptsFactor = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Growth Step Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double growthStepBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Growth Step Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double growthStepFactor = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Cost Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodCostBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Cost Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodCostFactor = 1.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pulse Millis Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pulseMillisBase = 900;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pulse Millis Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pulseMillisFactor = 650;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bee Pull Strength Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double beePullStrengthBase = 0.07;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bee Pull Strength Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double beePullStrengthFactor = 0.14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Growth for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerGrowth = 0.9;
    }
}
