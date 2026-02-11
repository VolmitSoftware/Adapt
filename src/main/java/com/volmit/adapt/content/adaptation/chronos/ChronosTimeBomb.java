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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.ChronoTimeBombItem;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChronosTimeBomb extends SimpleAdaptation<ChronosTimeBomb.Config> {
    private static final EnumSet<Action> SUPPORTED_ACTIONS = EnumSet.of(
            Action.RIGHT_CLICK_AIR,
            Action.RIGHT_CLICK_BLOCK,
            Action.LEFT_CLICK_AIR,
            Action.LEFT_CLICK_BLOCK
    );

    private final Map<UUID, Long> cooldowns;
    private final Set<UUID> cooldownReadyNotify;
    private final List<TemporalField> fields;
    private final Map<UUID, FrozenEntityState> frozenEntities;
    private final Map<UUID, FrozenPlayerState> frozenPlayers;
    private final AtomicBoolean syncTickQueued;

    public ChronosTimeBomb() {
        super("chronos-time-bomb");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("chronos", "timebomb", "description"));
        setDisplayName(Localizer.dLocalize("chronos", "timebomb", "name"));
        setIcon(Material.CLOCK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(50);

        registerRecipe(AdaptRecipe.shapeless()
                .key("chronos-time-bomb")
                .ingredient(Material.SNOWBALL)
                .ingredient(Material.CLOCK)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.SAND)
                .result(ChronoTimeBombItem.withData())
                .build());

        cooldowns = new HashMap<>();
        cooldownReadyNotify = new HashSet<>();
        fields = new ArrayList<>();
        frozenEntities = new HashMap<>();
        frozenPlayers = new HashMap<>();
        syncTickQueued = new AtomicBoolean(false);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getRadius(level) + " " + Localizer.dLocalize("chronos", "timebomb", "lore1"));
        v.addLore(C.YELLOW + "+ " + (getDurationTicks(level) / 20D) + "s " + Localizer.dLocalize("chronos", "timebomb", "lore2"));
        v.addLore(C.RED + "* " + (getCooldownMillis() / 1000D) + "s " + Localizer.dLocalize("chronos", "timebomb", "lore3"));
        v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos", "timebomb", "lore4"));
    }

    private double getRadius(int level) {
        return getConfig().baseRadius + ((Math.max(1, level) - 1) * getConfig().radiusPerLevel);
    }

    private int getDurationTicks(int level) {
        return getConfig().baseDurationTicks + ((Math.max(1, level) - 1) * getConfig().durationPerLevelTicks);
    }

    private long getCooldownMillis() {
        return Math.max(0L, getConfig().cooldownMillis);
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        cooldowns.remove(id);
        cooldownReadyNotify.remove(id);
        frozenPlayers.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (!SUPPORTED_ACTIONS.contains(action)) {
            return;
        }

        EquipmentSlot handSlot = e.getHand();
        if (handSlot == null) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        if (handSlot == EquipmentSlot.OFF_HAND && ChronoTimeBombItem.isBindableItem(p.getInventory().getItemInMainHand())) {
            return;
        }

        ItemStack hand = getItemInHand(p, handSlot);
        if (!ChronoTimeBombItem.isBindableItem(hand)) {
            return;
        }

        e.setCancelled(true);

        long now = M.ms();
        long cooldown = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (cooldown > now) {
            if (getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(p);
            }
            return;
        }

        int level = getLevel(p);
        Location deployCenter;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean farDeploy = action == Action.LEFT_CLICK_BLOCK
                || (leftClick && p.isSneaking())
                || (rightClick && p.isSneaking());

        if (farDeploy) {
            Location rayCenter = resolveRaycastDeployCenter(p);
            if (rayCenter != null) {
                deployCenter = rayCenter;
            } else {
                if (getConfig().playClockSounds) {
                    ChronosSoundFX.playClockReject(p);
                }
                return;
            }
        } else {
            deployCenter = p.getLocation().clone().add(0, getConfig().fieldCenterYOffset, 0);
        }

        if (!canInteract(p, deployCenter)) {
            return;
        }

        deployField(p.getUniqueId(), level, deployCenter);
        cooldowns.put(p.getUniqueId(), now + getCooldownMillis());
        cooldownReadyNotify.add(p.getUniqueId());
        decrementItemstack(hand, p);

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playTimeBombArm(p);
        }
    }

    private ItemStack getItemInHand(Player p, EquipmentSlot handSlot) {
        return handSlot == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
    }

    private Location resolveRaycastDeployCenter(Player p) {
        RayTraceResult result = p.rayTraceBlocks(getConfig().targetDeployRange, FluidCollisionMode.NEVER);
        if (result == null || result.getHitBlock() == null) {
            return null;
        }

        return result.getHitBlock().getLocation().add(0.5, 0.5 + getConfig().fieldCenterYOffset, 0.5);
    }

    private void deployField(UUID ownerId, int level, Location center) {
        TemporalField field = new TemporalField(
                ownerId,
                center,
                getRadius(level),
                M.ms() + (getDurationTicks(level) * 50L),
                level,
                M.ms(),
                M.ms());
        fields.add(field);

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playTimeBombDetonate(center);
        }

        if (getConfig().showParticles && center.getWorld() != null) {
            center.getWorld().spawnParticle(Particle.ENCHANT, center, 45, field.radius() * 0.4, 0.35, field.radius() * 0.4, 0.15);
            center.getWorld().spawnParticle(Particle.END_ROD, center, 20, field.radius() * 0.2, 0.2, field.radius() * 0.2, 0.02);
        }

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            xp(owner, center, getConfig().xpOnCast + (level * getConfig().xpPerLevel));
        }
    }

    private void freezeEntity(Entity entity) {
        if (entity instanceof Player) {
            return;
        }

        UUID id = entity.getUniqueId();
        FrozenEntityState state = frozenEntities.get(id);
        if (state == null) {
            Boolean hadAi = null;
            if (entity instanceof LivingEntity living) {
                hadAi = living.hasAI();
            }

            state = new FrozenEntityState(entity.getVelocity().clone(), entity.hasGravity(), hadAi);
            frozenEntities.put(id, state);
        } else if (getConfig().accumulateFrozenImpulse) {
            state.captureImpulse(entity.getVelocity(), getConfig().frozenImpulseMinMagnitude, getConfig().frozenImpulseSampleCap);
        }

        entity.setGravity(false);
        entity.setVelocity(new Vector());
        entity.setFallDistance(0);

        if (entity instanceof LivingEntity living) {
            living.setAI(false);
        }
    }

    private void freezePlayer(Player player) {
        UUID id = player.getUniqueId();
        if (!frozenPlayers.containsKey(id)) {
            frozenPlayers.put(id, new FrozenPlayerState(player.getAllowFlight(), player.isFlying(), player.hasGravity()));
        }

        if (!getConfig().freezePlayersInAir || player.isOnGround()) {
            return;
        }

        player.setFallDistance(0);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGravity(false);
        player.setVelocity(new Vector());
    }

    private void unfreezePlayer(UUID playerId, FrozenPlayerState state) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        player.setGravity(state.gravity());
        if (state.allowFlight()) {
            player.setAllowFlight(true);
            player.setFlying(state.flying());
        } else {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private boolean shouldFreezePlayer(Player player) {
        if (!getConfig().freezePlayersInAir) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        for (TemporalField field : fields) {
            if (field.center().getWorld() == null || !field.center().getWorld().equals(player.getWorld())) {
                continue;
            }

            if (field.center().distanceSquared(player.getLocation()) > field.radius() * field.radius()) {
                continue;
            }

            if (playerId.equals(field.owner())) {
                continue;
            }

            Player owner = Bukkit.getPlayer(field.owner());
            if (owner != null && !canPVP(owner, player.getLocation())) {
                continue;
            }

            return true;
        }

        return false;
    }

    private void unfreezeEntity(UUID entityId, FrozenEntityState state) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }

        entity.setGravity(state.gravity());
        entity.setVelocity(state.buildReleaseVelocity(getConfig().frozenImpulseReleaseCap));

        if (entity instanceof LivingEntity living && state.hadAi() != null) {
            living.setAI(state.hadAi());
        }
    }

    private boolean isInsideAnyField(Location location) {
        if (location.getWorld() == null) {
            return false;
        }

        for (TemporalField field : fields) {
            if (field.center().getWorld() == null || !field.center().getWorld().equals(location.getWorld())) {
                continue;
            }

            if (field.center().distanceSquared(location) <= field.radius() * field.radius()) {
                return true;
            }
        }

        return false;
    }

    private void applyField(TemporalField field, long now) {
        if (field.center().getWorld() == null) {
            return;
        }

        Player owner = Bukkit.getPlayer(field.owner());

        Collection<Entity> nearby = field.center().getWorld().getNearbyEntities(field.center(), field.radius(), field.radius(), field.radius());
        for (Entity entity : nearby) {
            if (entity.getLocation().distanceSquared(field.center()) > field.radius() * field.radius()) {
                continue;
            }

            if (!(entity instanceof Player)) {
                freezeEntity(entity);
                continue;
            }

            LivingEntity living = (LivingEntity) entity;
            boolean caster = entity.getUniqueId().equals(field.owner());
            if (caster) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getConfig().effectRefreshTicks, getConfig().casterSlownessAmplifier, true, false, false), true);
                living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, getConfig().effectRefreshTicks, getConfig().fatigueAmplifier, true, false, false), true);
                continue;
            }

            if (owner != null) {
                if (living instanceof Player targetPlayer) {
                    if (!canPVP(owner, targetPlayer.getLocation())) {
                        continue;
                    }
                } else {
                    if (!canPVE(owner, living.getLocation())) {
                        continue;
                    }
                }
            }

            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getConfig().effectRefreshTicks, getConfig().slownessAmplifier, true, false, false), true);
            living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, getConfig().effectRefreshTicks, getConfig().fatigueAmplifier, true, false, false), true);
            freezePlayer((Player) living);
            living.setVelocity(new Vector());
        }

        if (getConfig().showParticles) {
            spawnFieldSphere(field, now);
        }

        if (getConfig().playClockSounds && now >= field.nextTickSoundAt()) {
            long totalDurationMillis = Math.max(50L, getDurationTicks(field.level()) * 50L);
            long remainingMillis = Math.max(0L, field.expiresAt() - now);
            double progress = 1D - (remainingMillis / (double) totalDurationMillis);
            progress = Math.max(0D, Math.min(1D, progress));

            double pitchCurve = Math.pow(progress, Math.max(0.1D, getConfig().fieldTickPitchCurveExponent));
            float pitch = (float) (getConfig().fieldTickPitchStart
                    + ((getConfig().fieldTickPitchEnd - getConfig().fieldTickPitchStart) * pitchCurve));
            ChronosSoundFX.playTimeFieldTick(field.center(), pitch);

            double acceleration = Math.max(0D, Math.min(0.95D, getConfig().fieldTickAccelerationFactor));
            long interval = (long) Math.max(getConfig().fieldTickMinIntervalMillis,
                    getConfig().fieldTickSoundIntervalMillis * (1D - (progress * acceleration)));
            field.setNextTickSoundAt(now + interval);
        }
    }

    private void spawnFieldSphere(TemporalField field, long now) {
        if (!getConfig().showFieldSphere || now < field.nextVisualAt() || field.center().getWorld() == null) {
            return;
        }

        int particles = Math.max(24, getConfig().fieldSphereParticleCount);
        double radius = Math.max(0.1, field.radius());
        vfxFastSphere(field.center(), radius, Color.BLACK, particles);
        vfxFastSphere(field.center(), Math.max(0.2, radius * 0.75), Color.fromRGB(210, 210, 210), Math.max(12, particles / 2));

        field.setNextVisualAt(now + Math.max(1, getConfig().fieldSphereRefreshMillis));
    }

    @Override
    public void onTick() {
        if (Bukkit.isPrimaryThread()) {
            onTickSync();
            return;
        }

        if (!syncTickQueued.compareAndSet(false, true)) {
            return;
        }

        J.s(() -> {
            try {
                onTickSync();
            } finally {
                syncTickQueued.set(false);
            }
        });
    }

    private void onTickSync() {
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

        fields.removeIf(field -> field.expiresAt() <= now);
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);

        for (TemporalField field : fields) {
            applyField(field, now);
        }

        Iterator<Map.Entry<UUID, FrozenPlayerState>> frozenPlayerIterator = frozenPlayers.entrySet().iterator();
        while (frozenPlayerIterator.hasNext()) {
            Map.Entry<UUID, FrozenPlayerState> entry = frozenPlayerIterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) {
                frozenPlayerIterator.remove();
                continue;
            }

            if (shouldFreezePlayer(player)) {
                freezePlayer(player);
                continue;
            }

            unfreezePlayer(entry.getKey(), entry.getValue());
            frozenPlayerIterator.remove();
        }

        Iterator<Map.Entry<UUID, FrozenEntityState>> frozenIterator = frozenEntities.entrySet().iterator();
        while (frozenIterator.hasNext()) {
            Map.Entry<UUID, FrozenEntityState> entry = frozenIterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || entity.isDead() || !entity.isValid()) {
                frozenIterator.remove();
                continue;
            }

            if (isInsideAnyField(entity.getLocation())) {
                freezeEntity(entity);
                continue;
            }

            unfreezeEntity(entry.getKey(), entry.getValue());
            frozenIterator.remove();
        }
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
        boolean playClockSounds = true;
        int baseCost = 8;
        int maxLevel = 5;
        int initialCost = 7;
        double costFactor = 0.42;
        double baseRadius = 6;
        double radiusPerLevel = 1.5;
        int baseDurationTicks = 60;
        int durationPerLevelTicks = 25;
        long cooldownMillis = 15000;
        double targetDeployRange = 64;
        double fieldCenterYOffset = 1.25;
        int slownessAmplifier = 2;
        int casterSlownessAmplifier = 1;
        int fatigueAmplifier = 1;
        boolean freezePlayersInAir = true;
        boolean accumulateFrozenImpulse = true;
        double frozenImpulseMinMagnitude = 0.03;
        double frozenImpulseSampleCap = 2.8;
        double frozenImpulseReleaseCap = 7.5;
        int effectRefreshTicks = 24;
        boolean showFieldSphere = true;
        int fieldSphereParticleCount = 280;
        long fieldSphereRefreshMillis = 100;
        int fieldTickSoundIntervalMillis = 325;
        int fieldTickMinIntervalMillis = 70;
        double fieldTickPitchStart = 0.42;
        double fieldTickPitchEnd = 1.96;
        double fieldTickPitchCurveExponent = 3.75;
        double fieldTickAccelerationFactor = 0.82;
        double xpOnCast = 28;
        double xpPerLevel = 3;
    }

    private static final class TemporalField {
        private final UUID owner;
        private final Location center;
        private final double radius;
        private final long expiresAt;
        private final int level;
        private long nextTickSoundAt;
        private long nextVisualAt;

        private TemporalField(UUID owner, Location center, double radius, long expiresAt, int level, long nextTickSoundAt, long nextVisualAt) {
            this.owner = owner;
            this.center = center;
            this.radius = radius;
            this.expiresAt = expiresAt;
            this.level = level;
            this.nextTickSoundAt = nextTickSoundAt;
            this.nextVisualAt = nextVisualAt;
        }

        private UUID owner() {
            return owner;
        }

        private Location center() {
            return center;
        }

        private double radius() {
            return radius;
        }

        private long expiresAt() {
            return expiresAt;
        }

        private int level() {
            return level;
        }

        private long nextTickSoundAt() {
            return nextTickSoundAt;
        }

        private void setNextTickSoundAt(long nextTickSoundAt) {
            this.nextTickSoundAt = nextTickSoundAt;
        }

        private long nextVisualAt() {
            return nextVisualAt;
        }

        private void setNextVisualAt(long nextVisualAt) {
            this.nextVisualAt = nextVisualAt;
        }
    }

    private static final class FrozenEntityState {
        private final Vector originalVelocity;
        private final boolean gravity;
        private final Boolean hadAi;
        private final Vector impulseDirectionAccumulator;
        private double impulseMagnitudeAccumulator;
        private int impulseSamples;

        private FrozenEntityState(Vector originalVelocity, boolean gravity, Boolean hadAi) {
            this.originalVelocity = originalVelocity.clone();
            this.gravity = gravity;
            this.hadAi = hadAi;
            this.impulseDirectionAccumulator = new Vector();
            this.impulseMagnitudeAccumulator = 0D;
            this.impulseSamples = 0;
        }

        private boolean gravity() {
            return gravity;
        }

        private Boolean hadAi() {
            return hadAi;
        }

        private void captureImpulse(Vector currentVelocity, double minMagnitude, double sampleCap) {
            if (currentVelocity == null) {
                return;
            }

            Vector sample = currentVelocity.clone();
            double magnitude = sample.length();
            if (magnitude < Math.max(0D, minMagnitude)) {
                return;
            }

            if (sampleCap > 0D && magnitude > sampleCap) {
                sample = sample.normalize().multiply(sampleCap);
                magnitude = sampleCap;
            }

            if (magnitude <= 0D) {
                return;
            }

            impulseDirectionAccumulator.add(sample);
            impulseMagnitudeAccumulator += magnitude;
            impulseSamples++;
        }

        private Vector buildReleaseVelocity(double releaseCap) {
            Vector release = originalVelocity.clone();
            if (impulseSamples <= 0
                    || impulseMagnitudeAccumulator <= 0D
                    || impulseDirectionAccumulator.lengthSquared() <= 1.0E-6D) {
                return release;
            }

            double magnitude = impulseMagnitudeAccumulator;
            if (releaseCap > 0D) {
                magnitude = Math.min(magnitude, releaseCap);
            }

            Vector direction = impulseDirectionAccumulator.clone().normalize();
            release.add(direction.multiply(magnitude));
            return release;
        }
    }

    private record FrozenPlayerState(boolean allowFlight, boolean flying, boolean gravity) {
    }
}
