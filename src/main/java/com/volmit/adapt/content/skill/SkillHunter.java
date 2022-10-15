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

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.hunter.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
    public SkillHunter() {
        super("hunter", Localizer.dLocalize("skill", "hunter", "icon"));
        registerConfiguration(Config.class);
        setColor(C.RED);
        setDescription(Localizer.dLocalize("skill", "hunter", "description"));
        setDisplayName(Localizer.dLocalize("skill", "hunter", "name"));
        setInterval(4150);
        setIcon(Material.BONE);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled() && this.isEnabled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }
        if (e.getBlock().getType().equals(Material.TURTLE_EGG)) {
            xp(e.getBlock().getLocation(), getConfig().turtleEggKillXP, getConfig().turtleEggSpatialRadius, getConfig().turtleEggSpatialDuration);
            getPlayer(p).getData().addStat("killed.tutleeggs", 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }

        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }
        if (e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock() != null && e.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
            xp(e.getClickedBlock().getLocation(), getConfig().turtleEggKillXP, getConfig().turtleEggSpatialRadius, getConfig().turtleEggSpatialDuration);
            getPlayer(p).getData().addStat("killed.tutleeggs", 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDeathEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (AdaptConfig.get().blacklistedWorlds.contains(e.getEntity().getWorld().getName())) {
            return;
        }
        if (e.getEntity().getKiller() != null && e.getEntity().getKiller().getClass().getSimpleName().equals("CraftPlayer")) {
            if (!AdaptConfig.get().isXpInCreative() && (e.getEntity().getKiller().getGameMode().equals(GameMode.CREATIVE) || e.getEntity().getKiller().getGameMode().equals(GameMode.SPECTATOR))) {
                return;
            }
            double cmult = e.getEntity().getType().equals(EntityType.CREEPER) ? getConfig().creeperKillMultiplier : 1;
            if (e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                if (e.getEntity().getPortalCooldown() > 0) {
                    xp(e.getEntity().getLocation(), getConfig().spawnerMobReductionXpMultiplier * (e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthSpatialXPMultiplier * cmult), getConfig().killSpatialRadius, getConfig().killSpatialDuration);
                    xp(e.getEntity().getKiller(), e.getEntity().getLocation(), getConfig().spawnerMobReductionXpMultiplier * (e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthXPMultiplier * cmult));
                    getPlayer(e.getEntity().getKiller()).getData().addStat("killed.kills", 1);

                } else {
                    xp(e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthSpatialXPMultiplier * cmult, getConfig().killSpatialRadius, getConfig().killSpatialDuration);
                    xp(e.getEntity().getKiller(), e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthXPMultiplier * cmult);
                    getPlayer(e.getEntity().getKiller()).getData().addStat("killed.kills", 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(CreatureSpawnEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        if (e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER)) {
            Entity ent = e.getEntity();
            ent.setPortalCooldown(630726000);
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
        int turtleEggSpatialRadius = 5;
        long turtleEggSpatialDuration = 15000;
        double creeperKillMultiplier = 2;
        double killMaxHealthSpatialXPMultiplier = 3;
        double killMaxHealthXPMultiplier = 4;
        int killSpatialRadius = 25;
        long killSpatialDuration = 10000;
        double spawnerMobReductionXpMultiplier = 0.5;
    }
}
