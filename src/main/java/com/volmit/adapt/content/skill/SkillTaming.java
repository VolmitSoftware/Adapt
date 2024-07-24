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
import com.volmit.adapt.content.adaptation.taming.TamingDamage;
import com.volmit.adapt.content.adaptation.taming.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.taming.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillTaming() {
        super("taming", Localizer.dLocalize("skill", "taming", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "taming", "description"));
        setDisplayName(Localizer.dLocalize("skill", "taming", "name"));
        setColor(C.GOLD);
        setInterval(3480);
        setIcon(Material.LEAD);
        cooldowns = new HashMap<>();
        registerAdaptation(new TamingHealthBoost());
        registerAdaptation(new TamingDamage());
        registerAdaptation(new TamingHealthRegeneration());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityBreedEvent e) {
        if (e.isCancelled()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(p, e, () -> {
                if (p.getWorld() == e.getEntity().getWorld() && p.getLocation().distance(e.getEntity().getLocation()) <= 15) {
                    if (!isOnCooldown(p)) {
                        setCooldown(p);
                        xp(p, getConfig().tameXpBase);
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> {
                if (!isOnCooldown(p)) {
                    setCooldown(p);
                    xp(p, e.getEntity().getLocation(), e.getDamage() * getConfig().tameDamageXPMultiplier);
                }
            });
        }
    }

    private boolean isOnCooldown(Player p) {
        Long cooldown = cooldowns.get(p);
        return cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis();
    }

    private void setCooldown(Player p) {
        cooldowns.put(p, System.currentTimeMillis());
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
        double tameXpBase = 30;
        long cooldownDelay = 2250;
        double tameDamageXPMultiplier = 7.85;
    }
}
