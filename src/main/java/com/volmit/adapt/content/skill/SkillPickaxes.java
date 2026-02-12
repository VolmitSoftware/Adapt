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
import com.volmit.adapt.content.adaptation.pickaxe.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SkillPickaxes extends SimpleSkill<SkillPickaxes.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillPickaxes() {
        super("pickaxe", Localizer.dLocalize("skill.pickaxe.icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill.pickaxe.description"));
        setDisplayName(Localizer.dLocalize("skill.pickaxe.name"));
        setColor(C.GOLD);
        setInterval(2750);
        setIcon(Material.NETHERITE_PICKAXE);
        cooldowns = new HashMap<>();
        registerAdaptation(new PickaxeChisel());
        registerAdaptation(new PickaxeVeinminer());
        registerAdaptation(new PickaxeAutosmelt());
        registerAdaptation(new PickaxeDropToInventory());
        registerAdaptation(new PickaxeSilkSpawner());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_PICKAXE)
                .key("challenge_pickaxe_1k")
                .title(Localizer.dLocalize("advancement.challenge_pickaxe_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_pickaxe_1k.description"))
                .model(CustomModel.get(Material.WOODEN_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.STONE_PICKAXE)
                        .key("challenge_pickaxe_5k")
                        .title(Localizer.dLocalize("advancement.challenge_pickaxe_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_pickaxe_5k.description"))
                        .model(CustomModel.get(Material.STONE_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.IRON_PICKAXE)
                                .key("challenge_pickaxe_50k")
                                .title(Localizer.dLocalize("advancement.challenge_pickaxe_50k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_pickaxe_50k.description"))
                                .model(CustomModel.get(Material.IRON_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_50k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .child(AdaptAdvancement.builder()
                                        .icon(Material.DIAMOND_PICKAXE)
                                        .key("challenge_pickaxe_500k")
                                        .title(Localizer.dLocalize("advancement.challenge_pickaxe_500k.title"))
                                        .description(Localizer.dLocalize("advancement.challenge_pickaxe_500k.description"))
                                        .model(CustomModel.get(Material.DIAMOND_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_500k"))
                                        .frame(AdaptAdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                                        .child(AdaptAdvancement.builder()
                                                .icon(Material.NETHERITE_PICKAXE)
                                                .key("challenge_pickaxe_5m")
                                                .title(Localizer.dLocalize("advancement.challenge_pickaxe_5m.title"))
                                                .description(Localizer.dLocalize("advancement.challenge_pickaxe_5m.description"))
                                                .model(CustomModel.get(Material.NETHERITE_PICKAXE, "advancement", "pickaxe", "challenge_pickaxe_5m"))
                                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());

        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pickaxe_1k").goal(100).stat("pickaxe.blocks.broken").reward(getConfig().emeraldBonus*2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pickaxe_5k").goal(500).stat("pickaxe.blocks.broken").reward(getConfig().emeraldBonus*5).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pickaxe_50k").goal(5000).stat("pickaxe.blocks.broken").reward(getConfig().emeraldBonus*10).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pickaxe_500k").goal(50000).stat("pickaxe.blocks.broken").reward(getConfig().emeraldBonus*10).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pickaxe_5m").goal(500000).stat("pickaxe.blocks.broken").reward(getConfig().emeraldBonus*50).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_PICKAXE).key("challenge_pick_swing_500")
                .title(Localizer.dLocalize("advancement.challenge_pick_swing_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_pick_swing_500.description"))
                .model(CustomModel.get(Material.WOODEN_PICKAXE, "advancement", "pickaxe", "challenge_pick_swing_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_PICKAXE)
                        .key("challenge_pick_swing_5k")
                        .title(Localizer.dLocalize("advancement.challenge_pick_swing_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_pick_swing_5k.description"))
                        .model(CustomModel.get(Material.IRON_PICKAXE, "advancement", "pickaxe", "challenge_pick_swing_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_swing_500").goal(500).stat("pickaxe.swings").reward(getConfig().emeraldBonus).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_swing_5k").goal(5000).stat("pickaxe.swings").reward(getConfig().emeraldBonus * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GOLDEN_PICKAXE).key("challenge_pick_damage_1k")
                .title(Localizer.dLocalize("advancement.challenge_pick_damage_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_pick_damage_1k.description"))
                .model(CustomModel.get(Material.GOLDEN_PICKAXE, "advancement", "pickaxe", "challenge_pick_damage_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_PICKAXE)
                        .key("challenge_pick_damage_10k")
                        .title(Localizer.dLocalize("advancement.challenge_pick_damage_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_pick_damage_10k.description"))
                        .model(CustomModel.get(Material.DIAMOND_PICKAXE, "advancement", "pickaxe", "challenge_pick_damage_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_damage_1k").goal(1000).stat("pickaxe.damage").reward(getConfig().emeraldBonus).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_damage_10k").goal(10000).stat("pickaxe.damage").reward(getConfig().emeraldBonus * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.RAW_IRON).key("challenge_pick_value_5k")
                .title(Localizer.dLocalize("advancement.challenge_pick_value_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_pick_value_5k.description"))
                .model(CustomModel.get(Material.RAW_IRON, "advancement", "pickaxe", "challenge_pick_value_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.RAW_GOLD)
                        .key("challenge_pick_value_50k")
                        .title(Localizer.dLocalize("advancement.challenge_pick_value_50k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_pick_value_50k.description"))
                        .model(CustomModel.get(Material.RAW_GOLD, "advancement", "pickaxe", "challenge_pick_value_50k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_value_5k").goal(5000).stat("pickaxe.blocks.value").reward(getConfig().emeraldBonus).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_value_50k").goal(50000).stat("pickaxe.blocks.value").reward(getConfig().emeraldBonus * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_ORE).key("challenge_pick_ores_500")
                .title(Localizer.dLocalize("advancement.challenge_pick_ores_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_pick_ores_500.description"))
                .model(CustomModel.get(Material.IRON_ORE, "advancement", "pickaxe", "challenge_pick_ores_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_ORE)
                        .key("challenge_pick_ores_5k")
                        .title(Localizer.dLocalize("advancement.challenge_pick_ores_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_pick_ores_5k.description"))
                        .model(CustomModel.get(Material.DIAMOND_ORE, "advancement", "pickaxe", "challenge_pick_ores_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_ores_500").goal(500).stat("pickaxe.ores").reward(getConfig().emeraldBonus).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_pick_ores_5k").goal(5000).stat("pickaxe.ores").reward(getConfig().emeraldBonus * 2).build());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getDamager() instanceof Player ? (Player) e.getDamager() : null;
        if (!getConfig().getXpForAttackingWithTools || p == null) {
            return;
        }

        shouldReturnForPlayer(p, () -> {
            if (checkValidEntity(e.getEntity().getType())) {
                AdaptPlayer a = getPlayer(p);
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (isPickaxe(hand)) {
                    a.getData().addStat("pickaxe.swings", 1);
                    a.getData().addStat("pickaxe.damage", e.getDamage());
                    handleCooldown(p, () -> xp(p, e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage()));
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(p, () -> {
            ItemStack mainHand = p.getInventory().getItemInMainHand();

            if (isPickaxe(mainHand)) {
                Material blockType = e.getBlock().getType();
                double blockValue = getValue(blockType);
                AdaptPlayer adaptPlayer = getPlayer(p);

                adaptPlayer.getData().addStat("pickaxe.blocks.broken", 1);
                adaptPlayer.getData().addStat("pickaxe.blocks.value", blockValue);
                if (blockType.name().contains("_ORE")) {
                    adaptPlayer.getData().addStat("pickaxe.ores", 1);
                }

                handleCooldown(p, () -> {
                    if (mainHand.getEnchantments().containsKey(Enchantment.SILK_TOUCH)) {
                        xp(p, 5);
                    } else {
                        Location blockLocation = e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5);
                        J.a(() -> xp(p, blockLocation, blockXP(e.getBlock(), blockValue)));
                    }
                });
            }
        });
    }

    public double getValue(Material type) {
        Config c = getConfig();
        double value = super.getValue(type) * c.blockValueMultiplier;
        value += Math.min(c.maxHardnessBonus, type.getHardness());
        value += Math.min(c.maxBlastResistanceBonus, type.getBlastResistance());

        value += switch (type) {
            case COAL_ORE -> c.coalBonus;
            case COPPER_ORE -> c.copperBonus;
            case IRON_ORE -> c.ironBonus;
            case GOLD_ORE -> c.goldBonus;
            case LAPIS_ORE -> c.lapisBonus;
            case DIAMOND_ORE -> c.diamondBonus;
            case EMERALD_ORE -> c.emeraldBonus;
            case NETHER_GOLD_ORE -> c.netherGoldBonus;
            case NETHER_QUARTZ_ORE -> c.netherQuartzBonus;
            case REDSTONE_ORE -> c.redstoneBonus;
            case ANCIENT_DEBRIS -> c.debrisBonus;
            case DEEPSLATE_COAL_ORE -> c.coalBonus * c.deepslateMultiplier;
            case DEEPSLATE_COPPER_ORE -> c.copperBonus * c.deepslateMultiplier;
            case DEEPSLATE_IRON_ORE -> c.ironBonus * c.deepslateMultiplier;
            case DEEPSLATE_GOLD_ORE -> c.goldBonus * c.deepslateMultiplier;
            case DEEPSLATE_LAPIS_ORE -> c.lapisBonus * c.deepslateMultiplier;
            case DEEPSLATE_DIAMOND_ORE -> c.diamondBonus * c.deepslateMultiplier;
            case DEEPSLATE_EMERALD_ORE -> c.emeraldBonus * c.deepslateMultiplier;
            case DEEPSLATE_REDSTONE_ORE -> c.redstoneBonus * c.deepslateMultiplier;
            default -> 0;
        };

        return value * 0.48;
    }


    private void handleCooldown(Player p, Runnable action) {
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        action.run();
    }

    @Override
    public void onTick() {
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Debris Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double debrisBonus = 210;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Pickaxes skill.", impact = "True enables this behavior and false disables it.")
        boolean getXpForAttackingWithTools = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage XPMultiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageXPMultiplier = 6.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Block Value Multiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double blockValueMultiplier = 0.125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Hardness Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHardnessBonus = 9;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxBlastResistanceBonus = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Coal Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double coalBonus = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Iron Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double ironBonus = 30;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Redstone Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double redstoneBonus = 55;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Copper Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double copperBonus = 22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Gold Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double goldBonus = 38;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Lapis Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double lapisBonus = 75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Diamond Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double diamondBonus = 175;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Emerald Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double emeraldBonus = 210;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Nether Gold Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double netherGoldBonus = 105;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Nether Quartz Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double netherQuartzBonus = 125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Deepslate Multiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double deepslateMultiplier = 1.35;
    }
}
