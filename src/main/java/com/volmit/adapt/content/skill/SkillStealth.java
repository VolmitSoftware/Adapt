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
        double sneakXP = 10.5;
    }
}
