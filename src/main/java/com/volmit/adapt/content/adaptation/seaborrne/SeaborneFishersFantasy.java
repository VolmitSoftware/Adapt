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

package com.volmit.adapt.content.adaptation.seaborrne;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class SeaborneFishersFantasy extends SimpleAdaptation<SeaborneFishersFantasy.Config> {

    public SeaborneFishersFantasy() {
        super("seaborne-fishers-fantasy");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Seaborn", "FishersFantasy", "Description"));
        setDisplayName(Adapt.dLocalize("Seaborn", "FishersFantasy", "Name"));
        setIcon(Material.FISHING_ROD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(8080);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("Seaborn", "FishersFantasy", "Lore1"));
    }

    @EventHandler
    public void on(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Random random = new Random();
            for (int i = 0; i < getLevel(p); i++) {
                ItemStack item = new ItemStack(ItemListings.getFishingDrops().getRandom(), 1);
                if (random.nextBoolean()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), item);
                    p.getWorld().spawn(p.getLocation(), ExperienceOrb.class);
                    xp(p, 15 * getLevel(p));
                }
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

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 7;
        int initialCost = 2;
        double costFactor = 1.525;
    }
}
