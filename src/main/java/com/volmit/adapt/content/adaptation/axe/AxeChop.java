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
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.RNG;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class AxeChop extends SimpleAdaptation<AxeChop.Config> {

    public AxeChop() {
        super("axe-chop");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Axe", "Chop", "Description"));
        setDisplayName(Adapt.dLocalize("Axe", "Chop", "Name"));
        setIcon(Material.IRON_AXE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(5000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + level + C.GRAY + " " + Adapt.dLocalize("Axe", "Chop", "Lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " " + Adapt.dLocalize("Axe", "Chop", "Lore2"));
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + " " + Adapt.dLocalize("Axe", "Chop", "Lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isAxe(e.getPlayer().getInventory().getItemInMainHand()) && getLevel(e.getPlayer()) > 0) {
            if (!ItemListings.getStripList().contains(e.getMaterial())) {
                return;
            }

            if (e.getPlayer().getCooldown(e.getPlayer().getInventory().getItemInMainHand().getType()) >= 0) {
                e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_AXE_STRIP, 0.2f, (1.5f + RNG.r.f(0.5f)));
                getSkill().xp(e.getPlayer(), 2.25);
                return;
            }

            BlockData b = e.getClickedBlock().getBlockData();
            if (isLog(new ItemStack(b.getMaterial()))) {
                e.setCancelled(true);
                e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_AXE_STRIP, 1.25f, 0.6f);

                for (int i = 0; i < getLevel(e.getPlayer()); i++) {
                    if (breakStuff(e.getClickedBlock(), getRange(getLevel(e.getPlayer())))) {
                        getSkill().xp(e.getPlayer(), 37);
                        e.getPlayer().setCooldown(e.getPlayer().getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(e.getPlayer())));
                        damageHand(e.getPlayer(), getDamagePerBlock(getLevelPercent(e.getPlayer())));
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

    private boolean breakStuff(Block b, int power) {
        Block last = b;
        for (int i = b.getY(); i < power + b.getY(); i++) {
            Block bb = b.getWorld().getBlockAt(b.getX(), i, b.getZ());
            if (isLog(new ItemStack(bb.getType()))) {
                last = bb;
            } else {
                break;
            }
        }

        if (!isLog(new ItemStack(last.getType()))) {
            return false;
        }

        Block ll = last;
        b.getWorld().playSound(ll.getLocation(), Sound.ITEM_AXE_STRIP, 0.75f, 1.3f);
        ll.breakNaturally();
        return true;
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
        int baseCost = 3;
        double costFactor = 0.75;
        int maxLevel = 3;
        int initialCost = 5;
        int rangeLevelMultiplier = 5;
        double cooldownTicksBase = 15;
        double cooldownTicksInverseLevelMultiplier = 16;
        double damagePerBlockBase = 1;
        double damagePerBlockInverseLevelMultiplier = 4;
    }
}
