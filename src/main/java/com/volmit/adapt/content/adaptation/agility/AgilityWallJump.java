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

package com.volmit.adapt.content.adaptation.agility;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.enums.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AgilityWallJump extends SimpleAdaptation<AgilityWallJump.Config> {
    private final Map<Player, Double> airjumps;

    public AgilityWallJump() {
        super("agility-wall-jump");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility", "walljump", "description"));
        setDisplayName(Localizer.dLocalize("agility", "walljump", "name"));
        setIcon(Material.LADDER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(50);
        airjumps = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getMaxJumps(level) + C.GRAY + " " + Localizer.dLocalize("agility", "walljump", "lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getJumpHeight(level), 0) + C.GRAY + " " + Localizer.dLocalize("agility", "walljump", "lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        airjumps.remove(p);
    }

    private int getMaxJumps(int level) {
        return (int) (level + (level / getConfig().maxJumpsLevelBonusDivisor));
    }

    private double getJumpHeight(int level) {
        return getConfig().jumpHeightBase + (getLevelPercent(level) * getConfig().jumpHeightBonusLevelMultiplier);
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (canInteract(p, p.getLocation())) {
            return;
        }
        if (airjumps.containsKey(p)) {
            if (p.isOnGround() && !p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData().getMaterial().isAir()) {
                airjumps.remove(p);
            }
        }
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int level = getLevel(p);
            if (level <= 0) {
                continue;
            }

            Double j = airjumps.get(p);

            if (j != null && j - 0.25 >= getMaxJumps(level)) {
                p.setGravity(true);
                continue;
            }

            if (canInteract(p, p.getLocation())) {
                return;
            }

            if (p.isFlying() || !p.isSneaking() || p.getFallDistance() < 0.3) {
                boolean jumped = false;

                if (!p.hasGravity() && p.getFallDistance() > 0.45 && canStick(p)) {
                    j = j == null ? 0 : j;
                    j++;

                    if (j - 0.25 <= getMaxJumps(level) && getStick(p) != null) {
                        jumped = true;
                        p.setVelocity(p.getVelocity().setY(getJumpHeight(level)));
                        if (getConfig().showParticles) {

                            p.getWorld().spawnParticle(Particles.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.8, 0.1, 0.1, Objects.requireNonNull(getStick(p)).getBlockData());
                        }
                    }
                    airjumps.put(p, j);
                }

                if (!jumped && !p.hasGravity()) {
                    p.setGravity(true);
                    SoundPlayer spw = SoundPlayer.of(p.getWorld());
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.439f);
                }
                continue;
            }

            if (canStick(p)) {
                if (p.hasGravity()) {
                    SoundPlayer spw = SoundPlayer.of(p.getWorld());
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.89f);
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 1.39f);
                    if (getConfig().showParticles && getStick(p) != null) {
                        p.getWorld().spawnParticle(Particles.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.2, 0.1, 0.1, Objects.requireNonNull(getStick(p)).getBlockData());
                    }
                }

                p.setGravity(false);
                Vector c = p.getVelocity();
                p.setVelocity(p.getVelocity().setY((c.getY() * 0.35) - 0.0025));
                Double vv = airjumps.get(p);
                vv = vv == null ? 0 : vv;
                vv += 0.0127;
                airjumps.put(p, vv);
            }

            if (!canStick(p) && !p.hasGravity()) {
                p.setGravity(true);
            }
        }
    }

    private boolean canStick(Player p) {

        for (Block i : getBlocks(p)) {
            if (i.getBlockData().getMaterial().isSolid()) {
                Vector velocity = p.getVelocity();
                Vector shift = p.getLocation().subtract(i.getLocation().clone().add(0.5, 0.5, 0.5)).toVector();
                velocity.setX(velocity.getX() - (shift.getX() / 16));
                velocity.setZ(velocity.getZ() - (shift.getZ() / 16));
                p.setVelocity(velocity);

                return true;
            }
        }

        return false;
    }

    private Block getStick(Player p) {
        getBlocks(p);

        for (Block i : getBlocks(p)) {
            if (i.getBlockData().getMaterial().isSolid()) {
                return i;
            }
        }

        return null;
    }

    private Block[] getBlocks(Player p) {
        return new Block[]{
                p.getLocation().getBlock().getRelative(BlockFace.NORTH),
                p.getLocation().getBlock().getRelative(BlockFace.SOUTH),
                p.getLocation().getBlock().getRelative(BlockFace.EAST),
                p.getLocation().getBlock().getRelative(BlockFace.WEST),
                p.getLocation().getBlock().getRelative(BlockFace.NORTH_EAST),
                p.getLocation().getBlock().getRelative(BlockFace.SOUTH_EAST),
                p.getLocation().getBlock().getRelative(BlockFace.NORTH_WEST),
                p.getLocation().getBlock().getRelative(BlockFace.SOUTH_WEST),
                p.getLocation().getBlock().getRelative(BlockFace.NORTH_EAST).getRelative(BlockFace.UP),
                p.getLocation().getBlock().getRelative(BlockFace.SOUTH_EAST).getRelative(BlockFace.UP),
                p.getLocation().getBlock().getRelative(BlockFace.NORTH_WEST).getRelative(BlockFace.UP),
                p.getLocation().getBlock().getRelative(BlockFace.SOUTH_WEST).getRelative(BlockFace.UP),
                p.getLocation().getBlock().getRelative(BlockFace.NORTH).getRelative(BlockFace.UP),
                p.getLocation().getBlock().getRelative(BlockFace.SOUTH).getRelative(BlockFace.UP),
                p.getLocation().getBlock().getRelative(BlockFace.EAST).getRelative(BlockFace.UP),
                p.getLocation().getBlock().getRelative(BlockFace.WEST).getRelative(BlockFace.UP),
        };
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
        final boolean showParticles = true;
        final int baseCost = 2;
        final double costFactor = 0.65;
        final int maxLevel = 5;
        final int initialCost = 8;
        final double maxJumpsLevelBonusDivisor = 2;
        final double jumpHeightBase = 0.625;
        final double jumpHeightBonusLevelMultiplier = 0.225;
    }
}
