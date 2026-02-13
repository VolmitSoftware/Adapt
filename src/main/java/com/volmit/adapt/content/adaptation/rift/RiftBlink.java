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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.event.AdaptAdaptationTeleportEvent;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.volmit.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;


public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> {
    private final Map<UUID, Long> lastBlink = new HashMap<>();
    private final Map<UUID, Long> jumpArmUntil = new HashMap<>();
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();

    public RiftBlink() {
        super("rift-blink");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift.blink.description"));
        setDisplayName(Localizer.dLocalize("rift.blink.name"));
        setIcon(Material.FEATHER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9288);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENDER_PEARL)
                .key("challenge_rift_blink_500")
                .title(Localizer.dLocalize("advancement.challenge_rift_blink_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_rift_blink_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENDER_EYE)
                        .key("challenge_rift_blink_5k")
                        .title(Localizer.dLocalize("advancement.challenge_rift_blink_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_rift_blink_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_rift_blink_500", "rift.blink.blinks", 500, 400);
        registerMilestone("challenge_rift_blink_5k", "rift.blink.distance-blinked", 5000, 1500);
    }

    private double getBlinkDistance(int level) {
        return getConfig().baseDistance + (getLevelPercent(level) * getConfig().distanceFactor);
    }

    private long getCooldownDuration() {
        return Math.max(0L, getConfig().cooldownMillis);
    }

    private boolean isBlinkEligible(Player p) {
        return hasAdaptation(p) && p.getGameMode() == GameMode.SURVIVAL;
    }

    private boolean isOnCooldown(UUID id) {
        return M.ms() - lastBlink.getOrDefault(id, 0L) <= getCooldownDuration();
    }

    private void clearDoubleJumpArm(Player p, UUID id) {
        if (jumpArmUntil.remove(id) == null) {
            return;
        }

        if (p.getGameMode() == GameMode.SURVIVAL) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
    }

    private void armDoubleJump(Player p, UUID id) {
        int triggerWindowMillis = Math.max(150, getConfig().doubleJumpWindowMillis);
        long expires = M.ms() + triggerWindowMillis;
        jumpArmUntil.put(id, expires);
        p.setAllowFlight(true);
        J.s(() -> {
            if (!p.isOnline()) {
                return;
            }

            Long armUntil = jumpArmUntil.get(id);
            if (armUntil != null && armUntil <= M.ms()) {
                clearDoubleJumpArm(p, id);
            }
        }, Math.max(1, (int) Math.ceil(triggerWindowMillis / 50D)));
    }

    private boolean isClickAction(Action action) {
        return action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isLeftClick(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isBlockClick(Action action) {
        return action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isActionAllowed(Action action) {
        if (!getConfig().allowAirClicks && !isBlockClick(action)) {
            return false;
        }

        if (!getConfig().allowBlockClicks && isBlockClick(action)) {
            return false;
        }

        return true;
    }

    private boolean shouldTriggerSprintClick(PlayerInteractEvent e) {
        if (!getConfig().enableSprintClickTrigger || !e.getPlayer().isSprinting()) {
            return false;
        }

        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
            return false;
        }

        Action action = e.getAction();
        if (!isClickAction(action) || !isActionAllowed(action)) {
            return false;
        }

        if (isLeftClick(action) && !getConfig().sprintClickLeftClick) {
            return false;
        }

        return !isRightClick(action) || getConfig().sprintClickRightClick;
    }

    private boolean shouldTriggerPearlClick(PlayerInteractEvent e) {
        if (!getConfig().enableEnderPearlClickTrigger || e.getItem() == null || e.getItem().getType() != Material.ENDER_PEARL) {
            return false;
        }

        Action action = e.getAction();
        if (!isClickAction(action) || !isActionAllowed(action)) {
            return false;
        }

        if (isLeftClick(action) && !getConfig().enderPearlClickLeftClick) {
            return false;
        }

        return !isRightClick(action) || getConfig().enderPearlClickRightClick;
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
        v.addLore(C.GREEN + "+ " + (getBlinkDistance(level)) + C.GRAY + " " + Localizer.dLocalize("rift.blink.lore1"));
        java.util.List<String> combos = getTriggerCombos();
        if (combos.isEmpty()) {
            v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + "none");
            return;
        }

        for (String combo : combos) {
            v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + combo);
        }
    }

    @Override
    public String getDescription() {
        return "Short-ranged instant teleportation to safe ground. " + summarizeTriggerDescription();
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
        String clickSurface = getClickSurfaceLabel();
        if (getConfig().enableDoubleJumpTrigger) {
            triggers.add(getConfig().doubleJumpRequiresSprint ? "Double Jump + Sprint" : "Double Jump");
        }

        if (getConfig().enableSprintClickTrigger) {
            appendClickCombos(triggers, "Sprint", getConfig().sprintClickLeftClick, getConfig().sprintClickRightClick, clickSurface);
        }

        if (getConfig().enableSingleSneakTrigger) {
            triggers.add(getConfig().singleSneakRequiresSprint ? "Sprint + Sneak" : "Sneak");
        }

        if (getConfig().enableEnderPearlClickTrigger) {
            appendClickCombos(triggers, "Ender Pearl", getConfig().enderPearlClickLeftClick, getConfig().enderPearlClickRightClick, clickSurface);
        }

        return triggers;
    }

    private void appendClickCombos(java.util.List<String> triggers, String prefix, boolean allowLeft, boolean allowRight, String clickSurface) {
        if (clickSurface.isBlank()) {
            return;
        }

        if (allowLeft) {
            triggers.add(prefix + " + Left Click" + clickSurface);
        }

        if (allowRight) {
            triggers.add(prefix + " + Right Click" + clickSurface);
        }
    }

    private String getClickSurfaceLabel() {
        if (getConfig().allowAirClicks && getConfig().allowBlockClicks) {
            return " (air/block)";
        }

        if (getConfig().allowAirClicks) {
            return " (air)";
        }

        if (getConfig().allowBlockClicks) {
            return " (block)";
        }

        return "";
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastBlink.remove(id);
        jumpArmUntil.remove(id);
        lastOnGround.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!isBlinkEligible(p) || !getConfig().enableDoubleJumpTrigger) {
            return;
        }

        Long armUntil = jumpArmUntil.get(id);
        if (armUntil == null) {
            return;
        }

        e.setCancelled(true);
        p.setFlying(false);
        clearDoubleJumpArm(p, id);
        if (armUntil > M.ms()) {
            attemptBlink(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isBlinkEligible(p)) {
            return;
        }

        if (shouldTriggerPearlClick(e)) {
            e.setCancelled(true);
            attemptBlink(p);
            return;
        }

        if (shouldTriggerSprintClick(e)) {
            attemptBlink(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking() || !isBlinkEligible(p) || !getConfig().enableSingleSneakTrigger) {
            return;
        }

        if (getConfig().singleSneakRequiresSprint && !p.isSprinting()) {
            return;
        }

        attemptBlink(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean wasOnGround = lastOnGround.getOrDefault(id, true);
        boolean onGround = p.isOnGround();
        lastOnGround.put(id, onGround);

        if (!isBlinkEligible(p) || !getConfig().enableDoubleJumpTrigger) {
            clearDoubleJumpArm(p, id);
            return;
        }

        if (!wasOnGround && onGround) {
            clearDoubleJumpArm(p, id);
            return;
        }

        if (isOnCooldown(id)) {
            clearDoubleJumpArm(p, id);
            return;
        }

        if (isDoubleJumpStart(wasOnGround, onGround, p)) {
            if (getConfig().doubleJumpRequiresSprint && !p.isSprinting()) {
                return;
            }

            armDoubleJump(p, id);
            return;
        }

        Long armUntil = jumpArmUntil.get(id);
        if (armUntil != null && armUntil <= M.ms()) {
            clearDoubleJumpArm(p, id);
        }
    }

    private boolean isDoubleJumpStart(boolean wasOnGround, boolean onGround, Player p) {
        return wasOnGround
                && !onGround
                && p.getVelocity().getY() >= getConfig().doubleJumpMinVerticalVelocity;
    }

    private boolean attemptBlink(Player p) {
        UUID id = p.getUniqueId();
        if (isOnCooldown(id)) {
            return false;
        }

        Location locOG = p.getLocation().clone();
        SoundPlayer spw = SoundPlayer.of(p);
        Location destinationGround = findBlinkGround(p);
        if (destinationGround == null) {
            spw.play(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1f, 1.24f);
            lastBlink.put(id, M.ms());
            return false;
        }

        PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("rift");
        PlayerAdaptation adaptation = line != null ? line.getAdaptation("rift-resist") : null;
        if (adaptation != null && adaptation.getLevel() > 0) {
            RiftResist.riftResistStackAdd(p, 10, 5);
        }

        if (areParticlesEnabled()) {
            vfxParticleLine(locOG, destinationGround, Particle.REVERSE_PORTAL, 50, 8, 0.1D, 1D, 0.1D, 0D, null, false, l -> l.getBlock().isPassable());
        }

        Vector v = p.getVelocity().clone();
        loadChunkAsync(destinationGround, chunk -> J.s(() -> {
            Location toLoc = destinationGround.clone().add(0, 1, 0);

            AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(false, getPlayer(p), this, locOG, destinationGround.clone());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }

            p.teleport(toLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
            p.setVelocity(v.multiply(3));
        }));

        getPlayer(p).getData().addStat("rift.teleports", 1);
        getPlayer(p).getData().addStat("rift.blink.blinks", 1);
        getPlayer(p).getData().addStat("rift.blink.distance-blinked", (int) locOG.distance(destinationGround));
        lastBlink.put(id, M.ms());
        spw.play(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.50f, 1.0f);
        vfxLevelUp(p);
        return true;
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
    @ConfigDescription("Short-ranged instant teleportation by double-tapping jump while sprinting.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Rift Blink adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Cooldown between successful Rift Blink triggers in milliseconds.", impact = "Higher values reduce blink frequency; lower values allow faster reuse.")
        int cooldownMillis = 2000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables double-tap jump detection for Rift Blink.", impact = "True allows jump-based activation; false disables jump activation.")
        boolean enableDoubleJumpTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Require sprinting for the double-tap jump trigger.", impact = "True requires sprinting while double-tapping jump; false allows it without sprint.")
        boolean doubleJumpRequiresSprint = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum time window between jump taps in milliseconds.", impact = "Higher values make double-tap detection easier; lower values make it stricter.")
        int doubleJumpWindowMillis = 450;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Minimum upward velocity required to arm double-jump blink.", impact = "Higher values reduce accidental arming; lower values make detection more sensitive.")
        double doubleJumpMinVerticalVelocity = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables sprint + click activation for Rift Blink.", impact = "True allows clicking while sprinting to blink; false disables this trigger.")
        boolean enableSprintClickTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables single-sneak activation for Rift Blink.", impact = "True allows pressing sneak once to trigger blink.")
        boolean enableSingleSneakTrigger = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Require sprinting for single-sneak trigger.", impact = "True requires sprint state when using single-sneak activation.")
        boolean singleSneakRequiresSprint = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows left-click as a sprint-click trigger.", impact = "True allows left-click activation while sprinting; false disables left-click activation.")
        boolean sprintClickLeftClick = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows right-click as a sprint-click trigger.", impact = "True allows right-click activation while sprinting; false disables right-click activation.")
        boolean sprintClickRightClick = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables click-with-ender-pearl activation for Rift Blink.", impact = "True allows pearl-click activation; false disables pearl-click activation.")
        boolean enableEnderPearlClickTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows left-click with an ender pearl to trigger Rift Blink.", impact = "True enables left-click pearl activation; false disables it.")
        boolean enderPearlClickLeftClick = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows right-click with an ender pearl to trigger Rift Blink.", impact = "True enables right-click pearl activation; false disables it.")
        boolean enderPearlClickRightClick = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows air-click interactions to trigger Rift Blink.", impact = "True lets air clicks trigger enabled click modes; false blocks air-click triggers.")
        boolean allowAirClicks = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows block-click interactions to trigger Rift Blink.", impact = "True lets block clicks trigger enabled click modes; false blocks block-click triggers.")
        boolean allowBlockClicks = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Distance for the Rift Blink adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseDistance = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Distance Factor for the Rift Blink adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double distanceFactor = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Vertical Adjustment for the Rift Blink adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int maxVerticalAdjustment = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Distance Search Step for the Rift Blink adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double distanceSearchStep = 0.5;
    }
}
