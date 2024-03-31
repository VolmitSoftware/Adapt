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

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.agility.AgilityArmorUp;
import com.volmit.adapt.content.adaptation.agility.AgilitySuperJump;
import com.volmit.adapt.content.adaptation.agility.AgilityWallJump;
import com.volmit.adapt.content.adaptation.agility.AgilityWindUp;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
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
        super("agility", Localizer.dLocalize("skill", "agility", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "agility", "description"));
        setDisplayName(Localizer.dLocalize("skill", "agility", "name"));
        setColor(C.GREEN);
        setInterval(975);
        setIcon(Material.FEATHER);
        registerAdaptation(new AgilityWindUp());
        registerAdaptation(new AgilityWallJump());
        registerAdaptation(new AgilitySuperJump());
        registerAdaptation(new AgilityArmorUp());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_BOOTS)
                .key("challenge_move_1k")
                .title(Localizer.dLocalize("advancement", "challenge_move_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_move_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_BOOTS)
                        .key("challenge_sprint_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_sprint_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_sprint_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.DIAMOND_BOOTS)
                                .key("challenge_sprint_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_sprint_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_sprint_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.NETHERITE_BOOTS)
                                        .key("challenge_sprint_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_sprint_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_sprint_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                                        .build())
                                .build())
                        .build())
                .child(AdaptAdvancement.builder()
                        .icon(Material.GOLDEN_BOOTS)
                        .key("challenge_sprint_marathon")
                        .title(Localizer.dLocalize("advancement", "challenge_sprint_marathon", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_sprint_marathon", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_move_1k").goal(1000).stat("move").reward(getConfig().challengeMove1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_5k").goal(5000).stat("move").reward(getConfig().challengeSprint5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_50k").goal(50000).stat("move").reward(getConfig().challengeSprint5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_500k").goal(500000).stat("move").reward(getConfig().challengeSprint5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_marathon").goal(42195).stat("move").reward(getConfig().challengeSprintMarathonReward).build());
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
                xpSilent(p, getConfig().moveXpPassive * d);
            }
        });
    }


    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> {
                checkStatTrackers(getPlayer(i));

                // Check for sprinting
                if (i.isSprinting() && !i.isFlying() && !i.isSwimming() && !i.isSneaking()) {
                    xpSilent(i, getConfig().sprintXpPassive);
                }

                // Check for swimming
                if (i.isSwimming() && !i.isFlying() && !i.isSprinting() && !i.isSneaking()) {
                    xpSilent(i, getConfig().swimXpPassive);
                }

                // Check for jumping
                if (i.getLocation().subtract(0, 1, 0).getBlock().getType().isAir() && !i.isFlying() && !i.isSneaking()) {
                    xpSilent(i, getConfig().jumpXpPassive);
                }

                // Check for climbing ladders
                if (i.getLocation().getBlock().getType() == Material.LADDER && !i.isFlying() && !i.isSneaking()) {
                    xpSilent(i, getConfig().climbXpPassive);
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
        boolean enabled = true;
        double challengeMove1kReward = 500;
        double challengeSprint5kReward = 2000;
        double challengeSprintMarathonReward = 6500;
        double sprintXpPassive = 1.25;
        double swimXpPassive = 1.25;
        double jumpXpPassive = 0.25;
        double climbXpPassive = 1.25;
        double moveXpPassive = 0.1;
    }
}
