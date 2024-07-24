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
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.architect.ArchitectFoundation;
import com.volmit.adapt.content.adaptation.architect.ArchitectGlass;
import com.volmit.adapt.content.adaptation.architect.ArchitectPlacement;
import com.volmit.adapt.content.adaptation.architect.ArchitectWirelessRedstone;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
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
        super("architect", Localizer.dLocalize("skill", "architect", "icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Localizer.dLocalize("skill", "architect", "description"));
        setDisplayName(Localizer.dLocalize("skill", "architect", "name"));
        setInterval(3100);
        setIcon(Material.IRON_BARS);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BRICK).key("challenge_place_1k")
                .title(Localizer.dLocalize("advancement", "challenge_place_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_place_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.BRICK)
                        .key("challenge_place_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_place_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_place_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.NETHER_BRICK)
                                .key("challenge_place_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_place_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_place_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.NETHER_BRICK)
                                        .key("challenge_place_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_place_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_place_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.IRON_INGOT)
                                                .key("challenge_place_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_place_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_place_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_place_1k").goal(1000).stat("blocks.placed").reward(getConfig().challengePlace1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_place_5k").goal(5000).stat("blocks.placed").reward(getConfig().challengePlace1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_place_50k").goal(50000).stat("blocks.placed").reward(getConfig().challengePlace1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_place_500k").goal(500000).stat("blocks.placed").reward(getConfig().challengePlace1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_place_5m").goal(5000000).stat("blocks.placed").reward(getConfig().challengePlace1kReward).build());
        setIcon(Material.SMITHING_TABLE);
        registerAdaptation(new ArchitectGlass());
        registerAdaptation(new ArchitectFoundation());
        registerAdaptation(new ArchitectPlacement());
        registerAdaptation(new ArchitectWirelessRedstone());
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

                handleBlockCooldown(p, () -> {
                    try {
                        J.a(() -> xp(p, e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), getConfig().xpBase + v)));
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
        shouldReturnForPlayer(p, e, () -> getPlayer(p).getData().addStat("blocks.broken", 1));
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> checkStatTrackers(getPlayer(i)));
        }
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
        boolean enabled = true;
        double challengePlace1kReward = 1750;
        double xpValueMultiplier = 1;
        long cooldownDelay = 1250;
        double xpBase = 1;
    }
}
