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

package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

public class HerbalismLuck extends SimpleAdaptation<HerbalismLuck.Config> {

    public HerbalismLuck() {
        super("herbalism-luck");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism", "luck", "description"));
        setDisplayName(Localizer.dLocalize("herbalism", "luck", "name"));
        setIcon(Material.EMERALD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(8121);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("herbalism", "luck", "lore0"));
        v.addLore(C.GREEN + "+ (" + (getEffectiveness(level)) + C.GRAY + "%) + " + Localizer.dLocalize("herbalism", "luck", "lore1"));
        v.addLore(C.GREEN + "+ (" + (getEffectiveness(level)) + C.GRAY + "%) + " + Localizer.dLocalize("herbalism", "luck", "lore2"));
    }

    private double getEffectiveness(double factor) {
        return Math.min(getConfig().highChance, factor * factor + getConfig().lowChance);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void on(BlockDropItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        Block broken = e.getBlock();
        if (broken.getType() == Material.SHORT_GRASS || broken.getType() == Material.TALL_GRASS) {
            var d = Math.random() * 100;
            Material m = ItemListings.getHerbalLuckSeeds().getRandom();
            if (d < getEffectiveness(getLevel(p))) {
                xp(p, 100);
                ItemStack luckDrop = new ItemStack(m, 1);
                e.getBlock().getWorld().dropItem(e.getBlock().getLocation(), luckDrop);
            }
        }

        if (ItemListings.getFlowers().contains(broken.getType())) {
            var d = Math.random() * 100;
            Material m = ItemListings.getHerbalLuckFood().getRandom();
            if (d < getEffectiveness(getLevel(p))) {
                xp(p, 100);
                ItemStack luckDrop = new ItemStack(m, 1);
                e.getBlock().getWorld().dropItem(e.getBlock().getLocation(), luckDrop);
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

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 8;
        int maxLevel = 7;
        int initialCost = 3;
        double costFactor = 0.75;
        double lowChance = 0.0;
        double highChance = 90;
    }
}
