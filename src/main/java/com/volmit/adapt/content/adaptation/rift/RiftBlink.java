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
import com.volmit.adapt.api.world.AdaptPlayer;
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
                Location loc = p.getLocation().clone();
                Location locOG = p.getLocation().clone();
                Vector dir = loc.getDirection();
                double dist = getBlinkDistance(getLevel(p));
                dir.multiply(dist);
                loc.add(dir);
                double cd = dist * 2;
                loc.subtract(0, dist, 0);
                while (!isSafe(loc) && cd-- > 0) {
                    loc.add(0, 1, 0);
                }
                if (!isSafe(loc)) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1f, 1.24f);
                    lastJump.put(p, M.ms());
                    return;
                }
                if (getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist") != null &&
                        getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist").getLevel() > 0) {
                    RiftResist.riftResistStackAdd(p, 10, 5);
                }
                if (getConfig().showParticles) {

                    vfxParticleLine(locOG, loc, Particle.REVERSE_PORTAL, 50, 8, 0.1D, 1D, 0.1D, 0D, null, false, l -> l.getBlock().isPassable());
                }
                Vector v = p.getVelocity().clone();
                loadChunkAsync(loc, chunk -> {
                    Location toLoc = loc.add(0, 1, 0);

                    AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(!Bukkit.isPrimaryThread(), getPlayer(p), this, locOG, loc);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        return;
                    }

                    J.s(() -> p.teleport(toLoc, PlayerTeleportEvent.TeleportCause.PLUGIN));
                    J.s(() -> p.setVelocity(v.multiply(3)), 2);
                });
                lastJump.put(p, M.ms());
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.50f, 1.0f);
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
            Location loc = p.getLocation().clone();
            Vector dir = loc.getDirection();
            double dist = getBlinkDistance(getLevel(p));
            dir.multiply(dist);
            loc.add(dir);
            double cd = dist * 2;
            loc.subtract(0, dist, 0);

            while (!isSafe(loc) && cd-- > 0) {
                loc.add(0, 1, 0);
            }

            if (isSafe(loc)) {
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
    }
}