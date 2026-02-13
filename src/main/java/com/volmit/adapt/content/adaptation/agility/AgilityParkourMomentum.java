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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.VelocitySpeed;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgilityParkourMomentum extends SimpleAdaptation<AgilityParkourMomentum.Config> {
    private final Map<UUID, Integer> momentum = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private final Map<UUID, Boolean> speedBoosting = new HashMap<>();

    public AgilityParkourMomentum() {
        super("agility-parkour-momentum");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility.parkour_momentum.description"));
        setDisplayName(Localizer.dLocalize("agility.parkour_momentum.name"));
        setIcon(Material.RABBIT_FOOT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(10);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.RABBIT_FOOT)
                .key("challenge_agility_parkour_500")
                .title(Localizer.dLocalize("advancement.challenge_agility_parkour_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_parkour_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_agility_parkour_500", "agility.parkour-momentum.ledge-landings", 500, 400);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getMaxMomentum(level) + C.GRAY + " " + Localizer.dLocalize("agility.parkour_momentum.lore1"));
        v.addLore(C.GREEN + "+ " + getMaxSpeedAmplifier(level) + C.GRAY + " " + Localizer.dLocalize("agility.parkour_momentum.lore2"));
        v.addLore(C.GREEN + "+ " + getMaxJumpAmplifier(level) + C.GRAY + " " + Localizer.dLocalize("agility.parkour_momentum.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        momentum.remove(id);
        wasOnGround.remove(id);
        speedBoosting.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom().distanceSquared(e.getTo()) < getConfig().minimumMoveSquared) {
            return;
        }

        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!hasAdaptation(p)) {
            momentum.remove(id);
            wasOnGround.remove(id);
            return;
        }

        boolean onGroundNow = p.isOnGround();
        boolean onGroundBefore = wasOnGround.getOrDefault(id, onGroundNow);
        int current = momentum.getOrDefault(id, 0);

        if (!onGroundBefore && onGroundNow) {
            if (isMomentumLanding(p) && isOnLedge(p)) {
                current += getConfig().landingGain;
                getPlayer(p).getData().addStat("agility.parkour-momentum.ledge-landings", 1);
            } else {
                current -= getConfig().failedLandingPenalty;
            }
        } else if (onGroundNow && !p.isSprinting()) {
            current -= getConfig().groundDecayOnMove;
        }

        current = clampMomentum(current, getMaxMomentum(getLevel(p)));
        momentum.put(id, current);
        wasOnGround.put(id, onGroundNow);
    }

    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            UUID id = p.getUniqueId();
            if (!hasAdaptation(p)) {
                momentum.remove(id);
                wasOnGround.remove(id);
                invalidateMomentumSpeed(p, id, true);
                continue;
            }

            int level = getLevel(p);
            int maxMomentum = getMaxMomentum(level);
            int current = momentum.getOrDefault(id, 0);
            if (current <= 0) {
                invalidateMomentumSpeed(p, id, false);
                continue;
            }

            if (p.isOnGround() && !isOnLedge(p)) {
                current -= getConfig().offLedgeDecayPerTick;
                momentum.put(id, clampMomentum(current, maxMomentum));
                brakeMomentumSpeed(p, id);
                continue;
            }

            int speedAmp = Math.max(0, Math.min(getMaxSpeedAmplifier(level), (int) Math.floor((current / (double) maxMomentum) * (getMaxSpeedAmplifier(level) + 1)) - 1));
            int jumpAmp = Math.max(0, Math.min(getMaxJumpAmplifier(level), (int) Math.floor((current / (double) maxMomentum) * (getMaxJumpAmplifier(level) + 1)) - 1));

            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 25, jumpAmp, false, false));

            if (speedAmp <= 0) {
                brakeMomentumSpeed(p, id);
            } else if (!isVelocityEligible(p)) {
                invalidateMomentumSpeed(p, id, true);
            } else {
                VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(p, getConfig().fallbackInputVelocityThresholdSquared());
                if (!input.hasHorizontal()) {
                    brakeMomentumSpeed(p, id);
                } else {
                    applyMomentumSpeed(p, id, input, speedAmp);
                }
            }

            if (p.isOnGround() && !p.isSprinting()) {
                current -= getConfig().passiveGroundDecayPerTick;
            }

            momentum.put(id, clampMomentum(current, maxMomentum));
        }
    }

    private void applyMomentumSpeed(Player p, UUID id, VelocitySpeed.InputSnapshot input, int speedAmp) {
        Vector direction = VelocitySpeed.resolveHorizontalDirection(p, input);
        if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
            brakeMomentumSpeed(p, id);
            return;
        }

        double targetSpeed = Math.min(getConfig().maxHorizontalSpeed,
                Math.max(0, getConfig().baseHorizontalSpeed * VelocitySpeed.speedAmplifierScalar(speedAmp)));
        Vector horizontal = VelocitySpeed.horizontalOnly(p.getVelocity());
        Vector targetHorizontal = direction.multiply(targetSpeed);
        Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, targetHorizontal, Math.max(0, getConfig().accelPerTick));
        nextHorizontal = VelocitySpeed.clampHorizontal(nextHorizontal, getConfig().maxHorizontalSpeed);
        VelocitySpeed.setHorizontalVelocity(p, nextHorizontal);
        speedBoosting.put(id, true);
    }

    private void brakeMomentumSpeed(Player p, UUID id) {
        if (!speedBoosting.getOrDefault(id, false)) {
            return;
        }

        Vector horizontal = VelocitySpeed.horizontalOnly(p.getVelocity());
        double stopThreshold = Math.max(0, getConfig().stopThreshold);
        if (horizontal.lengthSquared() <= stopThreshold * stopThreshold) {
            VelocitySpeed.hardStopHorizontal(p);
            speedBoosting.put(id, false);
            return;
        }

        Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, new Vector(), Math.max(0, getConfig().brakePerTick));
        if (nextHorizontal.lengthSquared() <= stopThreshold * stopThreshold) {
            VelocitySpeed.hardStopHorizontal(p);
            speedBoosting.put(id, false);
            return;
        }

        VelocitySpeed.setHorizontalVelocity(p, nextHorizontal);
    }

    private void invalidateMomentumSpeed(Player p, UUID id, boolean invalidState) {
        if (!speedBoosting.getOrDefault(id, false)) {
            return;
        }

        if (invalidState && getConfig().hardStopOnInvalidState) {
            VelocitySpeed.hardStopHorizontal(p);
        }

        speedBoosting.put(id, false);
    }

    private boolean isVelocityEligible(Player p) {
        GameMode mode = p.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return false;
        }

        return !p.isDead() && !p.isFlying() && !p.isGliding() && !p.isSwimming() && p.getVehicle() == null;
    }

    private boolean isMomentumLanding(Player p) {
        return p.isSprinting() && !p.isSwimming() && !p.isGliding() && !p.isFlying();
    }

    private boolean isOnLedge(Player p) {
        if (!p.isOnGround()) {
            return false;
        }

        Block feet = p.getLocation().getBlock();
        Block below = feet.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) {
            return false;
        }

        BlockFace[] sides = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace side : sides) {
            Block sideAtFeet = feet.getRelative(side);
            Block sideBelow = below.getRelative(side);
            if (!sideAtFeet.getType().isSolid() && !sideBelow.getType().isSolid()) {
                return true;
            }
        }

        return false;
    }

    private int clampMomentum(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private int getMaxMomentum(int level) {
        return Math.max(3, (int) Math.round(getConfig().momentumBase + (getLevelPercent(level) * getConfig().momentumFactor)));
    }

    private int getMaxSpeedAmplifier(int level) {
        return Math.max(0, (int) Math.round(getConfig().speedAmplifierBase + (getLevelPercent(level) * getConfig().speedAmplifierFactor)));
    }

    private int getMaxJumpAmplifier(int level) {
        return Math.max(0, (int) Math.round(getConfig().jumpAmplifierBase + (getLevelPercent(level) * getConfig().jumpAmplifierFactor)));
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
    @ConfigDescription("Build momentum by chaining sprint-jumps and landings to gain speed and jump boosts.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Momentum Base for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double momentumBase = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Momentum Factor for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double momentumFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Amplifier Base for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedAmplifierBase = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Amplifier Factor for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedAmplifierFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Jump Amplifier Base for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double jumpAmplifierBase = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Jump Amplifier Factor for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double jumpAmplifierFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Landing Gain for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int landingGain = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Failed Landing Penalty for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int failedLandingPenalty = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ground Decay On Move for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int groundDecayOnMove = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Passive Ground Decay Per Tick for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int passiveGroundDecayPerTick = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Off Ledge Decay Per Tick for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int offLedgeDecayPerTick = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Move Squared for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minimumMoveSquared = 0.0025;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base horizontal speed used for momentum velocity scaling.", impact = "Higher values increase movement speed when momentum speed is active.")
        double baseHorizontalSpeed = 0.13;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum horizontal speed this adaptation can force.", impact = "Acts as a hard cap to prevent excessive momentum carry.")
        double maxHorizontalSpeed = 0.3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "How fast velocity accelerates toward the momentum target per tick.", impact = "Higher values accelerate faster; lower values feel smoother.")
        double accelPerTick = 0.04;
        @com.volmit.adapt.util.config.ConfigDoc(value = "How fast velocity decays when movement input is released.", impact = "Higher values stop faster and reduce carry momentum.")
        double brakePerTick = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Horizontal velocity threshold considered fully stopped.", impact = "Higher values stop sooner; lower values preserve tiny motion longer.")
        double stopThreshold = 0.01;
        @com.volmit.adapt.util.config.ConfigDoc(value = "If true, speed velocity is force-cleared when entering invalid states.", impact = "Prevents retained boosts if state transitions skip expected checks.")
        boolean hardStopOnInvalidState = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Fallback movement threshold used when direct input API is unavailable.", impact = "Only used on runtimes without Player input access.")
        double fallbackInputVelocityThreshold = 0.0008;

        double fallbackInputVelocityThresholdSquared() {
            double threshold = Math.max(0, fallbackInputVelocityThreshold);
            return threshold * threshold;
        }
    }
}
