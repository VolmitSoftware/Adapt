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

package com.volmit.adapt.content.adaptation.crafting;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;


public class CraftingXP extends SimpleAdaptation<CraftingXP.Config> {
    private final Map<Player, Long> cooldown = new HashMap<>();


    public CraftingXP() {
        super("crafting-xp");
        registerConfiguration(CraftingXP.Config.class);
        setDisplayName(Adapt.dLocalize("crafting", "xp", "name"));
        setDescription(Adapt.dLocalize("crafting", "xp", "description"));
        setIcon(Material.EXPERIENCE_BOTTLE);
        setInterval(5580);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("crafting", "xp", "lore1"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        cooldown.remove(p);
    }


    @EventHandler(priority = EventPriority.LOW)
    public void on(CraftItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getResult() != null && !e.isCancelled() && hasAdaptation(p) && e.getInventory().getResult().getAmount() > 0) {
            if (e.getInventory().getResult() != null && e.getCursor() != null && e.getCursor().getAmount() < 64) {
                if (p.getInventory().addItem(e.getCurrentItem()).isEmpty()) {
                    p.getInventory().removeItem(e.getCurrentItem());
                    if (cooldown.containsKey(p) && cooldown.get(p) + 20000 < System.currentTimeMillis()) {
                        cooldown.remove(p);
                    } else if (cooldown.containsKey(p) && cooldown.get(p) + 20000 > System.currentTimeMillis()) {
                        return;
                    }
                    cooldown.put(p, System.currentTimeMillis());
                    p.getWorld().spawn(p.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(getLevel(p) * 2);
                }
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 2;
        int initialCost = 3;
        double costFactor = 0.3;
        int maxLevel = 7;
    }
}
