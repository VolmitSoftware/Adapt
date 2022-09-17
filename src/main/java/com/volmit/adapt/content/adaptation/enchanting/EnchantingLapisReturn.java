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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

public class EnchantingLapisReturn extends SimpleAdaptation<EnchantingLapisReturn.Config> {

    public EnchantingLapisReturn() {
        super("enchanting-lapis-return");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("enchanting", "lapisreturn", "description"));
        setDisplayName(Adapt.dLocalize("enchanting", "lapisreturn", "name"));
        setIcon(Material.LAPIS_LAZULI);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(20999);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("enchanting", "lapisreturn", "lore1"));
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
        int xp = e.getExpLevelCost();
        xp = xp + getLevel(p); // Add a level for each enchant
        e.setExpLevelCost(xp);
        int lapis = (int) (Math.random() * 1);
        lapis = lapis + (int) (Math.random() * (getLevel(p) + 1));
        p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(Material.LAPIS_LAZULI, lapis));
        xp(p, 15);


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
        int baseCost = 1;
        int maxLevel = 7;
        int initialCost = 2;
        double costFactor = 1.25;
    }
}
