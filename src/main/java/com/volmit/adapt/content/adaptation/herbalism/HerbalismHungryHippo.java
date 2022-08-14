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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class HerbalismHungryHippo extends SimpleAdaptation<HerbalismHungryHippo.Config> {

    public HerbalismHungryHippo() {
        super("herbalism-hippo");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Herbalism", "Hippo", "Description"));
        setDisplayName(Adapt.dLocalize("Herbalism", "Hippo", "Name"));
        setIcon(Material.POTATO);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(8111);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ (" + (2 + level) + C.GRAY + " + " +Adapt.dLocalize("Herbalism", "Hippo", "Lore1"));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void on(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (ItemListings.getFood().contains(e.getItem().getType())) {
            p.setFoodLevel(p.getFoodLevel() + 2 + getLevel(p));
            p.playSound(p.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1, 0.25f);
            vfxFastRing(p.getLocation().add(0, 0.25, 0), 2, Color.GREEN);
            xp(p, 5);
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
        int baseCost = 8;
        int maxLevel = 7;
        int initialCost = 3;
        double costFactor = 0.75;
    }
}
