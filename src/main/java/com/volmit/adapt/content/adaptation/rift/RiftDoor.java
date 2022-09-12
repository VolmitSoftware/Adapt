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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.util.Vector;

import java.util.List;


public class RiftDoor extends SimpleAdaptation<RiftDoor.Config> {
    public RiftDoor() {
        super("rift-door");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Rift", "RiftResistance", "Description"));
        setDisplayName(Adapt.dLocalize("Rift", "RiftResistance", "Name"));
        setIcon(Material.IRON_DOOR);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(2218);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.ITALIC + Adapt.dLocalize("Rift", "RiftResistance", "Lore1"));
        v.addLore(C.UNDERLINE + Adapt.dLocalize("Rift", "RiftResistance", "Lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockDamageEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(p) && !e.isCancelled()) {
            Block b = e.getBlock();
            List<Block> door = List.of(
                    e.getBlock().getRelative(0, 1, 0), getRightBlock(p, e.getBlock().getRelative(0, 1, 0)), getLeftBlock(p, e.getBlock().getRelative(0, 1, 0)),
                    e.getBlock(), getRightBlock(p, b), getLeftBlock(p, b),
                    e.getBlock().getRelative(0, -1, 0), getRightBlock(p, e.getBlock().getRelative(0, -1, 0)), getLeftBlock(p, e.getBlock().getRelative(0, -1, 0))
            );
            for (Block block : door) {
                doBlockThings(block);
            }
        }
    }

    void doBlockThings(Block b) {
        World w = b.getWorld();
        Material mat = b.getType();
        if (b.getType().equals(Material.AIR)) {
            return;
        }
        Entity entity = w.spawnFallingBlock(b.getLocation().add(0.5, 0, 0.5), mat.createBlockData());
        b.setType(Material.AIR);
        J.a(() -> {
            J.s(() -> {
                entity.setGravity(false);
                entity.setVelocity(new Vector(0, 0, 0));
                if (getConfig().showParticles) {
                    vfxSingleCubeOutline(b, Particle.REVERSE_PORTAL);
                }
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
            });
            try {
                Thread.sleep(3000);
            } catch (Exception ignored) {
            }
            J.s(() -> {
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1f, 1f);
                entity.getLocation().getWorld().setBlockData(entity.getLocation(), mat.createBlockData());
                entity.remove();
            });
        });
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
        boolean enabled = false;
        boolean showParticles = true;
        int baseCost = 3;
        double costFactor = 1;
        int maxLevel = 1;
        int initialCost = 5;
    }
}