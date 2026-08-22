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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.LoadedChunkAccess;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class ChronosTimeBomb extends SimpleAdaptation<ChronosTimeBomb.Config> {
  private static final EnumSet<Action> SUPPORTED_ACTIONS = EnumSet.of(
      Action.RIGHT_CLICK_AIR,
      Action.RIGHT_CLICK_BLOCK
  );
  private static final long PROJECTILE_TRACK_TTL_MS = 15000L;
  private static final long ACTIVE_TICK_INTERVAL_MILLIS = 50L;
  private static final long IDLE_TICK_INTERVAL_MILLIS = Long.MAX_VALUE;
  private static final long MIN_FIELD_SCAN_INTERVAL_MILLIS = 200L;
  private static final long MAX_FIELD_SCAN_INTERVAL_MILLIS = 250L;
  private static final double HARD_MAX_FIELD_RADIUS = 16D;
  private static final int HARD_MAX_ACTIVE_FIELDS = 64;
  private static final int HARD_MAX_REGION_SCANS_PER_CYCLE = 128;
  private static final int HARD_MAX_REGION_SCANS_PER_FIELD = 16;
  private static final double[] FATIGUE_DIG_FACTORS = {0.3D, 0.09D, 0.0027D, 8.1E-4D};
  private static final int HARD_MAX_ENTITIES_PER_SCAN_CYCLE = 2048;
  private static final int HARD_MAX_ENTITIES_PER_FIELD = 256;
  private static final int HARD_MAX_ENTITIES_PER_REGION = 128;
  private static final int HARD_MAX_FROZEN_ENTITIES_PER_TICK = 512;
  private static final int HARD_MAX_FROZEN_PLAYERS_PER_TICK = 256;
  private static final int HARD_MAX_FIELD_FX_PARTICLES_PER_TICK = 4096;
  private static final int HARD_MAX_FIELD_FX_PARTICLES_PER_FIELD = 64;
  private static final int HARD_MAX_IMMEDIATE_FIELD_FX_PARTICLES = 512;
  private static final int HARD_MAX_FREEZE_FX_PARTICLES_PER_SCAN = 1024;
  private static final int HARD_MAX_FREEZE_FX_PARTICLES_PER_FIELD = 24;

  private final Map<UUID, Long> cooldowns;
  private final Map<UUID, Boolean> cooldownReadyNotify;
  private final List<TemporalField> fields;
  private final Map<UUID, ArmedBombProjectile> activeBombProjectiles;
  private final Map<UUID, FrozenEntityState> frozenEntities;
  private final Map<UUID, FrozenPlayerState> frozenPlayers;
  private final AtomicInteger immediateFieldFxBudget;
  private final NamespacedKey frozenStampKey;
  private final NamespacedKey frozenPlayerStampKey;
  private final AtomicBoolean startupSweepDone;
  private Iterator<Map.Entry<UUID, FrozenEntityState>> frozenEntityCursor = Collections.emptyIterator();
  private Iterator<Map.Entry<UUID, FrozenPlayerState>> frozenPlayerCursor = Collections.emptyIterator();
  private int fieldScanRotation;
  private int presentationRotation;

  public ChronosTimeBomb() {
    super("chronos-time-bomb");
    registerConfiguration(Config.class);
    setIcon(Material.TNT);
    setInterval(IDLE_TICK_INTERVAL_MILLIS);

    registerRecipe(AdaptRecipe.shapeless()
        .key("chronos-time-bomb")
        .ingredient(Material.SNOWBALL)
        .ingredient(Material.CLOCK)
        .ingredient(Material.DIAMOND)
        .ingredient(Material.SAND)
        .result(ChronoTimeBombItem.withData())
        .build());

    cooldowns = new ConcurrentHashMap<>();
    cooldownReadyNotify = new ConcurrentHashMap<>();
    fields = new CopyOnWriteArrayList<>();
    activeBombProjectiles = new ConcurrentHashMap<>();
    frozenEntities = new ConcurrentHashMap<>();
    frozenPlayers = new ConcurrentHashMap<>();
    immediateFieldFxBudget = new AtomicInteger(HARD_MAX_IMMEDIATE_FIELD_FX_PARTICLES);
    frozenStampKey = new NamespacedKey(Adapt.instance, "chronos_time_bomb_frozen");
    frozenPlayerStampKey = new NamespacedKey(Adapt.instance, "chronos_time_bomb_player_frozen");
    startupSweepDone = new AtomicBoolean(false);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ICE)
        .key("challenge_chronos_bomb_freeze_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ICE)
        .key("challenge_chronos_bomb_crowd_8")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_bomb_freeze_50", "chronos.time-bomb.projectiles-frozen", 50, 500);
  }

  @Override
  protected void onRuntimeActivated() {
    wakeRuntime();
    for (Player player : Bukkit.getOnlinePlayers()) {
      J.runEntity(player, () -> restoreStrandedFrozenPlayer(player));
    }
  }

  @Override
  public void unregister() {
    fields.clear();
    activeBombProjectiles.clear();
    cooldowns.clear();
    cooldownReadyNotify.clear();

    List<Map.Entry<UUID, FrozenPlayerState>> players = new ArrayList<>(frozenPlayers.entrySet());
    List<Map.Entry<UUID, FrozenEntityState>> entities = new ArrayList<>(frozenEntities.entrySet());
    frozenPlayers.clear();
    frozenEntities.clear();
    frozenPlayerCursor = Collections.emptyIterator();
    frozenEntityCursor = Collections.emptyIterator();

    for (Map.Entry<UUID, FrozenPlayerState> entry : players) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player != null) {
        boolean scheduled = J.runEntity(player, () -> unfreezePlayer(player, entry.getValue()));
        if (!scheduled && J.isOwnedByCurrentRegion(player)) {
          unfreezePlayer(player, entry.getValue());
        }
      }
    }
    for (Map.Entry<UUID, FrozenEntityState> entry : entities) {
      Entity entity = Bukkit.getEntity(entry.getKey());
      if (entity != null) {
        boolean scheduled = J.runEntity(entity, () -> unfreezeEntity(entity, entry.getValue()));
        if (!scheduled && J.isOwnedByCurrentRegion(entity)) {
          unfreezeEntity(entity, entry.getValue());
        }
      }
    }
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, C.YELLOW, "+ ", (getDurationTicks(level) / 20D) + "s", 2);
    statLore(v, C.RED, "* ", (getCooldownMillis() / 1000D) + "s", 3);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.TIME_BOMB_LORE4));
  }

  private double getRadius(int level) {
    double configured = getConfig().baseRadius + ((Math.max(1, level) - 1) * getConfig().radiusPerLevel);
    return Math.max(1D, Math.min(HARD_MAX_FIELD_RADIUS, configured));
  }

  private int getDurationTicks(int level) {
    return getConfig().baseDurationTicks + ((Math.max(1, level) - 1) * getConfig().durationPerLevelTicks);
  }

  private long getCooldownMillis() {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID id = player.getUniqueId();
    cooldowns.remove(id);
    cooldownReadyNotify.remove(id);
    FrozenPlayerState state = frozenPlayers.remove(id);
    if (state != null) {
      unfreezePlayer(player, state);
    }
    activeBombProjectiles.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    restoreStrandedFrozenPlayer(e.getPlayer());
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

    if (!hasActiveAdaptation(p)) {
      e.setCancelled(true);
      return;
    }

    if (ChronoTimeBombItem.io.ensureCooldownGroup(hand)) {
      if (handSlot == EquipmentSlot.OFF_HAND) {
        p.getInventory().setItemInOffHand(hand);
      } else {
        p.getInventory().setItemInMainHand(hand);
      }
    }

    long now = M.ms();
    long cooldown = cooldowns.getOrDefault(p.getUniqueId(), 0L);
    if (cooldown > now) {
      e.setCancelled(true);
      ItemCooldowns.pushGroup(p, ChronoTimeBombItem.COOLDOWN_GROUP, cooldown - now);
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

    int level = getActiveLevel(p);
    if (level <= 0) {
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

    long cooldownMillis = getCooldownMillis();
    cooldowns.put(p.getUniqueId(), now + cooldownMillis);
    cooldownReadyNotify.put(p.getUniqueId(), true);
    // Vanilla finishes the throw after this handler and would overwrite an
    // overlay pushed now with the item's group marker, so push on the next tick.
    J.runEntity(p, () -> {
      if (p.isOnline()) {
        ItemCooldowns.pushGroup(p, ChronoTimeBombItem.COOLDOWN_GROUP,
            cooldowns.getOrDefault(p.getUniqueId(), 0L) - M.ms());
      }
    }, 1);
    activeBombProjectiles.put(potion.getUniqueId(), new ArmedBombProjectile(p.getUniqueId(), level, now));
    wakeRuntime();

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
    long now = M.ms();
    TemporalField field = new TemporalField(
        ownerId,
        center,
        getRadius(level),
        now + (getDurationTicks(level) * 50L),
        level,
        now,
        now,
        now);
    TemporalField replaced = addFieldBounded(field);
    wakeRuntime();
    if (replaced != null && claimBudget(immediateFieldFxBudget, 25) == 25) {
      emitFieldThaw(replaced);
    }

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playTimeBombDetonate(center);
    }

    double fieldRadius = field.radius();
    Location detonateAt = center.clone();
    fx(detonateAt, FxPriority.GAMEPLAY)
        .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
        .particle(Particles.ENCHANTMENT_TABLE, 24, 0, 0.3D, 0, fieldRadius * 0.4D, 0.12D);
    timeline(detonateAt)
        .duration(4)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(fieldRadius + 24)
        .frame((f, tick, progress) -> f.ring(Particles.END_ROD, 0.5D + (fieldRadius * progress), 16, 0.2D))
        .start();

    Player owner = Bukkit.getPlayer(ownerId);
    if (owner != null) {
      xp(owner, center, getConfig().xpOnCast + (level * getConfig().xpPerLevel));
    }
  }

  private TemporalField addFieldBounded(TemporalField field) {
    int maximum = ChronosWorkBudget.bounded(getConfig().maxActiveFields, 1, HARD_MAX_ACTIVE_FIELDS);
    synchronized (fields) {
      TemporalField replaced = null;
      if (fields.size() >= maximum) {
        ArrayList<Long> expiries = new ArrayList<>(fields.size());
        for (TemporalField current : fields) {
          expiries.add(current.expiresAt());
        }
        int oldest = ChronosWorkBudget.oldestIndex(expiries);
        if (oldest >= 0) {
          replaced = fields.remove(oldest);
        }
      }
      fields.add(field);
      return replaced;
    }
  }

  private boolean freezeEntity(Entity entity, UUID fieldId) {
    if (entity instanceof Player) {
      return false;
    }

    UUID entityId = entity.getUniqueId();
    FrozenEntityState state = frozenEntities.get(entityId);
    boolean created = false;
    if (state == null) {
      Boolean hadAi = entity instanceof LivingEntity living ? living.hasAI() : null;
      FrozenEntityState candidate = new FrozenEntityState(entity.getVelocity(), entity.hasGravity(), hadAi);
      FrozenEntityState existing = frozenEntities.putIfAbsent(entityId, candidate);
      state = existing == null ? candidate : existing;
      created = existing == null;
    }

    state.addField(fieldId);
    if (created) {
      stampFrozenEntity(entity, state);
    } else if (getConfig().accumulateFrozenImpulse) {
      state.captureImpulse(entity.getVelocity(), getConfig().frozenImpulseMinMagnitude, getConfig().frozenImpulseSampleCap);
    }
    maintainFrozenEntity(entity);
    return created;
  }

  private void stampFrozenEntity(Entity entity, FrozenEntityState state) {
    byte flags = 0;
    if (state.gravity()) {
      flags |= 1;
    }
    Boolean hadAi = state.hadAi();
    if (hadAi != null) {
      flags |= 2;
      if (hadAi) {
        flags |= 4;
      }
    }
    entity.getPersistentDataContainer().set(frozenStampKey, PersistentDataType.BYTE, flags);
  }

  private void restoreStrandedFrozenEntity(Entity entity) {
    if (entity instanceof Player || frozenEntities.containsKey(entity.getUniqueId())) {
      return;
    }

    PersistentDataContainer container = entity.getPersistentDataContainer();
    Byte flags = container.get(frozenStampKey, PersistentDataType.BYTE);
    if (flags == null) {
      return;
    }

    container.remove(frozenStampKey);
    entity.setGravity((flags & 1) != 0);
    if (entity instanceof LivingEntity living && (flags & 2) != 0) {
      living.setAI((flags & 4) != 0);
    }
    entity.setFallDistance(0);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities()) {
      restoreStrandedFrozenEntity(entity);
    }
  }

  private void sweepStrandedFrozenEntities() {
    if (J.isFoliaThreading()) {
      return;
    }

    J.s(() -> {
      for (World world : Bukkit.getWorlds()) {
        for (Entity entity : world.getEntities()) {
          restoreStrandedFrozenEntity(entity);
        }
      }
    });
  }

  private void maintainFrozenEntity(Entity entity) {
    entity.setGravity(false);
    entity.setVelocity(new Vector());
    entity.setFallDistance(0);
    if (entity instanceof LivingEntity living) {
      living.setAI(false);
    }
  }

  private void freezePlayer(Player player, UUID fieldId) {
    UUID playerId = player.getUniqueId();
    FrozenPlayerState state = frozenPlayers.get(playerId);
    if (state == null) {
      FrozenPlayerState candidate = new FrozenPlayerState(player.getAllowFlight(), player.isFlying(), player.hasGravity());
      FrozenPlayerState existing = frozenPlayers.putIfAbsent(playerId, candidate);
      state = existing == null ? candidate : existing;
      if (existing == null) {
        stampFrozenPlayer(player, candidate);
      }
    }
    state.addField(fieldId);
    maintainFrozenPlayer(player);
  }

  private void stampFrozenPlayer(Player player, FrozenPlayerState state) {
    byte flags = 0;
    if (state.allowFlight()) {
      flags |= 1;
    }
    if (state.flying()) {
      flags |= 2;
    }
    if (state.gravity()) {
      flags |= 4;
    }
    player.getPersistentDataContainer().set(frozenPlayerStampKey, PersistentDataType.BYTE, flags);
  }

  private void restoreStrandedFrozenPlayer(Player player) {
    if (frozenPlayers.containsKey(player.getUniqueId())) {
      return;
    }

    PersistentDataContainer container = player.getPersistentDataContainer();
    Byte flags = container.get(frozenPlayerStampKey, PersistentDataType.BYTE);
    if (flags == null) {
      return;
    }

    container.remove(frozenPlayerStampKey);
    player.setGravity((flags & 4) != 0);
    if ((flags & 1) != 0) {
      player.setAllowFlight(true);
      player.setFlying((flags & 2) != 0);
      return;
    }
    player.setFlying(false);
    player.setAllowFlight(false);
  }

  private void maintainFrozenPlayer(Player player) {
    if (!getConfig().freezePlayersInAir || player.isOnGround()) {
      return;
    }
    player.setFallDistance(0);
    player.setAllowFlight(true);
    player.setFlying(true);
    player.setGravity(false);
    player.setVelocity(new Vector());
  }

  private void unfreezePlayer(Player player, FrozenPlayerState state) {
    player.getPersistentDataContainer().remove(frozenPlayerStampKey);
    player.setGravity(state.gravity());
    if (state.allowFlight()) {
      player.setAllowFlight(true);
      player.setFlying(state.flying());
      return;
    }
    player.setFlying(false);
    player.setAllowFlight(false);
  }

  private void unfreezeEntity(Entity entity, FrozenEntityState state) {
    entity.setGravity(state.gravity());
    entity.setVelocity(state.buildReleaseVelocity(getConfig().frozenImpulseReleaseCap));
    if (entity instanceof LivingEntity living && state.hadAi() != null) {
      living.setAI(state.hadAi());
    }
    entity.getPersistentDataContainer().remove(frozenStampKey);
  }

  private void evaluateFieldEntity(TemporalField field, Entity entity, FieldPulseState pulse, long now) {
    if (!isFieldActive(field, now) || !entity.isValid() || entity.isDead()) {
      return;
    }

    Location entityLocation = entity.getLocation();
    if (!field.contains(entityLocation)) {
      return;
    }

    if (entity instanceof Player player && player.getUniqueId().equals(field.owner())) {
      applyFieldDebuffs(player, getConfig().casterSlownessAmplifier);
      return;
    }

    if (isProtectedFriendlyOwned(field.owner(), entity)) {
      return;
    }

    Player owner = Bukkit.getPlayer(field.owner());
    if (owner == null) {
      return;
    }

    Location targetLocation = entityLocation.clone();
    boolean playerTarget = entity instanceof Player;
    J.runEntity(owner, () -> {
      if (!owner.isOnline()) {
        return;
      }
      boolean allowed = playerTarget ? canPVP(owner, targetLocation) : canPVE(owner, targetLocation);
      if (allowed) {
        J.runEntity(entity, () -> applyFieldEntity(field, entity, field.owner(), pulse, M.ms()));
      }
    });
  }

  private void applyFieldEntity(TemporalField field, Entity entity, UUID ownerId, FieldPulseState pulse, long now) {
    if (!isFieldActive(field, now) || !entity.isValid() || entity.isDead() || !field.contains(entity.getLocation())) {
      return;
    }

    if (entity instanceof Player player) {
      applyFieldDebuffs(player, getConfig().slownessAmplifier);
      freezePlayer(player, field.id());
      player.setVelocity(new Vector());
      recordFieldImpact(ownerId, false, false, pulse.recordSlowed());
      return;
    }

    boolean newlyFrozen = freezeEntity(entity, field.id());
    if (!newlyFrozen) {
      return;
    }

    if (pulse.claimFxParticles(3) == 3) {
      fx(entity.getLocation(), FxPriority.COMBAT).ring(Particles.END_ROD, 0.8D, 3, 0.15D);
    }
    recordFieldImpact(ownerId, true, entity instanceof Projectile, pulse.recordSlowed());
  }

  private void applyFieldDebuffs(Player player, int slownessAmplifier) {
    int durationTicks = getConfig().effectRefreshTicks;
    if (durationTicks <= 0) {
      return;
    }

    AdaptAttributeService service = AdaptAttributeService.get();
    double slow = slowSpeedScalar(slownessAmplifier);
    if (slow < 0D) {
      service.applyTimed(player, getName(), "slow", Attributes.MOVEMENT_SPEED,
          slow, AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    }

    int fatigueAmplifier = getConfig().fatigueAmplifier;
    double breakSlow = fatigueBreakScalar(fatigueAmplifier);
    if (breakSlow < 0D) {
      service.applyTimed(player, getName(), "fatigue", Attributes.BLOCK_BREAK_SPEED,
          breakSlow, AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    }

    double recoverySlow = fatigueAttackScalar(fatigueAmplifier);
    if (recoverySlow < 0D) {
      service.applyTimed(player, getName(), "recovery", Attributes.ATTACK_SPEED,
          recoverySlow, AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    }
  }

  static double slowSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * Math.max(0L, amplifier + 1L));
  }

  static double fatigueBreakScalar(int amplifier) {
    if (amplifier < 0) {
      return 0.0D;
    }
    return FATIGUE_DIG_FACTORS[Math.min(amplifier, 3)] - 1.0D;
  }

  static double fatigueAttackScalar(int amplifier) {
    return -Math.min(1.0D, 0.10D * Math.max(0L, amplifier + 1L));
  }

  private void recordFieldImpact(UUID ownerId, boolean newlyFrozen, boolean projectile, boolean crowdThreshold) {
    if (!newlyFrozen && !crowdThreshold) {
      return;
    }
    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null) {
      return;
    }

    J.runEntity(owner, () -> {
      if (!owner.isOnline()) {
        return;
      }
      if (newlyFrozen) {
        addStat(owner, "chronos.time-bomb.entities-slowed", 1);
        if (projectile) {
          addStat(owner, "chronos.time-bomb.projectiles-frozen", 1);
        }
      }
      if (crowdThreshold
          && AdaptConfig.get().isAdvancements()
          && !getPlayer(owner).getData().isGranted("challenge_chronos_bomb_crowd_8")) {
        getPlayer(owner).getAdvancementHandler().grant("challenge_chronos_bomb_crowd_8");
      }
    });
  }

  private boolean isFieldActive(TemporalField field, long now) {
    return field != null && field.expiresAt() > now && fields.contains(field);
  }

  private boolean isAuthorizedFieldAt(UUID fieldId, Location location, long now) {
    for (TemporalField field : fields) {
      if (field.id().equals(fieldId) && field.expiresAt() > now && field.contains(location)) {
        return true;
      }
    }
    return false;
  }

  private void spawnFieldSphere(TemporalField field, long now, int particleCount) {
    if (particleCount <= 0 || field.center().getWorld() == null) {
      return;
    }

    double radius = Math.max(0.1, field.radius());
    double spin = ((now % 3000L) / 3000.0D) * Math.PI * 2D;
    FxEmitter lattice = fx(field.center(), FxPriority.AMBIENT);
    double goldenAngle = Math.PI * (3D - Math.sqrt(5D));
    for (int i = 0; i < particleCount; i++) {
      double y = 1D - (2D * (i + 0.5D) / particleCount);
      double ringRadius = Math.sqrt(Math.max(0D, 1D - (y * y)));
      double angle = (goldenAngle * i) + spin;
      lattice.particle(Particles.END_ROD, 1,
          Math.cos(angle) * ringRadius * radius,
          y * radius,
          Math.sin(angle) * ringRadius * radius,
          0,
          0);
    }
  }

  private void emitFieldThaw(TemporalField field) {
    emitFieldThaw(field, 25);
  }

  private void emitFieldThaw(TemporalField field, int particleBudget) {
    if (field.center().getWorld() == null || particleBudget <= 0) {
      return;
    }

    Location center = field.center().clone();
    int portalParticles = Math.max(0, particleBudget - 1);
    J.runAt(center, () -> fx(center, FxPriority.TRANSITION)
        .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
        .particle(Particle.PORTAL, portalParticles, 0, 0, 0, field.radius() * 0.4D, 0.6D)
        .sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.6F, 0.9F));
  }

  @Override
  public void onTick() {
    if (startupSweepDone.compareAndSet(false, true)) {
      sweepStrandedFrozenEntities();
    }

    if (!hasRuntimeWork()) {
      setInterval(IDLE_TICK_INTERVAL_MILLIS);
      return;
    }

    long now = M.ms();
    immediateFieldFxBudget.set(Math.min(HARD_MAX_IMMEDIATE_FIELD_FX_PARTICLES,
        ChronosWorkBudget.bounded(getConfig().maxFieldFxParticlesPerTick,
            1, HARD_MAX_FIELD_FX_PARTICLES_PER_TICK)));
    cleanupBombProjectiles(now);
    notifyReadyCooldowns(now);

    int fieldFxBudget = ChronosWorkBudget.bounded(getConfig().maxFieldFxParticlesPerTick,
        1, HARD_MAX_FIELD_FX_PARTICLES_PER_TICK);
    fieldFxBudget = expireFields(now, fieldFxBudget);
    presentFields(now, fieldFxBudget);
    scheduleDueFieldScans(now);
    reconcileFrozenPlayers(now);
    reconcileFrozenEntities(now);
    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    scheduleNextTick(now);
  }

  private void wakeRuntime() {
    setInterval(ACTIVE_TICK_INTERVAL_MILLIS);
    if (!isBursting()) {
      retick();
    }
  }

  private boolean hasRuntimeWork() {
    return !cooldowns.isEmpty()
        || !cooldownReadyNotify.isEmpty()
        || !fields.isEmpty()
        || !activeBombProjectiles.isEmpty()
        || !frozenEntities.isEmpty()
        || !frozenPlayers.isEmpty();
  }

  private void scheduleNextTick(long now) {
    boolean continuousWork = !fields.isEmpty() || !frozenEntities.isEmpty() || !frozenPlayers.isEmpty();
    long nextDeadline = Long.MAX_VALUE;
    for (long deadline : cooldowns.values()) {
      nextDeadline = Math.min(nextDeadline, deadline);
    }
    for (ArmedBombProjectile projectile : activeBombProjectiles.values()) {
      nextDeadline = Math.min(nextDeadline, projectile.launchedAt() + PROJECTILE_TRACK_TTL_MS + 1L);
    }

    boolean unresolvedNotification = !cooldownReadyNotify.isEmpty() && cooldowns.isEmpty();
    setInterval(selectRuntimeInterval(now, continuousWork, nextDeadline, unresolvedNotification));
  }

  static long selectRuntimeInterval(
      long now,
      boolean continuousWork,
      long nextDeadline,
      boolean unresolvedNotification
  ) {
    if (continuousWork || unresolvedNotification || nextDeadline <= now) {
      return ACTIVE_TICK_INTERVAL_MILLIS;
    }
    if (nextDeadline == Long.MAX_VALUE) {
      return IDLE_TICK_INTERVAL_MILLIS;
    }
    return Math.max(ACTIVE_TICK_INTERVAL_MILLIS, nextDeadline - now);
  }

  private void notifyReadyCooldowns(long now) {
    for (UUID id : cooldownReadyNotify.keySet()) {
      if (cooldowns.getOrDefault(id, 0L) > now || cooldownReadyNotify.remove(id) == null) {
        continue;
      }
      Player player = Bukkit.getPlayer(id);
      if (player != null && getConfig().playClockSounds) {
        J.runEntity(player, () -> {
          if (player.isOnline()) {
            ChronosSoundFX.playCooldownReady(player);
          }
        });
      }
    }
  }

  private int expireFields(long now, int particleBudget) {
    int remaining = particleBudget;
    for (TemporalField field : fields) {
      if (field.expiresAt() > now || !fields.remove(field)) {
        continue;
      }
      int thawParticles = Math.min(25, remaining);
      if (thawParticles > 0) {
        emitFieldThaw(field, thawParticles);
        remaining -= thawParticles;
      }
    }
    return remaining;
  }

  private void presentFields(long now, int particleBudget) {
    if (fields.isEmpty()) {
      return;
    }

    ArrayList<TemporalField> activeFields = new ArrayList<>(fields);
    int fieldCount = activeFields.size();
    if (fieldCount == 0) {
      return;
    }

    int perFieldParticles = ChronosWorkBudget.bounded(getConfig().maxFieldFxParticlesPerField,
        1, HARD_MAX_FIELD_FX_PARTICLES_PER_FIELD);
    int requestedSphereParticles = Math.min(
        ChronosWorkBudget.bounded(getConfig().fieldSphereParticleCount, 1, HARD_MAX_FIELD_FX_PARTICLES_PER_FIELD),
        ChronosWorkBudget.bounded(getConfig().maxFieldSphereParticlesPerField,
            1, HARD_MAX_FIELD_FX_PARTICLES_PER_FIELD));
    ChronosWorkBudget.Allocation allocation = ChronosWorkBudget.allocate(
        fieldCount, particleBudget, perFieldParticles, presentationRotation);
    presentationRotation = Math.floorMod(presentationRotation + 1, fieldCount);

    for (int i = 0; i < fieldCount; i++) {
      TemporalField field = activeFields.get(i);
      boolean visualDue = getConfig().showFieldSphere && now >= field.nextVisualAt();
      boolean soundDue = getConfig().playClockSounds && now >= field.nextTickSoundAt();
      if (!visualDue && !soundDue) {
        continue;
      }

      int visualParticles = visualDue ? Math.min(requestedSphereParticles, allocation.grant(i)) : 0;
      if (visualParticles > 0) {
        field.setNextVisualAt(now + Math.max(1L, getConfig().fieldSphereRefreshMillis));
        Location center = field.center().clone();
        J.runAt(center, () -> spawnFieldSphere(field, now, visualParticles));
      }

      if (soundDue) {
        float pitch = calculateFieldPitch(field, now);
        field.setNextTickSoundAt(now + calculateFieldSoundInterval(field, now));
        ChronosSoundFX.playTimeFieldTick(field.center(), pitch);
      }
    }
  }

  private float calculateFieldPitch(TemporalField field, long now) {
    double progress = calculateFieldProgress(field, now);
    double pitchCurve = Math.pow(progress, Math.max(0.1D, getConfig().fieldTickPitchCurveExponent));
    return (float) (getConfig().fieldTickPitchStart
        + ((getConfig().fieldTickPitchEnd - getConfig().fieldTickPitchStart) * pitchCurve));
  }

  private long calculateFieldSoundInterval(TemporalField field, long now) {
    double progress = calculateFieldProgress(field, now);
    double acceleration = Math.max(0D, Math.min(0.95D, getConfig().fieldTickAccelerationFactor));
    return (long) Math.max(getConfig().fieldTickMinIntervalMillis,
        getConfig().fieldTickSoundIntervalMillis * (1D - (progress * acceleration)));
  }

  private double calculateFieldProgress(TemporalField field, long now) {
    long totalDurationMillis = Math.max(50L, getDurationTicks(field.level()) * 50L);
    long remainingMillis = Math.max(0L, field.expiresAt() - now);
    return Math.max(0D, Math.min(1D, 1D - (remainingMillis / (double) totalDurationMillis)));
  }

  private void scheduleDueFieldScans(long now) {
    if (fields.isEmpty()) {
      return;
    }

    ArrayList<TemporalField> dueFields = new ArrayList<>();
    long interval = ChronosWorkBudget.bounded(getConfig().fieldScanIntervalMillis,
        MIN_FIELD_SCAN_INTERVAL_MILLIS, MAX_FIELD_SCAN_INTERVAL_MILLIS);
    for (TemporalField field : fields) {
      if (field.expiresAt() > now && now >= field.nextScanAt()) {
        field.setNextScanAt(now + interval);
        dueFields.add(field);
      }
    }
    int fieldCount = dueFields.size();
    if (fieldCount == 0) {
      return;
    }

    int regionGlobal = ChronosWorkBudget.bounded(getConfig().maxRegionScansPerCycle,
        1, HARD_MAX_REGION_SCANS_PER_CYCLE);
    int regionPerField = ChronosWorkBudget.bounded(getConfig().maxRegionScansPerField,
        1, HARD_MAX_REGION_SCANS_PER_FIELD);
    int entityGlobal = ChronosWorkBudget.bounded(getConfig().maxEntitiesPerScanCycle,
        1, HARD_MAX_ENTITIES_PER_SCAN_CYCLE);
    int entityPerField = ChronosWorkBudget.bounded(getConfig().maxEntitiesPerField,
        1, HARD_MAX_ENTITIES_PER_FIELD);
    int fxGlobal = ChronosWorkBudget.bounded(getConfig().maxFreezeFxParticlesPerScan,
        1, HARD_MAX_FREEZE_FX_PARTICLES_PER_SCAN);
    int fxPerField = ChronosWorkBudget.bounded(getConfig().maxFreezeFxParticlesPerField,
        1, HARD_MAX_FREEZE_FX_PARTICLES_PER_FIELD);
    ChronosWorkBudget.Allocation regions = ChronosWorkBudget.allocate(
        fieldCount, regionGlobal, regionPerField, fieldScanRotation);
    ChronosWorkBudget.Allocation entities = ChronosWorkBudget.allocate(
        fieldCount, entityGlobal, entityPerField, fieldScanRotation);
    ChronosWorkBudget.Allocation particles = ChronosWorkBudget.allocate(
        fieldCount, fxGlobal, fxPerField, fieldScanRotation);
    fieldScanRotation = Math.floorMod(fieldScanRotation + 1, fieldCount);

    for (int i = 0; i < fieldCount; i++) {
      TemporalField field = dueFields.get(i);
      int regionGrant = regions.grant(i);
      int entityGrant = entities.grant(i);
      if (regionGrant <= 0 || entityGrant <= 0) {
        continue;
      }
      FieldPulseState pulse = new FieldPulseState(entityGrant, particles.grant(i));
      scheduleFieldRegions(field, regionGrant, pulse, now);
    }
  }

  private void scheduleFieldRegions(TemporalField field, int regionGrant, FieldPulseState pulse, long now) {
    World world = field.center().getWorld();
    if (world == null) {
      return;
    }

    int minChunkX = ((int) Math.floor(field.center().getX() - field.radius())) >> 4;
    int maxChunkX = ((int) Math.floor(field.center().getX() + field.radius())) >> 4;
    int minChunkZ = ((int) Math.floor(field.center().getZ() - field.radius())) >> 4;
    int maxChunkZ = ((int) Math.floor(field.center().getZ() + field.radius())) >> 4;
    int width = (maxChunkX - minChunkX) + 1;
    int depth = (maxChunkZ - minChunkZ) + 1;
    long regionCountLong = (long) width * depth;
    if (width <= 0 || depth <= 0 || regionCountLong <= 0L || regionCountLong > Integer.MAX_VALUE) {
      return;
    }

    int regionCount = (int) regionCountLong;
    int scanCount = Math.min(regionGrant, regionCount);
    int start = field.claimRegionStart(regionCount, scanCount);
    for (int i = 0; i < scanCount; i++) {
      int index = (start + i) % regionCount;
      int chunkX = minChunkX + (index % width);
      int chunkZ = minChunkZ + (index / width);
      Location regionCenter = new Location(world, (chunkX << 4) + 8, field.center().getY(), (chunkZ << 4) + 8);
      J.runAt(regionCenter, () -> scanFieldRegion(field, world, chunkX, chunkZ, pulse, now));
    }
  }

  private void scanFieldRegion(TemporalField field, World world, int chunkX, int chunkZ,
                               FieldPulseState pulse, long now) {
    if (!isFieldActive(field, now)) {
      return;
    }

    LoadedChunkAccess loadedChunks = new LoadedChunkAccess(world, 0);
    int blockX = (chunkX << 4) + 8;
    int blockZ = (chunkZ << 4) + 8;
    if (!loadedChunks.canRead(blockX, blockZ)) {
      return;
    }

    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    Entity[] chunkEntities = chunk.getEntities();
    int entityLimit = ChronosWorkBudget.bounded(getConfig().maxEntitiesPerRegion,
        1, HARD_MAX_ENTITIES_PER_REGION);
    int scheduled = 0;
    for (Entity entity : chunkEntities) {
      if (scheduled >= entityLimit || !pulse.tryClaimEntity()) {
        break;
      }
      scheduled++;
      J.runEntity(entity, () -> evaluateFieldEntity(field, entity, pulse, M.ms()));
    }
  }

  private void reconcileFrozenPlayers(long now) {
    int limit = Math.min(frozenPlayers.size(), ChronosWorkBudget.bounded(getConfig().maxFrozenPlayersPerTick,
        1, HARD_MAX_FROZEN_PLAYERS_PER_TICK));
    for (int processed = 0; processed < limit; processed++) {
      if (!frozenPlayerCursor.hasNext()) {
        frozenPlayerCursor = frozenPlayers.entrySet().iterator();
        if (!frozenPlayerCursor.hasNext()) {
          return;
        }
      }

      Map.Entry<UUID, FrozenPlayerState> entry = frozenPlayerCursor.next();
      UUID playerId = entry.getKey();
      FrozenPlayerState state = entry.getValue();
      Player player = Bukkit.getPlayer(playerId);
      if (player == null) {
        frozenPlayers.remove(playerId, state);
        continue;
      }
      J.runEntity(player, () -> reconcileFrozenPlayer(player, state, now));
    }
  }

  private void reconcileFrozenPlayer(Player player, FrozenPlayerState state, long now) {
    UUID playerId = player.getUniqueId();
    if (frozenPlayers.get(playerId) != state) {
      return;
    }
    if (!player.isOnline() || player.isDead()) {
      frozenPlayers.remove(playerId, state);
      return;
    }

    Location location = player.getLocation();
    state.retainFields(fieldId -> isAuthorizedFieldAt(fieldId, location, now));
    if (state.hasFields()) {
      maintainFrozenPlayer(player);
      return;
    }
    unfreezePlayer(player, state);
    frozenPlayers.remove(playerId, state);
  }

  private void reconcileFrozenEntities(long now) {
    int limit = Math.min(frozenEntities.size(), ChronosWorkBudget.bounded(getConfig().maxFrozenEntitiesPerTick,
        1, HARD_MAX_FROZEN_ENTITIES_PER_TICK));
    for (int processed = 0; processed < limit; processed++) {
      if (!frozenEntityCursor.hasNext()) {
        frozenEntityCursor = frozenEntities.entrySet().iterator();
        if (!frozenEntityCursor.hasNext()) {
          return;
        }
      }

      Map.Entry<UUID, FrozenEntityState> entry = frozenEntityCursor.next();
      UUID entityId = entry.getKey();
      FrozenEntityState state = entry.getValue();
      Entity entity = Bukkit.getEntity(entityId);
      if (entity == null) {
        frozenEntities.remove(entityId, state);
        continue;
      }
      J.runEntity(entity, () -> reconcileFrozenEntity(entity, state, now));
    }
  }

  private void reconcileFrozenEntity(Entity entity, FrozenEntityState state, long now) {
    UUID entityId = entity.getUniqueId();
    if (frozenEntities.get(entityId) != state) {
      return;
    }
    if (entity.isDead() || !entity.isValid()) {
      frozenEntities.remove(entityId, state);
      return;
    }

    Location location = entity.getLocation();
    state.retainFields(fieldId -> isAuthorizedFieldAt(fieldId, location, now));
    if (state.hasFields()) {
      if (getConfig().accumulateFrozenImpulse) {
        state.captureImpulse(entity.getVelocity(), getConfig().frozenImpulseMinMagnitude,
            getConfig().frozenImpulseSampleCap);
      }
      maintainFrozenEntity(entity);
      return;
    }
    unfreezeEntity(entity, state);
    frozenEntities.remove(entityId, state);
  }

  private void cleanupBombProjectiles(long now) {
    activeBombProjectiles.entrySet().removeIf(
        entry -> now - entry.getValue().launchedAt() > PROJECTILE_TRACK_TTL_MS);
  }

  private int claimBudget(AtomicInteger budget, int requested) {
    int current = budget.get();
    while (current >= requested) {
      if (budget.compareAndSet(current, current - requested)) {
        return requested;
      }
      current = budget.get();
    }
    return 0;
  }

  @ConfigDescription("Throw a crafted chrono bomb that creates a temporal field, slowing entities and freezing projectiles.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Time Bomb adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base temporal-field radius in blocks.", impact = "Level scaling can expand the field up to the hard 16-block radius limit.")
    double baseRadius = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional temporal-field radius per adaptation level.", impact = "Higher values reach the hard 16-block radius limit sooner.")
    double radiusPerLevel = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Duration Ticks for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Per Level Ticks for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durationPerLevelTicks = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownMillis = 15000;
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum concurrent temporal fields processed by this server.", impact = "Bounds active field state and replaces the field nearest expiry when full.")
    int maxActiveFields = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Interval between temporal field entity scans.", impact = "Values are constrained to 200-250 milliseconds to preserve feel while bounding scan frequency.")
    long fieldScanIntervalMillis = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum loaded-region scans shared by all temporal fields per scan cycle.", impact = "Bounds region scheduler and chunk-query work.")
    int maxRegionScansPerCycle = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum loaded-region scans assigned to one temporal field per scan cycle.", impact = "Prevents one large field from monopolizing scan work.")
    int maxRegionScansPerField = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum entities scheduled for temporal field evaluation per scan cycle.", impact = "Bounds aggregate entity scheduler work.")
    int maxEntitiesPerScanCycle = 512;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum entities assigned to one temporal field per scan cycle.", impact = "Shares entity work fairly across concurrent fields.")
    int maxEntitiesPerField = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum entities read from one owned loaded region during a field scan.", impact = "Bounds burst work in entity-dense chunks.")
    int maxEntitiesPerRegion = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum frozen non-player entities reconciled per tick.", impact = "Bounds retained-state checks and release work.")
    int maxFrozenEntitiesPerTick = 256;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum frozen players reconciled per tick.", impact = "Bounds player-owned retained-state checks and release work.")
    int maxFrozenPlayersPerTick = 128;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum field presentation particles shared by all temporal fields per tick.", impact = "Bounds aggregate field and thaw visual effects.")
    int maxFieldFxParticlesPerTick = 512;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum field presentation particles assigned to one temporal field per tick.", impact = "Prevents one field from monopolizing visual effects.")
    int maxFieldFxParticlesPerField = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum sphere particles emitted by one field refresh.", impact = "Bounds the visible lattice while retaining full field scale.")
    int maxFieldSphereParticlesPerField = 36;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum freeze-impact particles shared by all temporal fields per scan cycle.", impact = "Bounds combat visual effect work.")
    int maxFreezeFxParticlesPerScan = 256;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum freeze-impact particles assigned to one temporal field per scan cycle.", impact = "Shares combat visual effects fairly across fields.")
    int maxFreezeFxParticlesPerField = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Cast for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnCast = 28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Level for the Chronos Time Bomb adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerLevel = 3;

    public Config() {
      baseCost = 8;
      costFactor = 0.42;
      initialCost = 7;
    }
  }

  private static final class TemporalField {
    private final UUID id;
    private final UUID owner;
    private final Location center;
    private final double radius;
    private final long expiresAt;
    private final int level;
    private volatile long nextScanAt;
    private volatile long nextTickSoundAt;
    private volatile long nextVisualAt;
    private int regionCursor;

    private TemporalField(UUID owner, Location center, double radius, long expiresAt, int level,
                          long nextScanAt, long nextTickSoundAt, long nextVisualAt) {
      this.id = UUID.randomUUID();
      this.owner = owner;
      this.center = center.clone();
      this.radius = radius;
      this.expiresAt = expiresAt;
      this.level = level;
      this.nextScanAt = nextScanAt;
      this.nextTickSoundAt = nextTickSoundAt;
      this.nextVisualAt = nextVisualAt;
    }

    private UUID id() {
      return id;
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

    private long nextScanAt() {
      return nextScanAt;
    }

    private void setNextScanAt(long nextScanAt) {
      this.nextScanAt = nextScanAt;
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

    private boolean contains(Location location) {
      World fieldWorld = center.getWorld();
      return fieldWorld != null
          && location != null
          && fieldWorld.equals(location.getWorld())
          && center.distanceSquared(location) <= radius * radius;
    }

    private synchronized int claimRegionStart(int regionCount, int scanCount) {
      if (regionCount <= 0) {
        return 0;
      }
      int start = Math.floorMod(regionCursor, regionCount);
      regionCursor = (start + Math.max(0, scanCount)) % regionCount;
      return start;
    }
  }

  private static final class FrozenEntityState {
    private final Vector originalVelocity;
    private final boolean gravity;
    private final Boolean hadAi;
    private final Vector impulseDirectionAccumulator;
    private final Set<UUID> fieldIds;
    private double impulseMagnitudeAccumulator;
    private int impulseSamples;

    private FrozenEntityState(Vector originalVelocity, boolean gravity, Boolean hadAi) {
      this.originalVelocity = originalVelocity.clone();
      this.gravity = gravity;
      this.hadAi = hadAi;
      this.impulseDirectionAccumulator = new Vector();
      this.fieldIds = ConcurrentHashMap.newKeySet();
      this.impulseMagnitudeAccumulator = 0D;
      this.impulseSamples = 0;
    }

    private boolean gravity() {
      return gravity;
    }

    private Boolean hadAi() {
      return hadAi;
    }

    private void addField(UUID fieldId) {
      fieldIds.add(fieldId);
    }

    private void retainFields(Predicate<UUID> retain) {
      for (UUID fieldId : fieldIds) {
        if (!retain.test(fieldId)) {
          fieldIds.remove(fieldId);
        }
      }
    }

    private boolean hasFields() {
      return !fieldIds.isEmpty();
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

  private static final class FrozenPlayerState {
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean gravity;
    private final Set<UUID> fieldIds;

    private FrozenPlayerState(boolean allowFlight, boolean flying, boolean gravity) {
      this.allowFlight = allowFlight;
      this.flying = flying;
      this.gravity = gravity;
      this.fieldIds = ConcurrentHashMap.newKeySet();
    }

    private boolean allowFlight() {
      return allowFlight;
    }

    private boolean flying() {
      return flying;
    }

    private boolean gravity() {
      return gravity;
    }

    private void addField(UUID fieldId) {
      fieldIds.add(fieldId);
    }

    private void retainFields(Predicate<UUID> retain) {
      for (UUID fieldId : fieldIds) {
        if (!retain.test(fieldId)) {
          fieldIds.remove(fieldId);
        }
      }
    }

    private boolean hasFields() {
      return !fieldIds.isEmpty();
    }
  }

  private static final class FieldPulseState {
    private final AtomicInteger remainingEntities;
    private final AtomicInteger remainingFxParticles;
    private final AtomicInteger slowedEntities;
    private final AtomicBoolean crowdAwarded;

    private FieldPulseState(int entityBudget, int fxParticleBudget) {
      this.remainingEntities = new AtomicInteger(Math.max(0, entityBudget));
      this.remainingFxParticles = new AtomicInteger(Math.max(0, fxParticleBudget));
      this.slowedEntities = new AtomicInteger();
      this.crowdAwarded = new AtomicBoolean();
    }

    private boolean tryClaimEntity() {
      int current = remainingEntities.get();
      while (current > 0) {
        if (remainingEntities.compareAndSet(current, current - 1)) {
          return true;
        }
        current = remainingEntities.get();
      }
      return false;
    }

    private int claimFxParticles(int requested) {
      if (requested <= 0) {
        return 0;
      }
      int current = remainingFxParticles.get();
      while (current >= requested) {
        if (remainingFxParticles.compareAndSet(current, current - requested)) {
          return requested;
        }
        current = remainingFxParticles.get();
      }
      return 0;
    }

    private boolean recordSlowed() {
      return slowedEntities.incrementAndGet() >= 8 && crowdAwarded.compareAndSet(false, true);
    }
  }

  private record ArmedBombProjectile(UUID owner, int level, long launchedAt) {
  }
}
