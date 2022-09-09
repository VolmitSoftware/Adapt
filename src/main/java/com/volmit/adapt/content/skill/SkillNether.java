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
import com.volmit.adapt.content.adaptation.nether.NetherSkullYeet;
import com.volmit.adapt.content.adaptation.nether.NetherWitherResist;
import com.volmit.adapt.util.C;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class SkillNether extends SimpleSkill<SkillNether.Config> {
    private int witherRoseCooldown;

    public SkillNether() {
        super("nether", Adapt.dLocalize("Skill", "Nether", "Icon"));
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Skill", "Nether", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Nether", "Name"));
        setInterval(7425);
        setColor(C.DARK_GRAY);
        setIcon(Material.NETHER_STAR);
        registerAdaptation(new NetherWitherResist());
        registerAdaptation(new NetherSkullYeet());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.WITHER && event.getEntity() instanceof Player p && !(event instanceof EntityDamageByBlockEvent)) {
            if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().name().contains("CREATIVE")) {
                return;
            }
            xp(p, getConfig().getWitherDamageXp());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!AdaptConfig.get().isXpInCreative() && e.getPlayer().getGameMode().name().contains("CREATIVE")) {
            return;
        }
        if (e.getBlock().getType() == Material.WITHER_ROSE && witherRoseCooldown == 0) {
            witherRoseCooldown = getConfig().getWitherRoseBreakCooldown();
            xp(e.getPlayer(), e.getBlock().getLocation().add(.5D, .5D, .5D), getConfig().getWitherRoseBreakXp());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            Player p = e.getEntity().getKiller();
            if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().name().contains("CREATIVE")) {
                return;
            }
            if (e.getEntityType() == EntityType.WITHER_SKELETON) {
                xp(p, getConfig().getWitherSkeletonKillXp());
            } else if (e.getEntityType() == EntityType.WITHER) {
                xp(p, getConfig().getWitherKillXp());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().name().contains("CREATIVE")) {
                return;
            }
            xp(p, getConfig().getWitherAttackXp());
        }
    }

    @Override
    public void onTick() {
        if (witherRoseCooldown > 0) {
            witherRoseCooldown--;
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().isEnabled();
    }

    @Data
    @NoArgsConstructor
    public static class Config {
        private boolean enabled = true;
        private double witherDamageXp = 1.0D;
        private double witherAttackXp = 1.0D;
        private double witherSkeletonKillXp = 1.0D;
        private double witherKillXp = 1.0D;
        private double witherRoseBreakXp = 1.0D;
        private int witherRoseBreakCooldown = 60 * 20;
    }
}
