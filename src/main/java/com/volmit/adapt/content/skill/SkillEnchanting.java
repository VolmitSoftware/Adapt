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
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingLapisReturn;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingQuickEnchant;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingXPReturn;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
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
        super("enchanting", Localizer.dLocalize("skill.enchanting.icon"));
        registerConfiguration(Config.class);
        setColor(C.LIGHT_PURPLE);
        setDescription(Localizer.dLocalize("skill.enchanting.description"));
        setDisplayName(Localizer.dLocalize("skill.enchanting.name"));
        setInterval(3909);
        setIcon(Material.KNOWLEDGE_BOOK);
        cooldowns = new HashMap<>();
        registerAdaptation(new EnchantingQuickEnchant());
        registerAdaptation(new EnchantingLapisReturn());
        registerAdaptation(new EnchantingXPReturn()); //
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CRAFTING_TABLE).key("challenge_enchant_1k")
                .title(Localizer.dLocalize("advancement.challenge_enchant_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchant_1k.description"))
                .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "enchanting", "challenge_enchant_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.KNOWLEDGE_BOOK)
                        .key("challenge_enchant_5k")
                        .title(Localizer.dLocalize("advancement.challenge_enchant_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_enchant_5k.description"))
                        .model(CustomModel.get(Material.KNOWLEDGE_BOOK, "advancement", "enchanting", "challenge_enchant_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.KNOWLEDGE_BOOK)
                                .key("challenge_enchant_50k")
                                .title(Localizer.dLocalize("advancement.challenge_enchant_50k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_enchant_50k.description"))
                                .model(CustomModel.get(Material.KNOWLEDGE_BOOK, "advancement", "enchanting", "challenge_enchant_50k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.KNOWLEDGE_BOOK)
                                        .key("challenge_enchant_500k")
                                        .title(Localizer.dLocalize("advancement.challenge_enchant_500k.title"))
                                        .description(Localizer.dLocalize("advancement.challenge_enchant_500k.description"))
                                        .model(CustomModel.get(Material.KNOWLEDGE_BOOK, "advancement", "enchanting", "challenge_enchant_500k"))
                                        .frame(AdaptAdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.KNOWLEDGE_BOOK)
                                                .key("challenge_enchant_5m")
                                                .title(Localizer.dLocalize("advancement.challenge_enchant_5m.title"))
                                                .description(Localizer.dLocalize("advancement.challenge_enchant_5m.description"))
                                                .model(CustomModel.get(Material.KNOWLEDGE_BOOK, "advancement", "enchanting", "challenge_enchant_5m"))
                                                .frame(AdaptAdvancementFrame.CHALLENGE)
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

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.EXPERIENCE_BOTTLE)
                .key("challenge_enchant_power_100")
                .title(Localizer.dLocalize("advancement.challenge_enchant_power_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchant_power_100.description"))
                .model(CustomModel.get(Material.EXPERIENCE_BOTTLE, "advancement", "enchanting", "challenge_enchant_power_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENCHANTING_TABLE)
                        .key("challenge_enchant_power_1k")
                        .title(Localizer.dLocalize("advancement.challenge_enchant_power_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_enchant_power_1k.description"))
                        .model(CustomModel.get(Material.ENCHANTING_TABLE, "advancement", "enchanting", "challenge_enchant_power_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_power_100").goal(100).stat("enchanted.power").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_power_1k").goal(1000).stat("enchanted.power").reward(getConfig().challengeEnchantReward * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LAPIS_LAZULI)
                .key("challenge_enchant_levels_1k")
                .title(Localizer.dLocalize("advancement.challenge_enchant_levels_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchant_levels_1k.description"))
                .model(CustomModel.get(Material.LAPIS_LAZULI, "advancement", "enchanting", "challenge_enchant_levels_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.LAPIS_BLOCK)
                        .key("challenge_enchant_levels_10k")
                        .title(Localizer.dLocalize("advancement.challenge_enchant_levels_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_enchant_levels_10k.description"))
                        .model(CustomModel.get(Material.LAPIS_BLOCK, "advancement", "enchanting", "challenge_enchant_levels_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_levels_1k").goal(1000).stat("enchanted.levels.spent").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_levels_10k").goal(10000).stat("enchanted.levels.spent").reward(getConfig().challengeEnchantReward * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BOOKSHELF)
                .key("challenge_enchant_high_25")
                .title(Localizer.dLocalize("advancement.challenge_enchant_high_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchant_high_25.description"))
                .model(CustomModel.get(Material.BOOKSHELF, "advancement", "enchanting", "challenge_enchant_high_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENCHANTED_BOOK)
                        .key("challenge_enchant_high_250")
                        .title(Localizer.dLocalize("advancement.challenge_enchant_high_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_enchant_high_250.description"))
                        .model(CustomModel.get(Material.ENCHANTED_BOOK, "advancement", "enchanting", "challenge_enchant_high_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_high_25").goal(25).stat("enchanting.high.level").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_high_250").goal(250).stat("enchanting.high.level").reward(getConfig().challengeEnchantReward * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.EXPERIENCE_BOTTLE)
                .key("challenge_enchant_total_500")
                .title(Localizer.dLocalize("advancement.challenge_enchant_total_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchant_total_500.description"))
                .model(CustomModel.get(Material.EXPERIENCE_BOTTLE, "advancement", "enchanting", "challenge_enchant_total_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DRAGON_BREATH)
                        .key("challenge_enchant_total_5k")
                        .title(Localizer.dLocalize("advancement.challenge_enchant_total_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_enchant_total_5k.description"))
                        .model(CustomModel.get(Material.DRAGON_BREATH, "advancement", "enchanting", "challenge_enchant_total_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_total_500").goal(500).stat("enchanting.total.levels").reward(getConfig().challengeEnchantReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_total_5k").goal(5000).stat("enchanting.total.levels").reward(getConfig().challengeEnchantReward * 2).build());
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
        if (e.getExpLevelCost() >= 30) {
            adaptPlayer.getData().addStat("enchanting.high.level", 1);
        }
        adaptPlayer.getData().addStat("enchanting.total.levels", e.getExpLevelCost());

        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        xp(p, getConfig().enchantPowerXPMultiplier * e.getEnchantsToAdd().values().stream().mapToInt((i) -> i).sum());
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> checkStatTrackers(getPlayer(i)));
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Enchant Power XPMultiplier for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double enchantPowerXPMultiplier = 70;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 5250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Enchant Reward for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeEnchantReward = 2500;
    }
}
