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
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.content.adaptation.rift.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.reflect.enums.Attributes;
import com.volmit.adapt.util.reflect.enums.EntityTypes;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
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
        if (entity instanceof LivingEntity living) {
            var attribute = Version.get().getAttribute(living, Attributes.GENERIC_MAX_HEALTH);
            double baseHealth = attribute == null ? 1 : attribute.getBaseValue();
            double multiplier = switch (entity.getType()) {
                case ENDERMAN -> getConfig().damageEndermanXPMultiplier;
                case ENDERMITE -> getConfig().damageEndermiteXPMultiplier;
                case ENDER_DRAGON -> getConfig().damageEnderdragonXPMultiplier;
                default -> 0;
            };
            double xp = multiplier * Math.min(damage, baseHealth);
            if (xp > 0) xp(p, xp);
        } else if (entity.getType() == EntityTypes.ENDER_CRYSTAL) {
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
        if (this.isEnabled()) {
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
        return !getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean enabled = true;
        final double destroyEndCrystalXP = 350;
        final double damageEndCrystalXP = 110;
        final double damageEndermanXPMultiplier = 4;
        final double damageEndermiteXPMultiplier = 2;
        final double damageEnderdragonXPMultiplier = 8;
        final double throwEnderpearlXP = 105;
        final double throwEnderEyeXP = 45;
        final double teleportXP = 15;
        final double teleportXPCooldown = 60000;
    }
}
