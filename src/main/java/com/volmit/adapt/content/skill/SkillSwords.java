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
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.sword.SwordsBloodyBlade;
import com.volmit.adapt.content.adaptation.sword.SwordsMachete;
import com.volmit.adapt.content.adaptation.sword.SwordsPoisonedBlade;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SkillSwords extends SimpleSkill<SkillSwords.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillSwords() {
        super("swords", Localizer.dLocalize("skill", "swords", "icon"));
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription(Localizer.dLocalize("skill", "swords", "description"));
        setDisplayName(Localizer.dLocalize("skill", "swords", "name"));
        setInterval(2150);
        setIcon(Material.DIAMOND_SWORD);
        cooldowns = new HashMap<>();
        registerAdaptation(new SwordsMachete());
        registerAdaptation(new SwordsPoisonedBlade());
        registerAdaptation(new SwordsBloodyBlade());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && checkValidEntity(e.getEntity().getType())) {
            shouldReturnForPlayer(p, e, () -> {
                AdaptPlayer a = getPlayer(p);
                ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();
                if (isSword(hand)) {
                    getPlayer(p).getData().addStat("sword.hits", 1);
                    getPlayer(p).getData().addStat("sword.damage", e.getDamage());
                    if (!isOnCooldown(p)) {
                        setCooldown(p);
                        xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
                    }
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
        long cooldownDelay = 1250;
        double damageXPMultiplier = 7.26;
    }
}
