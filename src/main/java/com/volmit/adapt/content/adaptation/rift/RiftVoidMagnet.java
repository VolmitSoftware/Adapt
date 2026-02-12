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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class RiftVoidMagnet extends SimpleAdaptation<RiftVoidMagnet.Config> {
    public RiftVoidMagnet() {
        super("rift-void-magnet");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift.void_magnet.description"));
        setDisplayName(Localizer.dLocalize("rift.void_magnet.name"));
        setIcon(Material.ENDER_CHEST);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(20);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + C.GRAY + " " + Localizer.dLocalize("rift.void_magnet.lore1"));
        v.addLore(C.GREEN + "+ " + getMaxItems(level) + C.GRAY + " " + Localizer.dLocalize("rift.void_magnet.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getPulseTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("rift.void_magnet.lore3"));
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(p) || !p.isSneaking() || p.getTicksLived() % getPulseTicks(getLevel(p)) != 0) {
                continue;
            }

            int level = getLevel(p);
            int moved = collectNearbyItems(p, level);
            if (moved <= 0) {
                continue;
            }

            p.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
            SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.45f, 1.6f);
            xp(p, moved * getConfig().xpPerMovedItem);
        }
    }

    private int collectNearbyItems(Player p, int level) {
        int moved = 0;
        int max = getMaxItems(level);
        double r = getRadius(level);
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), r, r, r)) {
            if (!(entity instanceof Item item)) {
                continue;
            }

            if (moved >= max || item.isDead() || !item.isValid()) {
                continue;
            }

            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            int requestAmount = Math.min(stack.getAmount(), max - moved);
            if (requestAmount <= 0) {
                continue;
            }

            ItemStack toChest = stack.clone();
            toChest.setAmount(requestAmount);
            Map<Integer, ItemStack> chestOverflow = p.getEnderChest().addItem(toChest);
            int chestRemaining = chestOverflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            int movedAmount = Math.max(0, requestAmount - chestRemaining);

            if (chestRemaining > 0 && getConfig().allowEnderChestOverflow) {
                ItemStack toInventory = stack.clone();
                toInventory.setAmount(chestRemaining);
                Map<Integer, ItemStack> inventoryOverflow = p.getInventory().addItem(toInventory);
                int inventoryRemaining = inventoryOverflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                movedAmount += Math.max(0, chestRemaining - inventoryRemaining);
            }

            if (movedAmount <= 0) {
                continue;
            }

            if (movedAmount >= stack.getAmount()) {
                item.remove();
            } else {
                stack.setAmount(stack.getAmount() - movedAmount);
                item.setItemStack(stack);
            }
            moved += movedAmount;
        }

        return moved;
    }

    private double getRadius(int level) {
        return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    }

    private int getMaxItems(int level) {
        return Math.max(1, (int) Math.round(getConfig().maxItemsBase + (getLevelPercent(level) * getConfig().maxItemsFactor)));
    }

    private int getPulseTicks(int level) {
        return Math.max(2, (int) Math.round(getConfig().pulseTicksBase - (getLevelPercent(level) * getConfig().pulseTicksFactor)));
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
    @ConfigDescription("Sneak to periodically pull nearby dropped items into your ender chest first.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Allow Ender Chest Overflow for the Rift Void Magnet adaptation.", impact = "When true, leftovers that do not fit in ender chest can spill into player inventory.")
        boolean allowEnderChestOverflow = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 9;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Items Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxItemsBase = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Items Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxItemsFactor = 22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pulse Ticks Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pulseTicksBase = 20;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pulse Ticks Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pulseTicksFactor = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Moved Item for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerMovedItem = 0.7;
    }
}
