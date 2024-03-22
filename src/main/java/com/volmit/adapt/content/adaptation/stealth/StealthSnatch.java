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

package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import net.minecraft.network.protocol.game.PacketPlayOutCollect;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StealthSnatch extends SimpleAdaptation<StealthSnatch.Config> {
    private final List<Integer> holds;

    public StealthSnatch() {
        super("stealth-snatch");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("stealth", "snatch", "description"));
        setDisplayName(Localizer.dLocalize("stealth", "snatch", "name"));
        setIcon(Material.CHEST_MINECART);
        setBaseCost(getConfig().baseCost);
        setInterval(getConfig().snatchRate);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        holds = new ArrayList<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRange(getLevelPercent(level)), 1) + C.GRAY + " " + Localizer.dLocalize("stealth", "snatch", "lore1"));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (!canAccessChest(p, p.getLocation())) {
            return;
        }
        if (e.isSneaking()) {
            snatch(p);
        }
    }

    private void snatch(Player player) {
        double factor = getLevelPercent(player);

        if (factor == 0) {
            return;
        }

        double range = getRange(factor);
        HashSet<Item> items = new HashSet<>();
        for (Entity droppedItemEntity : player.getWorld().getNearbyEntities(player.getLocation(), range, range / 1.5, range)) {
            if (droppedItemEntity instanceof Item droppedItem) {
                if (droppedItem.getPickupDelay() <= 0 || droppedItem.getTicksLived() > 1) {
                    items.add(droppedItem);
                }
            }
        }

        for (Item droppedItemEntity : items) {
            if (!holds.contains(droppedItemEntity.getEntityId())) {
                double dist = droppedItemEntity.getLocation().distanceSquared(player.getLocation());
                if (dist < range * range) {
                    ItemStack is = droppedItemEntity.getItemStack().clone();

                    if (Inventories.hasSpace(player.getInventory(), is)) {
                        holds.add(droppedItemEntity.getEntityId());
                        for (Player players : player.getWorld().getPlayers()) {
                            players.playSound(player.getLocation(), Sound.BLOCK_LAVA_POP, 1f, (float) (1.0 + (Math.random() / 3)));
                        }
                        safeGiveItem(player, droppedItemEntity, is);
                        sendCollected(player, droppedItemEntity);
                        int id = droppedItemEntity.getEntityId();
                        J.s(() -> holds.remove(Integer.valueOf(id)));
                    }
                }
            }
        }

    }

    private double getRange(double factor) {
        return (factor * getConfig().radiusFactor) + 1;
    }

    public void sendCollected(Player p, Item item) {
        try {
            PacketPlayOutCollect packet = new PacketPlayOutCollect(item.getEntityId(), p.getEntityId(), item.getItemStack().getAmount());
            for (Entity i : p.getWorld().getNearbyEntities(p.getLocation(), 8, 8, 8, entity -> entity instanceof Player)) {
                ((CraftPlayer) i).getHandle().c.a(packet);
            }
        } catch (Exception e) {
            Adapt.error("Failed to send collected packet");
            e.printStackTrace();
        }
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (i.isSneaking()) {
                J.s(() -> snatch(i));
            }
        }
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
        int snatchRate = 250;
        int baseCost = 4;
        int maxLevel = 3;
        int initialCost = 12;
        double costFactor = 0.125;
        double radiusFactor = 5.55;
    }
}
