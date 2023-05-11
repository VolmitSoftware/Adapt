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

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.nether.NetherFireResist;
import com.volmit.adapt.content.adaptation.nether.NetherSkullYeet;
import com.volmit.adapt.content.adaptation.nether.NetherWitherResist;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
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
        super("nether", Localizer.dLocalize("skill", "nether", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "nether", "description"));
        setDisplayName(Localizer.dLocalize("skill", "nether", "name"));
        setInterval(7425);
        setColor(C.DARK_GRAY);
        setIcon(Material.NETHER_STAR);
        registerAdaptation(new NetherWitherResist());
        registerAdaptation(new NetherSkullYeet());
        registerAdaptation(new NetherFireResist());
    }

    private boolean shouldReturnForEventWithCause(Player p, EntityDamageEvent.DamageCause cause) {
        return shouldReturnForPlayer(p) || cause != EntityDamageEvent.DamageCause.WITHER;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageEvent e) {
        if (!this.isEnabled() || e.isCancelled() || !(e.getEntity() instanceof Player p) || shouldReturnForEventWithCause(p, e.getCause()) || e instanceof EntityDamageByBlockEvent) {
            return;
        }
        xp(p, getConfig().getWitherDamageXp());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getBlock().getType() == Material.WITHER_ROSE && witherRoseCooldown == 0) {
                witherRoseCooldown = getConfig().getWitherRoseBreakCooldown();
                xp(p, e.getBlock().getLocation().add(.5D, .5D, .5D), getConfig().getWitherRoseBreakXp());
            }
        });

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || !p.getClass().getSimpleName().equals("CraftPlayer") || shouldReturnForPlayer(p)) {
            return;
        }
        if (e.getEntityType() == EntityType.WITHER_SKELETON) {
            xp(p, getConfig().getWitherSkeletonKillXp());
        } else if (e.getEntityType() == EntityType.WITHER) {
            xp(p, getConfig().getWitherKillXp());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || shouldReturnForEventWithCause(p, e.getCause())) {
            return;
        }
        xp(p, getConfig().getWitherAttackXp());
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
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
        private double witherDamageXp = 26.0;
        private double witherAttackXp = 15;
        private double witherSkeletonKillXp = 325;
        private double witherKillXp = 1250;
        private double witherRoseBreakXp = 125;
        private int witherRoseBreakCooldown = 60 * 20;
    }
}
