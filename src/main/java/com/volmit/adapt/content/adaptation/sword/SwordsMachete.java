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

package com.volmit.adapt.content.adaptation.sword;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SwordsMachete extends SimpleAdaptation<SwordsMachete.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public SwordsMachete() {
        super("sword-machete");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("sword", "machete", "description"));
        setDisplayName(Localizer.dLocalize("sword", "machete", "name"));
        setIcon(Material.IRON_SWORD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(5234);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getRadius(level) + C.GRAY + " " + Localizer.dLocalize("sword", "machete", "lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("sword", "machete", "lore2"));
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + " " + Localizer.dLocalize("sword", "machete", "lore3"));
    }

    public double getRadius(int level) {
        return (getLevelPercent(level) * getConfig().radiusFactor) + getConfig().radiusBase;
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != null && e.getHand().equals(EquipmentSlot.HAND) && e.getAction().equals(Action.LEFT_CLICK_AIR) || e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            int dmg = 0;
            ItemStack is = e.getItem();
            if (isSword(is)) {
                if (is != null && !p.hasCooldown(is.getType()) && hasAdaptation(p)) {
                    Location ctr = p.getEyeLocation().clone().add(p.getLocation().getDirection().clone().multiply(2.25)).add(0, -0.5, 0);

                    int lvl = getLevel(p);
                    Cuboid c = new Cuboid(ctr);
                    c = c.expand(Cuboid.CuboidDirection.Up, (int) Math.floor(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.Down, (int) Math.floor(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.North, (int) Math.round(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.South, (int) Math.round(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.East, (int) Math.round(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.West, (int) Math.round(getRadius(lvl)));

                    if (dmg > 0) {
                        return;
                    }

                    for (Block i : c) {
                        if (M.r((getLevelPercent(lvl) * 2.8) / (i.getLocation().distanceSquared(ctr)))) {
                            if (i.getType().equals(Material.TALL_GRASS)
                                    || i.getType().equals(Material.CACTUS)
                                    || i.getType().equals(Material.SUGAR_CANE)
                                    || i.getType().equals(Material.CARROT)
                                    || i.getType().equals(Material.POTATO)
                                    || i.getType().equals(Material.NETHER_WART)
                                    || i.getType().equals(Material.SHORT_GRASS)
                                    || i.getType().equals(Material.TALL_GRASS)
                                    || i.getType().equals(Material.FERN)
                                    || i.getType().equals(Material.LARGE_FERN)
                                    || i.getType().equals(Material.VINE)
                                    || i.getType().equals(Material.ROSE_BUSH)
                                    || i.getType().equals(Material.WITHER_ROSE)
                                    || i.getType().equals(Material.ACACIA_LEAVES)
                                    || i.getType().equals(Material.BIRCH_LEAVES)
                                    || i.getType().equals(Material.DARK_OAK_LEAVES)
                                    || i.getType().equals(Material.JUNGLE_LEAVES)
                                    || i.getType().equals(Material.OAK_LEAVES)
                                    || i.getType().equals(Material.SPRUCE_LEAVES)
                                    || i.getType().equals(Material.BROWN_MUSHROOM)
                                    || i.getType().equals(Material.RED_MUSHROOM)
                                    || i.getType().equals(Material.DEAD_BUSH)
                                    || i.getType().equals(Material.DANDELION)
                                    || i.getType().equals(Material.TALL_SEAGRASS)
                                    || i.getType().equals(Material.SEAGRASS)
                                    || i.getType().equals(Material.WHITE_TULIP)
                                    || i.getType().equals(Material.RED_TULIP)
                                    || i.getType().equals(Material.PINK_TULIP)
                                    || i.getType().equals(Material.ORANGE_TULIP)
                                    || i.getType().equals(Material.LILY_OF_THE_VALLEY)
                                    || i.getType().equals(Material.ALLIUM)
                                    || i.getType().equals(Material.AZURE_BLUET)
                                    || i.getType().equals(Material.SUNFLOWER)
                                    || i.getType().equals(Material.CORNFLOWER)
                                    || i.getType().equals(Material.CHORUS_FLOWER)
                                    || i.getType().equals(Material.BAMBOO)
                                    || i.getType().equals(Material.BAMBOO_SAPLING)
                                    || i.getType().equals(Material.LILAC)
                                    || i.getType().equals(Material.PEONY)
                                    || i.getType().equals(Material.LILY_PAD)
                                    || i.getType().equals(Material.COCOA)
                                    || i.getType().equals(Material.MANGROVE_LEAVES)

                            ) {
                                if (!canBlockBreak(p, i.getLocation())) continue;
                                BlockBreakEvent ee = new BlockBreakEvent(i, p);
                                Bukkit.getPluginManager().callEvent(ee);

                                if (!ee.isCancelled()) {
                                    dmg += 1;
                                    J.s(() -> {
                                        i.breakNaturally();
                                        for (Player players : p.getWorld().getPlayers()) {
                                            players.playSound(i.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.4f, (float) Math.random() * 1.85f);
                                        }
                                    }, RNG.r.i(0, (getMaxLevel() - lvl * 2) + 1));
                                }
                            }
                        }
                    }

                    if (dmg > 0) {
                        p.setCooldown(is.getType(), getCooldownTime(getLevelPercent(lvl)));
//                        if (getConfig().showParticles) {
//                            ParticleEffect.SWEEP_ATTACK.display(p.getEyeLocation().clone().add(p.getLocation().getDirection().clone().multiply(1.25)).add(0, -0.5, 0), 0f, 0f, 0f, 0.1f, 1, null);
//                        }
                        for (Player players : p.getWorld().getPlayers()) {
                            players.playSound(p.getEyeLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, (float) (Math.random() / 2) + 0.65f);
                        }
                        damageHand(p, dmg * getDamagePerBlock(getLevelPercent(lvl)));
                        getSkill().xp(p, dmg * 11.25);
                    }
                }
            }
        }
    }

    private int getCooldownTime(double levelPercent) {
        return (int) (((int) ((1D - levelPercent) * getConfig().cooldownTicksSlowest)) + getConfig().cooldownTicksBase);
    }

    private int getDamagePerBlock(double levelPercent) {
        return (int) (getConfig().toolDamageBase + (getConfig().toolDamageInverseLevelFactor * ((1D - levelPercent))));
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
        int baseCost = 4;
        int maxLevel = 3;
        int initialCost = 7;
        double costFactor = 0.225;
        double radiusBase = 0.6;
        double radiusFactor = 2.36;
        double cooldownTicksBase = 7;
        double cooldownTicksSlowest = 35;
        double toolDamageBase = 1;
        double toolDamageInverseLevelFactor = 5;
    }
}
