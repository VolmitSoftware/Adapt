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
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.seaborrne.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillSeaborne() {
        super("seaborne", Localizer.dLocalize("skill.seaborne.icon"));
        registerConfiguration(Config.class);
        setColor(C.BLUE);
        setDescription(Localizer.dLocalize("skill.seaborne.description"));
        setDisplayName(Localizer.dLocalize("skill.seaborne.name"));
        setInterval(2120);
        setIcon(Material.TRIDENT);
        registerAdaptation(new SeaborneOxygen());
        registerAdaptation(new SeaborneSpeed());
        registerAdaptation(new SeaborneFishersFantasy());
        registerAdaptation(new SeaborneTurtlesVision());
        registerAdaptation(new SeaborneTurtlesMiningSpeed());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TURTLE_HELMET)
                .key("challenge_swim_1nm")
                .title(Localizer.dLocalize("advancement.challenge_swim_1nm.title"))
                .description(Localizer.dLocalize("advancement.challenge_swim_1nm.description"))
                .model(CustomModel.get(Material.TURTLE_HELMET, "advancement", "seaborne", "challenge_swim_1nm"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.HEART_OF_THE_SEA)
                        .key("challenge_swim_5k")
                        .title(Localizer.dLocalize("advancement.challenge_swim_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_swim_5k.description"))
                        .model(CustomModel.get(Material.HEART_OF_THE_SEA, "advancement", "seaborne", "challenge_swim_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.TRIDENT)
                                .key("challenge_swim_20k")
                                .title(Localizer.dLocalize("advancement.challenge_swim_20k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_swim_20k.description"))
                                .model(CustomModel.get(Material.TRIDENT, "advancement", "seaborne", "challenge_swim_20k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_swim_1nm").goal(1852).stat("move.swim").reward(getConfig().challengeSwim1nmReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_swim_5k").goal(5000).stat("move.swim").reward(getConfig().challengeSwim5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_swim_20k").goal(20000).stat("move.swim").reward(getConfig().challengeSwim20kReward).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FISHING_ROD)
                .key("challenge_fish_25")
                .title(Localizer.dLocalize("advancement.challenge_fish_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_fish_25.description"))
                .model(CustomModel.get(Material.FISHING_ROD, "advancement", "seaborne", "challenge_fish_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TROPICAL_FISH)
                        .key("challenge_fish_250")
                        .title(Localizer.dLocalize("advancement.challenge_fish_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_fish_250.description"))
                        .model(CustomModel.get(Material.TROPICAL_FISH, "advancement", "seaborne", "challenge_fish_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_fish_25").goal(25).stat("seaborne.fish.caught").reward(getConfig().challengeSwim1nmReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_fish_250").goal(250).stat("seaborne.fish.caught").reward(getConfig().challengeSwim1nmReward).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ROTTEN_FLESH)
                .key("challenge_drowned_25")
                .title(Localizer.dLocalize("advancement.challenge_drowned_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_drowned_25.description"))
                .model(CustomModel.get(Material.ROTTEN_FLESH, "advancement", "seaborne", "challenge_drowned_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TRIDENT)
                        .key("challenge_drowned_250")
                        .title(Localizer.dLocalize("advancement.challenge_drowned_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_drowned_250.description"))
                        .model(CustomModel.get(Material.TRIDENT, "advancement", "seaborne", "challenge_drowned_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_drowned_25").goal(25).stat("seaborne.drowned.kills").reward(getConfig().challengeSwim1nmReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_drowned_250").goal(250).stat("seaborne.drowned.kills").reward(getConfig().challengeSwim1nmReward).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.PRISMARINE_SHARD)
                .key("challenge_guardian_10")
                .title(Localizer.dLocalize("advancement.challenge_guardian_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_guardian_10.description"))
                .model(CustomModel.get(Material.PRISMARINE_SHARD, "advancement", "seaborne", "challenge_guardian_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SEA_LANTERN)
                        .key("challenge_guardian_100")
                        .title(Localizer.dLocalize("advancement.challenge_guardian_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_guardian_100.description"))
                        .model(CustomModel.get(Material.SEA_LANTERN, "advancement", "seaborne", "challenge_guardian_100"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_guardian_10").goal(10).stat("seaborne.guardian.kills").reward(getConfig().challengeSwim1nmReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_guardian_100").goal(100).stat("seaborne.guardian.kills").reward(getConfig().challengeSwim1nmReward).build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.PRISMARINE)
                .key("challenge_underwater_blocks_100")
                .title(Localizer.dLocalize("advancement.challenge_underwater_blocks_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_underwater_blocks_100.description"))
                .model(CustomModel.get(Material.PRISMARINE, "advancement", "seaborne", "challenge_underwater_blocks_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CONDUIT)
                        .key("challenge_underwater_blocks_1k")
                        .title(Localizer.dLocalize("advancement.challenge_underwater_blocks_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_underwater_blocks_1k.description"))
                        .model(CustomModel.get(Material.CONDUIT, "advancement", "seaborne", "challenge_underwater_blocks_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_underwater_blocks_100").goal(100).stat("seaborne.underwater.blocks").reward(getConfig().challengeSwim1nmReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_underwater_blocks_1k").goal(1000).stat("seaborne.underwater.blocks").reward(getConfig().challengeSwim1nmReward).build());
        cooldowns = new HashMap<>();
    }

    private boolean isOnCooldown(Player p, long cooldown) {
        Long lastCooldown = cooldowns.get(p);
        return lastCooldown != null && lastCooldown + cooldown > System.currentTimeMillis();
    }

    private void setCooldown(Player p) {
        cooldowns.put(p, System.currentTimeMillis());
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> {
                if (i.getWorld().getBlockAt(i.getLocation()).isLiquid() && i.isSwimming() && i.getPlayer() != null && i.getPlayer().getRemainingAir() < i.getMaximumAir()) {
                    Adapt.verbose("seaborne Tick");
                    checkStatTrackers(getPlayer(i));
                    xpSilent(i, getConfig().swimXP);
                }
            });

        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerFishEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getState().equals(PlayerFishEvent.State.CAUGHT_FISH)) {
                getPlayer(p).getData().addStat("seaborne.fish.caught", 1);
                xp(p, 300);
            } else if (e.getState().equals(PlayerFishEvent.State.CAUGHT_ENTITY)) {
                xp(p, 10);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (isOnCooldown(p, getConfig().seaPickleCooldown)) {
                return;
            }
            setCooldown(p);
            if (p.isSwimming() || p.isInWater()) {
                getPlayer(p).getData().addStat("seaborne.underwater.blocks", 1);
            }
            if (e.getBlock().getType().equals(Material.SEA_PICKLE) && p.isSwimming() && p.getRemainingAir() < p.getMaximumAir()) { // BECAUSE I LIKE PICKLES
                xpSilent(p, 10);
            } else {
                xpSilent(p, 3);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || !p.getClass().getSimpleName().equals("CraftPlayer") || shouldReturnForPlayer(p)) {
            return;
        }
        if (e.getEntityType() == EntityType.DROWNED) {
            getPlayer(p).getData().addStat("seaborne.drowned.kills", 1);
        } else if (e.getEntityType() == EntityType.GUARDIAN || e.getEntityType() == EntityType.ELDER_GUARDIAN) {
            getPlayer(p).getData().addStat("seaborne.guardian.kills", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof LivingEntity entity))
            return;

        if (e.getEntity().getType() == EntityType.DROWNED && e.getDamager() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> {
                if (isOnCooldown(p, getConfig().seaPickleCooldown)) {
                    return;
                }
                setCooldown(p);
                xp(p, getConfig().damagedrownxpmultiplier * Math.min(e.getDamage(), getBaseHealth(entity)));
            });
        } else if (e.getDamager().getType() == EntityType.TRIDENT) {
            var shooter = ((Trident) e.getDamager()).getShooter();
            if (shooter instanceof Player p) {
                shouldReturnForPlayer(p, e, () -> xp(p, getConfig().tridentxpmultiplier * Math.min(e.getDamage(), getBaseHealth(entity))));
            }
        } else if (e.getDamager() instanceof Player p) {
            if (p.getInventory().getItemInMainHand().getType().equals(Material.TRIDENT)) {
                shouldReturnForPlayer(p, e, () -> xp(p, getConfig().tridentxpmultiplier * Math.min(e.getDamage(), getBaseHealth(entity))));
            }
        }
    }

    private double getBaseHealth(LivingEntity entity) {
        var attribute = Version.get().getAttribute(entity, Attributes.GENERIC_MAX_HEALTH);
        return attribute == null ? 0 : attribute.getBaseValue();
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Sea Pickle Cooldown for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public long seaPickleCooldown = 60000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Tridentxpmultiplier for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double tridentxpmultiplier = 2.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damagedrownxpmultiplier for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damagedrownxpmultiplier = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Swim1nm Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSwim1nmReward = 750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Swim5k Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSwim5kReward = 1500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Swim20k Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSwim20kReward = 3750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Swim XP for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double swimXP = 28.7;
    }
}
