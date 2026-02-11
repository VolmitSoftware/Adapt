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
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ChronoTimeBombItem;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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

    private final Map<UUID, Deque<Snapshot>> snapshots;
    private final Map<UUID, Long> lastSnapshot;
    private final Map<UUID, Long> cooldowns;
    private final Set<UUID> cooldownReadyNotify;
    private final Map<UUID, Long> rewindProtection;
    private final Set<UUID> rewinding;
    private final Map<UUID, RecallXPFarmStamp> recallXpStamps;

    public ChronosInstantRecall() {
        super("chronos-instant-recall");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("chronos", "instantrecall", "description"));
        setDisplayName(Localizer.dLocalize("chronos", "instantrecall", "name"));
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
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration(getRewindDurationMillis(level), 1) + " " + Localizer.dLocalize("chronos", "instantrecall", "lore1"));
        v.addLore(C.RED + "* " + Form.duration(getCooldownMillis(level), 1) + " " + Localizer.dLocalize("chronos", "instantrecall", "lore2"));
        v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos", "instantrecall", "lore3"));
    }

    private long getRewindDurationMillis(int level) {
        return (long) ((getConfig().baseRewindSeconds + (level * getConfig().rewindSecondsPerLevel)) * 1000D);
    }

    private long getCooldownMillis(int level) {
        return getRewindDurationMillis(level) + (getConfig().cooldownPaddingSeconds * 1000L);
    }

    private long getMaximumHistoryMillis() {
        return (long) ((getConfig().baseRewindSeconds + (getConfig().maxLevel * getConfig().rewindSecondsPerLevel) + getConfig().historyPaddingSeconds) * 1000D);
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
        if (!RECALL_ACTIONS.contains(e.getAction())) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || p.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        EquipmentSlot recallHand = resolveRecallHand(p, e.getHand());
        if (recallHand == null) {
            return;
        }

        e.setCancelled(true);
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

    private void clearPlayerState(UUID id) {
        snapshots.remove(id);
        lastSnapshot.remove(id);
        cooldowns.remove(id);
        cooldownReadyNotify.remove(id);
        rewindProtection.remove(id);
        rewinding.remove(id);
        recallXpStamps.remove(id);
    }

    private boolean isRecallClock(ItemStack stack) {
        return stack != null
                && stack.getType() == Material.CLOCK
                && !ChronoTimeBombItem.isBindableItem(stack);
    }

    private void attemptRecall(Player p) {
        UUID id = p.getUniqueId();
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

        List<Snapshot> path = buildRewindPath(p, rewindMillis, anchor);
        RecallXPContext xpContext = buildRecallXPContext(path.get(0), anchor);
        int animationTicks = Math.max(1, getConfig().rewindAnimationTicks);
        long castAt = M.ms();

        cooldowns.put(id, castAt + getCooldownMillis(level));
        cooldownReadyNotify.add(id);
        rewinding.add(id);
        rewindProtection.put(id, castAt + ((long) (animationTicks + getConfig().rewindProtectionTicks) * 50L));
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, animationTicks + getConfig().rewindProtectionTicks, 0, true, false, false), true);

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playRewindStart(p);
        }

        new BukkitRunnable() {
            int step = 0;
            Location lastLoc = p.getLocation().clone();

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    rewinding.remove(id);
                    cancel();
                    return;
                }

                float progress = animationTicks <= 1 ? 1f : (float) step / (float) (animationTicks - 1);
                int index = Math.min(path.size() - 1, Math.round(progress * (path.size() - 1)));
                Snapshot snapshot = path.get(index);
                Location destination = toLocation(snapshot, p.getWorld());

                if (getConfig().showRewindTraceParticles && lastLoc.getWorld() != null && lastLoc.getWorld().equals(destination.getWorld())) {
                    vfxParticleLine(lastLoc.clone().add(0, 1, 0), destination.clone().add(0, 1, 0), Particle.REVERSE_PORTAL,
                            Math.max(4, getConfig().rewindTracePoints), 1, 0.08D, 0.08D, 0.08D, 0D, null, true,
                            l -> l.getBlock().isPassable());
                }

                p.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                applySnapshotState(p, snapshot);

                if (getConfig().playClockSounds) {
                    ChronosSoundFX.playRewindStep(p, progress);
                }

                lastLoc = destination;
                step++;

                if (step >= animationTicks) {
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 26, 0.25, 0.35, 0.25, 0.01);
                    p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 1, 0), 18, 0.30, 0.30, 0.30, 0.01, new ItemStack(Material.CLOCK));
                    if (getConfig().playClockSounds) {
                        ChronosSoundFX.playRewindFinish(p);
                    }
                    rewinding.remove(id);
                    long awardAt = M.ms();
                    double xpGain = computeRecallXPGain(id, level, xpContext, awardAt);
                    if (xpGain > 0D) {
                        xp(p, destination, xpGain);
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
        if (!hasAdaptation(p) || p.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        captureSnapshot(p);
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
        boolean playClockSounds = true;
        boolean showRewindTraceParticles = true;
        int rewindTracePoints = 18;
        int rewindAnimationTicks = 10;
        int baseCost = 3;
        int maxLevel = 5;
        int initialCost = 3;
        double costFactor = 0.45;
        double baseRewindSeconds = 3;
        double rewindSecondsPerLevel = 1;
        int cooldownPaddingSeconds = 1;
        int snapshotIntervalMillis = 50;
        int historyPaddingSeconds = 2;
        int rewindProtectionTicks = 25;
        double xpPerDistanceBlock = 0.35;
        double xpPerHealthPoint = 0.85;
        double xpPerHungerPoint = 0.7;
        double xpPerSaturationPoint = 0.18;
        double xpLevelMultiplierPerLevel = 0.08;
        double xpMinRawReward = 1.35;
        double xpMinAward = 0.5;
        double xpMaxAward = 36;
        double xpCrossWorldDistanceCredit = 16;
        long xpDiminishWindowMillis = 45000;
        double xpDiminishMinMultiplier = 0.18;
        long xpRepeatWindowMillis = 180000;
        double xpRepeatSourceRadius = 3.5;
        double xpRepeatTargetRadius = 3.5;
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
