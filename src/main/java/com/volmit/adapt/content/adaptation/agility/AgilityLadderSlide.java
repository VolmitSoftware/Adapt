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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgilityLadderSlide extends SimpleAdaptation<AgilityLadderSlide.Config> {
    private final Map<UUID, UpwardState> upwardStates;

    public AgilityLadderSlide() {
        super("agility-ladder-slide");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility", "ladderslide", "description"));
        setDisplayName(Localizer.dLocalize("agility", "ladderslide", "name"));
        setIcon(Material.LADDER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(50);
        upwardStates = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getConfig().speedMultiplier, 1) + "x " + C.GRAY + Localizer.dLocalize("agility", "ladderslide", "lore1"));
        v.addLore(C.YELLOW + "Downward speed boost coming in a future update.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerMoveEvent e) {
        if (e.getTo() == null) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || p.isFlying() || p.isGliding() || p.isSwimming()) {
            clearUpwardState(p);
            return;
        }

        Location location = p.getLocation();
        if (!canInteract(p, location)) {
            clearUpwardState(p);
            return;
        }

        Block activeLadder = getActiveLadderBlock(location);
        if (activeLadder == null) {
            clearUpwardState(p);
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();
        boolean lookingUp = p.getLocation().getPitch() <= -Math.abs(getConfig().lookUpPitchThreshold);
        if (!lookingUp) {
            clearUpwardState(p);
            return;
        }

        double epsilon = Math.abs(getConfig().movementDirectionEpsilonUpward);
        Vector velocity = p.getVelocity();
        if (p.isSneaking()) {
            clearUpwardState(p);
            applyVerticalVelocity(p, velocity, 0);
            return;
        }

        boolean movingUp = dy > epsilon;
        if (!movingUp) {
            clearUpwardState(p);
            return;
        }

        double baseUp = Math.max(0, getConfig().normalUpwardLadderSpeed);
        double targetUp = isNearLadderEnd(activeLadder, true) ? baseUp : getUpwardSpeed();
        applySmoothUpwardVelocity(p, velocity, baseUp, targetUp);
        p.setFallDistance(0);
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        clearUpwardState(e.getPlayer());
    }

    private void applySmoothUpwardVelocity(Player p, Vector velocity, double baseUpwardSpeed, double targetUpwardSpeed) {
        double minUp = Math.max(0, baseUpwardSpeed);
        double target = Math.max(minUp, targetUpwardSpeed);
        long now = System.currentTimeMillis();
        UpwardState state = upwardStates.get(p.getUniqueId());
        if (state == null || now - state.lastSeenAt > Math.max(0, getConfig().upwardStateResetMs)) {
            state = new UpwardState(Math.max(minUp, Math.max(0, velocity.getY())), now);
            upwardStates.put(p.getUniqueId(), state);
        }

        double smoothing = clamp(getConfig().upwardAccelerationSmoothing, 0.01, 1.0);
        double current = Math.max(minUp, state.currentSpeed);
        double next = current + ((target - current) * smoothing);
        state.currentSpeed = Math.min(target, Math.max(minUp, next));
        state.lastSeenAt = now;
        applyVerticalVelocity(p, velocity, state.currentSpeed);
    }

    private void clearUpwardState(Player p) {
        if (p == null) {
            return;
        }
        upwardStates.remove(p.getUniqueId());
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void applyVerticalVelocity(Player p, Vector velocity, double targetY) {
        if (Math.abs(velocity.getY() - targetY) <= getConfig().velocityEpsilon) {
            return;
        }

        p.setVelocity(velocity.setY(targetY));
    }

    private Block getActiveLadderBlock(Location location) {
        Block feet = location.getBlock();
        if (feet.getType() == Material.LADDER) {
            return feet;
        }

        Block head = location.clone().add(0, 1, 0).getBlock();
        if (head.getType() == Material.LADDER) {
            return head;
        }

        return null;
    }

    private boolean isNearLadderEnd(Block ladder, boolean upward) {
        int laddersAhead = 0;
        Block cursor = ladder;
        BlockFace direction = upward ? BlockFace.UP : BlockFace.DOWN;
        int limit = Math.max(1, getConfig().maxLadderScanDistance);
        for (int i = 0; i < limit; i++) {
            cursor = cursor.getRelative(direction);
            if (cursor.getType() != Material.LADDER) {
                break;
            }

            laddersAhead++;
            if (laddersAhead > getConfig().revertDistanceBlocks) {
                return false;
            }
        }

        return laddersAhead <= getConfig().revertDistanceBlocks;
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
        int baseCost = 1;
        int initialCost = 1;
        double costFactor = 0.12;
        int maxLevel = 1;
        double speedMultiplier = 2.0;
        double velocityEpsilon = 0.003;
        double baseUpwardLadderSpeed = 0.12;
        double normalUpwardLadderSpeed = 0.2;
        double movementDirectionEpsilonUpward = 0.0004;
        double upwardAccelerationSmoothing = 0.28;
        long upwardStateResetMs = 200;
        double lookUpPitchThreshold = 15;
        int revertDistanceBlocks = 1;
        int maxLadderScanDistance = 64;
    }

    private double getUpwardSpeed() {
        return getConfig().baseUpwardLadderSpeed * getConfig().speedMultiplier;
    }

    private static class UpwardState {
        private double currentSpeed;
        private long lastSeenAt;

        private UpwardState(double currentSpeed, long lastSeenAt) {
            this.currentSpeed = currentSpeed;
            this.lastSeenAt = lastSeenAt;
        }
    }
}
