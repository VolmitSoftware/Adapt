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
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.hunter.*;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
    public SkillHunter() {
        super("hunter", Adapt.dLocalize("Skill", "Hunter", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.RED);
        setDescription(Adapt.dLocalize("Skill", "Hunter", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Hunter", "Name"));
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
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }
        if (e.getBlock().getType().equals(Material.TURTLE_EGG)) {
            xp(e.getBlock().getLocation(), getConfig().turtleEggKillXP, getConfig().turtleEggSpatialRadius, getConfig().turtleEggSpatialDuration);
            getPlayer(p).getData().addStat("killed.tutleeggs", 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }
        if (e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock() != null && e.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
            xp(e.getClickedBlock().getLocation(), getConfig().turtleEggKillXP, getConfig().turtleEggSpatialRadius, getConfig().turtleEggSpatialDuration);
            getPlayer(p).getData().addStat("killed.tutleeggs", 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDeathEvent e) {
        if (AdaptConfig.get().blacklistedWorlds.contains(e.getEntity().getWorld().getName())) {
            return;
        }
        if (e.getEntity().getKiller() != null) {
            if (!AdaptConfig.get().isXpInCreative() && e.getEntity().getKiller().getGameMode().name().contains("CREATIVE")) {
                return;
            }
            double cmult = e.getEntity().getType().equals(EntityType.CREEPER) ? getConfig().creeperKillMultiplier : 1;
            if (e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                xp(e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthSpatialXPMultiplier * cmult, getConfig().killSpatialRadius, getConfig().killSpatialDuration);
                xp(e.getEntity().getKiller(), e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthXPMultiplier * cmult);
                getPlayer(e.getEntity().getKiller()).getData().addStat("killed.kills", 1);
            }
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
        double turtleEggKillXP = 125;
        int turtleEggSpatialRadius = 24;
        long turtleEggSpatialDuration = 15000;
        double creeperKillMultiplier = 4;
        double killMaxHealthSpatialXPMultiplier = 3;
        double killMaxHealthXPMultiplier = 6;
        int killSpatialRadius = 24;
        long killSpatialDuration = 15000;
    }
}
