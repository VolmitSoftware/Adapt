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

package com.volmit.adapt.content.adaptation.seaborrne;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

public class SeaborneTidecaller extends SimpleAdaptation<SeaborneTidecaller.Config> {
    public SeaborneTidecaller() {
        super("seaborne-tidecaller");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("seaborn.tidecaller.description"));
        setDisplayName(Localizer.dLocalize("seaborn.tidecaller.name"));
        setIcon(Material.HEART_OF_THE_SEA);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1600);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TRIDENT)
                .key("challenge_seaborne_tidecaller_200")
                .title(Localizer.dLocalize("advancement.challenge_seaborne_tidecaller_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_seaborne_tidecaller_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.HEART_OF_THE_SEA)
                        .key("challenge_seaborne_tidecaller_5k")
                        .title(Localizer.dLocalize("advancement.challenge_seaborne_tidecaller_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_seaborne_tidecaller_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_seaborne_tidecaller_200", "seaborne.tidecaller.dashes", 200, 300);
        registerMilestone("challenge_seaborne_tidecaller_5k", "seaborne.tidecaller.dashes", 5000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getDashDistance(level), 1) + C.GRAY + " " + Localizer.dLocalize("seaborn.tidecaller.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("seaborn.tidecaller.lore2"));
        java.util.List<String> combos = getTriggerCombos();
        if (combos.isEmpty()) {
            v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + "none");
        } else {
            for (String combo : combos) {
                v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + combo);
            }
        }
        v.addLore(C.AQUA + "* " + C.GRAY + "Environment: " + C.WHITE + getEnvironmentSummary());
    }

    @Override
    public String getDescription() {
        return "Surge forward with a water burst in valid environments. " + summarizeTriggerDescription();
    }

    private String summarizeTriggerDescription() {
        java.util.List<String> combos = getTriggerCombos();
        if (combos.isEmpty()) {
            return "No active triggers are currently enabled.";
        }

        if (combos.size() == 1) {
            return "Trigger: " + combos.get(0) + ".";
        }

        if (combos.size() == 2) {
            return "Triggers: " + combos.get(0) + " or " + combos.get(1) + ".";
        }

        return "Triggers: " + combos.get(0) + ", " + combos.get(1) + ", +" + (combos.size() - 2) + " more.";
    }

    private java.util.List<String> getTriggerCombos() {
        java.util.List<String> triggers = new java.util.ArrayList<>();
        String env = getEnvironmentSummary();
        if (getConfig().enableSneakTrigger) {
            triggers.add("Sneak" + (env.equals("none") ? "" : " (" + env + ")"));
        }

        if (getConfig().enableAttackTrigger) {
            String combo = getConfig().attackTriggerRequiresSneak ? "Sneak + Attack" : "Attack";
            if (getConfig().attackTriggerWaterOnly) {
                combo += " (water only)";
            } else if (!env.equals("none")) {
                combo += " (" + env + ")";
            }
            triggers.add(combo);
        }

        return triggers;
    }

    private String getEnvironmentSummary() {
        java.util.List<String> modes = new java.util.ArrayList<>();
        if (getConfig().allowWaterTrigger) {
            modes.add("water");
        }

        if (getConfig().allowRainTrigger) {
            modes.add("rain");
        }

        if (modes.isEmpty()) {
            return "none";
        }

        return String.join("/", modes);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking()) {
            return;
        }

        tryDash(p, TriggerType.SNEAK);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        tryDash(e.getPlayer(), TriggerType.ATTACK);
    }

    private void tryDash(Player p, TriggerType triggerType) {
        if (!hasAdaptation(p) || p.hasCooldown(Material.HEART_OF_THE_SEA)) {
            return;
        }

        if (triggerType == TriggerType.SNEAK && !getConfig().enableSneakTrigger) {
            return;
        }

        if (triggerType == TriggerType.ATTACK) {
            if (!getConfig().enableAttackTrigger) {
                return;
            }

            if (getConfig().attackTriggerRequiresSneak && !p.isSneaking()) {
                return;
            }

            if (getConfig().attackTriggerWaterOnly && !isInWaterDashState(p)) {
                return;
            }
        }

        if (!isDashEnvironmentValid(p)) {
            return;
        }

        int level = getLevel(p);
        Vector direction = resolveDashDirection(p);
        if (direction.lengthSquared() <= 0.000001) {
            return;
        }

        if (isBlockedAhead(p, direction)) {
            return;
        }

        boolean wasSwimming = p.isSwimming();
        org.bukkit.Location target = null;
        org.bukkit.Location origin = null;

        if (areParticlesEnabled()) {
            p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 20, 0.25, 0.35, 0.25, 0.08);
        }

        if (getConfig().useVelocityDash) {
            applyVelocityDash(p, direction, level);
            target = p.getLocation().clone().add(direction.clone().multiply(Math.max(0.35, getDashDistance(level) * 0.35)));
        } else {
            origin = p.getLocation().clone();
            target = findSafeDashTarget(p, getDashDistance(level));
            if (target == null) {
                return;
            }
            p.teleport(target);
            applyDashMomentum(p, origin, target);
        }

        preserveSwimStateAfterDash(p, wasSwimming);
        if (areParticlesEnabled()) {
            p.getWorld().spawnParticle(Particle.SPLASH, target.clone().add(0, 1, 0), 30, 0.35, 0.45, 0.35, 0.08);
        }
        if (areParticlesEnabled()) {
            p.getWorld().spawnParticle(Particle.BUBBLE, target.clone().add(0, 0.9, 0), 18, 0.4, 0.35, 0.4, 0.05);
        }
        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 0.75f, 1.2f);
        sp.play(target, Sound.ENTITY_DOLPHIN_SPLASH, 0.65f, 1.15f);
        p.setCooldown(Material.HEART_OF_THE_SEA, getCooldownTicks(level));
        xp(p, getConfig().xpPerBurst);
        getPlayer(p).getData().addStat("seaborne.tidecaller.dashes", 1);
    }

    private void applyVelocityDash(Player p, Vector direction, int level) {
        Vector velocity = direction.clone().normalize();
        double scalar = Math.max(0, getConfig().velocityStrengthBase + (getLevelPercent(level) * getConfig().velocityStrengthFactor));
        velocity.multiply(scalar);

        double y = getConfig().velocityVerticalBase + (getLevelPercent(level) * getConfig().velocityVerticalFactor);
        if (getConfig().velocityAdditive) {
            velocity = p.getVelocity().clone().add(velocity).add(new Vector(0, y, 0));
        } else {
            velocity.setY(y);
        }

        double max = Math.max(0, getConfig().maxResultingVelocity);
        if (max > 0 && velocity.lengthSquared() > max * max) {
            velocity = velocity.normalize().multiply(max);
        }

        p.setVelocity(velocity);
    }

    private void applyDashMomentum(Player p, org.bukkit.Location origin, org.bukkit.Location target) {
        if (!getConfig().applyForwardMomentumAfterDash) {
            return;
        }

        Vector momentum = target.toVector().subtract(origin.toVector());
        if (momentum.lengthSquared() <= 0.000001) {
            momentum = origin.getDirection().clone();
        }

        momentum.normalize().multiply(Math.max(0, getConfig().forwardMomentum));
        double y = p.getVelocity().getY();
        if (getConfig().replaceVerticalMomentum) {
            y = getConfig().verticalMomentum;
        } else {
            y += getConfig().verticalMomentum;
        }

        momentum.setY(y);
        p.setVelocity(momentum);
    }

    private Vector resolveDashDirection(Player p) {
        Vector direction = p.getLocation().getDirection().clone();
        if (getConfig().flattenVelocityDashDirection) {
            direction.setY(0);
        }

        if (direction.lengthSquared() <= 0.000001) {
            return new Vector();
        }

        return direction.normalize();
    }

    private boolean isBlockedAhead(Player p, Vector direction) {
        if (!getConfig().blockDashWhenWallAhead) {
            return false;
        }

        double distance = Math.max(0.1, getConfig().wallCheckDistance);
        var hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), direction, distance, FluidCollisionMode.NEVER, true);
        return hit != null && hit.getHitBlock() != null;
    }

    private void preserveSwimStateAfterDash(Player p, boolean wasSwimming) {
        if (!getConfig().preserveSwimmingAfterDash || !wasSwimming) {
            return;
        }

        if (!isInWaterDashState(p)) {
            return;
        }

        p.setSwimming(true);
        J.s(() -> {
            if (p.isOnline() && isInWaterDashState(p)) {
                p.setSwimming(true);
            }
        }, 1);
    }

    private boolean isDashEnvironmentValid(Player p) {
        if (getConfig().allowWaterTrigger && isInWaterDashState(p)) {
            return true;
        }

        return getConfig().allowRainTrigger && isRainingAt(p);
    }

    private boolean isInWaterDashState(Player p) {
        return p.isInWater() || p.isSwimming() || p.getLocation().getBlock().isLiquid() || p.getEyeLocation().getBlock().isLiquid();
    }

    private boolean isRainingAt(Player p) {
        if (!p.getWorld().hasStorm()) {
            return false;
        }

        int topY = p.getWorld().getHighestBlockYAt(p.getLocation());
        return p.getLocation().getY() >= topY - 1;
    }

    private org.bukkit.Location findSafeDashTarget(Player p, double maxDistance) {
        Vector direction = p.getLocation().getDirection().clone();
        if (direction.lengthSquared() <= 0.0001) {
            return null;
        }

        direction.normalize();
        for (double d = maxDistance; d >= 1.0; d -= 0.5) {
            org.bukkit.Location c = p.getLocation().clone().add(direction.clone().multiply(d));
            if (isSafe(c)) {
                return c;
            }
        }

        return null;
    }

    private boolean isSafe(org.bukkit.Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        Block floor = location.clone().subtract(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable() && (floor.getType().isSolid() || floor.isLiquid());
    }

    private double getDashDistance(int level) {
        return getConfig().dashDistanceBase + (getLevelPercent(level) * getConfig().dashDistanceFactor);
    }

    private int getCooldownTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
    }

    private enum TriggerType {
        SNEAK,
        ATTACK
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
    @ConfigDescription("Sneak while it is raining to dash like a water blink through the storm.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dash Distance Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dashDistanceBase = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Dash Distance Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dashDistanceFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 140;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 80;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Burst for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerBurst = 11;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows the original rain-based trigger for Tidecaller dashes.", impact = "Disable this to make dashes water-only.")
        boolean allowRainTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows Tidecaller dashes while the player is in water.", impact = "Enable this to make sneak/attack water woosh work consistently.")
        boolean allowWaterTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables sneak-to-dash trigger.", impact = "Disable this if you only want attack-based trigger behavior.")
        boolean enableSneakTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables attack-swing trigger (any item or empty hand).", impact = "Enable this for left-click water flings without requiring special items.")
        boolean enableAttackTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Requires sneaking for attack-swing trigger.", impact = "True makes attack trigger only fire while sneaking; false allows it anytime in valid dash environments.")
        boolean attackTriggerRequiresSneak = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Restricts attack-swing trigger to water states only.", impact = "True prevents accidental attack-trigger dashes on land even if rain trigger is enabled.")
        boolean attackTriggerWaterOnly = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Uses velocity-based dash movement instead of teleporting to a target.", impact = "True makes tidecaller behave like a movement burst and prevents blink-style wall bypass.")
        boolean useVelocityDash = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "If true, removes pitch from velocity dash direction.", impact = "True keeps movement mostly horizontal; false follows exact look direction including up/down.")
        boolean flattenVelocityDashDirection = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base forward velocity strength of a velocity dash.", impact = "Higher values produce faster bursts.")
        double velocityStrengthBase = 1.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Additional forward velocity strength gained by adaptation level.", impact = "Higher values make higher levels burst farther/faster.")
        double velocityStrengthFactor = 0.85;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base vertical velocity contribution for velocity dashes.", impact = "Small positive values keep water movement fluid; lower values stay flatter.")
        double velocityVerticalBase = 0.01;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Additional vertical velocity contribution gained by adaptation level.", impact = "Higher values increase upward kick at higher levels.")
        double velocityVerticalFactor = 0.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Adds dash velocity on top of current velocity when true.", impact = "True preserves momentum chains; false applies a fresh velocity vector.")
        boolean velocityAdditive = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Hard cap on resulting velocity magnitude after dash.", impact = "Lower values are safer for anticheat and collisions.")
        double maxResultingVelocity = 2.25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Cancels dash if a solid block is detected directly ahead.", impact = "Prevents wall clipping and blink-like wall bypass.")
        boolean blockDashWhenWallAhead = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Distance ahead used to detect blocking walls.", impact = "Higher values are safer but can block dashes near tight spaces.")
        double wallCheckDistance = 1.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Applies forward velocity after the dash teleport.", impact = "True makes the dash feel fluid instead of instantly stopping at the target.")
        boolean applyForwardMomentumAfterDash = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Horizontal forward momentum applied after the dash teleport.", impact = "Higher values create a stronger forward burst after each dash.")
        double forwardMomentum = 1.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Vertical momentum added or set after the dash teleport.", impact = "Small positive values help keep water movement smooth; negative values push downward.")
        double verticalMomentum = 0.02;
        @com.volmit.adapt.util.config.ConfigDoc(value = "If true, replaces current vertical velocity with verticalMomentum.", impact = "False adds verticalMomentum on top of existing vertical motion.")
        boolean replaceVerticalMomentum = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Re-applies swimming pose after dash when the player started swimming and remains in water.", impact = "Prevents dash teleports from popping swimmers out of swim posture.")
        boolean preserveSwimmingAfterDash = true;
    }
}
