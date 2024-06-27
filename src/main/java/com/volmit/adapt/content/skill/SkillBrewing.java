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

package com.volmit.adapt.content.skill;

import art.arcane.spatial.matter.SpatialMatter;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.brewing.*;
import com.volmit.adapt.content.matter.BrewingStandOwner;
import com.volmit.adapt.content.matter.BrewingStandOwnerMatter;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.HashMap;
import java.util.Map;

public class SkillBrewing extends SimpleSkill<SkillBrewing.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillBrewing() {
        super("brewing", Localizer.dLocalize("skill", "brewing", "icon"));
        registerConfiguration(Config.class);
        setColor(C.LIGHT_PURPLE);
        setDescription(Localizer.dLocalize("skill", "brewing", "description"));
        setDisplayName(Localizer.dLocalize("skill", "brewing", "name"));
        setInterval(5851);
        setIcon(Material.LINGERING_POTION);
        cooldowns = new HashMap<>();
        registerAdaptation(new BrewingLingering()); // Features
        registerAdaptation(new BrewingSuperHeated());
        registerAdaptation(new BrewingAbsorption()); // Brews
        registerAdaptation(new BrewingBlindness());
        registerAdaptation(new BrewingDarkness());
        registerAdaptation(new BrewingDecay());
        registerAdaptation(new BrewingFatigue());
        registerAdaptation(new BrewingHaste());
        registerAdaptation(new BrewingHealthBoost());
        registerAdaptation(new BrewingHunger());
        registerAdaptation(new BrewingNausea());
        registerAdaptation(new BrewingResistance());
        registerAdaptation(new BrewingSaturation());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.POTION).key("challenge_brew_1k")
                .title(Localizer.dLocalize("advancement", "challenge_brew_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_brew_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.POTION)
                        .key("challenge_brew_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_brew_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_brew_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.POTION)
                                .key("challenge_brew_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_brew_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_brew_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.POTION)
                                        .key("challenge_brew_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_brew_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_brew_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.POTION)
                                                .key("challenge_brew_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_brew_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_brew_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brew_1k").goal(1000).stat("brewing.consumed").reward(getConfig().challengeBrew1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brew_5k").goal(5000).stat("brewing.consumed").reward(getConfig().challengeBrew1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brew_50k").goal(50000).stat("brewing.consumed").reward(getConfig().challengeBrew1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brew_500k").goal(500000).stat("brewing.consumed").reward(getConfig().challengeBrew1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brew_5m").goal(5000000).stat("brewing.consumed").reward(getConfig().challengeBrew1k).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPLASH_POTION).key("challenge_brewsplash_1k")
                .title(Localizer.dLocalize("advancement", "challenge_brewsplash_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_brewsplash_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.SPLASH_POTION)
                        .key("challenge_brewsplash_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_brewsplash_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_brewsplash_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.SPLASH_POTION)
                                .key("challenge_brewsplash_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_brewsplash_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_brewsplash_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.SPLASH_POTION)
                                        .key("challenge_brewsplash_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_brewsplash_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_brewsplash_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.SPLASH_POTION)
                                                .key("challenge_brewsplash_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_brewsplash_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_brewsplash_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewsplash_1k").goal(1000).stat("brewing.splashes").reward(getConfig().challengeBrewSplash1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewsplash_5k").goal(5000).stat("brewing.splashes").reward(getConfig().challengeBrewSplash1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewsplash_50k").goal(50000).stat("brewing.splashes").reward(getConfig().challengeBrewSplash1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewsplash_500k").goal(500000).stat("brewing.splashes").reward(getConfig().challengeBrewSplash1k).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_brewsplash_5m").goal(5000000).stat("brewing.splashes").reward(getConfig().challengeBrewSplash1k).build());

        SpatialMatter.registerSliceType(new BrewingStandOwnerMatter());
    }

    private void handleCooldown(Player p, Runnable runnable) {
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        runnable.run();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getItem().getItemMeta() instanceof PotionMeta o
                    && !e.getItem().toString().contains("potion-type=minecraft:water")
                    && !e.getItem().toString().contains("potion-type=minecraft:mundane")
                    && !e.getItem().toString().contains("potion-type=minecraft:thick")
                    && !e.getItem().toString().contains("potion-type=minecraft:awkward")) {
                getPlayer(p).getData().addStat("brewing.consumed", 1);
                handleCooldown(p, () -> xp(p, p.getLocation(),
                        getConfig().splashXP
                                + (getConfig().splashMultiplier * o.getCustomEffects().stream().mapToDouble(i -> (i.getAmplifier() + 1) * (i.getDuration() / 20D)).sum())
                                + (getConfig().splashMultiplier * (o.getBasePotionData().isUpgraded() ? 50 : 25))));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PotionSplashEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getPotion().getShooter() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> {
                AdaptPlayer a = getPlayer(p);
                getPlayer(p).getData().addStat("brewing.splashes", 1);
                xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().splashXP + (getConfig().splashMultiplier * e.getPotion().getEffects().stream().mapToDouble(i -> (i.getAmplifier() + 1) * (i.getDuration() / 20D)).sum()));
            });
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPlaceEvent e) {
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getBlock().getType().equals(Material.BREWING_STAND)) {
                WorldData.of(e.getBlock().getWorld()).getMantle().set(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), new BrewingStandOwner(e.getPlayer().getUniqueId()));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getBlock().getType().equals(Material.BREWING_STAND)) {
                WorldData.of(e.getBlock().getWorld()).getMantle().remove(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), BrewingStandOwner.class);
            }
        });
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double challengeBrew1k = 1000;
        double challengeBrewSplash1k = 1000;
        double splashXP = 100;
        long cooldownDelay = 3250;
        double splashMultiplier = 0.25;
    }
}
