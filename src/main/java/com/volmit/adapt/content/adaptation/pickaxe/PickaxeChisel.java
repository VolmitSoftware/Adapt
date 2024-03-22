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

package com.volmit.adapt.content.adaptation.pickaxe;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PickaxeChisel extends SimpleAdaptation<PickaxeChisel.Config> {
    public PickaxeChisel() {
        super("pickaxe-chisel");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("pickaxe", "chisel", "description"));
        setDisplayName(Localizer.dLocalize("pickaxe", "chisel", "name"));
        setIcon(Material.IRON_NUGGET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(7433);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDropChance(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("pickaxe", "chisel", "lore1"));
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + " " + Localizer.dLocalize("pickaxe", "chisel", "lore2"));
    }

    private int getCooldownTime(double levelPercent) {
        return getConfig().cooldownTime;
    }

    private double getDropChance(double levelPercent) {
        return ((levelPercent) * getConfig().dropChanceFactor) + getConfig().dropChanceBase;
    }

    private double getBreakChance(double levelPercent) {
        return getConfig().breakChance;
    }

    private int getDamagePerBlock(double levelPercent) {
        return (int) (getConfig().damagePerBlockBase + (getConfig().damageFactorInverseMultiplier * ((1D - levelPercent))));
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (e.getClickedBlock() != null && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isPickaxe(p.getInventory().getItemInMainHand()) && hasAdaptation(p)) {
            if (p.getInventory().getItemInMainHand().getEnchantments().containsKey(Enchantment.SILK_TOUCH) || p.getInventory().getItemInMainHand().getEnchantments().containsKey(Enchantment.MENDING)) {
                return;
            }
            if (p.getCooldown(p.getInventory().getItemInMainHand().getType()) > 0) {
                return;
            }
            if (!canBlockBreak(p, e.getClickedBlock().getLocation())) {
                return;
            }
            BlockData b = e.getClickedBlock().getBlockData();
            if (isOre(b)) {
                for (Player players : p.getWorld().getPlayers()) {
                    players.playSound(p.getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 1.4f);
                    players.playSound(p.getLocation(), Sound.BLOCK_METAL_HIT, 1.25f, 1.7f);
                }

                p.setCooldown(p.getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(p)));
                damageHand(p, getDamagePerBlock(getLevelPercent(p)));

                Location c = p.rayTraceBlocks(8).getHitPosition().toLocation(p.getWorld());

                ItemStack is = getDropFor(b);
                if (M.r(getDropChance(getLevelPercent(p)))) {
                    if (getConfig().showParticles) {
                        e.getClickedBlock().getWorld().spawnParticle(Particle.ITEM_CRACK, c, 14, 0.10, 0.01, 0.01, 0.1, is);
                    }
                    for (Player players : p.getWorld().getPlayers()) {
                        players.playSound(p.getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.787f);
                        players.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.55f, 1.89f);
                    }
                    e.getClickedBlock().getWorld().dropItemNaturally(c.clone().subtract(p.getLocation().getDirection().clone().multiply(0.1)), is);
                } else {
                    if (getConfig().showParticles) {
                        e.getClickedBlock().getWorld().spawnParticle(Particle.ITEM_CRACK, c, 3, 0.01, 0.01, 0.01, 0.1, is);
                        e.getClickedBlock().getWorld().spawnParticle(Particle.BLOCK_CRACK, c, 9, 0.1, 0.1, 0.1, e.getClickedBlock().getBlockData());
                    }
                }

                if (M.r(getBreakChance(getLevelPercent(p)))) {
                    for (Player players : p.getWorld().getPlayers()) {
                        players.playSound(p.getLocation(), Sound.BLOCK_BASALT_BREAK, 1.25f, 0.4f);
                        players.playSound(p.getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.887f);
                    }
                    e.getClickedBlock().breakNaturally(p.getInventory().getItemInMainHand());
                }
            }

        }
    }

    private ItemStack getDropFor(BlockData b) {
        return switch (b.getMaterial()) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.RAW_COPPER);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> new ItemStack(Material.RAW_GOLD);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.RAW_IRON);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new ItemStack(Material.DIAMOND);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new ItemStack(Material.LAPIS_LAZULI);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new ItemStack(Material.EMERALD);
            case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ);
            case REDSTONE_ORE -> new ItemStack(Material.REDSTONE);

            default -> new ItemStack(Material.AIR);
        };
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
        boolean showParticles = true;
        int baseCost = 6;
        int maxLevel = 7;
        int initialCost = 5;
        double costFactor = 0.4;
        int cooldownTime = 5;
        double dropChanceBase = 0.07;
        double dropChanceFactor = 0.22;
        double breakChance = 0.25;
        double damagePerBlockBase = 1;
        double damageFactorInverseMultiplier = 2;
    }
}
