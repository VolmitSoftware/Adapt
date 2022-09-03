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
import com.volmit.adapt.content.adaptation.taming.TamingDamage;
import com.volmit.adapt.content.adaptation.taming.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.taming.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
    public SkillTaming() {
        super("taming", Adapt.dLocalize("Skill", "Taming", "Icon"));
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Skill", "Taming", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Taming", "Name"));
        setColor(C.GOLD);
        setInterval(3480);
        setIcon(Material.LEAD);
        registerAdaptation(new TamingHealthBoost());
        registerAdaptation(new TamingDamage());
        registerAdaptation(new TamingHealthRegeneration());
    }

    @EventHandler
    public void on(EntityBreedEvent e) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("AHH: " + getConfig().tameXpBase);
            if (player.getLocation().distance(e.getEntity().getLocation()) <= 15) {
                xp(player, getConfig().tameXpBase);
            }
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Tameable &&
                ((Tameable) e.getDamager()).isTamed() &&
                ((Tameable) e.getDamager()).getOwner() instanceof Player owner) {
            xp(owner, e.getEntity().getLocation(), e.getDamage() * getConfig().tameDamageXPMultiplier);
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
        double tameXpBase = 55;
        double tameHealthXPMultiplier = 63;
        double tameDamageXPMultiplier = 9.85;
    }
}
