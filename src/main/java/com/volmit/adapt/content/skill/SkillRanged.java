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
import com.volmit.adapt.content.adaptation.ranged.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import net.minecraft.world.entity.projectile.EntityFishingHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SkillRanged extends SimpleSkill<SkillRanged.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillRanged() {
        super("ranged", Localizer.dLocalize("skill", "ranged", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "ranged", "description"));
        setDisplayName(Localizer.dLocalize("skill", "ranged", "name"));
        setColor(C.DARK_GREEN);
        setInterval(3044);
        registerAdaptation(new RangedForce());
        registerAdaptation(new RangedPiercing());
        registerAdaptation(new RangedArrowRecovery());
        registerAdaptation(new RangedLungeShot());
        registerAdaptation(new RangedWebBomb());
        setIcon(Material.CROSSBOW);
        cooldowns = new HashMap<>();
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
            if (e.getEntity() instanceof Snowball || e.getEntity().getType().name().toLowerCase(Locale.ROOT).contains("hook")) {
                return; // Ignore snowballs and fishing hooks
            }

            getPlayer(p).getData().addStat("ranged.shotsfired", 1);
            getPlayer(p).getData().addStat("ranged.shotsfired." + e.getEntity().getType().name().toLowerCase(Locale.ROOT), 1);
            if (cooldowns.containsKey(p)) {
                if (cooldowns.get(p) + getConfig().cooldownDelay > System.currentTimeMillis()) {
                    return;
                } else {
                    cooldowns.remove(p);
                }
            }
            cooldowns.put(p, System.currentTimeMillis());
            xp(p, getConfig().shootXP);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getDamager() instanceof Projectile) || !(((Projectile) e.getDamager()).getShooter() instanceof Player p) || !checkValidEntity(e.getEntity().getType())) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity() instanceof Snowball || e.getEntity() instanceof EntityFishingHook) {
                return; // Ignore snowballs and fishing hooks
            }
                if (e.getEntity().getLocation().getWorld().equals(p.getLocation().getWorld())) {
                    getPlayer(p).getData().addStat("ranged.distance", e.getEntity().getLocation().distance(p.getLocation()));
                    getPlayer(p).getData().addStat("ranged.distance." + e.getDamager().getType().name().toLowerCase(Locale.ROOT), e.getEntity().getLocation().distance(p.getLocation()));
                }
                getPlayer(p).getData().addStat("ranged.damage", e.getDamage());
                getPlayer(p).getData().addStat("ranged.damage." + e.getDamager().getType().name().toLowerCase(Locale.ROOT), e.getDamage());
                if (cooldowns.containsKey(p)) {
                    if (cooldowns.get(p) + getConfig().cooldownDelay > System.currentTimeMillis()) {
                        return;
                    } else {
                        cooldowns.remove(p);
                    }
                }
                cooldowns.put(p, System.currentTimeMillis());
                xp(p, e.getEntity().getLocation(), (getConfig().hitDamageXPMultiplier * e.getDamage()) + (e.getEntity().getLocation().distance(p.getLocation()) * getConfig().hitDistanceXPMultiplier));

        });
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
        double shootXP = 5;
        long cooldownDelay = 1250;
        double hitDamageXPMultiplier = 2.125;
        double hitDistanceXPMultiplier = 1.7;
    }
}
