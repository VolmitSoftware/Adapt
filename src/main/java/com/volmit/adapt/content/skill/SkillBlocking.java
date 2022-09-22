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
import com.volmit.adapt.content.adaptation.blocking.BlockingMultiArmor;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillBlocking extends SimpleSkill<SkillBlocking.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillBlocking() {
        super("blocking", Adapt.dLocalize("skill", "blocking", "icon"));
        registerConfiguration(Config.class);
        setColor(C.DARK_GRAY);
        setDescription(Adapt.dLocalize("skill", "blocking", "description"));
        setDisplayName(Adapt.dLocalize("skill", "blocking", "name"));
        setInterval(5000);
        setIcon(Material.SHIELD);
        registerAdaptation(new BlockingMultiArmor());
        cooldowns = new HashMap<>();
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Player p) {
            if (canUseSkill(p)) {
                return;
            }
            if (cooldowns.containsKey(p)) {
                if (cooldowns.get(p) + getConfig().cooldownDelay > System.currentTimeMillis()) {
                    return;
                } else {
                    cooldowns.remove(p);
                }
            }
            if (p.isBlocking()) {
                xp(p, getConfig().xpOnBlockedAttack);
                cooldowns.put(p, System.currentTimeMillis());
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 0.77f);
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 0.77f);
            }
        }
    }


    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (canUseSkill(i)) {
                return;
            }
            checkStatTrackers(getPlayer(i));
            if (i.getPlayer() != null && (i.getPlayer().getInventory().getItemInOffHand().getType().equals(Material.SHIELD) || i.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.SHIELD))) {
                xpSilent(i, getConfig().passiveXpForUsingShield);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double xpOnBlockedAttack = 350;
        long cooldownDelay = 3000;
        long passiveXpForUsingShield = 5;
    }
}
