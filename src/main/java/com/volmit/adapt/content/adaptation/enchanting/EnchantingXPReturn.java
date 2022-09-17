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

public class EnchantingXPReturn extends SimpleAdaptation<EnchantingXPReturn.Config> {

    public EnchantingXPReturn() {
        super("enchanting-xp-return");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("enchanting", "return", "description"));
        setDisplayName(Adapt.dLocalize("enchanting", "return", "name"));
        setIcon(Material.EXPERIENCE_BOTTLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(13001);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("enchanting", "return", "lore1"));
        v.addLore(C.GREEN + "" + getConfig().xpReturn * (level * level) + Adapt.dLocalize("enchanting", "return", "lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EnchantItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        int level = getLevel(e.getEnchanter());
        Player p = e.getEnchanter();
        if (!hasAdaptation(p)) {
            return;
        }
        xp(p, 5);
        p.getWorld().spawn(p.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(getConfig().xpReturn * (level * level));

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
        public int xpReturn = 2;
        boolean enabled = true;
        int baseCost = 1;
        int maxLevel = 7;
        int initialCost = 2;
        double costFactor = 1.97;
    }
}
