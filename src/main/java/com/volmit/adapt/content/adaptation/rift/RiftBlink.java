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
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.event.AdaptAdaptationTeleportEvent;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

import static com.volmit.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;


public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> {
    private final Map<Player, Long> lastJump = new HashMap<>();
    private final Map<Player, Boolean> canBlink = new HashMap<>();

    private final double jumpVelocity = -0.0784000015258789;

    public RiftBlink() {
        super("rift-blink");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift", "blink", "description"));
        setDisplayName(Localizer.dLocalize("rift", "blink", "name"));
        setIcon(Material.FEATHER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9288);
    }

    private double getBlinkDistance(int level) {
        return getConfig().baseDistance + (getLevelPercent(level) * getConfig().distanceFactor);
    }

    private int getCooldownDuration() {
        return 2000;
    }

    private Location findBlinkGround(Player player) {
        Location start = player.getLocation().clone();
        Vector direction = start.getDirection().clone().setY(0);
        if (direction.lengthSquared() <= 0.0001) {
            double yawRadians = Math.toRadians(start.getYaw());
            direction = new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
        }
        direction.normalize();

        int maxVerticalAdjustment = Math.max(0, getConfig().maxVerticalAdjustment);
        double step = Math.max(0.25, getConfig().distanceSearchStep);
        double maxDistance = getBlinkDistance(getLevel(player));

        for (double distance = maxDistance; distance >= 1; distance -= step) {
            Location horizontalTarget = start.clone().add(direction.clone().multiply(distance));
            Location safe = findSafeGroundNear(horizontalTarget, maxVerticalAdjustment);
            if (safe != null) {
                return safe;
            }
        }

        return null;
    }

    private Location findSafeGroundNear(Location base, int maxVerticalAdjustment) {
        if (isSafe(base)) {
            return base;
        }

        for (int y = 1; y <= maxVerticalAdjustment; y++) {
            Location down = base.clone().subtract(0, y, 0);
            if (isSafe(down)) {
                return down;
            }

            Location up = base.clone().add(0, y, 0);
            if (isSafe(up)) {
                return up;
            }
        }

        return null;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (getBlinkDistance(level)) + C.GRAY + " " + Localizer.dLocalize("rift", "blink", "lore1"));
        v.addLore(C.ITALIC + Localizer.dLocalize("rift", "blink", "lore2") + C.DARK_PURPLE + Localizer.dLocalize("rift", "blink", "lore3"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lastJump.remove(p);
        canBlink.remove(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(p) && p.getGameMode().equals(GameMode.SURVIVAL)) {
            e.setCancelled(true);
            p.setAllowFlight(false);
            if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration()) {
                return;
            }
            if (p.isSprinting()) {
                Location locOG = p.getLocation().clone();
                SoundPlayer spw = SoundPlayer.of(p);
                Location destinationGround = findBlinkGround(p);
                if (destinationGround == null) {
                    spw.play(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1f, 1.24f);
                    lastJump.put(p, M.ms());
                    return;
                }
                PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("rift");
                PlayerAdaptation adaptation = line != null ? line.getAdaptation("rift-resist") : null;
                if (adaptation != null && adaptation.getLevel() > 0) {
                    RiftResist.riftResistStackAdd(p, 10, 5);
                }
                if (getConfig().showParticles) {

                    vfxParticleLine(locOG, destinationGround, Particle.REVERSE_PORTAL, 50, 8, 0.1D, 1D, 0.1D, 0D, null, false, l -> l.getBlock().isPassable());
                }
                Vector v = p.getVelocity().clone();
                loadChunkAsync(destinationGround, chunk -> {
                    J.s(() -> {
                        Location toLoc = destinationGround.clone().add(0, 1, 0);

                        AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(false, getPlayer(p), this, locOG, destinationGround.clone());
                        Bukkit.getPluginManager().callEvent(event);
                        if (event.isCancelled()) return;

                        p.teleport(toLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
                        p.setVelocity(v.multiply(3));
                    });
                });
                lastJump.put(p, M.ms());
                spw.play(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.50f, 1.0f);
                vfxLevelUp(p);
            }
        }
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        boolean isJumping = p.getVelocity().getY() > jumpVelocity;
        if (isJumping && !canBlink.containsKey(p) && hasAdaptation(p) && p.getGameMode().equals(GameMode.SURVIVAL) && p.isSprinting()) {
            if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration()) {
                p.setAllowFlight(false);
                return;
            }
            Location destinationGround = findBlinkGround(p);
            if (destinationGround != null) {
                canBlink.put(p, true);
                p.setAllowFlight(true);
                Adapt.verbose("Allowing flight for " + p.getName() + "");
                J.a(() -> {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                    Adapt.verbose("Disabling flight for " + p.getName() + "");
                    canBlink.remove(p);
                }, 25);
            }
        } else {
            canBlink.remove(p);
        }
    }

    private boolean isSafe(Location l) {
        return l.getBlock().getType().isSolid()
                && !l.getBlock().getRelative(BlockFace.UP).getType().isSolid()
                && !l.getBlock().getRelative(BlockFace.UP).getRelative(BlockFace.UP).getType().isSolid();
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
        int baseCost = 7;
        double costFactor = 0.12;
        int maxLevel = 5;
        int initialCost = 1;
        double baseDistance = 6;
        double distanceFactor = 5;
        int maxVerticalAdjustment = 4;
        double distanceSearchStep = 0.5;
    }
}
