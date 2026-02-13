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

package com.volmit.adapt.content.adaptation.chronos;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.item.ChronoTimeBombItem;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ChronosInstantRecall extends SimpleAdaptation<ChronosInstantRecall.Config> {
    private static final EnumSet<Action> RECALL_ACTIONS = EnumSet.of(
            Action.RIGHT_CLICK_AIR,
            Action.RIGHT_CLICK_BLOCK,
            Action.LEFT_CLICK_AIR,
            Action.LEFT_CLICK_BLOCK
    );
    private static final Map<UUID, Long> TELEPORT_XP_SUPPRESS_UNTIL = new HashMap<>();

    private final Map<UUID, Deque<Snapshot>> snapshots;
    private final Map<UUID, Long> lastSnapshot;
    private final Map<UUID, Long> cooldowns;
    private final Set<UUID> cooldownReadyNotify;
    private final Map<UUID, Long> rewindProtection;
    private final Set<UUID> rewinding;
    private final Map<UUID, RecallXPFarmStamp> recallXpStamps;
    private final Map<UUID, Long> jumpArmUntil;
    private final Map<UUID, Boolean> lastOnGround;

    public ChronosInstantRecall() {
        super("chronos-instant-recall");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("chronos.instant_recall.description"));
        setDisplayName(Localizer.dLocalize("chronos.instant_recall.name"));
        setIcon(Material.RECOVERY_COMPASS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(50);
        snapshots = new HashMap<>();
        lastSnapshot = new HashMap<>();
        cooldowns = new HashMap<>();
        cooldownReadyNotify = new HashSet<>();
        rewindProtection = new HashMap<>();
        rewinding = new HashSet<>();
        recallXpStamps = new HashMap<>();
        jumpArmUntil = new HashMap<>();
        lastOnGround = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CLOCK)
                .key("challenge_chronos_recall_50")
                .title(Localizer.dLocalize("advancement.challenge_chronos_recall_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_recall_50.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.RECOVERY_COMPASS)
                        .key("challenge_chronos_recall_1k")
                        .title(Localizer.dLocalize("advancement.challenge_chronos_recall_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_chronos_recall_1k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.RECOVERY_COMPASS)
                .key("challenge_chronos_recall_cheat_death")
                .title(Localizer.dLocalize("advancement.challenge_chronos_recall_cheat_death.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_recall_cheat_death.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_chronos_recall_50", "chronos.instant-recall.recalls", 50, 300);
        registerMilestone("challenge_chronos_recall_1k", "chronos.instant-recall.recalls", 1000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration(getRewindDurationMillis(level), 1) + " " + Localizer.dLocalize("chronos.instant_recall.lore1"));
        v.addLore(C.RED + "* " + Form.duration(getCooldownMillis(level), 1) + " " + Localizer.dLocalize("chronos.instant_recall.lore2"));
        v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.instant_recall.lore3"));
        List<String> combos = getTriggerCombos();
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
        return "Rewind to a recent snapshot with health and hunger restored. " + summarizeTriggerDescription();
    }

    private String summarizeTriggerDescription() {
        List<String> combos = getTriggerCombos();
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

    private long getRewindDurationMillis(int level) {
        return (long) (getRewindDurationSeconds(level) * 1000D);
    }

    private double getRewindDurationSeconds(int level) {
        double raw = getConfig().baseRewindSeconds + (level * getConfig().rewindSecondsPerLevel);
        return Math.max(0.25D, Math.min(getConfig().maxRewindSeconds, raw));
    }

    private long getCooldownMillis(int level) {
        return getRewindDurationMillis(level) + (getConfig().cooldownPaddingSeconds * 1000L);
    }

    private long getMaximumHistoryMillis() {
        return (long) ((getRewindDurationSeconds(getConfig().maxLevel) + getConfig().historyPaddingSeconds) * 1000D);
    }

    private int getRewindAnimationTicks() {
        int durationMillis = Math.max(100, getConfig().rewindAnimationDurationMillis);
        int ticks = (int) Math.ceil(durationMillis / 50D);
        if (ticks <= 1) {
            ticks = Math.max(2, getConfig().rewindAnimationTicks);
        }

        return Math.max(2, ticks);
    }

    private boolean isSingleWorldPath(List<Snapshot> path) {
        if (path.isEmpty()) {
            return false;
        }

        String world = path.get(0).worldName();
        for (Snapshot snapshot : path) {
            if (!Objects.equals(world, snapshot.worldName())) {
                return false;
            }
        }

        return true;
    }

    private ArmorStand spawnRecallCameraAnchor(Player p) {
        Location location = p.getLocation().clone();
        if (location.getWorld() == null) {
            return null;
        }

        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(false);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setBasePlate(false);
            stand.setSmall(true);
            stand.setPersistent(false);
        });
    }

    private List<String> getTriggerCombos() {
        List<String> triggers = new ArrayList<>();
        String clickSurface = getClickSurfaceLabel();
        if (getConfig().enableClockClickTrigger) {
            appendClickCombos(triggers, "Clock", getConfig().clockClickLeftClick, getConfig().clockClickRightClick, clickSurface);
        }

        if (getConfig().enableSprintClickTrigger) {
            appendClickCombos(triggers, "Sprint + Clock", getConfig().sprintClickLeftClick, getConfig().sprintClickRightClick, clickSurface);
        }

        if (getConfig().enableSingleSneakTrigger) {
            String combo = getConfig().singleSneakRequiresSprint ? "Sprint + Sneak" : "Sneak";
            if (getConfig().singleSneakRequiresClockInHand) {
                combo += " + Clock";
            }
            triggers.add(combo);
        }

        if (getConfig().enableDoubleJumpTrigger) {
            String combo = "Double Jump";
            if (getConfig().doubleJumpRequiresSprint) {
                combo += " + Sprint";
            }
            if (getConfig().doubleJumpRequiresClockInHand) {
                combo += " + Clock";
            }
            triggers.add(combo);
        }

        return triggers;
    }

    private void appendClickCombos(List<String> triggers, String prefix, boolean allowLeft, boolean allowRight, String clickSurface) {
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

    private RecallXPContext buildRecallXPContext(Snapshot from, Snapshot to) {
        double distance;
        if (Objects.equals(from.worldName(), to.worldName())) {
            double dx = from.x() - to.x();
            double dy = from.y() - to.y();
            double dz = from.z() - to.z();
            distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        } else {
            distance = getConfig().xpCrossWorldDistanceCredit;
        }

        double healthRecovered = Math.max(0D, to.health() - from.health());
        double hungerRecovered = Math.max(0D, to.foodLevel() - from.foodLevel());
        double saturationRecovered = Math.max(0D, to.saturation() - from.saturation());

        return new RecallXPContext(
                from.worldName(),
                from.x(),
                from.y(),
                from.z(),
                to.worldName(),
                to.x(),
                to.y(),
                to.z(),
                distance,
                healthRecovered,
                hungerRecovered,
                saturationRecovered);
    }

    private boolean pointsAreSimilar(String worldA, double ax, double ay, double az, String worldB, double bx, double by, double bz, double radius) {
        if (!Objects.equals(worldA, worldB)) {
            return false;
        }

        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return (dx * dx) + (dy * dy) + (dz * dz) <= (radius * radius);
    }

    private boolean isRepeatRecall(RecallXPFarmStamp stamp, RecallXPContext context) {
        return pointsAreSimilar(stamp.fromWorld(), stamp.fromX(), stamp.fromY(), stamp.fromZ(),
                context.fromWorld(), context.fromX(), context.fromY(), context.fromZ(),
                getConfig().xpRepeatSourceRadius)
                && pointsAreSimilar(stamp.toWorld(), stamp.toX(), stamp.toY(), stamp.toZ(),
                context.toWorld(), context.toX(), context.toY(), context.toZ(),
                getConfig().xpRepeatTargetRadius);
    }

    private double computeRecallXPGain(UUID playerId, int level, RecallXPContext context, long now) {
        double raw = (context.distance() * getConfig().xpPerDistanceBlock)
                + (context.healthRecovered() * getConfig().xpPerHealthPoint)
                + (context.hungerRecovered() * getConfig().xpPerHungerPoint)
                + (context.saturationRecovered() * getConfig().xpPerSaturationPoint);

        if (raw < getConfig().xpMinRawReward) {
            return 0D;
        }

        double leveled = raw * (1D + ((Math.max(1, level) - 1) * getConfig().xpLevelMultiplierPerLevel));
        double multiplier = 1D;

        RecallXPFarmStamp previous = recallXpStamps.get(playerId);
        if (previous != null) {
            long elapsed = now - previous.awardedAt();
            if (elapsed < getConfig().xpDiminishWindowMillis) {
                double t = Math.max(0D, Math.min(1D, elapsed / (double) Math.max(1L, getConfig().xpDiminishWindowMillis)));
                multiplier *= getConfig().xpDiminishMinMultiplier + ((1D - getConfig().xpDiminishMinMultiplier) * t);
            }

            if (elapsed < getConfig().xpRepeatWindowMillis && isRepeatRecall(previous, context)) {
                multiplier *= getConfig().xpRepeatPenaltyMultiplier;
            }
        }

        double reward = Math.min(getConfig().xpMaxAward, leveled * multiplier);
        if (reward < getConfig().xpMinAward) {
            return 0D;
        }

        return reward;
    }

    private Snapshot snapshotFromPlayer(Player p, long now) {
        return new Snapshot(now,
                p.getWorld().getName(),
                p.getLocation().getX(),
                p.getLocation().getY(),
                p.getLocation().getZ(),
                p.getLocation().getYaw(),
                p.getLocation().getPitch(),
                p.getHealth(),
                p.getFoodLevel(),
                p.getSaturation(),
                p.getExhaustion(),
                p.getFireTicks());
    }

    private void captureSnapshot(Player p) {
        long now = M.ms();
        UUID id = p.getUniqueId();
        long last = lastSnapshot.getOrDefault(id, 0L);
        if (now - last < getConfig().snapshotIntervalMillis) {
            return;
        }

        lastSnapshot.put(id, now);
        Deque<Snapshot> queue = snapshots.computeIfAbsent(id, k -> new ArrayDeque<>());
        queue.addLast(snapshotFromPlayer(p, now));

        long maxAge = getMaximumHistoryMillis();
        while (!queue.isEmpty() && now - queue.getFirst().timestamp() > maxAge) {
            queue.removeFirst();
        }
    }

    private Snapshot findSnapshot(Player p, long rewindMillis) {
        Deque<Snapshot> queue = snapshots.get(p.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        long target = M.ms() - rewindMillis;
        Snapshot fallback = queue.getFirst();

        for (Snapshot s : queue) {
            if (s.timestamp() <= target) {
                fallback = s;
            } else {
                break;
            }
        }

        return fallback;
    }

    private List<Snapshot> buildRewindPath(Player p, long rewindMillis, Snapshot anchor) {
        List<Snapshot> path = new ArrayList<>();
        long now = M.ms();
        path.add(snapshotFromPlayer(p, now));

        Deque<Snapshot> queue = snapshots.get(p.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            path.add(anchor);
            return path;
        }

        Iterator<Snapshot> reverse = queue.descendingIterator();
        while (reverse.hasNext()) {
            Snapshot snap = reverse.next();
            if (snap.timestamp() < anchor.timestamp()) {
                break;
            }
            if (snap.timestamp() <= now) {
                path.add(snap);
            }
        }

        Snapshot last = path.get(path.size() - 1);
        if (last.timestamp() != anchor.timestamp()) {
            path.add(anchor);
        }

        if (path.size() < 2) {
            path.add(anchor);
        }

        return path;
    }

    private List<Snapshot> buildAnimationPath(List<Snapshot> rawPath, int animationTicks) {
        List<Snapshot> animationPath = new ArrayList<>();
        if (rawPath.isEmpty()) {
            return animationPath;
        }

        if (animationTicks <= 1 || rawPath.size() == 1) {
            animationPath.add(rawPath.get(rawPath.size() - 1));
            return animationPath;
        }

        for (int step = 0; step < animationTicks; step++) {
            double progress = step / (double) (animationTicks - 1);
            double scaled = progress * (rawPath.size() - 1);
            int lower = (int) Math.floor(scaled);
            int upper = Math.min(rawPath.size() - 1, lower + 1);
            double alpha = scaled - lower;
            Snapshot a = rawPath.get(lower);
            Snapshot b = rawPath.get(upper);
            animationPath.add(interpolateSnapshot(a, b, alpha));
        }

        Snapshot anchor = rawPath.get(rawPath.size() - 1);
        animationPath.set(animationPath.size() - 1, anchor);
        return animationPath;
    }

    private Snapshot interpolateSnapshot(Snapshot a, Snapshot b, double alpha) {
        if (alpha <= 0D) {
            return a;
        }
        if (alpha >= 1D) {
            return b;
        }

        long timestamp = (long) Math.round(lerp(a.timestamp(), b.timestamp(), alpha));
        String worldName = alpha < 0.5D ? a.worldName() : b.worldName();
        double x = lerp(a.x(), b.x(), alpha);
        double y = lerp(a.y(), b.y(), alpha);
        double z = lerp(a.z(), b.z(), alpha);
        float yaw = lerpAngle(a.yaw(), b.yaw(), alpha);
        float pitch = (float) lerp(a.pitch(), b.pitch(), alpha);
        double health = lerp(a.health(), b.health(), alpha);
        int foodLevel = (int) Math.round(lerp(a.foodLevel(), b.foodLevel(), alpha));
        float saturation = (float) lerp(a.saturation(), b.saturation(), alpha);
        float exhaustion = (float) lerp(a.exhaustion(), b.exhaustion(), alpha);
        int fireTicks = (int) Math.round(lerp(a.fireTicks(), b.fireTicks(), alpha));

        return new Snapshot(timestamp, worldName, x, y, z, yaw, pitch, health, foodLevel, saturation, exhaustion, fireTicks);
    }

    private double lerp(double a, double b, double alpha) {
        return a + ((b - a) * alpha);
    }

    private float lerpAngle(float from, float to, double alpha) {
        float delta = to - from;
        while (delta > 180F) {
            delta -= 360F;
        }
        while (delta < -180F) {
            delta += 360F;
        }

        return from + (float) (delta * alpha);
    }

    private Location toLocation(Snapshot snapshot, World fallback) {
        World world = Bukkit.getWorld(snapshot.worldName());
        if (world == null) {
            world = fallback;
        }
        return new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
    }

    private void applySnapshotState(Player p, Snapshot snapshot) {
        double maxHealth = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20D : p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(1, Math.min(maxHealth, snapshot.health())));
        p.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel())));
        p.setSaturation(Math.max(0, snapshot.saturation()));
        p.setExhaustion(Math.max(0, snapshot.exhaustion()));
        p.setFireTicks(Math.max(0, snapshot.fireTicks()));
        p.setFallDistance(0);
        p.setVelocity(new Vector());
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        clearPlayerState(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isRecallEligible(p)) {
            return;
        }

        if (shouldTriggerSprintClockClick(e)) {
            e.setCancelled(true);
            attemptRecall(p);
            return;
        }

        if (shouldTriggerClockClick(e)) {
            e.setCancelled(true);
            attemptRecall(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!isRecallEligible(p) || !getConfig().enableDoubleJumpTrigger) {
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
            attemptRecall(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking() || !isRecallEligible(p) || !getConfig().enableSingleSneakTrigger) {
            return;
        }

        if (getConfig().singleSneakRequiresSprint && !p.isSprinting()) {
            return;
        }

        if (getConfig().singleSneakRequiresClockInHand && !hasRecallClockInEitherHand(p)) {
            return;
        }

        attemptRecall(p);
    }

    private EquipmentSlot resolveRecallHand(Player p, EquipmentSlot eventHand) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();

        if (eventHand == null) {
            if (isRecallClock(main)) {
                return EquipmentSlot.HAND;
            }

            if (isRecallClock(off)) {
                return EquipmentSlot.OFF_HAND;
            }

            return null;
        }

        if (eventHand == EquipmentSlot.HAND) {
            return isRecallClock(main) ? EquipmentSlot.HAND : null;
        }

        if (eventHand == EquipmentSlot.OFF_HAND) {
            if (isRecallClock(main)) {
                return null;
            }

            return isRecallClock(off) ? EquipmentSlot.OFF_HAND : null;
        }

        return null;
    }

    private boolean isRecallEligible(Player p) {
        return hasAdaptation(p) && p.getGameMode() == GameMode.SURVIVAL;
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
        if (!RECALL_ACTIONS.contains(action)) {
            return false;
        }

        if (!getConfig().allowAirClicks && !isBlockClick(action)) {
            return false;
        }

        if (!getConfig().allowBlockClicks && isBlockClick(action)) {
            return false;
        }

        return true;
    }

    private boolean shouldTriggerClockClick(PlayerInteractEvent e) {
        if (!getConfig().enableClockClickTrigger) {
            return false;
        }

        Action action = e.getAction();
        if (!isActionAllowed(action)) {
            return false;
        }

        if (isLeftClick(action) && !getConfig().clockClickLeftClick) {
            return false;
        }

        if (isRightClick(action) && !getConfig().clockClickRightClick) {
            return false;
        }

        return resolveRecallHand(e.getPlayer(), e.getHand()) != null;
    }

    private boolean shouldTriggerSprintClockClick(PlayerInteractEvent e) {
        if (!getConfig().enableSprintClickTrigger || !e.getPlayer().isSprinting()) {
            return false;
        }

        Action action = e.getAction();
        if (!isActionAllowed(action)) {
            return false;
        }

        if (isLeftClick(action) && !getConfig().sprintClickLeftClick) {
            return false;
        }

        if (isRightClick(action) && !getConfig().sprintClickRightClick) {
            return false;
        }

        return resolveRecallHand(e.getPlayer(), e.getHand()) != null;
    }

    private boolean hasRecallClockInEitherHand(Player p) {
        return isRecallClock(p.getInventory().getItemInMainHand())
                || isRecallClock(p.getInventory().getItemInOffHand());
    }

    private boolean canArmDoubleJump(Player p) {
        if (rewinding.contains(p.getUniqueId())) {
            return false;
        }

        if (getConfig().doubleJumpRequiresSprint && !p.isSprinting()) {
            return false;
        }

        return !getConfig().doubleJumpRequiresClockInHand || hasRecallClockInEitherHand(p);
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
        jumpArmUntil.put(id, M.ms() + triggerWindowMillis);
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

    private boolean isDoubleJumpStart(boolean wasOnGround, boolean onGround, Player p) {
        return wasOnGround
                && !onGround
                && p.getVelocity().getY() >= getConfig().doubleJumpMinVerticalVelocity;
    }

    private void clearPlayerState(UUID id) {
        snapshots.remove(id);
        lastSnapshot.remove(id);
        cooldowns.remove(id);
        cooldownReadyNotify.remove(id);
        rewindProtection.remove(id);
        rewinding.remove(id);
        recallXpStamps.remove(id);
        jumpArmUntil.remove(id);
        lastOnGround.remove(id);
        TELEPORT_XP_SUPPRESS_UNTIL.remove(id);
    }

    private static void markRecallTeleportSuppressed(UUID id, long suppressUntilMillis) {
        long current = TELEPORT_XP_SUPPRESS_UNTIL.getOrDefault(id, 0L);
        if (suppressUntilMillis > current) {
            TELEPORT_XP_SUPPRESS_UNTIL.put(id, suppressUntilMillis);
        }
    }

    public static boolean isRecallTeleportSuppressed(Player p) {
        if (p == null) {
            return false;
        }

        UUID id = p.getUniqueId();
        long until = TELEPORT_XP_SUPPRESS_UNTIL.getOrDefault(id, 0L);
        if (until <= M.ms()) {
            TELEPORT_XP_SUPPRESS_UNTIL.remove(id);
            return false;
        }

        return true;
    }

    private boolean isRecallClock(ItemStack stack) {
        return stack != null
                && stack.getType() == Material.CLOCK
                && !ChronoTimeBombItem.isBindableItem(stack);
    }

    private void attemptRecall(Player p) {
        UUID id = p.getUniqueId();
        if (!isRecallEligible(p)) {
            return;
        }

        clearDoubleJumpArm(p, id);
        if (rewinding.contains(id)) {
            return;
        }

        long now = M.ms();
        long cooldown = cooldowns.getOrDefault(id, 0L);
        if (cooldown > now) {
            if (getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(p);
            }
            return;
        }

        int level = getLevel(p);
        long rewindMillis = getRewindDurationMillis(level);
        Snapshot anchor = findSnapshot(p, rewindMillis);
        if (anchor == null) {
            if (getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(p);
            }
            return;
        }

        int animationTicks = getRewindAnimationTicks();
        List<Snapshot> path = buildRewindPath(p, rewindMillis, anchor);
        List<Snapshot> animationPath = buildAnimationPath(path, animationTicks);
        if (animationPath.isEmpty()) {
            if (getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(p);
            }
            return;
        }

        Snapshot finalSnapshot = animationPath.get(animationPath.size() - 1);
        RecallXPContext xpContext = buildRecallXPContext(animationPath.get(0), finalSnapshot);
        double healthBeforeRecall = p.getHealth();
        double healthAfterRecall = finalSnapshot.health();
        long castAt = M.ms();
        GameMode originalGameMode = p.getGameMode();
        boolean temporarySpectator = getConfig().rewindUseTemporarySpectator && originalGameMode == GameMode.SURVIVAL;
        boolean allowClientCamera = temporarySpectator
                && getConfig().rewindUseClientCamera
                && isSingleWorldPath(animationPath);
        ArmorStand cameraAnchor = null;
        if (temporarySpectator) {
            p.setGameMode(GameMode.SPECTATOR);
            p.setFlying(true);
            if (allowClientCamera) {
                cameraAnchor = spawnRecallCameraAnchor(p);
                if (cameraAnchor != null && cameraAnchor.isValid()) {
                    p.setSpectatorTarget(cameraAnchor);
                } else {
                    allowClientCamera = false;
                }
            }
        }

        cooldowns.put(id, castAt + getCooldownMillis(level));
        cooldownReadyNotify.add(id);
        rewinding.add(id);
        long protectionUntil = castAt + ((long) (animationTicks + getConfig().rewindProtectionTicks) * 50L);
        rewindProtection.put(id, protectionUntil);
        markRecallTeleportSuppressed(id, protectionUntil + ((long) Math.max(0, getConfig().rewindTeleportXpSuppressExtraTicks) * 50L));
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, animationTicks + getConfig().rewindProtectionTicks, 0, true, false, false), true);

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playRewindStart(p);
            ChronosSoundFX.playRewindFinish(p);
        }

        final boolean initialClientCamera = allowClientCamera;
        final ArmorStand initialCameraAnchor = cameraAnchor;
        new BukkitRunnable() {
            int step = 0;
            Location lastLoc = p.getLocation().clone();
            final boolean[] clientCameraActive = new boolean[]{initialClientCamera};
            final ArmorStand[] cameraRef = new ArmorStand[]{initialCameraAnchor};

            private void cleanupVisualState(boolean restoreGameMode) {
                if (cameraRef[0] != null) {
                    Entity anchorEntity = cameraRef[0];
                    cameraRef[0] = null;
                    if (anchorEntity.isValid()) {
                        anchorEntity.remove();
                    }
                }

                if (temporarySpectator) {
                    p.setSpectatorTarget(null);
                    if (restoreGameMode && p.getGameMode() == GameMode.SPECTATOR) {
                        p.setGameMode(originalGameMode);
                        p.setFlying(false);
                    }
                }
            }

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    rewinding.remove(id);
                    cleanupVisualState(true);
                    cancel();
                    return;
                }

                float progress = animationTicks <= 1 ? 1f : (float) step / (float) (animationTicks - 1);
                int index = Math.min(animationPath.size() - 1, step);
                Snapshot snapshot = animationPath.get(index);
                Location destination = toLocation(snapshot, p.getWorld());

                if (getConfig().showRewindTraceParticles && lastLoc.getWorld() != null && lastLoc.getWorld().equals(destination.getWorld())) {
                    vfxParticleLine(lastLoc.clone().add(0, 1, 0), destination.clone().add(0, 1, 0), Particle.REVERSE_PORTAL,
                            Math.max(4, getConfig().rewindTracePoints), 1, 0.08D, 0.08D, 0.08D, 0D, null, true,
                            l -> l.getBlock().isPassable());
                }

                boolean movedClient = false;
                if (clientCameraActive[0] && cameraRef[0] != null && cameraRef[0].isValid()) {
                    Entity target = p.getSpectatorTarget();
                    if (target == null || !target.getUniqueId().equals(cameraRef[0].getUniqueId())) {
                        p.setSpectatorTarget(cameraRef[0]);
                        target = p.getSpectatorTarget();
                    }

                    if (target != null
                            && target.getUniqueId().equals(cameraRef[0].getUniqueId())
                            && destination.getWorld() != null
                            && destination.getWorld().equals(cameraRef[0].getWorld())) {
                        cameraRef[0].teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                        movedClient = true;
                    } else {
                        clientCameraActive[0] = false;
                    }
                }

                if (!movedClient) {
                    p.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                }

                if (!temporarySpectator || step >= animationPath.size() - 1) {
                    applySnapshotState(p, snapshot);
                }

                if (getConfig().playClockSounds) {
                    ChronosSoundFX.playRewindStep(p, progress);
                }

                lastLoc = destination;
                step++;

                if (step >= animationTicks) {
                    cleanupVisualState(true);
                    Location finalDestination = toLocation(finalSnapshot, p.getWorld());
                    if (finalDestination.getWorld() != null) {
                        p.teleport(finalDestination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                    applySnapshotState(p, finalSnapshot);

                    if (areParticlesEnabled()) {
                        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 26, 0.25, 0.35, 0.25, 0.01);
                    }
                    if (areParticlesEnabled()) {
                        p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 1, 0), 18, 0.30, 0.30, 0.30, 0.01, new ItemStack(Material.CLOCK));
                    }
                    if (getConfig().playClockSounds) {
                        ChronosSoundFX.playRewindFinish(p);
                    }
                    rewinding.remove(id);
                    getPlayer(p).getData().addStat("chronos.instant-recall.recalls", 1);
                    if (healthBeforeRecall <= 4 && healthAfterRecall >= 16
                            && AdaptConfig.get().isAdvancements()
                            && !getPlayer(p).getData().isGranted("challenge_chronos_recall_cheat_death")) {
                        getPlayer(p).getAdvancementHandler().grant("challenge_chronos_recall_cheat_death");
                    }
                    long awardAt = M.ms();
                    double xpGain = computeRecallXPGain(id, level, xpContext, awardAt);
                    if (xpGain > 0D) {
                        xp(p, p.getLocation(), xpGain);
                        recallXpStamps.put(id, new RecallXPFarmStamp(
                                awardAt,
                                xpContext.fromWorld(),
                                xpContext.fromX(),
                                xpContext.fromY(),
                                xpContext.fromZ(),
                                xpContext.toWorld(),
                                xpContext.toX(),
                                xpContext.toY(),
                                xpContext.toZ()));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(Adapt.instance, 0, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }

        UUID id = p.getUniqueId();
        long protectedUntil = rewindProtection.getOrDefault(id, 0L);
        if (rewinding.contains(id) || protectedUntil > M.ms()) {
            e.setCancelled(true);
            p.setNoDamageTicks(Math.max(p.getNoDamageTicks(), getConfig().rewindProtectionTicks));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isRecallEligible(p)) {
            return;
        }

        captureSnapshot(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDoubleJumpMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean wasOnGround = lastOnGround.getOrDefault(id, true);
        boolean onGround = p.isOnGround();
        lastOnGround.put(id, onGround);

        if (!isRecallEligible(p) || !getConfig().enableDoubleJumpTrigger) {
            clearDoubleJumpArm(p, id);
            return;
        }

        if (!wasOnGround && onGround) {
            clearDoubleJumpArm(p, id);
            return;
        }

        if (!canArmDoubleJump(p)) {
            clearDoubleJumpArm(p, id);
            return;
        }

        if (cooldowns.getOrDefault(id, 0L) > M.ms()) {
            clearDoubleJumpArm(p, id);
            return;
        }

        if (isDoubleJumpStart(wasOnGround, onGround, p)) {
            armDoubleJump(p, id);
            return;
        }

        Long armUntil = jumpArmUntil.get(id);
        if (armUntil != null && armUntil <= M.ms()) {
            clearDoubleJumpArm(p, id);
        }
    }

    @Override
    public void onTick() {
        long now = M.ms();

        Iterator<UUID> ready = cooldownReadyNotify.iterator();
        while (ready.hasNext()) {
            UUID id = ready.next();
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                ready.remove();
                continue;
            }

            long cooldown = cooldowns.getOrDefault(id, 0L);
            if (cooldown <= now) {
                if (getConfig().playClockSounds) {
                    ChronosSoundFX.playCooldownReady(p);
                }
                ready.remove();
            }
        }

        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        rewindProtection.entrySet().removeIf(entry -> entry.getValue() <= now);
        TELEPORT_XP_SUPPRESS_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
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
    @ConfigDescription("Click with a clock to rewind to a recent snapshot with health and hunger restored.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Instant Recall adaptation.", impact = "True enables this behavior and false disables it.")
        boolean playClockSounds = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Rewind Trace Particles for the Chronos Instant Recall adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showRewindTraceParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Rewind Trace Points for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int rewindTracePoints = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Target rewind animation duration in milliseconds.", impact = "Higher values make recall rewind visuals slower/smoother; lower values make them faster.")
        int rewindAnimationDurationMillis = 1000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Legacy fallback rewind animation ticks used when duration is invalid.", impact = "Retained for backward compatibility with older configs.")
        int rewindAnimationTicks = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Temporarily switches to spectator during rewind animation for smoother camera movement through obstacles.", impact = "True improves rewind smoothness; false keeps player in survival during rewind.")
        boolean rewindUseTemporarySpectator = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Uses a client-side spectator camera anchor during rewind so server position only updates at the end.", impact = "True reduces jitter and rubber-banding by avoiding per-tick player teleports.")
        boolean rewindUseClientCamera = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Extra ticks to suppress teleport XP/stat tracking after recall rewinds.", impact = "Higher values make it safer against teleport-XP side effects from other skills.")
        int rewindTeleportXpSuppressExtraTicks = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables direct click-with-clock activation for instant recall.", impact = "True allows recall by clicking with a valid recall clock; false disables direct clock-click activation.")
        boolean enableClockClickTrigger = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows left-click to activate recall when clock-click trigger is enabled.", impact = "True allows left-click activation; false blocks left-click activation.")
        boolean clockClickLeftClick = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows right-click to activate recall when clock-click trigger is enabled.", impact = "True allows right-click activation; false blocks right-click activation.")
        boolean clockClickRightClick = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables sprint + click activation for instant recall with a valid recall clock.", impact = "True allows sprint-click activation; false disables sprint-click activation.")
        boolean enableSprintClickTrigger = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows left-click for sprint-click recall trigger.", impact = "True enables left-click sprint activation; false disables it.")
        boolean sprintClickLeftClick = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows right-click for sprint-click recall trigger.", impact = "True enables right-click sprint activation; false disables it.")
        boolean sprintClickRightClick = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows click-in-air interactions for recall click triggers.", impact = "True lets air-clicks activate enabled click modes; false blocks air-click activations.")
        boolean allowAirClicks = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Allows click-on-block interactions for recall click triggers.", impact = "True lets block-clicks activate enabled click modes; false blocks block-click activations.")
        boolean allowBlockClicks = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables single-sneak activation for instant recall.", impact = "True allows pressing sneak once to trigger recall.")
        boolean enableSingleSneakTrigger = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Require sprinting for single-sneak instant recall trigger.", impact = "True requires sprint state when using single-sneak activation.")
        boolean singleSneakRequiresSprint = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Require holding a valid recall clock in either hand for single-sneak trigger.", impact = "True keeps single-sneak recall tied to clock usage.")
        boolean singleSneakRequiresClockInHand = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables double-tap jump activation for instant recall.", impact = "True allows jump-based recall trigger; false disables jump trigger.")
        boolean enableDoubleJumpTrigger = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Require sprinting while double-jumping to trigger recall.", impact = "True requires sprint state for double-jump trigger; false allows it without sprint.")
        boolean doubleJumpRequiresSprint = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Require holding a valid recall clock in either hand for double-jump trigger.", impact = "True requires clock-in-hand for jump trigger; false allows jump trigger without holding a clock.")
        boolean doubleJumpRequiresClockInHand = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum milliseconds allowed between jump taps for double-jump recall.", impact = "Higher values make double-tap detection easier; lower values make it stricter.")
        int doubleJumpWindowMillis = 450;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Minimum upward velocity required to arm double-jump recall.", impact = "Higher values reduce accidental arm events; lower values increase sensitivity.")
        double doubleJumpMinVerticalVelocity = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Rewind Seconds for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseRewindSeconds = 3.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Rewind Seconds Per Level for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rewindSecondsPerLevel = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Hard cap for recall rewind duration in seconds.", impact = "Prevents high levels/config values from exceeding this rewind window.")
        double maxRewindSeconds = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Padding Seconds for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int cooldownPaddingSeconds = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Snapshot Interval Millis for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int snapshotIntervalMillis = 50;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls History Padding Seconds for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int historyPaddingSeconds = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Rewind Protection Ticks for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int rewindProtectionTicks = 25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Distance Block for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerDistanceBlock = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Health Point for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerHealthPoint = 0.85;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Hunger Point for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerHungerPoint = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Saturation Point for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerSaturationPoint = 0.18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Level Multiplier Per Level for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpLevelMultiplierPerLevel = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Min Raw Reward for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpMinRawReward = 1.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Min Award for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpMinAward = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Max Award for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpMaxAward = 36;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Cross World Distance Credit for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpCrossWorldDistanceCredit = 16;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Diminish Window Millis for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long xpDiminishWindowMillis = 45000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Diminish Min Multiplier for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpDiminishMinMultiplier = 0.18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Window Millis for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long xpRepeatWindowMillis = 180000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Source Radius for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpRepeatSourceRadius = 3.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Target Radius for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpRepeatTargetRadius = 3.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Repeat Penalty Multiplier for the Chronos Instant Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpRepeatPenaltyMultiplier = 0.2;
    }

    private record Snapshot(long timestamp,
                            String worldName,
                            double x,
                            double y,
                            double z,
                            float yaw,
                            float pitch,
                            double health,
                            int foodLevel,
                            float saturation,
                            float exhaustion,
                            int fireTicks) {
    }

    private record RecallXPContext(String fromWorld,
                                   double fromX,
                                   double fromY,
                                   double fromZ,
                                   String toWorld,
                                   double toX,
                                   double toY,
                                   double toZ,
                                   double distance,
                                   double healthRecovered,
                                   double hungerRecovered,
                                   double saturationRecovered) {
    }

    private record RecallXPFarmStamp(long awardedAt,
                                     String fromWorld,
                                     double fromX,
                                     double fromY,
                                     double fromZ,
                                     String toWorld,
                                     double toX,
                                     double toY,
                                     double toZ) {
    }
}
