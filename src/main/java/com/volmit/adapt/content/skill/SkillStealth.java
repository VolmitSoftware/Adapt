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

import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.stealth.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillStealth extends SimpleSkill<SkillStealth.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillStealth() {
        super("stealth", Localizer.dLocalize("skill.stealth.icon"));
        registerConfiguration(Config.class);
        setColor(C.DARK_GRAY);
        setInterval(1412);
        setIcon(Material.WITHER_ROSE);
        cooldowns = new HashMap<>();
        setDescription(Localizer.dLocalize("skill.stealth.description"));
        setDisplayName(Localizer.dLocalize("skill.stealth.name"));
        registerAdaptation(new StealthSpeed());
        registerAdaptation(new StealthSnatch());
        registerAdaptation(new StealthGhostArmor());
        registerAdaptation(new StealthSight());
        registerAdaptation(new StealthEnderVeil());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_LEGGINGS)
                .key("challenge_sneak_1k")
                .title(Localizer.dLocalize("advancement.challenge_sneak_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_sneak_1k.description"))
                .model(CustomModel.get(Material.LEATHER_LEGGINGS, "advancement", "stealth", "challenge_sneak_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CHAINMAIL_LEGGINGS)
                        .key("challenge_sneak_5k")
                        .title(Localizer.dLocalize("advancement.challenge_sneak_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_sneak_5k.description"))
                        .model(CustomModel.get(Material.CHAINMAIL_LEGGINGS, "advancement", "stealth", "challenge_sneak_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.NETHERITE_LEGGINGS)
                                .key("challenge_sneak_20k")
                                .title(Localizer.dLocalize("advancement.challenge_sneak_20k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_sneak_20k.description"))
                                .model(CustomModel.get(Material.NETHERITE_LEGGINGS, "advancement", "stealth", "challenge_sneak_20k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sneak_1k").goal(1000).stat("move.sneak").reward(getConfig().challengeSneak1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sneak_5k").goal(5000).stat("move.sneak").reward(getConfig().challengeSneak5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sneak_20k").goal(20000).stat("move.sneak").reward(getConfig().challengeSneak20kReward).build());

        // Chain 2 - Stealth Damage While Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.STONE_SWORD)
                .key("challenge_stealth_dmg_500")
                .title(Localizer.dLocalize("advancement.challenge_stealth_dmg_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_dmg_500.description"))
                .model(CustomModel.get(Material.STONE_SWORD, "advancement", "stealth", "challenge_stealth_dmg_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_SWORD)
                        .key("challenge_stealth_dmg_5k")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_dmg_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_dmg_5k.description"))
                        .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "stealth", "challenge_stealth_dmg_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_dmg_500").goal(500).stat("stealth.damage.sneaking").reward(getConfig().challengeStealthDmg500Reward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_dmg_5k").goal(5000).stat("stealth.damage.sneaking").reward(getConfig().challengeStealthDmg5kReward).build());

        // Chain 3 - Stealth Kills While Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SKELETON_SKULL)
                .key("challenge_stealth_kills_10")
                .title(Localizer.dLocalize("advancement.challenge_stealth_kills_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_kills_10.description"))
                .model(CustomModel.get(Material.SKELETON_SKULL, "advancement", "stealth", "challenge_stealth_kills_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.WITHER_ROSE)
                        .key("challenge_stealth_kills_100")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_kills_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_kills_100.description"))
                        .model(CustomModel.get(Material.WITHER_ROSE, "advancement", "stealth", "challenge_stealth_kills_100"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_kills_10").goal(10).stat("stealth.kills.sneaking").reward(getConfig().challengeStealthKills10Reward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_kills_100").goal(100).stat("stealth.kills.sneaking").reward(getConfig().challengeStealthKills100Reward).build());

        // Chain 4 - Stealth Time Spent Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_BOOTS)
                .key("challenge_stealth_time_1h")
                .title(Localizer.dLocalize("advancement.challenge_stealth_time_1h.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_time_1h.description"))
                .model(CustomModel.get(Material.LEATHER_BOOTS, "advancement", "stealth", "challenge_stealth_time_1h"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CHAINMAIL_BOOTS)
                        .key("challenge_stealth_time_10h")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_time_10h.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_time_10h.description"))
                        .model(CustomModel.get(Material.CHAINMAIL_BOOTS, "advancement", "stealth", "challenge_stealth_time_10h"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_time_1h").goal(3600).stat("stealth.time").reward(getConfig().challengeStealthTime1hReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_time_10h").goal(36000).stat("stealth.time").reward(getConfig().challengeStealthTime10hReward).build());

        // Chain 5 - Stealth Arrows Fired While Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BOW)
                .key("challenge_stealth_arrows_50")
                .title(Localizer.dLocalize("advancement.challenge_stealth_arrows_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_arrows_50.description"))
                .model(CustomModel.get(Material.BOW, "advancement", "stealth", "challenge_stealth_arrows_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CROSSBOW)
                        .key("challenge_stealth_arrows_500")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_arrows_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_arrows_500.description"))
                        .model(CustomModel.get(Material.CROSSBOW, "advancement", "stealth", "challenge_stealth_arrows_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_arrows_50").goal(50).stat("stealth.arrows.sneaking").reward(getConfig().challengeStealthArrows50Reward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_stealth_arrows_500").goal(500).stat("stealth.arrows.sneaking").reward(getConfig().challengeStealthArrows500Reward).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && p.isSneaking()) {
            shouldReturnForPlayer(p, e, () -> {
                getPlayer(p).getData().addStat("stealth.damage.sneaking", e.getDamage());
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) {
            return;
        }
        Player p = e.getEntity().getKiller();
        if (p.isSneaking()) {
            shouldReturnForPlayer(p, () -> {
                getPlayer(p).getData().addStat("stealth.kills.sneaking", 1);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        if (p.isSneaking()) {
            shouldReturnForPlayer(p, e, () -> {
                getPlayer(p).getData().addStat("stealth.arrows.sneaking", 1);
            });
        }
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> {
                checkStatTrackers(getPlayer(i));
                if (i.isSneaking() && !i.isSwimming() && !i.isSprinting() && !i.isFlying() && !i.isGliding() && (i.getGameMode().equals(GameMode.SURVIVAL) || i.getGameMode().equals(GameMode.ADVENTURE))) {
                    xpSilent(i, getConfig().sneakXP);
                    getPlayer(i).getData().addStat("stealth.time", 1);
                }
            });

        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak1k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSneak1kReward = 1750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSneak5kReward = 3500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak20k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSneak20kReward = 8750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Sneak XP for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sneakXP = 3.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Dmg 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthDmg500Reward = 1500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Dmg 5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthDmg5kReward = 5000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Kills 10 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthKills10Reward = 1000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Kills 100 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthKills100Reward = 5000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Time 1h Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthTime1hReward = 2000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Time 10h Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthTime10hReward = 7500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Arrows 50 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthArrows50Reward = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Arrows 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthArrows500Reward = 5000;
    }
}
