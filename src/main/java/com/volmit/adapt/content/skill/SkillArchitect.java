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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.architect.ArchitectFoundation;
import com.volmit.adapt.content.adaptation.architect.ArchitectGlass;
import com.volmit.adapt.content.adaptation.architect.ArchitectPlacement;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.util.advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class SkillArchitect extends SimpleSkill<SkillArchitect.Config> {
    public SkillArchitect() {
        super("architect", Adapt.dLocalize("Skill", "Architect", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Adapt.dLocalize("Skill", "Architect", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Architect", "Name"));
        setInterval(3100);
        setIcon(Material.IRON_BARS);
        registerAdvancement(AdaptAdvancement.builder().icon(Material.BRICK).key("challenge_place_1k").title("So much to build!").description("Place over 1,000 blocks").frame(AdvancementDisplay.AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.PARENT_GRANTED).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_place_1k").goal(1000).stat("blocks.placed").reward(getConfig().challengePlace1kReward).build());
        setIcon(Material.SMITHING_TABLE);
        registerAdaptation(new ArchitectGlass());
        registerAdaptation(new ArchitectFoundation());
        registerAdaptation(new ArchitectPlacement());
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void on(BlockPlaceEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && e.getPlayer().getGameMode().name().contains("CREATIVE")) {
            return;
        }
        double v = getValue(e.getBlock()) * getConfig().xpValueMultiplier;
        J.a(() -> xp(e.getPlayer(), e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), getConfig().xpBase + v)));
        getPlayer(e.getPlayer()).getData().addStat("blocks.placed", 1);
        getPlayer(e.getPlayer()).getData().addStat("blocks.placed.value", v);

    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && e.getPlayer().getGameMode().name().contains("CREATIVE")) {
            return;
        }
        getPlayer(e.getPlayer()).getData().addStat("blocks.broken", 1);
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double challengePlace1kReward = 1750;
        double xpValueMultiplier = 1;
        double xpBase = 3;
    }
}
