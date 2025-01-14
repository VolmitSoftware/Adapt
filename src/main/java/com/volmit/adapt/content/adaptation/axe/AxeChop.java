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

package com.volmit.adapt.content.adaptation.axe;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class AxeChop extends SimpleAdaptation<AxeChop.Config> {

    public AxeChop() {
        super("axe-chop");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("axe", "chop", "description"));
        setDisplayName(Localizer.dLocalize("axe", "chop", "name"));
        setIcon(Material.IRON_AXE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(6911);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + level + C.GRAY + " " + Localizer.dLocalize("axe", "chop", "lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("axe", "chop", "lore2"));
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + " " + Localizer.dLocalize("axe", "chop", "lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.getCooldown(p.getInventory().getItemInMainHand().getType()) > 0) {
            return;
        }

        if (e.getClickedBlock() != null && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isAxe(p.getInventory().getItemInMainHand()) && hasAdaptation(p)) {
            if (!canBlockBreak(p, e.getClickedBlock().getLocation())) {
                return;
            }
            BlockData b = e.getClickedBlock().getBlockData();
            if (isLog(new ItemStack(b.getMaterial()))) {
                e.setCancelled(true);
                SoundPlayer spw = SoundPlayer.of(p.getWorld());
                spw.play(p.getLocation(), Sound.ITEM_AXE_STRIP, 1.25f, 0.6f);
                for (int i = 0; i < getLevel(p); i++) {
                    if (breakStuff(e.getClickedBlock(), getRange(getLevel(p)), p)) {
                        p.setCooldown(p.getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(p)));
                        damageHand(p, getDamagePerBlock(getLevelPercent(p)));
                    }
                }
            }
        }
    }

    private int getRange(int level) {
        return level * getConfig().rangeLevelMultiplier;
    }

    private int getCooldownTime(double levelPercent) {
        return (int) (getConfig().cooldownTicksBase + (getConfig().cooldownTicksInverseLevelMultiplier * ((1D - levelPercent))));
    }

    private int getDamagePerBlock(double levelPercent) {
        return (int) (getConfig().damagePerBlockBase + (getConfig().damagePerBlockInverseLevelMultiplier * ((1D - levelPercent))));
    }

    private boolean breakStuff(Block b, int power, Player player) {
        Block last = b;
        for (int i = b.getY(); i < power + b.getY(); i++) {
            Block bb = b.getWorld().getBlockAt(b.getX(), i, b.getZ());
            if (isLog(new ItemStack(bb.getType()))) {
                last = bb;
            } else {
                break;
            }
        }

        if (!canBlockBreak(player, last.getLocation())) {
            Adapt.verbose("Player " + player.getName() + " doesn't have permission.");
            return false;
        }

        if (!isLog(new ItemStack(last.getType()))) {
            return false;
        }

        Block ll = last;

        SoundPlayer spw = SoundPlayer.of(b.getWorld());
        spw.play(ll.getLocation(), Sound.ITEM_AXE_STRIP, 0.75f, 1.3f);

        player.breakBlock(ll);
        return true;
    }


    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = false;
        final boolean enabled = true;
        final int baseCost = 3;
        final double costFactor = 0.35;
        final int maxLevel = 5;
        final int initialCost = 2;
        final int rangeLevelMultiplier = 5;
        final double cooldownTicksBase = 15;
        final double cooldownTicksInverseLevelMultiplier = 16;
        final double damagePerBlockBase = 1;
        final double damagePerBlockInverseLevelMultiplier = 4;
    }
}
