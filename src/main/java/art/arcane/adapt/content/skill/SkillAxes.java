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
import art.arcane.adapt.content.adaptation.axe.*;
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

public class SkillAxes extends SimpleSkill<SkillAxes.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillAxes() {
        super("axes", Localizer.dLocalize("skill.axes.icon"));
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription(Localizer.dLocalize("skill.axes.description1") + C.ITALIC + Localizer.dLocalize("skill.axes.description2") + C.GRAY + " " + Localizer.dLocalize("skill.axes.description3"));
        setDisplayName(Localizer.dLocalize("skill.axes.name"));
        setInterval(5251);
        setIcon(Material.GOLDEN_AXE);
        cooldowns = new HashMap<>();
        registerAdaptation(new AxeGroundSmash());
        registerAdaptation(new AxeChop());
        registerAdaptation(new AxeDropToInventory());
        registerAdaptation(new AxeLeafVeinminer());
        registerAdaptation(new AxeWoodVeinminer());
        registerAdaptation(new AxeCraftLogSwap());
        registerAdaptation(new AxeTimberMark());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_AXE).key("challenge_chop_1k")
                .title(Localizer.dLocalize("advancement.challenge_chop_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_chop_1k.description"))
                .model(CustomModel.get(Material.WOODEN_AXE, "advancement", "axes", "challenge_chop_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.STONE_AXE)
                        .key("challenge_chop_5k")
                        .title(Localizer.dLocalize("advancement.challenge_chop_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_chop_5k.description"))
                        .model(CustomModel.get(Material.STONE_AXE, "advancement", "axes", "challenge_chop_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.IRON_AXE)
                                .key("challenge_chop_50k")
                                .title(Localizer.dLocalize("advancement.challenge_chop_50k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_chop_50k.description"))
                                .model(CustomModel.get(Material.IRON_AXE, "advancement", "axes", "challenge_chop_50k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.DIAMOND_AXE)
                                        .key("challenge_chop_500k")
                                        .title(Localizer.dLocalize("advancement.challenge_chop_500k.title"))
                                        .description(Localizer.dLocalize("advancement.challenge_chop_500k.description"))
                                        .model(CustomModel.get(Material.DIAMOND_AXE, "advancement", "axes", "challenge_chop_500k"))
                                        .frame(AdaptAdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.NETHERITE_AXE)
                                                .key("challenge_chop_5m")
                                                .title(Localizer.dLocalize("advancement.challenge_chop_5m.title"))
                                                .description(Localizer.dLocalize("advancement.challenge_chop_5m.description"))
                                                .model(CustomModel.get(Material.NETHERITE_AXE, "advancement", "axes", "challenge_chop_5m"))
                                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_chop_1k", "axes.blocks.broken", 1000, getConfig().challengeChopReward);
        registerMilestone("challenge_chop_5k", "axes.blocks.broken", 5000, getConfig().challengeChopReward);
        registerMilestone("challenge_chop_50k", "axes.blocks.broken", 50000, getConfig().challengeChopReward);
        registerMilestone("challenge_chop_500k", "axes.blocks.broken", 500000, getConfig().challengeChopReward);
        registerMilestone("challenge_chop_5m", "axes.blocks.broken", 5000000, getConfig().challengeChopReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_AXE).key("challenge_axe_swing_500")
                .title(Localizer.dLocalize("advancement.challenge_axe_swing_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_axe_swing_500.description"))
                .model(CustomModel.get(Material.WOODEN_AXE, "advancement", "axes", "challenge_axe_swing_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_AXE)
                        .key("challenge_axe_swing_5k")
                        .title(Localizer.dLocalize("advancement.challenge_axe_swing_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_axe_swing_5k.description"))
                        .model(CustomModel.get(Material.IRON_AXE, "advancement", "axes", "challenge_axe_swing_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_axe_swing_500", "axes.swings", 500, getConfig().challengeChopReward);
        registerMilestone("challenge_axe_swing_5k", "axes.swings", 5000, getConfig().challengeChopReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GOLDEN_AXE).key("challenge_axe_damage_1k")
                .title(Localizer.dLocalize("advancement.challenge_axe_damage_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_axe_damage_1k.description"))
                .model(CustomModel.get(Material.GOLDEN_AXE, "advancement", "axes", "challenge_axe_damage_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_AXE)
                        .key("challenge_axe_damage_10k")
                        .title(Localizer.dLocalize("advancement.challenge_axe_damage_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_axe_damage_10k.description"))
                        .model(CustomModel.get(Material.DIAMOND_AXE, "advancement", "axes", "challenge_axe_damage_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_axe_damage_1k", "axes.damage", 1000, getConfig().challengeChopReward);
        registerMilestone("challenge_axe_damage_10k", "axes.damage", 10000, getConfig().challengeChopReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.OAK_LOG).key("challenge_axe_value_5k")
                .title(Localizer.dLocalize("advancement.challenge_axe_value_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_axe_value_5k.description"))
                .model(CustomModel.get(Material.OAK_LOG, "advancement", "axes", "challenge_axe_value_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DARK_OAK_LOG)
                        .key("challenge_axe_value_50k")
                        .title(Localizer.dLocalize("advancement.challenge_axe_value_50k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_axe_value_50k.description"))
                        .model(CustomModel.get(Material.DARK_OAK_LOG, "advancement", "axes", "challenge_axe_value_50k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_axe_value_5k", "axes.blocks.value", 5000, getConfig().challengeChopReward);
        registerMilestone("challenge_axe_value_50k", "axes.blocks.value", 50000, getConfig().challengeChopReward * 2);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.OAK_LEAVES).key("challenge_leaves_500")
                .title(Localizer.dLocalize("advancement.challenge_leaves_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_leaves_500.description"))
                .model(CustomModel.get(Material.OAK_LEAVES, "advancement", "axes", "challenge_leaves_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.AZALEA_LEAVES)
                        .key("challenge_leaves_5k")
                        .title(Localizer.dLocalize("advancement.challenge_leaves_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_leaves_5k.description"))
                        .model(CustomModel.get(Material.AZALEA_LEAVES, "advancement", "axes", "challenge_leaves_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_leaves_500", "axes.leaves", 500, getConfig().challengeChopReward);
        registerMilestone("challenge_leaves_5k", "axes.leaves", 5000, getConfig().challengeChopReward * 2);
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
            shouldReturnForPlayer(p, () -> {
                if (e.getEntity().isDead() || e.getEntity().isInvulnerable() || p.isDead() || p.isInvulnerable()) {
                    return;
                }
                AdaptPlayer a = getPlayer(p);
                ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

                if (isAxe(hand)) {
                    handleCooldown(p, () -> {
                        a.getData().addStat("axes.swings", 1);
                        a.getData().addStat("axes.damage", e.getDamage());
                        xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().axeDamageXPMultiplier * e.getDamage());
                    });
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(p, () -> {
            if (isAxe(p.getInventory().getItemInMainHand())) {
                if (isLog(new ItemStack(e.getBlock().getType()))) {
                    double v = getValue(e.getBlock().getType());
                    AdaptPlayer a = getPlayer(p);
                    a.getData().addStat("axes.blocks.broken", 1);
                    a.getData().addStat("axes.blocks.value", getValue(e.getBlock().getBlockData()));
                    handleCooldown(p, () -> xp(p, e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), v)));
                }
                if (e.getBlock().getType().name().endsWith("_LEAVES")) {
                    getPlayer(p).getData().addStat("axes.leaves", 1);
                }
            }
        });
    }

    private void handleCooldown(Player p, Runnable action) {
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        action.run();
    }

    public double getValue(Material type) {
        double value = super.getValue(type) * getConfig().valueXPMultiplier;
        value += Math.min(getConfig().maxHardnessBonus, type.getHardness());
        value += Math.min(getConfig().maxBlastResistanceBonus, type.getBlastResistance());

        if (type.name().endsWith("_LOG") || type.name().endsWith("_WOOD")) {
            value += getConfig().logOrWoodXPMultiplier;
        }
        if (type.name().endsWith("_LEAVES")) {
            value += getConfig().leavesMultiplier;
        }

        if (type.getHardness() == 0) {
            value = 0;
        }

        return value;
    }


    @Override
    public void onTick() {
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
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Axes skill.", impact = "True enables this behavior and false disables it.")
        boolean getXpForAttackingWithTools = true;

        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Hardness Bonus for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHardnessBonus = 9;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxBlastResistanceBonus = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Chop Reward for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeChopReward = 1750;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Log Or Wood XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double logOrWoodXPMultiplier = 2.0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaves Multiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double leavesMultiplier = 0.75;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1500;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Value XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double valueXPMultiplier = 0.175;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Axe Damage XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double axeDamageXPMultiplier = 7.0;
    }
}
