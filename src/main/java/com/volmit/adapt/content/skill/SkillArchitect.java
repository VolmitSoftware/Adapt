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
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.architect.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillArchitect extends SimpleSkill<SkillArchitect.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillArchitect() {
        super("architect", Localizer.dLocalize("skill.architect.icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Localizer.dLocalize("skill.architect.description"));
        setDisplayName(Localizer.dLocalize("skill.architect.name"));
        setInterval(3100);
        setIcon(Material.IRON_BARS);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BRICK).key("challenge_place_1k")
                .title(Localizer.dLocalize("advancement.challenge_place_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_place_1k.description"))
                .model(CustomModel.get(Material.BRICK, "advancement", "architect", "challenge_place_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.BRICK)
                        .key("challenge_place_5k")
                        .title(Localizer.dLocalize("advancement.challenge_place_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_place_5k.description"))
                        .model(CustomModel.get(Material.BRICK, "advancement", "architect", "challenge_place_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.NETHER_BRICK)
                                .key("challenge_place_50k")
                                .title(Localizer.dLocalize("advancement.challenge_place_50k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_place_50k.description"))
                                .model(CustomModel.get(Material.NETHER_BRICK, "advancement", "architect", "challenge_place_50k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.NETHER_BRICK)
                                        .key("challenge_place_500k")
                                        .title(Localizer.dLocalize("advancement.challenge_place_500k.title"))
                                        .description(Localizer.dLocalize("advancement.challenge_place_500k.description"))
                                        .model(CustomModel.get(Material.NETHER_BRICK, "advancement", "architect", "challenge_place_500k"))
                                        .frame(AdaptAdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.IRON_INGOT)
                                                .key("challenge_place_5m")
                                                .title(Localizer.dLocalize("advancement.challenge_place_5m.title"))
                                                .description(Localizer.dLocalize("advancement.challenge_place_5m.description"))
                                                .model(CustomModel.get(Material.IRON_INGOT, "advancement", "architect", "challenge_place_5m"))
                                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_place_1k", "blocks.placed", 1000, getConfig().challengePlace1kReward);
        registerMilestone("challenge_place_5k", "blocks.placed", 5000, getConfig().challengePlace1kReward);
        registerMilestone("challenge_place_50k", "blocks.placed", 50000, getConfig().challengePlace1kReward);
        registerMilestone("challenge_place_500k", "blocks.placed", 500000, getConfig().challengePlace1kReward);
        registerMilestone("challenge_place_5m", "blocks.placed", 5000000, getConfig().challengePlace1kReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_PICKAXE).key("challenge_demolish_500")
                .title(Localizer.dLocalize("advancement.challenge_demolish_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_demolish_500.description"))
                .model(CustomModel.get(Material.IRON_PICKAXE, "advancement", "architect", "challenge_demolish_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TNT)
                        .key("challenge_demolish_5k")
                        .title(Localizer.dLocalize("advancement.challenge_demolish_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_demolish_5k.description"))
                        .model(CustomModel.get(Material.TNT, "advancement", "architect", "challenge_demolish_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_demolish_500", "blocks.broken", 500, getConfig().challengePlace1kReward);
        registerMilestone("challenge_demolish_5k", "blocks.broken", 5000, getConfig().challengePlace1kReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GOLD_INGOT).key("challenge_value_placed_10k")
                .title(Localizer.dLocalize("advancement.challenge_value_placed_10k.title"))
                .description(Localizer.dLocalize("advancement.challenge_value_placed_10k.description"))
                .model(CustomModel.get(Material.GOLD_INGOT, "advancement", "architect", "challenge_value_placed_10k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND)
                        .key("challenge_value_placed_100k")
                        .title(Localizer.dLocalize("advancement.challenge_value_placed_100k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_value_placed_100k.description"))
                        .model(CustomModel.get(Material.DIAMOND, "advancement", "architect", "challenge_value_placed_100k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_value_placed_10k", "blocks.placed.value", 10000, getConfig().challengePlace1kReward);
        registerMilestone("challenge_value_placed_100k", "blocks.placed.value", 100000, getConfig().challengePlace1kReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TNT_MINECART).key("challenge_demolish_val_5k")
                .title(Localizer.dLocalize("advancement.challenge_demolish_val_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_demolish_val_5k.description"))
                .model(CustomModel.get(Material.TNT_MINECART, "advancement", "architect", "challenge_demolish_val_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.END_CRYSTAL)
                        .key("challenge_demolish_val_50k")
                        .title(Localizer.dLocalize("advancement.challenge_demolish_val_50k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_demolish_val_50k.description"))
                        .model(CustomModel.get(Material.END_CRYSTAL, "advancement", "architect", "challenge_demolish_val_50k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_demolish_val_5k", "architect.demolish.value", 5000, getConfig().challengePlace1kReward);
        registerMilestone("challenge_demolish_val_50k", "architect.demolish.value", 50000, getConfig().challengePlace1kReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SCAFFOLDING).key("challenge_high_build_100")
                .title(Localizer.dLocalize("advancement.challenge_high_build_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_high_build_100.description"))
                .model(CustomModel.get(Material.SCAFFOLDING, "advancement", "architect", "challenge_high_build_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.LIGHTNING_ROD)
                        .key("challenge_high_build_1k")
                        .title(Localizer.dLocalize("advancement.challenge_high_build_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_high_build_1k.description"))
                        .model(CustomModel.get(Material.LIGHTNING_ROD, "advancement", "architect", "challenge_high_build_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_high_build_100", "architect.builds.high", 100, getConfig().challengePlace1kReward);
        registerMilestone("challenge_high_build_1k", "architect.builds.high", 1000, getConfig().challengePlace1kReward * 2);

        setIcon(Material.SMITHING_TABLE);
        registerAdaptation(new ArchitectGlass());
        registerAdaptation(new ArchitectFoundation());
        registerAdaptation(new ArchitectPlacement());
        registerAdaptation(new ArchitectWirelessRedstone());
        registerAdaptation(new ArchitectElevator());
        registerAdaptation(new ArchitectSmartShape());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (!isStorage(e.getBlock().getType().createBlockData())) {
                double v = getValue(e.getBlock()) * getConfig().xpValueMultiplier;
                AdaptPlayer adaptPlayer = getPlayer(p);
                adaptPlayer.getData().addStat("blocks.placed", 1);
                adaptPlayer.getData().addStat("blocks.placed.value", v);
                if (e.getBlock().getY() > 128) {
                    adaptPlayer.getData().addStat("architect.builds.high", 1);
                }

                handleBlockCooldown(p, () -> {
                    try {
                        xp(p, e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), getConfig().xpBase + v));
                    } catch (Exception ignored) {
                        Adapt.verbose("Failed to give XP to " + p.getName() + " for placing " + e.getBlock().getType().name());
                    }
                });
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            AdaptPlayer adaptPlayer = getPlayer(p);
            adaptPlayer.getData().addStat("blocks.broken", 1);
            adaptPlayer.getData().addStat("architect.demolish.value", getValue(e.getBlock()));
        });
    }

    @Override
    public void onTick() {
        checkStatTrackersForOnlinePlayers();
    }

    private void handleBlockCooldown(Player p, Runnable action) {
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        action.run();
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Place1k Reward for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengePlace1kReward = 1750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Value Multiplier for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpValueMultiplier = 1.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Base for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpBase = 3;
    }
}
