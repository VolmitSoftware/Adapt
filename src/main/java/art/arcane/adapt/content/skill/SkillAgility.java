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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.adaptation.agility.AgilityArmorUp;
import art.arcane.adapt.content.adaptation.agility.AgilityLadderSlide;
import art.arcane.adapt.content.adaptation.agility.AgilityParkourMomentum;
import art.arcane.adapt.content.adaptation.agility.AgilityRollLanding;
import art.arcane.adapt.content.adaptation.agility.AgilitySuperJump;
import art.arcane.adapt.content.adaptation.agility.AgilityWallJump;
import art.arcane.adapt.content.adaptation.agility.AgilityWindUp;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.format.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillAgility extends SimpleSkill<SkillAgility.Config> {
    private Map<UUID, Location> lastLocations;

    public SkillAgility() {
        super("agility", Localizer.dLocalize("skill.agility.icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill.agility.description"));
        setDisplayName(Localizer.dLocalize("skill.agility.name"));
        setColor(C.GREEN);
        setInterval(975);
        setIcon(Material.FEATHER);
        registerAdaptation(new AgilityWindUp());
        registerAdaptation(new AgilityWallJump());
        registerAdaptation(new AgilitySuperJump());
        registerAdaptation(new AgilityArmorUp());
        registerAdaptation(new AgilityLadderSlide());
        registerAdaptation(new AgilityParkourMomentum());
        registerAdaptation(new AgilityRollLanding());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_BOOTS)
                .key("challenge_move_1k")
                .title(Localizer.dLocalize("advancement.challenge_move_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_move_1k.description"))
                .model(CustomModel.get(Material.LEATHER_BOOTS, "advancement", "agility", "challenge_move_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_BOOTS)
                        .key("challenge_sprint_5k")
                        .title(Localizer.dLocalize("advancement.challenge_sprint_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_sprint_5k.description"))
                        .model(CustomModel.get(Material.IRON_BOOTS, "advancement", "agility", "challenge_sprint_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.DIAMOND_BOOTS)
                                .key("challenge_sprint_50k")
                                .title(Localizer.dLocalize("advancement.challenge_sprint_50k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_sprint_50k.description"))
                                .model(CustomModel.get(Material.DIAMOND_BOOTS, "advancement", "agility", "challenge_sprint_50k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.NETHERITE_BOOTS)
                                        .key("challenge_sprint_500k")
                                        .title(Localizer.dLocalize("advancement.challenge_sprint_500k.title"))
                                        .description(Localizer.dLocalize("advancement.challenge_sprint_500k.description"))
                                        .model(CustomModel.get(Material.NETHERITE_BOOTS, "advancement", "agility", "challenge_sprint_500k"))
                                        .frame(AdaptAdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                                        .build())
                                .build())
                        .build())
                .child(AdaptAdvancement.builder()
                        .icon(Material.GOLDEN_BOOTS)
                        .key("challenge_sprint_marathon")
                        .title(Localizer.dLocalize("advancement.challenge_sprint_marathon.title"))
                        .description(Localizer.dLocalize("advancement.challenge_sprint_marathon.description"))
                        .model(CustomModel.get(Material.GOLDEN_BOOTS, "advancement", "agility", "challenge_sprint_marathon"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_move_1k", "move", 1000, getConfig().challengeMove1kReward);
        registerMilestone("challenge_sprint_5k", "move", 5000, getConfig().challengeSprint5kReward);
        registerMilestone("challenge_sprint_50k", "move", 50000, getConfig().challengeSprint5kReward);
        registerMilestone("challenge_sprint_500k", "move", 500000, getConfig().challengeSprint5kReward);
        registerMilestone("challenge_sprint_marathon", "move", 42195, getConfig().challengeSprintMarathonReward);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GOLDEN_BOOTS).key("challenge_sprint_dist_5k")
                .title(Localizer.dLocalize("advancement.challenge_sprint_dist_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_sprint_dist_5k.description"))
                .model(CustomModel.get(Material.GOLDEN_BOOTS, "advancement", "agility", "challenge_sprint_dist_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_BOOTS)
                        .key("challenge_sprint_dist_50k")
                        .title(Localizer.dLocalize("advancement.challenge_sprint_dist_50k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_sprint_dist_50k.description"))
                        .model(CustomModel.get(Material.DIAMOND_BOOTS, "advancement", "agility", "challenge_sprint_dist_50k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_sprint_dist_5k", "move.sprint", 5000, getConfig().challengeSprint5kReward);
        registerMilestone("challenge_sprint_dist_50k", "move.sprint", 50000, getConfig().challengeSprint5kReward * 2);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LILY_PAD).key("challenge_agility_swim_1k")
                .title(Localizer.dLocalize("advancement.challenge_agility_swim_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_swim_1k.description"))
                .model(CustomModel.get(Material.LILY_PAD, "advancement", "agility", "challenge_agility_swim_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.HEART_OF_THE_SEA)
                        .key("challenge_agility_swim_10k")
                        .title(Localizer.dLocalize("advancement.challenge_agility_swim_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_agility_swim_10k.description"))
                        .model(CustomModel.get(Material.HEART_OF_THE_SEA, "advancement", "agility", "challenge_agility_swim_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_agility_swim_1k", "move.swim", 1000, getConfig().challengeSprint5kReward);
        registerMilestone("challenge_agility_swim_10k", "move.swim", 10000, getConfig().challengeSprint5kReward * 2);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FEATHER).key("challenge_fly_1k")
                .title(Localizer.dLocalize("advancement.challenge_fly_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_fly_1k.description"))
                .model(CustomModel.get(Material.FEATHER, "advancement", "agility", "challenge_fly_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ELYTRA)
                        .key("challenge_fly_10k")
                        .title(Localizer.dLocalize("advancement.challenge_fly_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_fly_10k.description"))
                        .model(CustomModel.get(Material.ELYTRA, "advancement", "agility", "challenge_fly_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_fly_1k", "move.fly", 1000, getConfig().challengeSprint5kReward);
        registerMilestone("challenge_fly_10k", "move.fly", 10000, getConfig().challengeSprint5kReward * 2);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_LEGGINGS).key("challenge_agility_sneak_500")
                .title(Localizer.dLocalize("advancement.challenge_agility_sneak_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_sneak_500.description"))
                .model(CustomModel.get(Material.LEATHER_LEGGINGS, "advancement", "agility", "challenge_agility_sneak_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_LEGGINGS)
                        .key("challenge_agility_sneak_5k")
                        .title(Localizer.dLocalize("advancement.challenge_agility_sneak_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_agility_sneak_5k.description"))
                        .model(CustomModel.get(Material.IRON_LEGGINGS, "advancement", "agility", "challenge_agility_sneak_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_agility_sneak_500", "move.sneak", 500, getConfig().challengeSprint5kReward);
        registerMilestone("challenge_agility_sneak_5k", "move.sneak", 5000, getConfig().challengeSprint5kReward * 2);
        lastLocations = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getFrom().getWorld() != null && e.getTo() != null && e.getFrom().getWorld().equals(e.getTo().getWorld())) {
                double d = e.getFrom().distance(e.getTo());
                AdaptPlayer adaptPlayer = getPlayer(p);
                adaptPlayer.getData().addStat("move", d);

                if (p.isSneaking()) {
                    adaptPlayer.getData().addStat("move.sneak", d);
                } else if (p.isFlying()) {
                    adaptPlayer.getData().addStat("move.fly", d);
                } else if (p.isSwimming()) {
                    adaptPlayer.getData().addStat("move.swim", d);
                } else if (p.isSprinting()) {
                    adaptPlayer.getData().addStat("move.sprint", d);
                }

                // Add XP for moving
                xpSilent(p, getConfig().moveXpPassive * d, "agility:move");
            }
        });
    }


    @Override
    public void onTick() {
        for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player i = adaptPlayer.getPlayer();
            shouldReturnForPlayer(i, () -> {
                checkStatTrackers(adaptPlayer);

                // Check for sprinting
                if (i.isSprinting() && !i.isFlying() && !i.isSwimming() && !i.isSneaking()) {
                    xpSilent(i, getConfig().sprintXpPassive, "agility:sprint");
                }

                // Check for swimming
                if (i.isSwimming() && !i.isFlying() && !i.isSprinting() && !i.isSneaking()) {
                    xpSilent(i, getConfig().swimXpPassive, "agility:swim");
                }

                // Check for jumping
                if (i.getLocation().subtract(0, 1, 0).getBlock().getType().isAir() && !i.isFlying() && !i.isSneaking()) {
                    xpSilent(i, getConfig().jumpXpPassive, "agility:jump");
                }

                // Check for climbing ladders
                if (i.getLocation().getBlock().getType() == Material.LADDER && !i.isFlying() && !i.isSneaking()) {
                    xpSilent(i, getConfig().climbXpPassive, "agility:climb");
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
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Move1k Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeMove1kReward = 500;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sprint5k Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSprint5kReward = 2000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sprint Marathon Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSprintMarathonReward = 6500;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sprint Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sprintXpPassive = 0.35;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Swim Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double swimXpPassive = 0.4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double jumpXpPassive = 0.15;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Climb Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double climbXpPassive = 0.4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Move Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double moveXpPassive = 0.05;
    }
}
