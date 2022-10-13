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

package com.volmit.adapt.content.adaptation.enchanting;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class EnchantingLapisReturn extends SimpleAdaptation<EnchantingLapisReturn.Config> {
    private final Map<Player, Long> cooldown = new HashMap<>();

    public EnchantingLapisReturn() {
        super("enchanting-lapis-return");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("enchanting", "lapisreturn", "description"));
        setDisplayName(Localizer.dLocalize("enchanting", "lapisreturn", "name"));
        setIcon(Material.LAPIS_LAZULI);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(20999);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("enchanting", "lapisreturn", "lore1"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        cooldown.remove(p);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void on(EnchantItemEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Player p = e.getEnchanter();
        if (!hasAdaptation(p)) {
            return;
        }


        if (Math.random() * 100 > 80) {
            if (cooldown.containsKey(p) && cooldown.get(p) + 20000 < System.currentTimeMillis()) {
                cooldown.remove(p);
            } else if (cooldown.containsKey(p) && cooldown.get(p) + 20000 > System.currentTimeMillis()) {
                return;
            }
            cooldown.put(p, System.currentTimeMillis());
            p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(Material.LAPIS_LAZULI, getLevel(p)));
        }
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 3;
        int initialCost = 2;
        double costFactor = 2.25;
    }
}
