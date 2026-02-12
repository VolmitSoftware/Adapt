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
import com.volmit.adapt.content.adaptation.nether.NetherFireResist;
import com.volmit.adapt.content.adaptation.nether.NetherSkullYeet;
import com.volmit.adapt.content.adaptation.nether.NetherWitherResist;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class SkillNether extends SimpleSkill<SkillNether.Config> {
    private int witherRoseCooldown;

    public SkillNether() {
        super("nether", Localizer.dLocalize("skill.nether.icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill.nether.description"));
        setDisplayName(Localizer.dLocalize("skill.nether.name"));
        setInterval(7425);
        setColor(C.DARK_GRAY);
        setIcon(Material.NETHER_STAR);
        registerAdaptation(new NetherWitherResist());
        registerAdaptation(new NetherSkullYeet());
        registerAdaptation(new NetherFireResist());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WITHER_SKELETON_SKULL)
                .key("challenge_nether_50")
                .title(Localizer.dLocalize("advancement.challenge_nether_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_nether_50.description"))
                .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "nether", "challenge_nether_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHER_STAR)
                        .key("challenge_nether_500")
                        .title(Localizer.dLocalize("advancement.challenge_nether_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_nether_500.description"))
                        .model(CustomModel.get(Material.NETHER_STAR, "advancement", "nether", "challenge_nether_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.BEACON)
                                .key("challenge_nether_5k")
                                .title(Localizer.dLocalize("advancement.challenge_nether_5k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_nether_5k.description"))
                                .model(CustomModel.get(Material.BEACON, "advancement", "nether", "challenge_nether_5k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_nether_50").goal(50).stat("nether.kills").reward(getConfig().getChallengeNetherReward()).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_nether_500").goal(500).stat("nether.kills").reward(getConfig().getChallengeNetherReward() * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_nether_5k").goal(5000).stat("nether.kills").reward(getConfig().getChallengeNetherReward() * 5).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WITHER_ROSE)
                .key("challenge_wither_dmg_500")
                .title(Localizer.dLocalize("advancement.challenge_wither_dmg_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_wither_dmg_500.description"))
                .model(CustomModel.get(Material.WITHER_ROSE, "advancement", "nether", "challenge_wither_dmg_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SOUL_LANTERN)
                        .key("challenge_wither_dmg_5k")
                        .title(Localizer.dLocalize("advancement.challenge_wither_dmg_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_wither_dmg_5k.description"))
                        .model(CustomModel.get(Material.SOUL_LANTERN, "advancement", "nether", "challenge_wither_dmg_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_wither_dmg_500").goal(500).stat("nether.wither.damage").reward(getConfig().getChallengeWitherDmgReward()).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_wither_dmg_5k").goal(5000).stat("nether.wither.damage").reward(getConfig().getChallengeWitherDmgReward() * 2).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BONE)
                .key("challenge_wither_skel_25")
                .title(Localizer.dLocalize("advancement.challenge_wither_skel_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_wither_skel_25.description"))
                .model(CustomModel.get(Material.BONE, "advancement", "nether", "challenge_wither_skel_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.WITHER_SKELETON_SKULL)
                        .key("challenge_wither_skel_250")
                        .title(Localizer.dLocalize("advancement.challenge_wither_skel_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_wither_skel_250.description"))
                        .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "nether", "challenge_wither_skel_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_wither_skel_25").goal(25).stat("nether.skeleton.kills").reward(getConfig().getChallengeWitherSkelReward()).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_wither_skel_250").goal(250).stat("nether.skeleton.kills").reward(getConfig().getChallengeWitherSkelReward() * 2).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.NETHER_STAR)
                .key("challenge_wither_boss_1")
                .title(Localizer.dLocalize("advancement.challenge_wither_boss_1.title"))
                .description(Localizer.dLocalize("advancement.challenge_wither_boss_1.description"))
                .model(CustomModel.get(Material.NETHER_STAR, "advancement", "nether", "challenge_wither_boss_1"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BEACON)
                        .key("challenge_wither_boss_10")
                        .title(Localizer.dLocalize("advancement.challenge_wither_boss_10.title"))
                        .description(Localizer.dLocalize("advancement.challenge_wither_boss_10.description"))
                        .model(CustomModel.get(Material.BEACON, "advancement", "nether", "challenge_wither_boss_10"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_wither_boss_1").goal(1).stat("nether.boss.kills").reward(getConfig().getChallengeWitherBossReward()).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_wither_boss_10").goal(10).stat("nether.boss.kills").reward(getConfig().getChallengeWitherBossReward() * 2).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WITHER_ROSE)
                .key("challenge_roses_10")
                .title(Localizer.dLocalize("advancement.challenge_roses_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_roses_10.description"))
                .model(CustomModel.get(Material.WITHER_ROSE, "advancement", "nether", "challenge_roses_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.FLOWER_POT)
                        .key("challenge_roses_100")
                        .title(Localizer.dLocalize("advancement.challenge_roses_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_roses_100.description"))
                        .model(CustomModel.get(Material.FLOWER_POT, "advancement", "nether", "challenge_roses_100"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_roses_10").goal(10).stat("nether.roses.broken").reward(getConfig().getChallengeRosesReward()).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_roses_100").goal(100).stat("nether.roses.broken").reward(getConfig().getChallengeRosesReward() * 2).build());
    }

    private boolean shouldReturnForEventWithCause(Player p, EntityDamageEvent.DamageCause cause) {
        return shouldReturnForPlayer(p) || cause != EntityDamageEvent.DamageCause.WITHER;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!this.isEnabled() || e.isCancelled() || !(e.getEntity() instanceof Player p) || shouldReturnForEventWithCause(p, e.getCause()) || e instanceof EntityDamageByBlockEvent) {
            return;
        }
        getPlayer(p).getData().addStat("nether.wither.damage", e.getDamage());
        xp(p, getConfig().getWitherDamageXp());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getBlock().getType() == Material.WITHER_ROSE && witherRoseCooldown == 0) {
                witherRoseCooldown = getConfig().getWitherRoseBreakCooldown();
                getPlayer(p).getData().addStat("nether.roses.broken", 1);
                xp(p, e.getBlock().getLocation().add(.5D, .5D, .5D), getConfig().getWitherRoseBreakXp());
            }
        });

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || !p.getClass().getSimpleName().equals("CraftPlayer") || shouldReturnForPlayer(p)) {
            return;
        }
        if (e.getEntityType() == EntityType.WITHER_SKELETON) {
            getPlayer(p).getData().addStat("nether.kills", 1);
            getPlayer(p).getData().addStat("nether.skeleton.kills", 1);
            xp(p, getConfig().getWitherSkeletonKillXp());
        } else if (e.getEntityType() == EntityType.WITHER) {
            getPlayer(p).getData().addStat("nether.kills", 1);
            getPlayer(p).getData().addStat("nether.boss.kills", 1);
            xp(p, getConfig().getWitherKillXp());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getDamager() instanceof Player p) || shouldReturnForEventWithCause(p, e.getCause())) {
            return;
        }
        xp(p, getConfig().getWitherAttackXp());
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        if (witherRoseCooldown > 0) {
            witherRoseCooldown--;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (!shouldReturnForPlayer(i)) {
                checkStatTrackers(getPlayer(i));
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().isEnabled();
    }

    @Data
    @NoArgsConstructor
    public static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        private boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Wither Damage Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double witherDamageXp = 26.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Wither Attack Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double witherAttackXp = 15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Wither Skeleton Kill Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double witherSkeletonKillXp = 225;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Wither Kill Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double witherKillXp = 900;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Wither Rose Break Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double witherRoseBreakXp = 125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Wither Rose Break Cooldown for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private int witherRoseBreakCooldown = 60 * 20;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Nether Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double challengeNetherReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Wither Damage Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double challengeWitherDmgReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Wither Skeleton Kill Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double challengeWitherSkelReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Wither Boss Kill Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double challengeWitherBossReward = 1000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Roses Broken Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double challengeRosesReward = 500;
    }
}
