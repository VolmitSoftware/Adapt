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
import com.volmit.adapt.content.adaptation.axe.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
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
        super("axes", Localizer.dLocalize("skill", "axes", "icon"));
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription(Localizer.dLocalize("skill", "axes", "description1") + C.ITALIC + Localizer.dLocalize("skill", "axes", "description2") + C.GRAY + " " + Localizer.dLocalize("skill", "axes", "description3"));
        setDisplayName(Localizer.dLocalize("skill", "axes", "name"));
        setInterval(5251);
        setIcon(Material.GOLDEN_AXE);
        cooldowns = new HashMap<>();
        registerAdaptation(new AxeGroundSmash());
        registerAdaptation(new AxeChop());
        registerAdaptation(new AxeDropToInventory());
        registerAdaptation(new AxeLeafVeinminer());
        registerAdaptation(new AxeWoodVeinminer());
        registerAdaptation(new AxeCraftLogSwap());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WOODEN_AXE).key("challenge_chop_1k")
                .title(Localizer.dLocalize("advancement", "challenge_chop_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_chop_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.STONE_AXE)
                        .key("challenge_chop_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_chop_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_chop_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.IRON_AXE)
                                .key("challenge_chop_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_chop_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_chop_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.DIAMOND_AXE)
                                        .key("challenge_chop_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_chop_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_chop_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.NETHERITE_AXE)
                                                .key("challenge_chop_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_chop_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_chop_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chop_1k").goal(1000).stat("axes.blocks.broken").reward(getConfig().challengeChopReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chop_5k").goal(5000).stat("axes.blocks.broken").reward(getConfig().challengeChopReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chop_50k").goal(50000).stat("axes.blocks.broken").reward(getConfig().challengeChopReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chop_500k").goal(500000).stat("axes.blocks.broken").reward(getConfig().challengeChopReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chop_5m").goal(5000000).stat("axes.blocks.broken").reward(getConfig().challengeChopReward).build());
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
            if (isAxe(p.getInventory().getItemInMainHand()) && isLog(new ItemStack(e.getBlock().getType()))) {
                double v = getValue(e.getBlock().getType());
                AdaptPlayer a = getPlayer(p);
                a.getData().addStat("axes.blocks.broken", 1);
                a.getData().addStat("axes.blocks.value", getValue(e.getBlock().getBlockData()));
                handleCooldown(p, () -> xp(p, e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), v)));
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

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        boolean getXpForAttackingWithTools = true;

        double maxHardnessBonus = 9;
        double maxBlastResistanceBonus = 10;
        double challengeChopReward = 1750;
        double logOrWoodXPMultiplier = 2.67;
        double leavesMultiplier = 1.11;
        long cooldownDelay = 2250;
        double valueXPMultiplier = 0.225;
        double axeDamageXPMultiplier = 13.26;
    }
}
