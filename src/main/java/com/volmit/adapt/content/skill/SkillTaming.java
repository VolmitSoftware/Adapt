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
import com.volmit.adapt.content.adaptation.taming.TamingDamage;
import com.volmit.adapt.content.adaptation.taming.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.taming.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
    public SkillTaming() {
        super("taming", Localizer.dLocalize("skill", "taming", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "taming", "description"));
        setDisplayName(Localizer.dLocalize("skill", "taming", "name"));
        setColor(C.GOLD);
        setInterval(3480);
        setIcon(Material.LEAD);
        registerAdaptation(new TamingHealthBoost());
        registerAdaptation(new TamingDamage());
        registerAdaptation(new TamingHealthRegeneration());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityBreedEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (e.isCancelled()) {
                return;
            }
            if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
                return;
            }
            if (p.getLocation().distance(e.getEntity().getLocation()) <= 15) {
                xp(p, getConfig().tameXpBase);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        if (AdaptConfig.get().blacklistedWorlds.contains(e.getEntity().getWorld().getName())) {
            return;
        }


        if (e.getDamager() instanceof Tameable && ((Tameable) e.getDamager()).isTamed() && ((Tameable) e.getDamager()).getOwner() instanceof Player p) {
            if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
                return;
            } else {
                xp(p, e.getEntity().getLocation(), e.getDamage() * getConfig().tameDamageXPMultiplier);
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
        double tameXpBase = 30;
        double tameDamageXPMultiplier = 7.85;
    }
}
