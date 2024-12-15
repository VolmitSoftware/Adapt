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

package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.enums.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ArchitectElevator extends SimpleAdaptation<ArchitectElevator.Config> {
    private final Map<Player, Integer> blockPower;
    private final Map<Player, Long> cooldowns;

    private static final double TELEPORT_OFFSET = 0.5;
    private static final int PARTICLE_COUNT = 20;
    private static final float SOUND_VOLUME = 1f;
    private static final float SOUND_PITCH = 1f;
    private static final int UP_INCREMENT = 1;
    private static final int DOWN_INCREMENT = -1;

    public ArchitectElevator() {
        super("architect-elevator");
        registerConfiguration(ArchitectElevator.Config.class);
        setDescription(Localizer.dLocalize("architect", "elevator", "description"));
        setDisplayName(Localizer.dLocalize("architect", "elevator", "name"));
        setIcon(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        setInterval(988);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        blockPower = new HashMap<>();
        cooldowns = new HashMap<>();
    }

    // TODO: Implement the functionality of the elevator. -Illyrius
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Block b = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (isElevator(b)) {
            handleElevatorMovement(p);
        }
    }

    private void handleElevatorMovement(Player p) {
        if (p.getVelocity().getY() > 0) {
            detectOtherElevator(p, true);
        } else if (p.isSneaking()) {
            detectOtherElevator(p, false);
        }
    }

    private static boolean isElevator(Block b) {
        return b.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE
                && b.getRelative(BlockFace.DOWN).getType() == Material.NOTE_BLOCK;
    }

    private void detectOtherElevator(Player p, boolean goingUp) {
        World w = p.getWorld();
        int increment = goingUp ? UP_INCREMENT : DOWN_INCREMENT;
        int x = p.getLocation().getBlockX();
        int y = p.getLocation().getBlockY() + increment;
        int z = p.getLocation().getBlockZ();

        while (y >= w.getMinHeight() && y <= w.getMaxHeight()) {
            Block b = w.getBlockAt(x, y, z);
            if (isElevator(b) && hasEnoughSpace(b)) {
                teleportPlayer(p, b.getLocation());
                return;
            }
            y += increment;
        }
    }

    private static boolean hasEnoughSpace(Block b) {
        Block above = b.getRelative(BlockFace.UP);
        return above.getType() == Material.AIR && above.getRelative(BlockFace.UP).getType() == Material.AIR;
    }

    private void teleportPlayer(Player p, Location l) {
        Location tpl = l.add(TELEPORT_OFFSET, 1, TELEPORT_OFFSET);
        playTeleportEffects(p);
        p.teleport(tpl);
        p.playSound(tpl, Sound.ENTITY_ENDERMAN_TELEPORT, SOUND_VOLUME, SOUND_PITCH);
        playTeleportEffects(p);
    }

    private void playTeleportEffects(Player p) {
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), PARTICLE_COUNT);
    }

    public int getBlockPower(double factor) {
        return (int) Math.floor(M.lerp(getConfig().minBlocks, getConfig().maxBlocks, factor));
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(i)) {
                continue;
            }

            boolean ready = !hasCooldown(i);
            int availablePower = getBlockPower(getLevelPercent(i));
            blockPower.compute(i, (k, v) -> {
                if ((k == null || v == null) || (ready && v != availablePower)) {
                    if (i == null) {
                        return 0;
                    }
                    final var world = i.getWorld();
                    final var location = i.getLocation();

                    SoundPlayer spw = SoundPlayer.of(world);
                    spw.play(location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 10.0f);
                    spw.play(location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.81f);

                    return availablePower;
                }
                return v;
            });
        }
    }

    private boolean hasCooldown(Player i) {
        if (cooldowns.containsKey(i)) {
            if (M.ms() >= cooldowns.get(i)) {
                cooldowns.remove(i);
            }
        }

        return cooldowns.containsKey(i);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    // TODO: adjust to be of use for this class. -Illyrius
    @NoArgsConstructor
    protected static class Config {
        public long duration = 3000;
        public int minBlocks = 9;
        public int maxBlocks = 35;
        public int cooldown = 5000;
        boolean permanent = false;
        boolean showParticles = true;
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 5;
        int initialCost = 1;
        double costFactor = 0.40;
    }
}