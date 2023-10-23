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

package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;


public class RiftEnderchest extends SimpleAdaptation<RiftEnderchest.Config> {
    public RiftEnderchest() {
        super("rift-enderchest");
        setDescription(Localizer.dLocalize("rift", "chest", "description"));
        setDisplayName(Localizer.dLocalize("rift", "chest", "name"));
        setIcon(Material.ENDER_CHEST);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(10);
        setInterval(9248);
        registerConfiguration(Config.class);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.ITALIC + Localizer.dLocalize("rift", "chest", "lore1"));
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (hand.getType() != Material.ENDER_CHEST || !hasAdaptation(p)) {
            return;
        }

        if (p.hasCooldown(hand.getType())) {
            e.setCancelled(true);
        } else {
            p.setCooldown(Material.ENDER_CHEST, 100);

            if ((e.getAction() == Action.RIGHT_CLICK_AIR) || (e.getAction() == Action.LEFT_CLICK_AIR) || (e.getAction() == Action.LEFT_CLICK_BLOCK)) {
                if (getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist") != null
                        && getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist").getLevel() > 0) {
                    RiftResist.riftResistStackAdd(p, 10, 2);
                }
                p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.10f);
                p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.10f);
                p.openInventory(p.getEnderChest());
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
    }
}