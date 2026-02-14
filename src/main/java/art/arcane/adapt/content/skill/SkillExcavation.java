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
import art.arcane.adapt.content.adaptation.excavation.ExcavationDropToInventory;
import art.arcane.adapt.content.adaptation.excavation.ExcavationHaste;
import art.arcane.adapt.content.adaptation.excavation.ExcavationOmniTool;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSpelunker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.format.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SkillExcavation extends SimpleSkill<SkillExcavation.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillExcavation() {
        super("excavation", Localizer.dLocalize("skill.excavation.icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill.excavation.description"));
        setDisplayName(Localizer.dLocalize("skill.excavation.name"));
        setColor(C.YELLOW);
        setInterval(5953);
        setIcon(Material.DIAMOND_SHOVEL);
        cooldowns = new HashMap<>();
        registerAdaptation(new ExcavationHaste());
        registerAdaptation(new ExcavationSpelunker());
        registerAdaptation(new ExcavationOmniTool());
        registerAdaptation(new ExcavationDropToInventory());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_SHOVEL).key("challenge_excavate_1k")
                .title(Localizer.dLocalize("advancement.challenge_excavate_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_excavate_1k.description"))
                .model(CustomModel.get(Material.WOODEN_SHOVEL, "advancement", "excavation", "challenge_excavate_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.KNOWLEDGE_BOOK)
                        .key("challenge_excavate_5k")
                        .title(Localizer.dLocalize("advancement.challenge_excavate_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_excavate_5k.description"))
                        .model(CustomModel.get(Material.KNOWLEDGE_BOOK, "advancement", "excavation", "challenge_excavate_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.STONE_SHOVEL)
                                .key("challenge_excavate_50k")
                                .title(Localizer.dLocalize("advancement.challenge_excavate_50k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_excavate_50k.description"))
                                .model(CustomModel.get(Material.STONE_SHOVEL, "advancement", "excavation", "challenge_excavate_50k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.IRON_SHOVEL)
                                        .key("challenge_excavate_500k")
                                        .title(Localizer.dLocalize("advancement.challenge_excavate_500k.title"))
                                        .description(Localizer.dLocalize("advancement.challenge_excavate_500k.description"))
                                        .model(CustomModel.get(Material.IRON_SHOVEL, "advancement", "excavation", "challenge_excavate_500k"))
                                        .frame(AdaptAdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.DIAMOND_SHOVEL)
                                                .key("challenge_excavate_5m")
                                                .title(Localizer.dLocalize("advancement.challenge_excavate_5m.title"))
                                                .description(Localizer.dLocalize("advancement.challenge_excavate_5m.description"))
                                                .model(CustomModel.get(Material.DIAMOND_SHOVEL, "advancement", "excavation", "challenge_excavate_5m"))
                                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_excavate_1k", "excavation.blocks.broken", 1000, getConfig().challengeExcavationReward);
        registerMilestone("challenge_excavate_5k", "excavation.blocks.broken", 5000, getConfig().challengeExcavationReward);
        registerMilestone("challenge_excavate_50k", "excavation.blocks.broken", 50000, getConfig().challengeExcavationReward);
        registerMilestone("challenge_enchant_500k", "excavation.blocks.broken", 500000, getConfig().challengeExcavationReward);
        registerMilestone("challenge_excavate_5m", "excavation.blocks.broken", 5000000, getConfig().challengeExcavationReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_SHOVEL).key("challenge_dig_swing_500")
                .title(Localizer.dLocalize("advancement.challenge_dig_swing_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_dig_swing_500.description"))
                .model(CustomModel.get(Material.WOODEN_SHOVEL, "advancement", "excavation", "challenge_dig_swing_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_SHOVEL)
                        .key("challenge_dig_swing_5k")
                        .title(Localizer.dLocalize("advancement.challenge_dig_swing_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_dig_swing_5k.description"))
                        .model(CustomModel.get(Material.IRON_SHOVEL, "advancement", "excavation", "challenge_dig_swing_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_dig_swing_500", "excavation.swings", 500, getConfig().challengeExcavationReward);
        registerMilestone("challenge_dig_swing_5k", "excavation.swings", 5000, getConfig().challengeExcavationReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GOLDEN_SHOVEL).key("challenge_dig_damage_1k")
                .title(Localizer.dLocalize("advancement.challenge_dig_damage_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_dig_damage_1k.description"))
                .model(CustomModel.get(Material.GOLDEN_SHOVEL, "advancement", "excavation", "challenge_dig_damage_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_SHOVEL)
                        .key("challenge_dig_damage_10k")
                        .title(Localizer.dLocalize("advancement.challenge_dig_damage_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_dig_damage_10k.description"))
                        .model(CustomModel.get(Material.DIAMOND_SHOVEL, "advancement", "excavation", "challenge_dig_damage_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_dig_damage_1k", "excavation.damage", 1000, getConfig().challengeExcavationReward);
        registerMilestone("challenge_dig_damage_10k", "excavation.damage", 10000, getConfig().challengeExcavationReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CLAY_BALL).key("challenge_dig_value_5k")
                .title(Localizer.dLocalize("advancement.challenge_dig_value_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_dig_value_5k.description"))
                .model(CustomModel.get(Material.CLAY_BALL, "advancement", "excavation", "challenge_dig_value_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BRICK)
                        .key("challenge_dig_value_50k")
                        .title(Localizer.dLocalize("advancement.challenge_dig_value_50k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_dig_value_50k.description"))
                        .model(CustomModel.get(Material.BRICK, "advancement", "excavation", "challenge_dig_value_50k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_dig_value_5k", "excavation.blocks.value", 5000, getConfig().challengeExcavationReward);
        registerMilestone("challenge_dig_value_50k", "excavation.blocks.value", 50000, getConfig().challengeExcavationReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GRAVEL).key("challenge_dig_gravel_500")
                .title(Localizer.dLocalize("advancement.challenge_dig_gravel_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_dig_gravel_500.description"))
                .model(CustomModel.get(Material.GRAVEL, "advancement", "excavation", "challenge_dig_gravel_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.FLINT)
                        .key("challenge_dig_gravel_5k")
                        .title(Localizer.dLocalize("advancement.challenge_dig_gravel_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_dig_gravel_5k.description"))
                        .model(CustomModel.get(Material.FLINT, "advancement", "excavation", "challenge_dig_gravel_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_dig_gravel_500", "excavation.gravel", 500, getConfig().challengeExcavationReward);
        registerMilestone("challenge_dig_gravel_5k", "excavation.gravel", 5000, getConfig().challengeExcavationReward * 2);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && checkValidEntity(e.getEntity().getType())) {
            if (!getConfig().getXpForAttackingWithTools) {
                return;
            }
            shouldReturnForPlayer(p, e, () -> handleEntityDamageByPlayer(p, e));
        }
    }

    private void handleEntityDamageByPlayer(Player p, EntityDamageByEntityEvent e) {
        AdaptPlayer a = getPlayer(p);
        ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();
        if (isShovel(hand)) {
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
                return;
            cooldowns.put(p, System.currentTimeMillis());
            getPlayer(p).getData().addStat("excavation.swings", 1);
            getPlayer(p).getData().addStat("excavation.damage", e.getDamage());
            xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().axeDamageXPMultiplier * e.getDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(p, e, () -> {
            if (isShovel(p.getInventory().getItemInMainHand())) {
                handleBlockBreakWithShovel(p, e);
            }
        });

    }

    private void handleBlockBreakWithShovel(Player p, BlockBreakEvent e) {
        getPlayer(p).getData().addStat("excavation.blocks.broken", 1);
        getPlayer(p).getData().addStat("excavation.blocks.value", getValue(e.getBlock().getBlockData()));
        Material blockType = e.getBlock().getType();
        if (blockType == Material.GRAVEL || blockType == Material.SAND || blockType == Material.RED_SAND
                || blockType == Material.CLAY || blockType == Material.SOUL_SAND || blockType == Material.SOUL_SOIL) {
            getPlayer(p).getData().addStat("excavation.gravel", 1);
        }
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        double v = getValue(e.getBlock().getType());
        xp(p, e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), v));
    }

    public double getValue(Material type) {
        double value = super.getValue(type) * getConfig().valueXPMultiplier;
        value += Math.min(getConfig().maxHardnessBonus, type.getHardness());
        value += Math.min(getConfig().maxBlastResistanceBonus, type.getBlastResistance());
        return value;
    }


    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        checkStatTrackersForOnlinePlayers();
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Excavation skill.", impact = "True enables this behavior and false disables it.")
        boolean getXpForAttackingWithTools = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Hardness Bonus for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHardnessBonus = 9;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxBlastResistanceBonus = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Excavation Reward for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeExcavationReward = 1200;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Value XPMultiplier for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double valueXPMultiplier = 0.6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1250;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Axe Damage XPMultiplier for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double axeDamageXPMultiplier = 4.0;
    }
}
