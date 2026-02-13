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
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.registries.Particles;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class HerbalismGrowthAura extends SimpleAdaptation<HerbalismGrowthAura.Config> {
    public HerbalismGrowthAura() {
        super("herbalism-growth-aura");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.growth_aura.description"));
        setDisplayName(Localizer.dLocalize("herbalism.growth_aura.name"));
        setIcon(Material.BONE_MEAL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(850);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WHEAT)
                .key("challenge_herbalism_growth_1k")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_growth_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_growth_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.HAY_BLOCK)
                        .key("challenge_herbalism_growth_25k")
                        .title(Localizer.dLocalize("advancement.challenge_herbalism_growth_25k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_herbalism_growth_25k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_herbalism_growth_1k", "herbalism.growth-aura.blocks-grown", 1000, 300);
        registerMilestone("challenge_herbalism_growth_25k", "herbalism.growth-aura.blocks-grown", 25000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("herbalism.growth_aura.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getStrength(level), 0) + C.GRAY + " " + Localizer.dLocalize("herbalism.growth_aura.lore2"));
        v.addLore(C.YELLOW + "+ " + Form.f(getFoodCost(getLevelPercent(level)), 2) + C.GRAY + " " + Localizer.dLocalize("herbalism.growth_aura.lore3"));
    }

    private double getRadius(double factor) {
        return factor * getConfig().radiusFactor;
    }

    private double getStrength(int level) {
        return level * getConfig().strengthFactor;
    }

    private double getFoodCost(double factor) {
        return M.lerp(1D - factor, getConfig().maxFoodCost, getConfig().minFoodCost);
    }


    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            try {
                if (hasAdaptation(p)) {
                    double rad = getRadius(getLevelPercent(p));
                    double strength = getStrength(getLevel(p));
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    double angle = Math.toRadians(random.nextDouble(360D));
                    double foodCost = getFoodCost(getLevelPercent(p));


                    for (int i = 0; i < Math.min(Math.min(rad * rad, 256), 3); i++) {
                        Location m = p.getLocation().clone().add(new Vector(Math.sin(angle), RNG.r.i(-1, 1), Math.cos(angle)).multiply(random.nextDouble(rad)));
                        Block a = m.getBlock();
                        if (getConfig().surfaceOnly) {
                            int max = a.getWorld().getHighestBlockYAt(m);

                            if (max + 1 != a.getY())
                                continue;
                        }

                        SoundPlayer spw = SoundPlayer.of(a.getWorld());
                        if (a.getBlockData() instanceof Ageable) {
                            Ageable ab = (Ageable) a.getBlockData();
                            int toGrowLeft = ab.getMaximumAge() - ab.getAge();

                            if (toGrowLeft > 0) {
                                int add = (int) Math.max(1, Math.min(strength, toGrowLeft));
                                AdaptPlayer player = getPlayer(p);
                                if (ab.getMaximumAge() > ab.getAge() && player.canConsumeFood(foodCost, 10)) {
                                    while (add-- > 0) {
                                        J.s(() -> {
                                            if (!p.isOnline()
                                                    || !player.consumeFood(foodCost, 10)
                                                    || !(a.getBlockData() instanceof Ageable aab)
                                                    || aab.getAge() == aab.getMaximumAge())
                                                return;

                                            aab.setAge(aab.getAge() + 1);
                                            a.setBlockData(aab, true);
                                            getPlayer(p).getData().addStat("herbalism.growth-aura.blocks-grown", 1);
                                            spw.play(a.getLocation(), Sound.BLOCK_CHORUS_FLOWER_DEATH, 0.25f, RNG.r.f(0.3f, 0.7f));
                                            if (areParticlesEnabled()) {
                                                p.spawnParticle(Particles.VILLAGER_HAPPY, a.getLocation().clone().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3, 0.9);
                                            }
//                                          xp(p, 1); // JESUS THIS IS FUCKING BUSTED
                                        }, RNG.r.i(30, 60));
                                    }
                                }
                            }


                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
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
    @ConfigDescription("Grow nature around you in an aura at the cost of hunger.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Herbalism Growth Aura adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Surface Only for the Herbalism Growth Aura adaptation.", impact = "True enables this behavior and false disables it.")
        boolean surfaceOnly = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.325;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minFoodCost = 0.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxFoodCost = 0.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Strength Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double strengthFactor = 0.75;
    }
}
