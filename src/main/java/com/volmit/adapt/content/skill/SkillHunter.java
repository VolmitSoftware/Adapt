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
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.hunter.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillHunter() {
        super("hunter", Localizer.dLocalize("skill", "hunter", "icon"));
        registerConfiguration(Config.class);
        setColor(C.RED);
        setDescription(Localizer.dLocalize("skill", "hunter", "description"));
        setDisplayName(Localizer.dLocalize("skill", "hunter", "name"));
        setInterval(4150);
        setIcon(Material.BONE);
        cooldowns = new HashMap<>();
        registerAdaptation(new HunterAdrenaline());
        registerAdaptation(new HunterRegen());
        registerAdaptation(new HunterInvis());
        registerAdaptation(new HunterJumpBoost());
        registerAdaptation(new HunterLuck());
        registerAdaptation(new HunterSpeed());
        registerAdaptation(new HunterStrength());
        registerAdaptation(new HunterResistance());
        registerAdaptation(new HunterDropToInventory());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TURTLE_EGG)
                .key("horrible_person")
                .title(Localizer.dLocalize("advancement", "horrible_person", "title"))
                .description(Localizer.dLocalize("advancement", "horrible_person", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.GOAL)
                .visibility(AdvancementVisibility.HIDDEN)
                .build()
        );
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TURTLE_EGG)
                .key("challenge_turtle_egg_smasher")
                .title(Localizer.dLocalize("advancement", "challenge_turtle_egg_smasher", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_turtle_egg_smasher", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TURTLE_EGG)
                        .key("challenge_turtle_egg_annihilator")
                        .title(Localizer.dLocalize("advancement", "challenge_turtle_egg_annihilator", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_turtle_egg_annihilator", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BONE)
                .key("challenge_novice_hunter")
                .title(Localizer.dLocalize("advancement", "challenge_novice_hunter", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_novice_hunter", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_SWORD)
                        .key("challenge_intermediate_hunter")
                        .title(Localizer.dLocalize("advancement", "challenge_intermediate_hunter", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_intermediate_hunter", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.DIAMOND_SWORD)
                                .key("challenge_advanced_hunter")
                                .title(Localizer.dLocalize("advancement", "challenge_advanced_hunter", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_advanced_hunter", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CREEPER_HEAD)
                .key("challenge_creeper_conqueror")
                .title(Localizer.dLocalize("advancement", "challenge_creeper_conqueror", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_creeper_conqueror", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TNT)
                        .key("challenge_creeper_annihilator")
                        .title(Localizer.dLocalize("advancement", "challenge_creeper_annihilator", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_creeper_annihilator", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());

        registerStatTracker(AdaptStatTracker.builder().advancement("horrible_person").goal(1).stat("killed.turtleeggs").reward(getConfig().turtleEggKillXP).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_turtle_egg_smasher").goal(100).stat("killed.turtleeggs").reward(getConfig().turtleEggKillXP*10).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_turtle_egg_annihilator").goal(1000).stat("killed.turtleeggs").reward(getConfig().turtleEggKillXP*10).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_novice_hunter").goal(100).stat("killed.monsters").reward(getConfig().turtleEggKillXP*3).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_intermediate_hunter").goal(1000).stat("killed.monsters").reward(getConfig().turtleEggKillXP*3).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_advanced_hunter").goal(10000).stat("killed.monsters").reward(getConfig().turtleEggKillXP*3).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_creeper_conqueror").goal(100).stat("killed.creepers").reward(getConfig().turtleEggKillXP*3).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_creeper_annihilator").goal(1000).stat("killed.creepers").reward(getConfig().turtleEggKillXP*3).build());
    }

    private void handleCooldownAndXp(Player p, double xpAmount) {
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        xp(p, xpAmount);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getBlock().getType().equals(Material.TURTLE_EGG)) {
                handleCooldownAndXp(p, getConfig().turtleEggKillXP);
                getPlayer(p).getData().addStat("killed.tutleeggs", 1);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock() != null && e.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
                handleCooldownAndXp(p, getConfig().turtleEggKillXP);
                getPlayer(p).getData().addStat("killed.tutleeggs", 1);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) {
            return;
        }
        Player p = e.getEntity().getKiller();

        if (!getConfig().getXpForAttackingWithTools) {
            return;
        }

        shouldReturnForPlayer(p, () -> {
            if (e.getEntity().getType().equals(EntityType.CREEPER)) {
                double cmult = getConfig().creeperKillMultiplier;
                double xpAmount = e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthXPMultiplier * cmult;
                if (e.getEntity().getPortalCooldown() > 0) {
                    xpAmount *= getConfig().spawnerMobReductionXpMultiplier;
                }
                getPlayer(p).getData().addStat("killed.kills", 1);
                handleCooldownAndXp(p,xpAmount);
            } else {
                handleEntityKill(p, e.getEntity());
            }
        });
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(CreatureSpawnEvent e) {
        if (!isEnabled() || e.isCancelled()) {
            return;
        }
        if (e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER)) {
            Entity ent = e.getEntity();
            ent.setPortalCooldown(630726000);
        }
    }

    private void handleEntityKill(Player p, Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            double xpAmount = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthXPMultiplier;
            if (entity.getPortalCooldown() > 0) {
                xpAmount *= getConfig().spawnerMobReductionXpMultiplier;
            }
            getPlayer(p).getData().addStat("killed.kills", 1);
            handleCooldownAndXp(p, xpAmount);
        }
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
        double turtleEggKillXP = 100;
        double creeperKillMultiplier = 2;
        double killMaxHealthXPMultiplier = 4;
        long cooldownDelay = 1000;
        double spawnerMobReductionXpMultiplier = 0.5;
    }
}
