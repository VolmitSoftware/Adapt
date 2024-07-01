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
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeChisel;
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeDropToInventory;
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeVeinminer;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
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
        super("pickaxe", Localizer.dLocalize("skill", "pickaxe", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "pickaxe", "description"));
        setDisplayName(Localizer.dLocalize("skill", "pickaxe", "name"));
        setColor(C.GOLD);
        setInterval(2750);
        setIcon(Material.NETHERITE_PICKAXE);
        cooldowns = new HashMap<>();
        registerAdaptation(new PickaxeChisel());
        registerAdaptation(new PickaxeVeinminer());
        registerAdaptation(new PickaxeAutosmelt());
        registerAdaptation(new PickaxeDropToInventory());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_PICKAXE)
                .key("challenge_pickaxe_1k")
                .title(Localizer.dLocalize("advancement", "challenge_pickaxe_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_pickaxe_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.STONE_PICKAXE)
                        .key("challenge_pickaxe_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_pickaxe_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_pickaxe_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.IRON_PICKAXE)
                                .key("challenge_pickaxe_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_pickaxe_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_pickaxe_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .child(AdaptAdvancement.builder()
                                        .icon(Material.DIAMOND_PICKAXE)
                                        .key("challenge_pickaxe_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_pickaxe_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_pickaxe_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                                        .child(AdaptAdvancement.builder()
                                                .icon(Material.NETHERITE_PICKAXE)
                                                .key("challenge_pickaxe_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_pickaxe_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_pickaxe_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
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
        
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getDamager() instanceof Player ? (Player) e.getDamager() : null;
        if (!getConfig().getXpForAttackingWithTools) {
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

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        public double debrisBonus = 300;
        boolean enabled = true;
        boolean getXpForAttackingWithTools = true;
        double damageXPMultiplier = 13.26;
        double blockValueMultiplier = 0.125;
        double maxHardnessBonus = 9;
        double maxBlastResistanceBonus = 10;
        double coalBonus = 25;
        double ironBonus = 40;
        double redstoneBonus = 75;
        double copperBonus = 30;
        double goldBonus = 50;
        double lapisBonus = 105;
        long cooldownDelay = 1250;
        double diamondBonus = 250;
        double emeraldBonus = 300;
        double netherGoldBonus = 150;
        double netherQuartzBonus = 175;
        double deepslateMultiplier = 1.35;
    }
}
