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
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.hunter.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
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
    }

    private void handleCooldownAndXp(Player p, double xpAmount) {
        if (cooldowns.containsKey(p)) {
            if (cooldowns.get(p) + getConfig().cooldownDelay > System.currentTimeMillis()) {
                return;
            } else {
                cooldowns.remove(p);
            }
        }
        cooldowns.put(p, System.currentTimeMillis());
        xp(p, xpAmount);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
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
            Adapt.info("Entity Death Event, XP: " + xpAmount);
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
        double turtleEggKillXP = 100;
        double creeperKillMultiplier = 2;
        double killMaxHealthXPMultiplier = 4;
        long cooldownDelay = 1000;
        double spawnerMobReductionXpMultiplier = 0.5;
    }
}
