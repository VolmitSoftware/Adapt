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
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.VelocitySpeed;
import com.volmit.adapt.util.reflect.events.api.ReflectiveHandler;
import com.volmit.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import com.volmit.adapt.util.reflect.events.api.entity.EntityMountEvent;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
    private final Map<UUID, Integer> ticksRunning;
    private final Map<UUID, Boolean> speedBoosting;

    public AgilityWindUp() {
        super("agility-wind-up");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility.wind_up.description"));
        setDisplayName(Localizer.dLocalize("agility.wind_up.name"));
        setIcon(Material.POWERED_RAIL);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setInitialCost(getConfig().initialCost);
        setInterval(120);
        ticksRunning = new HashMap<>();
        speedBoosting = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.POWERED_RAIL)
                .key("challenge_agility_wind_up_10min")
                .title(Localizer.dLocalize("advancement.challenge_agility_wind_up_10min.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_wind_up_10min.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ACTIVATOR_RAIL)
                        .key("challenge_agility_wind_up_2hr")
                        .title(Localizer.dLocalize("advancement.challenge_agility_wind_up_2hr.title"))
                        .description(Localizer.dLocalize("advancement.challenge_agility_wind_up_2hr.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_agility_wind_up_10min", "agility.wind-up.max-speed-ticks", 12000, 400);
        registerMilestone("challenge_agility_wind_up_2hr", "agility.wind-up.max-speed-ticks", 144000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getWindupSpeed(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("agility.wind_up.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("agility.wind_up.lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        ticksRunning.remove(id);
        speedBoosting.remove(id);
    }

    @ReflectiveHandler
    public void on(EntityMountEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER) {
            return;
        }

        Player p = (Player) event.getEntity();
        UUID id = p.getUniqueId();
        ticksRunning.remove(id);
        invalidateWindupSpeed(p, id, true);
    }

    @ReflectiveHandler
    public void on(EntityDismountEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER) {
            return;
        }

        Player p = (Player) event.getEntity();
        ticksRunning.remove(p.getUniqueId());
    }

    private double getWindupTicks(double factor) {
        return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
    }

    private double getWindupSpeed(double factor) {
        return getConfig().windupSpeedBase + (factor * getConfig().windupSpeedLevelMultiplier);
    }

    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            UUID id = p.getUniqueId();
            if (!hasAdaptation(p) || !isVelocityEligible(p)) {
                ticksRunning.remove(id);
                invalidateWindupSpeed(p, id, true);
                continue;
            }

            if (!p.isSprinting()) {
                ticksRunning.remove(id);
                brakeWindupSpeed(p, id);
                continue;
            }

            ticksRunning.compute(id, (k, v) -> v == null ? 1 : v + 1);
            int tr = ticksRunning.getOrDefault(id, 0);
            if (tr <= 0) {
                continue;
            }

            double factor = getLevelPercent(p);
            double ticksToMax = getWindupTicks(factor);
            double progress = Math.min(M.lerpInverse(0, ticksToMax, tr), 1);
            double speedIncrease = M.lerp(0, getWindupSpeed(factor), progress);

            if (areParticlesEnabled()) {
                if (M.r(0.2 * progress)) {
                    p.getWorld().spawnParticle(Particle.LAVA, p.getLocation(), 1);
                }

                if (M.r(0.25 * progress)) {
                    p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 1, 0, 0, 0, 0);
                }
            }

            VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(p, getConfig().fallbackInputVelocityThresholdSquared());
            if (!input.hasHorizontal()) {
                brakeWindupSpeed(p, id);
            } else {
                applyWindupSpeed(p, id, input, speedIncrease);
            }

            if (progress >= 1.0) {
                getPlayer(p).getData().addStat("agility.wind-up.max-speed-ticks", 1);
            }
        }
    }

    private void applyWindupSpeed(Player p, UUID id, VelocitySpeed.InputSnapshot input, double speedIncrease) {
        Vector direction = VelocitySpeed.resolveHorizontalDirection(p, input);
        if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
            brakeWindupSpeed(p, id);
            return;
        }

        double targetSpeed = Math.min(getConfig().maxHorizontalSpeed,
                Math.max(0, getConfig().baseHorizontalSpeed * (1.0 + Math.max(0, speedIncrease))));
        Vector horizontal = VelocitySpeed.horizontalOnly(p.getVelocity());
        Vector targetHorizontal = direction.multiply(targetSpeed);
        Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, targetHorizontal, Math.max(0, getConfig().accelPerTick));
        nextHorizontal = VelocitySpeed.clampHorizontal(nextHorizontal, getConfig().maxHorizontalSpeed);
        VelocitySpeed.setHorizontalVelocity(p, nextHorizontal);
        speedBoosting.put(id, true);
    }

    private void brakeWindupSpeed(Player p, UUID id) {
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

    private void invalidateWindupSpeed(Player p, UUID id, boolean invalidState) {
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

        return !p.isDead()
                && !p.isSwimming()
                && !p.isFlying()
                && !p.isGliding()
                && !p.isSneaking()
                && p.getVehicle() == null;
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
    @ConfigDescription("Get faster the longer you sprint.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Agility Wind Up adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Slowest for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupTicksSlowest = 180;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Fastest for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupTicksFastest = 60;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Speed Base for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupSpeedBase = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Speed Level Multiplier for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupSpeedLevelMultiplier = 0.225;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base horizontal speed used for windup velocity scaling.", impact = "Higher values increase movement speed while windup is active.")
        double baseHorizontalSpeed = 0.13;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum horizontal speed this adaptation can force.", impact = "Acts as a hard cap to prevent runaway momentum.")
        double maxHorizontalSpeed = 0.31;
        @com.volmit.adapt.util.config.ConfigDoc(value = "How fast velocity accelerates toward the windup target per tick.", impact = "Higher values accelerate faster; lower values feel smoother.")
        double accelPerTick = 0.045;
        @com.volmit.adapt.util.config.ConfigDoc(value = "How fast velocity decays when sprint movement is not applied.", impact = "Higher values stop faster and reduce carry momentum.")
        double brakePerTick = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Horizontal velocity threshold considered fully stopped.", impact = "Higher values stop sooner; lower values preserve tiny motion longer.")
        double stopThreshold = 0.01;
        @com.volmit.adapt.util.config.ConfigDoc(value = "If true, windup velocity is force-cleared when entering invalid states.", impact = "Prevents retained speed from skipped state transitions.")
        boolean hardStopOnInvalidState = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Fallback movement threshold used when direct input API is unavailable.", impact = "Only used on runtimes without Player input access.")
        double fallbackInputVelocityThreshold = 0.0008;

        double fallbackInputVelocityThresholdSquared() {
            double threshold = Math.max(0, fallbackInputVelocityThreshold);
            return threshold * threshold;
        }
    }

}
