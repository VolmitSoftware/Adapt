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
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
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
        setDescription(Localizer.dLocalize("agility.ladder_slide.description"));
        setDisplayName(Localizer.dLocalize("agility.ladder_slide.name"));
        setIcon(Material.LADDER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(50);
        upwardStates = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LADDER)
                .key("challenge_agility_ladder_500")
                .title(Localizer.dLocalize("advancement.challenge_agility_ladder_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_ladder_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_CHAIN)
                        .key("challenge_agility_ladder_10k")
                        .title(Localizer.dLocalize("advancement.challenge_agility_ladder_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_agility_ladder_10k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_agility_ladder_500")
                .goal(500)
                .stat("agility.ladder-slide.blocks-climbed")
                .reward(300)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_agility_ladder_10k")
                .goal(10000)
                .stat("agility.ladder-slide.blocks-climbed")
                .reward(1000)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getConfig().speedMultiplier, 1) + "x " + C.GRAY + Localizer.dLocalize("agility.ladder_slide.lore1"));
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
        getPlayer(p).getData().addStat("agility.ladder-slide.blocks-climbed", 1);
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
    @ConfigDescription("Climb and slide ladders much faster in both directions.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Multiplier applied to baseUpwardLadderSpeed to compute the target climb speed.", impact = "Higher values increase final ladder climb speed after the ramp-up phase.")
        double speedMultiplier = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Velocity difference threshold used to skip tiny Y-velocity adjustments.", impact = "Lower values apply more frequent micro-updates; higher values reduce minor velocity writes.")
        double velocityEpsilon = 0.003;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Baseline climb speed used before the speed multiplier is applied.", impact = "Higher values raise the base climb profile and increase total ladder ascent speed.")
        double baseUpwardLadderSpeed = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Vanilla-like upward speed used near ladder endpoints to avoid overshooting.", impact = "Higher values make endpoint climbing snappier; lower values keep transitions conservative.")
        double normalUpwardLadderSpeed = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Minimum positive Y movement treated as intentional upward ladder motion.", impact = "Lower values are more sensitive to slight upward input; higher values require clearer upward movement.")
        double movementDirectionEpsilonUpward = 0.0004;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Smoothing factor for blending current upward velocity toward target ladder speed.", impact = "Values near 1.0 ramp quickly; lower values create a slower curve-like acceleration profile.")
        double upwardAccelerationSmoothing = 0.28;
        @com.volmit.adapt.util.config.ConfigDoc(value = "How long to retain previous upward speed state between ladder movement samples.", impact = "Lower values reset ramp-up sooner; higher values preserve momentum between short interruptions.")
        long upwardStateResetMs = 200;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Minimum upward look angle required to activate upward ladder acceleration.", impact = "Larger values require players to look farther upward before acceleration engages.")
        double lookUpPitchThreshold = 15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Distance from ladder top where motion reverts toward normal upward speed.", impact = "Higher values begin fallback earlier near ladder ends; lower values keep boosted speed longer.")
        int revertDistanceBlocks = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum blocks scanned to detect ladder continuity when checking ladder endpoints.", impact = "Higher values support taller ladders at slightly higher per-check cost.")
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
