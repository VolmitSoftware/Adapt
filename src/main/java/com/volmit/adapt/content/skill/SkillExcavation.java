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
import com.volmit.adapt.content.adaptation.excavation.ExcavationDropToInventory;
import com.volmit.adapt.content.adaptation.excavation.ExcavationHaste;
import com.volmit.adapt.content.adaptation.excavation.ExcavationOmniTool;
import com.volmit.adapt.content.adaptation.excavation.ExcavationSpelunker;
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

public class SkillExcavation extends SimpleSkill<SkillExcavation.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillExcavation() {
        super("excavation", Localizer.dLocalize("skill", "excavation", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "excavation", "description"));
        setDisplayName(Localizer.dLocalize("skill", "excavation", "name"));
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
                .title(Localizer.dLocalize("advancement", "challenge_excavate_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_excavate_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.KNOWLEDGE_BOOK)
                        .key("challenge_excavate_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_excavate_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_excavate_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.STONE_SHOVEL)
                                .key("challenge_excavate_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_excavate_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_excavate_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.IRON_SHOVEL)
                                        .key("challenge_excavate_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_excavate_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_excavate_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.DIAMOND_SHOVEL)
                                                .key("challenge_excavate_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_excavate_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_excavate_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_excavate_1k").goal(1000).stat("excavation.blocks.broken").reward(getConfig().challengeExcavationReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_excavate_5k").goal(5000).stat("excavation.blocks.broken").reward(getConfig().challengeExcavationReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_excavate_50k").goal(50000).stat("excavation.blocks.broken").reward(getConfig().challengeExcavationReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchant_500k").goal(500000).stat("excavation.blocks.broken").reward(getConfig().challengeExcavationReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_excavate_5m").goal(5000000).stat("excavation.blocks.broken").reward(getConfig().challengeExcavationReward).build());
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
        double challengeExcavationReward = 1200;
        double valueXPMultiplier = 0.825;
        long cooldownDelay = 1250;
        double axeDamageXPMultiplier = 6.5;
    }
}
