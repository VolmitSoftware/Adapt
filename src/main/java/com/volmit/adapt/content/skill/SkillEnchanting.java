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
import com.volmit.adapt.content.adaptation.enchanting.EnchantingLapisReturn;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingQuickEnchant;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingXPReturn;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillEnchanting extends SimpleSkill<SkillEnchanting.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillEnchanting() {
        super("enchanting", Localizer.dLocalize("skill", "enchanting", "icon"));
        registerConfiguration(Config.class);
        setColor(C.LIGHT_PURPLE);
        setDescription(Localizer.dLocalize("skill", "enchanting", "description"));
        setDisplayName(Localizer.dLocalize("skill", "enchanting", "name"));
        setInterval(3909);
        setIcon(Material.KNOWLEDGE_BOOK);
        cooldowns = new HashMap<>();
        registerAdaptation(new EnchantingQuickEnchant());
        registerAdaptation(new EnchantingLapisReturn());
        registerAdaptation(new EnchantingXPReturn()); //
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CRAFTING_TABLE).key("challenge_enchant_1k")
                .title(Localizer.dLocalize("advancement", "challenge_enchant_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_enchant_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.KNOWLEDGE_BOOK)
                        .key("challenge_enchant_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_enchant_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_enchant_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.KNOWLEDGE_BOOK)
                                .key("challenge_enchant_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_enchant_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_enchant_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.KNOWLEDGE_BOOK)
                                        .key("challenge_enchant_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_enchant_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_enchant_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.KNOWLEDGE_BOOK)
                                                .key("challenge_enchant_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_enchant_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_enchant_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_1k").goal(1000).stat("enchanted.items").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_5k").goal(5000).stat("enchanted.items").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_50k").goal(50000).stat("enchanted.items").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_500k").goal(500000).stat("enchanted.items").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_5m").goal(5000000).stat("enchanted.items").reward(getConfig().challengeEnchantReward).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EnchantItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getEnchanter();
        shouldReturnForPlayer(p, e, () -> {
            handleEnchantItemEvent(p, e);
        });

    }

    private void handleEnchantItemEvent(Player p, EnchantItemEvent e) {
        AdaptPlayer adaptPlayer = getPlayer(p);
        adaptPlayer.getData().addStat("enchanted.items", 1);
        adaptPlayer.getData().addStat("enchanted.power", e.getEnchantsToAdd().values().stream().mapToInt(i -> i).sum());
        adaptPlayer.getData().addStat("enchanted.levels.spent", e.getExpLevelCost());

        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        xp(p, getConfig().enchantPowerXPMultiplier * e.getEnchantsToAdd().values().stream().mapToInt((i) -> i).sum());
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double enchantPowerXPMultiplier = 70;
        long cooldownDelay = 5250;
        double challengeEnchantReward = 2500;
    }
}
