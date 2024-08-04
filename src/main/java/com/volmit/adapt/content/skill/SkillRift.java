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
import com.volmit.adapt.content.adaptation.rift.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillRift extends SimpleSkill<SkillRift.Config> {
    private final Map<Player, Long> lasttp;

    public SkillRift() {
        super("rift", Localizer.dLocalize("skill", "rift", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "rift", "description"));
        setDisplayName(Localizer.dLocalize("skill", "rift", "name"));
        setColor(C.DARK_PURPLE);
        setInterval(1154);
        setIcon(Material.ENDER_EYE);
        registerAdaptation(new RiftResist());
        registerAdaptation(new RiftAccess());
        registerAdaptation(new RiftEnderchest());
        registerAdaptation(new RiftGate());
        registerAdaptation(new RiftBlink());
        registerAdaptation(new RiftDescent());
        registerAdaptation(new RiftVisage());
        lasttp = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerTeleportEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (!lasttp.containsKey(p)) {
                xpSilent(p, getConfig().teleportXP);
                lasttp.put(p, M.ms());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity() instanceof EnderPearl) {
                xp(p, getConfig().throwEnderpearlXP);
            } else if (e.getEntity() instanceof EnderSignal) {
                xp(p, getConfig().throwEnderEyeXP);
            }
        });
    }

    private void handleEntityDamageByEntity(Entity entity, Player p, double damage) {

        if (entity instanceof Enderman) {
            xp(p, getConfig().damageEndermanXPMultiplier * Math.min(damage, ((Enderman) entity).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        } else if (entity instanceof Endermite) {
            xp(p, getConfig().damageEndermiteXPMultiplier * Math.min(damage, ((Endermite) entity).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        } else if (entity instanceof EnderDragon) {
            xp(p, getConfig().damageEnderdragonXPMultiplier * Math.min(damage, ((EnderDragon) entity).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        } else if (entity instanceof EnderCrystal) {
            xp(p, getConfig().damageEndCrystalXP);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(e.getEntity(), p, e.getDamage()));
        } else if (e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(e.getEntity(), p, e.getDamage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        if (e.getEntity() instanceof EnderCrystal && e.getEntity().getKiller() != null) {
            Player p = e.getEntity().getKiller();
            shouldReturnForPlayer(p, () -> xp(e.getEntity().getKiller(), getConfig().destroyEndCrystalXP));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lasttp.remove(p);
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : lasttp.k()) {
            shouldReturnForPlayer(i, () -> {
                if (M.ms() - lasttp.get(i) > getConfig().teleportXPCooldown) {
                    lasttp.remove(i);
                }
            });
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double destroyEndCrystalXP = 350;
        double damageEndCrystalXP = 110;
        double damageEndermanXPMultiplier = 4;
        double damageEndermiteXPMultiplier = 2;
        double damageEnderdragonXPMultiplier = 8;
        double throwEnderpearlXP = 105;
        double throwEnderEyeXP = 45;
        double teleportXP = 15;
        double teleportXPCooldown = 60000;
    }
}
