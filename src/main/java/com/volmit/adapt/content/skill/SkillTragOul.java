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
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SkillTragOul extends SimpleSkill<SkillTragOul.Config> {
    public SkillTragOul() {
        super("tragoul", Adapt.dLocalize("Skill", "TragOul", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Adapt.dLocalize("Skill", "TragOul", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "TragOul", "Name"));
        setInterval(3755);
        setIcon(Material.CRIMSON_ROOTS);
    }


    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (!e.isCancelled()) {
            if (e.getDamager() instanceof Player p) {
                AdaptPlayer a = getPlayer(p);
                xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());

            } else if(e.getEntity().getType() == EntityType.PLAYER){
                AdaptPlayer a = getPlayer((Player) e.getEntity());
                xp(a.getPlayer(), getConfig().damageReceivedXpMultiplier * e.getDamage());
            }
        }
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double damageXPMultiplier = 5.26;
        double damageReceivedXpMultiplier = 2.26;
    }
}
