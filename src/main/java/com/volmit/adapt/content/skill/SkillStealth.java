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
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.stealth.StealthGhostArmor;
import com.volmit.adapt.content.adaptation.stealth.StealthSight;
import com.volmit.adapt.content.adaptation.stealth.StealthSnatch;
import com.volmit.adapt.content.adaptation.stealth.StealthSpeed;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.nms.advancements.advancement.AdvancementVisibility;
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
        super("stealth", Localizer.dLocalize("skill", "stealth", "icon"));
        registerConfiguration(Config.class);
        setColor(C.DARK_GRAY);
        setInterval(1412);
        setIcon(Material.WITHER_ROSE);
        cooldowns = new HashMap<>();
        setDescription(Localizer.dLocalize("skill", "stealth", "description"));
        setDisplayName(Localizer.dLocalize("skill", "stealth", "name"));
        registerAdaptation(new StealthSpeed());
        registerAdaptation(new StealthSnatch());
        registerAdaptation(new StealthGhostArmor());
        registerAdaptation(new StealthSight());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_LEGGINGS)
                .key("challenge_sneak_1k")
                .title("Knee Pain")
                .description("Sneak over a kilometer (1,000 blocks)")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sneak_1k").goal(1000).stat("move.sneak").reward(getConfig().challengeSneak1kReward).build());
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> {
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
        boolean enabled = true;
        double challengeSneak1kReward = 1750;
        double sneakXP = 10.5;
    }
}
