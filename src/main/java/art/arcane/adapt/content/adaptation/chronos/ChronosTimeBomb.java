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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import art.arcane.adapt.util.common.math.Sphere;
import art.arcane.adapt.util.common.misc.Impulse;
import art.arcane.adapt.util.reflect.registries.Particles;

public class ChronosTimeBomb extends SimpleAdaptation<ChronosTimeBomb.Config> {
    private static final EnumSet<Action> SUPPORTED_ACTIONS = EnumSet.of(
            Action.RIGHT_CLICK_AIR,
            Action.RIGHT_CLICK_BLOCK
    );
    private static final long PROJECTILE_TRACK_TTL_MS = 15000L;

    private final Map<UUID, Long> cooldowns;
    private final Set<UUID> cooldownReadyNotify;
    private final List<TemporalField> fields;
    private final Map<UUID, ArmedBombProjectile> activeBombProjectiles;
    private final Map<UUID, FrozenEntityState> frozenEntities;
    private final Map<UUID, FrozenPlayerState> frozenPlayers;
    private final AtomicBoolean syncTickQueued;

    public ChronosTimeBomb() {
        super("chronos-time-bomb");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("chronos.time_bomb.description"));
        setDisplayName(Localizer.dLocalize("chronos.time_bomb.name"));
        setIcon(Material.TNT);
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
        activeBombProjectiles = new HashMap<>();
        frozenEntities = new HashMap<>();
        frozenPlayers = new HashMap<>();
        syncTickQueued = new AtomicBoolean(false);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ICE)
                .key("challenge_chronos_bomb_freeze_50")
                .title(Localizer.dLocalize("advancement.challenge_chronos_bomb_freeze_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_bomb_freeze_50.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ICE)
                .key("challenge_chronos_bomb_crowd_8")
                .title(Localizer.dLocalize("advancement.challenge_chronos_bomb_crowd_8.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_bomb_crowd_8.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_chronos_bomb_freeze_50", "chronos.time-bomb.projectiles-frozen", 50, 500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getRadius(level) + " " + Localizer.dLocalize("chronos.time_bomb.lore1"));
        v.addLore(C.YELLOW + "+ " + (getDurationTicks(level) / 20D) + "s " + Localizer.dLocalize("chronos.time_bomb.lore2"));
        v.addLore(C.RED + "* " + (getCooldownMillis() / 1000D) + "s " + Localizer.dLocalize("chronos.time_bomb.lore3"));
        v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.time_bomb.lore4"));
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
        activeBombProjectiles.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
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
        if (handSlot == EquipmentSlot.OFF_HAND && ChronoTimeBombItem.isBindableItem(p.getInventory().getItemInMainHand())) {
            return;
        }

        ItemStack hand = getItemInHand(p, handSlot);
        if (!ChronoTimeBombItem.isBindableItem(hand)) {
            return;
        }

        if (!hasAdaptation(p)) {
            e.setCancelled(true);
            return;
        }

        long now = M.ms();
        long cooldown = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (cooldown > now) {
            e.setCancelled(true);
            if (getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(p);
            }
        }
    }

    private ItemStack getItemInHand(Player p, EquipmentSlot handSlot) {
        return handSlot == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof ThrownPotion potion)) {
            return;
        }

        if (!(potion.getShooter() instanceof Player p)) {
            return;
        }

        ItemStack item = potion.getItem();
        if (!ChronoTimeBombItem.isBindableItem(item)) {
            return;
        }

        if (!hasAdaptation(p)) {
            e.setCancelled(true);
            return;
        }

        long now = M.ms();
        long cooldown = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (cooldown > now) {
            e.setCancelled(true);
            if (getConfig().playClockSounds) {
                ChronosSoundFX.playClockReject(p);
            }
            return;
        }

        int level = getLevel(p);
        cooldowns.put(p.getUniqueId(), now + getCooldownMillis());
        cooldownReadyNotify.add(p.getUniqueId());
        activeBombProjectiles.put(potion.getUniqueId(), new ArmedBombProjectile(p.getUniqueId(), level, now));

        if (getConfig().playClockSounds) {
            ChronosSoundFX.playTimeBombArm(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(LingeringPotionSplashEvent e) {
        ThrownPotion potion = e.getEntity();
        UUID projectileId = potion.getUniqueId();
        ArmedBombProjectile armed = activeBombProjectiles.remove(projectileId);
        boolean bindable = ChronoTimeBombItem.isBindableItem(potion.getItem());

        if (!bindable && armed == null) {
            return;
        }

        if (e.getAreaEffectCloud() != null) {
            e.getAreaEffectCloud().remove();
        }

        if (armed == null) {
            return;
        }

        Location deployCenter = potion.getLocation().clone().add(0, getConfig().fieldCenterYOffset, 0);
        Player owner = Bukkit.getPlayer(armed.owner());
        if (owner != null && !canInteract(owner, deployCenter)) {
            return;
        }

        deployField(armed.owner(), armed.level(), deployCenter);
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

        if (areParticlesEnabled() && center.getWorld() != null) {
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
        int entitiesSlowed = 0;
        for (Entity entity : nearby) {
            if (entity.getLocation().distanceSquared(field.center()) > field.radius() * field.radius()) {
                continue;
            }

            if (!(entity instanceof Player)) {
                boolean wasNew = !frozenEntities.containsKey(entity.getUniqueId());
                freezeEntity(entity);
                if (wasNew && owner != null) {
                    entitiesSlowed++;
                    if (entity instanceof Projectile) {
                        getPlayer(owner).getData().addStat("chronos.time-bomb.projectiles-frozen", 1);
                    }
                    getPlayer(owner).getData().addStat("chronos.time-bomb.entities-slowed", 1);
                }
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
            entitiesSlowed++;
        }

        if (entitiesSlowed >= 8 && owner != null
                && AdaptConfig.get().isAdvancements()
                && !getPlayer(owner).getData().isGranted("challenge_chronos_bomb_crowd_8")) {
            getPlayer(owner).getAdvancementHandler().grant("challenge_chronos_bomb_crowd_8");
        }

        if (areParticlesEnabled()) {
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
        cleanupBombProjectiles(now);

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

    private void cleanupBombProjectiles(long now) {
        Iterator<Map.Entry<UUID, ArmedBombProjectile>> iterator = activeBombProjectiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArmedBombProjectile> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                iterator.remove();
                continue;
            }

            if (now - entry.getValue().launchedAt() > PROJECTILE_TRACK_TTL_MS) {
                iterator.remove();
            }
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
    @ConfigDescription("Throw a crafted chrono bomb that creates a temporal field, slowing entities and freezing projectiles.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Chronos Time Bomb adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Time Bomb adaptation.", impact = "True enables this behavior and false disables it.")
        boolean playClockSounds = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 7;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.42;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Radius for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseRadius = 6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Per Level for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusPerLevel = 1.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Duration Ticks for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseDurationTicks = 60;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Per Level Ticks for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int durationPerLevelTicks = 25;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownMillis = 15000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Target Deploy Range for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double targetDeployRange = 64;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Center YOffset for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fieldCenterYOffset = 1.25;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slowness Amplifier for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int slownessAmplifier = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Caster Slowness Amplifier for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int casterSlownessAmplifier = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Amplifier for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int fatigueAmplifier = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Freeze Players In Air for the Chronos Time Bomb adaptation.", impact = "True enables this behavior and false disables it.")
        boolean freezePlayersInAir = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Accumulate Frozen Impulse for the Chronos Time Bomb adaptation.", impact = "True enables this behavior and false disables it.")
        boolean accumulateFrozenImpulse = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Frozen Impulse Min Magnitude for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double frozenImpulseMinMagnitude = 0.03;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Frozen Impulse Sample Cap for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double frozenImpulseSampleCap = 2.8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Frozen Impulse Release Cap for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double frozenImpulseReleaseCap = 7.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Refresh Ticks for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int effectRefreshTicks = 24;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Field Sphere for the Chronos Time Bomb adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showFieldSphere = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Sphere Particle Count for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int fieldSphereParticleCount = 280;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Sphere Refresh Millis for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long fieldSphereRefreshMillis = 100;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Tick Sound Interval Millis for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int fieldTickSoundIntervalMillis = 325;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Tick Min Interval Millis for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int fieldTickMinIntervalMillis = 70;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Tick Pitch Start for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fieldTickPitchStart = 0.42;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Tick Pitch End for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fieldTickPitchEnd = 1.96;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Tick Pitch Curve Exponent for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fieldTickPitchCurveExponent = 3.75;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Field Tick Acceleration Factor for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fieldTickAccelerationFactor = 0.82;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Cast for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnCast = 28;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Level for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
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

    private record ArmedBombProjectile(UUID owner, int level, long launchedAt) {
    }
}
