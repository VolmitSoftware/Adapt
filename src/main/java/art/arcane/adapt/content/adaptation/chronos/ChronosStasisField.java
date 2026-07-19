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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class ChronosStasisField extends SimpleAdaptation<ChronosStasisField.Config> {
  static final int HARD_MAX_ACTIVE_BUBBLES = 256;
  static final int HARD_MAX_BUBBLES_PER_OWNER = 2;
  static final int HARD_MAX_FROZEN_PROJECTILES = 1_024;
  static final int HARD_MAX_BUBBLE_QUEUE_CHECKS_PER_TICK = 256;
  static final int HARD_MAX_BUBBLE_REGION_DISPATCHES_PER_TICK = 64;
  static final int HARD_MAX_RESTORES_PER_TICK = 128;
  static final int HARD_MAX_FROZEN_AUDITS_PER_TICK = 64;
  static final int HARD_MAX_INSPECTIONS_PER_WINDOW = 1_024;
  static final int HARD_MAX_ENTITY_HANDOFFS_PER_WINDOW = 512;
  static final int HARD_MAX_OUTLINE_POINTS_PER_WINDOW = 512;
  static final int HARD_MAX_FREEZE_FX_PER_WINDOW = 32;
  static final int HARD_MAX_CAST_FX_PER_WINDOW = 16;
  static final int HARD_MAX_EXPIRY_FX_PER_WINDOW = 32;
  static final int HARD_MAX_INSPECTIONS_PER_BUBBLE = 64;
  static final int HARD_MAX_INITIAL_HANDOFFS_PER_BUBBLE = 32;
  static final long WORK_WINDOW_NANOS = 50_000_000L;
  private static final long MAX_BUBBLE_SCHEDULING_DELAY_MILLIS = 200L;

  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, StasisBubble> bubbles = new ConcurrentHashMap<>();
  private final Map<UUID, FrozenProjectileState> frozenProjectiles = new ConcurrentHashMap<>();
  private final Map<UUID, Set<UUID>> frozenByBubble = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<UUID> restorationQueue = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<UUID> frozenAuditQueue = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedRestorations = new AtomicInteger();
  private final AtomicLong lifecycle = new AtomicLong(1L);
  private final AtomicBoolean acceptingCasts = new AtomicBoolean(true);
  private final StasisBubbleCoordinator coordinator = new StasisBubbleCoordinator(
      HARD_MAX_ACTIVE_BUBBLES,
      HARD_MAX_BUBBLES_PER_OWNER
  );
  private final StasisCapacityGate frozenCapacity = new StasisCapacityGate(HARD_MAX_FROZEN_PROJECTILES);
  private final StasisWindowBudget workBudget = new StasisWindowBudget(
      HARD_MAX_INSPECTIONS_PER_WINDOW,
      HARD_MAX_ENTITY_HANDOFFS_PER_WINDOW,
      HARD_MAX_OUTLINE_POINTS_PER_WINDOW,
      HARD_MAX_FREEZE_FX_PER_WINDOW,
      HARD_MAX_CAST_FX_PER_WINDOW,
      HARD_MAX_EXPIRY_FX_PER_WINDOW,
      WORK_WINDOW_NANOS
  );

  public ChronosStasisField() {
    super("chronos-stasis-field");
    registerConfiguration(Config.class);
    setIcon(Material.AMETHYST_SHARD);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.AMETHYST_SHARD)
        .key("challenge_chronos_stasis_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CLOCK)
            .key("challenge_chronos_stasis_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_chronos_stasis_50", "chronos.stasis-field.casts", 50, 400);
    registerMilestone("challenge_chronos_stasis_500", "chronos.stasis-field.casts", 500, 1500);
  }

  @Override
  public void unregister() {
    acceptingCasts.set(false);
    lifecycle.incrementAndGet();
    coordinator.clear();
    bubbles.clear();
    restorationQueue.clear();
    frozenAuditQueue.clear();
    queuedRestorations.set(0);

    List<FrozenProjectileState> frozen = new ArrayList<>(frozenProjectiles.values());
    frozenProjectiles.clear();
    frozenByBubble.clear();
    frozenCapacity.reset();
    boolean remove = getConfig().removeProjectilesOnExpire;
    for (FrozenProjectileState state : frozen) {
      J.runEntity(state.projectile, () -> restoreProjectileValuesOwned(state, remove));
    }
    cooldowns.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + " " + Localizer.dLocalize("chronos.stasis_field.lore1"));
    v.addLore(C.YELLOW + "+ " + Form.duration(getDurationMillis(level), 1) + " " + Localizer.dLocalize("chronos.stasis_field.lore2"));
    v.addLore(C.RED + "* " + Form.duration(getCooldownMillis(), 1) + " " + Localizer.dLocalize("chronos.stasis_field.lore3"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.stasis_field.lore4"));
    if (getConfig().consumeShard) {
      v.addLore(C.RED + "* " + Localizer.dLocalize("chronos.stasis_field.lore_cost_shard"));
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID ownerId = player.getUniqueId();
    cooldowns.remove(ownerId);
    for (StasisBubbleCoordinator.Lease lease : coordinator.removeOwner(ownerId)) {
      StasisBubble bubble = bubbles.remove(lease.bubbleId());
      if (bubble != null && bubble.generation == lease.generation()) {
        releaseBubbleProjectiles(bubble);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (e.getEntity() instanceof Projectile projectile) {
      forgetFrozenProjectile(projectile.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player player = e.getPlayer();
    if (!player.isSneaking()) {
      return;
    }
    EquipmentSlot hand = e.getHand();
    if (hand == null) {
      return;
    }

    ItemStack held = hand == EquipmentSlot.OFF_HAND
        ? player.getInventory().getItemInOffHand()
        : player.getInventory().getItemInMainHand();
    if (held.getType() != Material.AMETHYST_SHARD) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(player, player.getLocation());
    if (context == null) {
      return;
    }
    e.setCancelled(true);
    if (!acceptingCasts.get()) {
      return;
    }

    long now = M.ms();
    UUID ownerId = player.getUniqueId();
    long until = cooldowns.getOrDefault(ownerId, 0L);
    if (until > now) {
      playReject(player);
      return;
    }
    cooldowns.remove(ownerId, until);

    UUID bubbleId = UUID.randomUUID();
    long generation = coordinator.reserve(ownerId, bubbleId);
    if (generation < 0L) {
      playReject(player);
      return;
    }

    Location center = player.getLocation().clone().add(0D, getCenterYOffset(), 0D);
    StasisBubble bubble = new StasisBubble(
        bubbleId,
        generation,
        ownerId,
        player,
        center,
        getRadius(context.level()),
        now + getDurationMillis(context.level()),
        now,
        0L,
        lifecycle.get()
    );
    bubbles.put(bubbleId, bubble);
    if (!coordinator.activate(bubbleId, generation)) {
      bubbles.remove(bubbleId, bubble);
      coordinator.remove(bubbleId, generation);
      playReject(player);
      return;
    }
    if (!acceptingCasts.get()) {
      retireBubble(bubble, false);
      return;
    }

    if (getConfig().consumeShard) {
      held.setAmount(held.getAmount() - 1);
    }
    cooldowns.put(ownerId, now + getCooldownMillis());
    emitDeploy(player, bubble, context.level());
  }

  @Override
  public void onTick() {
    processRestorationQueue();
    auditFrozenProjectiles();
    processBubbles(M.ms());
  }

  private void processBubbles(long now) {
    List<StasisBubbleCoordinator.Lease> leases = coordinator.take(
        HARD_MAX_BUBBLE_QUEUE_CHECKS_PER_TICK
    );
    int dispatched = 0;
    for (StasisBubbleCoordinator.Lease lease : leases) {
      StasisBubble bubble = bubbles.get(lease.bubbleId());
      if (bubble == null || bubble.generation != lease.generation()) {
        coordinator.remove(lease.bubbleId(), lease.generation());
        continue;
      }
      if (now >= bubble.expiresAt) {
        retireBubble(bubble, true);
        continue;
      }
      if (dispatched >= HARD_MAX_BUBBLE_REGION_DISPATCHES_PER_TICK
          || now < bubble.nextPulseAt
          || !bubble.pulsePending.compareAndSet(false, true)) {
        continue;
      }

      boolean scheduled = J.runAt(bubble.center, () -> pulseBubbleRegionOwned(bubble));
      if (scheduled) {
        dispatched++;
      } else {
        bubble.pulsePending.set(false);
      }
    }
  }

  private void pulseBubbleRegionOwned(StasisBubble bubble) {
    try {
      long now = M.ms();
      if (!isBubbleCurrent(bubble)) {
        return;
      }
      if (now >= bubble.expiresAt) {
        retireBubble(bubble, true);
        return;
      }

      bubble.nextPulseAt = now + getPulseIntervalMillis();
      collectBubbleCandidatesRegionOwned(bubble);
      if (now >= bubble.nextVisualAt) {
        spawnOutlineRegionOwned(bubble, now);
        bubble.nextVisualAt = now + getOutlineRefreshMillis();
      }
    } finally {
      bubble.pulsePending.set(false);
    }
  }

  private void collectBubbleCandidatesRegionOwned(StasisBubble bubble) {
    Location center = bubble.center;
    double radius = bubble.radius;
    if (!isAreaLoaded(center, radius)) {
      return;
    }

    World world = center.getWorld();
    int inspected = 0;
    int handoffs = 0;
    for (Entity entity : world.getNearbyEntities(
        center,
        radius,
        radius,
        radius,
        candidate -> candidate instanceof Projectile
            || (candidate instanceof LivingEntity && !(candidate instanceof Player)))) {
      if (inspected >= HARD_MAX_INSPECTIONS_PER_BUBBLE
          || handoffs >= HARD_MAX_INITIAL_HANDOFFS_PER_BUBBLE
          || !workBudget.tryInspection()) {
        break;
      }
      inspected++;
      if (!workBudget.tryEntityHandoff()) {
        break;
      }
      if (J.runEntity(entity, () -> inspectCandidateOwned(bubble, entity))) {
        handoffs++;
      }
    }
  }

  private void inspectCandidateOwned(StasisBubble bubble, Entity entity) {
    if (!isBubbleCurrent(bubble) || !entity.isValid() || entity.isDead()) {
      return;
    }
    Location location = entity.getLocation();
    if (!insideBubble(bubble, location)) {
      return;
    }

    if (entity instanceof Projectile projectile) {
      freezeProjectileOwned(bubble, projectile);
      return;
    }
    if (!(entity instanceof LivingEntity living) || living instanceof Player
        || isProtectedFriendlyOwned(bubble.ownerId, living)) {
      return;
    }

    LivingTargetSnapshot snapshot = new LivingTargetSnapshot(living, location);
    if (!workBudget.tryEntityHandoff()) {
      return;
    }
    J.runEntity(bubble.owner, () -> validateLivingTargetOwnerOwned(bubble, snapshot));
  }

  private void validateLivingTargetOwnerOwned(StasisBubble bubble, LivingTargetSnapshot target) {
    if (!isBubbleCurrent(bubble) || !bubble.owner.isOnline()
        || !canPVE(bubble.owner, target.location)) {
      return;
    }
    if (!workBudget.tryEntityHandoff()) {
      return;
    }
    J.runEntity(target.entity, () -> applyLivingStasisOwned(bubble, target.entity));
  }

  private void applyLivingStasisOwned(StasisBubble bubble, LivingEntity living) {
    if (!isBubbleCurrent(bubble) || !living.isValid() || living.isDead()
        || !insideBubble(bubble, living.getLocation())
        || isProtectedFriendlyOwned(bubble.ownerId, living)) {
      return;
    }

    int refreshTicks = getEffectRefreshTicks();
    if (refreshTicks <= 0) {
      return;
    }
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(
        living,
        getName(),
        "slow",
        Attributes.MOVEMENT_SPEED,
        stasisSlowScalar(getSlownessAmplifier()),
        AttributeModifier.Operation.MULTIPLY_SCALAR_1,
        refreshTicks
    );
    double jumpScalar = jumpLockScalar(getJumpLockAmplifier());
    if (jumpScalar < 0D) {
      attributes.applyTimed(
          living,
          getName(),
          "jump",
          Attributes.JUMP_STRENGTH,
          jumpScalar,
          AttributeModifier.Operation.MULTIPLY_SCALAR_1,
          refreshTicks
      );
    }
  }

  private void freezeProjectileOwned(StasisBubble bubble, Projectile projectile) {
    UUID projectileId = projectile.getUniqueId();
    FrozenProjectileState current = frozenProjectiles.get(projectileId);
    if (current != null) {
      if (current.bubbleId.equals(bubble.id) && !current.restorationQueued.get()) {
        projectile.setGravity(false);
        projectile.setVelocity(new Vector());
      }
      return;
    }
    if (!frozenCapacity.tryAcquire()) {
      return;
    }

    FrozenProjectileState state = new FrozenProjectileState(
        projectileId,
        projectile,
        bubble.id,
        bubble.generation,
        projectile.getVelocity().clone(),
        projectile.hasGravity(),
        projectile.isPersistent()
    );
    FrozenProjectileState existing = frozenProjectiles.putIfAbsent(projectileId, state);
    if (existing != null) {
      frozenCapacity.release();
      return;
    }

    frozenByBubble.computeIfAbsent(bubble.id, ignored -> ConcurrentHashMap.newKeySet()).add(projectileId);
    frozenAuditQueue.add(projectileId);
    projectile.setPersistent(false);
    projectile.setGravity(false);
    projectile.setVelocity(new Vector());
    Adapt.instance.getAdaptServer().addStat(
        bubble.ownerId,
        "chronos.stasis-field.projectiles-frozen",
        1D
    );
    if (workBudget.tryFreezeFx()) {
      fx(projectile.getLocation(), FxPriority.COMBAT)
          .particle(Particles.END_ROD, 3, 0D, 0D, 0D, 0.1D, 0D);
    }
    if (!isBubbleCurrent(bubble)) {
      restoreOrphanedProjectileOwned(state);
    }
  }

  private boolean isProtectedFriendlyOwned(UUID ownerId, LivingEntity target) {
    if (target instanceof ArmorStand stand && stand.isMarker()) {
      return true;
    }
    if (target.isInvulnerable() || target.hasMetadata("NPC")
        || TragoulSkeletalServant.isServant(target)) {
      return true;
    }
    if (target instanceof Tameable tameable && tameable.isTamed()) {
      AnimalTamer tamer = tameable.getOwner();
      return tamer != null && ownerId.equals(tamer.getUniqueId());
    }
    return false;
  }

  private void retireBubble(StasisBubble bubble, boolean showFx) {
    if (!coordinator.remove(bubble.id, bubble.generation)) {
      return;
    }
    bubbles.remove(bubble.id, bubble);
    releaseBubbleProjectiles(bubble);
    if (showFx) {
      emitExpiry(bubble);
    }
  }

  private void releaseBubbleProjectiles(StasisBubble bubble) {
    Set<UUID> projectileIds = frozenByBubble.remove(bubble.id);
    if (projectileIds == null) {
      return;
    }
    for (UUID projectileId : projectileIds) {
      FrozenProjectileState state = frozenProjectiles.get(projectileId);
      if (state != null && state.bubbleId.equals(bubble.id)
          && state.bubbleGeneration == bubble.generation) {
        queueRestoration(state);
      }
    }
  }

  private void queueRestoration(FrozenProjectileState state) {
    if (!state.restorationQueued.compareAndSet(false, true)) {
      return;
    }
    if (queuedRestorations.incrementAndGet() > HARD_MAX_FROZEN_PROJECTILES) {
      queuedRestorations.decrementAndGet();
      state.restorationQueued.set(false);
      return;
    }
    restorationQueue.add(state.projectileId);
  }

  private void processRestorationQueue() {
    for (int index = 0; index < HARD_MAX_RESTORES_PER_TICK; index++) {
      UUID projectileId = restorationQueue.poll();
      if (projectileId == null) {
        return;
      }
      queuedRestorations.decrementAndGet();
      FrozenProjectileState state = frozenProjectiles.get(projectileId);
      if (state == null) {
        continue;
      }
      boolean scheduled = J.runEntity(state.projectile, () -> restoreProjectileOwned(state));
      if (!scheduled) {
        state.restorationQueued.set(false);
        queueRestoration(state);
      }
    }
  }

  private void restoreProjectileOwned(FrozenProjectileState state) {
    if (!frozenProjectiles.remove(state.projectileId, state)) {
      return;
    }
    removeFrozenIndex(state);
    frozenCapacity.release();
    restoreProjectileValuesOwned(state, getConfig().removeProjectilesOnExpire);
  }

  private void restoreOrphanedProjectileOwned(FrozenProjectileState state) {
    if (frozenProjectiles.remove(state.projectileId, state)) {
      removeFrozenIndex(state);
      frozenCapacity.release();
    }
    restoreProjectileValuesOwned(state, getConfig().removeProjectilesOnExpire);
  }

  private void restoreProjectileValuesOwned(FrozenProjectileState state, boolean remove) {
    Projectile projectile = state.projectile;
    if (!projectile.isValid() || projectile.isDead()) {
      return;
    }
    if (remove) {
      projectile.remove();
      return;
    }
    projectile.setPersistent(state.persistent);
    projectile.setGravity(state.gravity);
    projectile.setVelocity(state.velocity);
  }

  private void forgetFrozenProjectile(UUID projectileId) {
    FrozenProjectileState state = frozenProjectiles.remove(projectileId);
    if (state == null) {
      return;
    }
    removeFrozenIndex(state);
    frozenCapacity.release();
  }

  private void removeFrozenIndex(FrozenProjectileState state) {
    Set<UUID> projectileIds = frozenByBubble.get(state.bubbleId);
    if (projectileIds == null) {
      return;
    }
    projectileIds.remove(state.projectileId);
    if (projectileIds.isEmpty()) {
      frozenByBubble.remove(state.bubbleId, projectileIds);
    }
  }

  private void auditFrozenProjectiles() {
    for (int index = 0; index < HARD_MAX_FROZEN_AUDITS_PER_TICK; index++) {
      UUID projectileId = frozenAuditQueue.poll();
      if (projectileId == null) {
        return;
      }
      FrozenProjectileState state = frozenProjectiles.get(projectileId);
      if (state == null) {
        continue;
      }
      if (coordinator.isCurrent(state.bubbleId, state.bubbleGeneration)
          && !state.restorationQueued.get()) {
        frozenAuditQueue.add(projectileId);
      } else {
        queueRestoration(state);
      }
    }
  }

  private boolean isBubbleCurrent(StasisBubble bubble) {
    return lifecycle.get() == bubble.lifecycleToken
        && bubbles.get(bubble.id) == bubble
        && coordinator.isCurrent(bubble.id, bubble.generation);
  }

  private boolean insideBubble(StasisBubble bubble, Location location) {
    return location.getWorld() == bubble.center.getWorld()
        && location.distanceSquared(bubble.center) <= bubble.radiusSquared;
  }

  private boolean isAreaLoaded(Location center, double radius) {
    World world = center.getWorld();
    int minChunkX = ((int) Math.floor(center.getX() - radius)) >> 4;
    int maxChunkX = ((int) Math.floor(center.getX() + radius)) >> 4;
    int minChunkZ = ((int) Math.floor(center.getZ() - radius)) >> 4;
    int maxChunkZ = ((int) Math.floor(center.getZ() + radius)) >> 4;
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return false;
        }
      }
    }
    return true;
  }

  private void emitDeploy(Player player, StasisBubble bubble, int level) {
    if (getConfig().playClockSounds) {
      ChronosSoundFX.playTimeBombDetonate(bubble.center);
    }
    if (workBudget.tryCastFx()) {
      double radius = bubble.radius;
      Location center = bubble.center.clone();
      fx(center, FxPriority.GAMEPLAY)
          .particle(Particle.FLASH, 1, 0D, 0D, 0D, 0D, 0D)
          .dustRing(Color.fromRGB(120, 200, 255), radius, 24, 1.2F)
          .chord(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6F, 1.5F,
              Sound.BLOCK_GLASS_PLACE, 0.5F, 0.7F);
      timeline(center)
          .duration(4)
          .priority(FxPriority.GAMEPLAY)
          .cullRadius(radius + 16D)
          .frame((emitter, tick, progress) -> emitter.dome(
              Particles.END_ROD,
              radius * (0.4D + (0.6D * progress)),
              16
          ))
          .start();
    }
    addStat(player, "chronos.stasis-field.casts", 1D);
    xp(player, bubble.center, getCastXp(level));
  }

  private void spawnOutlineRegionOwned(StasisBubble bubble, long now) {
    int points = getOutlineParticleCount();
    double radius = bubble.radius;
    double spin = ((now % 4_000L) / 4_000D) * Math.PI * 2D;
    double golden = Math.PI * (3D - Math.sqrt(5D));
    FxEmitter shell = fx(bubble.center, FxPriority.AMBIENT);
    for (int index = 0; index < points; index++) {
      if (!workBudget.tryOutlinePoint()) {
        break;
      }
      double vertical = points == 1 ? 0D : (((2D * index) / (points - 1D)) - 1D);
      double ringRadius = Math.sqrt(Math.max(0D, 1D - (vertical * vertical)));
      double angle = (golden * index) + spin;
      shell.particle(
          Particles.END_ROD,
          1,
          Math.cos(angle) * ringRadius * radius,
          vertical * radius,
          Math.sin(angle) * ringRadius * radius,
          0D,
          0D
      );
    }
    if (workBudget.tryOutlinePoint()) {
      shell.particle(Particle.WAX_ON, 2, 0D, 0D, 0D, radius * 0.35D, 0D);
    }
  }

  private void emitExpiry(StasisBubble bubble) {
    if (!workBudget.tryExpiryFx()) {
      return;
    }
    Location center = bubble.center.clone();
    J.runAt(center, () -> fx(center, FxPriority.TRANSITION)
        .particle(Particle.FLASH, 1, 0D, 0D, 0D, 0D, 0D)
        .particle(Particle.PORTAL, 20, 0D, 0D, 0D, bubble.radius * 0.4D, 0.5D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.5F, 0.9F));
  }

  private void playReject(Player player) {
    if (getConfig().playClockSounds) {
      ChronosSoundFX.playClockReject(player);
    }
  }

  private double getRadius(int level) {
    double configured = getConfig().baseRadius
        + ((Math.max(1, level) - 1D) * getConfig().radiusPerLevel);
    return clamp(configured, 1D, 12D);
  }

  private long getDurationMillis(int level) {
    long configured = getConfig().baseDurationMillis
        + ((Math.max(1, level) - 1L) * getConfig().durationPerLevelMillis);
    return clamp(configured, 500L, 30_000L);
  }

  private long getCooldownMillis() {
    return clamp(getConfig().cooldownMillis, 0L, 3_600_000L);
  }

  private double getCenterYOffset() {
    return clamp(getConfig().centerYOffset, -8D, 8D);
  }

  private int getSlownessAmplifier() {
    return clamp(getConfig().slownessAmplifier, 0, 10);
  }

  private int getJumpLockAmplifier() {
    return clamp(getConfig().jumpLockAmplifier, -10, 10);
  }

  static double stasisSlowScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  static double jumpLockScalar(int amplifier) {
    return Math.max(-1.0D, Math.min(0.0D, amplifier / 6.0D));
  }

  private int getEffectRefreshTicks() {
    long maximumGap = getPulseIntervalMillis() + MAX_BUBBLE_SCHEDULING_DELAY_MILLIS;
    int bufferedMinimum = (int) Math.ceil(maximumGap / 50D) + 4;
    return Math.max(bufferedMinimum, clamp(getConfig().effectRefreshTicks, 5, 100));
  }

  private long getPulseIntervalMillis() {
    return clamp(getConfig().pulseIntervalMillis, 200L, 500L);
  }

  private int getOutlineParticleCount() {
    return clamp(getConfig().outlineParticleCount, 4, 24);
  }

  private long getOutlineRefreshMillis() {
    return clamp(getConfig().outlineRefreshMillis, 100L, 2_000L);
  }

  private double getCastXp(int level) {
    double configured = getConfig().xpOnCast + (Math.max(1, level) * getConfig().xpPerLevel);
    return clamp(configured, 0D, 10_000D);
  }

  private int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private long clamp(long value, long minimum, long maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static final class StasisBubble {
    private final UUID id;
    private final long generation;
    private final UUID ownerId;
    private final Player owner;
    private final Location center;
    private final double radius;
    private final double radiusSquared;
    private final long expiresAt;
    private final long lifecycleToken;
    private final AtomicBoolean pulsePending = new AtomicBoolean();
    private volatile long nextPulseAt;
    private volatile long nextVisualAt;

    private StasisBubble(UUID id, long generation, UUID ownerId, Player owner,
                         Location center, double radius, long expiresAt,
                         long nextPulseAt, long nextVisualAt, long lifecycleToken) {
      this.id = id;
      this.generation = generation;
      this.ownerId = ownerId;
      this.owner = owner;
      this.center = center;
      this.radius = radius;
      this.radiusSquared = radius * radius;
      this.expiresAt = expiresAt;
      this.nextPulseAt = nextPulseAt;
      this.nextVisualAt = nextVisualAt;
      this.lifecycleToken = lifecycleToken;
    }
  }

  private static final class FrozenProjectileState {
    private final UUID projectileId;
    private final Projectile projectile;
    private final UUID bubbleId;
    private final long bubbleGeneration;
    private final Vector velocity;
    private final boolean gravity;
    private final boolean persistent;
    private final AtomicBoolean restorationQueued = new AtomicBoolean();

    private FrozenProjectileState(UUID projectileId, Projectile projectile, UUID bubbleId,
                                  long bubbleGeneration, Vector velocity, boolean gravity,
                                  boolean persistent) {
      this.projectileId = projectileId;
      this.projectile = projectile;
      this.bubbleId = bubbleId;
      this.bubbleGeneration = bubbleGeneration;
      this.velocity = velocity;
      this.gravity = gravity;
      this.persistent = persistent;
    }
  }

  private record LivingTargetSnapshot(LivingEntity entity, Location location) {
  }

  @ConfigDescription("Sneak and right click with an amethyst shard to deploy a stasis bubble that freezes projectiles and locks down mobs inside.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls clock sounds for Chronos Stasis Field.", impact = "True enables deployment and rejection sounds.")
    boolean playClockSounds = true;
    @ConfigDoc(value = "Consumes the amethyst shard used to deploy a stasis bubble.", impact = "True makes each admitted cast cost one shard.")
    boolean consumeShard = true;
    @ConfigDoc(value = "Base radius of the stasis bubble in blocks.", impact = "Higher values affect a larger area within the hard radius limit.")
    double baseRadius = 3.5D;
    @ConfigDoc(value = "Extra bubble radius granted per adaptation level.", impact = "Higher values make leveling expand the bubble faster.")
    double radiusPerLevel = 0.75D;
    @ConfigDoc(value = "Base bubble lifetime in milliseconds.", impact = "Higher values keep the bubble active longer within the hard duration limit.")
    long baseDurationMillis = 3_000L;
    @ConfigDoc(value = "Extra bubble lifetime in milliseconds per adaptation level.", impact = "Higher values make leveling extend the bubble duration faster.")
    long durationPerLevelMillis = 750L;
    @ConfigDoc(value = "Cooldown between bubble deployments in milliseconds.", impact = "Higher values force longer waits between casts.")
    long cooldownMillis = 20_000L;
    @ConfigDoc(value = "Vertical offset of the bubble center above the caster.", impact = "Higher values raise the bubble center off the ground.")
    double centerYOffset = 1D;
    @ConfigDoc(value = "Slowness amplifier applied to mobs inside the bubble.", impact = "Higher values slow trapped mobs more severely within the supported effect range.")
    int slownessAmplifier = 5;
    @ConfigDoc(value = "Jump suppression amplifier applied to mobs inside the bubble as a jump_strength reduction scaling linearly from 0 (none) to -6 (full lock); positive values apply no modifier.", impact = "More negative values suppress jumping more strongly; -6 or below locks jumping entirely.")
    int jumpLockAmplifier = -6;
    @ConfigDoc(value = "Duration in ticks of each refreshed stasis pulse.", impact = "The runtime preserves enough buffering to cover the bounded pulse scheduler delay.")
    int effectRefreshTicks = 20;
    @ConfigDoc(value = "Milliseconds between bubble scans.", impact = "Lower values refresh effects sooner within the supported pulse range.")
    long pulseIntervalMillis = 250L;
    @ConfigDoc(value = "Removes frozen projectiles when the bubble expires instead of restoring their motion.", impact = "True deletes trapped projectiles; false releases their buffered motion.")
    boolean removeProjectilesOnExpire = false;
    @ConfigDoc(value = "Number of outline particles requested per visual refresh.", impact = "Higher values make the edge denser within per-bubble and global FX limits.")
    int outlineParticleCount = 10;
    @ConfigDoc(value = "Milliseconds between bubble outline refreshes.", impact = "Lower values redraw the outline more often within the global FX limit.")
    long outlineRefreshMillis = 400L;
    @ConfigDoc(value = "XP granted when an admitted bubble is deployed.", impact = "Higher values grant more skill XP per cast.")
    double xpOnCast = 22D;
    @ConfigDoc(value = "Extra XP granted per adaptation level on an admitted cast.", impact = "Higher values scale cast XP faster.")
    double xpPerLevel = 3D;

    public Config() {
      baseCost = 7;
      costFactor = 0.4D;
      initialCost = 6;
    }
  }
}

final class StasisBubbleCoordinator {
  private final int activeLimit;
  private final int ownerLimit;
  private final Map<UUID, Slot> slots = new HashMap<>();
  private final Map<UUID, Set<UUID>> ownerSlots = new HashMap<>();
  private final ArrayDeque<UUID> activeQueue = new ArrayDeque<>();
  private long nextGeneration;

  StasisBubbleCoordinator(int activeLimit, int ownerLimit) {
    if (activeLimit <= 0 || ownerLimit <= 0) {
      throw new IllegalArgumentException("Stasis bubble limits must be positive");
    }
    this.activeLimit = activeLimit;
    this.ownerLimit = ownerLimit;
  }

  synchronized long reserve(UUID ownerId, UUID bubbleId) {
    if (slots.size() >= activeLimit || slots.containsKey(bubbleId)) {
      return -1L;
    }
    Set<UUID> owned = ownerSlots.computeIfAbsent(ownerId, ignored -> new HashSet<>());
    if (owned.size() >= ownerLimit) {
      return -1L;
    }
    long generation = ++nextGeneration;
    slots.put(bubbleId, new Slot(ownerId, generation));
    owned.add(bubbleId);
    return generation;
  }

  synchronized boolean activate(UUID bubbleId, long generation) {
    Slot slot = slots.get(bubbleId);
    if (slot == null || slot.generation != generation || slot.active) {
      return false;
    }
    slot.active = true;
    activeQueue.addLast(bubbleId);
    return true;
  }

  synchronized List<Lease> take(int limit) {
    int count = Math.min(Math.max(0, limit), activeQueue.size());
    if (count == 0) {
      return List.of();
    }
    List<Lease> leases = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      UUID bubbleId = activeQueue.removeFirst();
      Slot slot = slots.get(bubbleId);
      if (slot == null || !slot.active) {
        continue;
      }
      activeQueue.addLast(bubbleId);
      leases.add(new Lease(bubbleId, slot.generation));
    }
    return leases;
  }

  synchronized boolean remove(UUID bubbleId, long generation) {
    Slot slot = slots.get(bubbleId);
    if (slot == null || slot.generation != generation) {
      return false;
    }
    removeSlot(bubbleId, slot);
    return true;
  }

  synchronized List<Lease> removeOwner(UUID ownerId) {
    Set<UUID> owned = ownerSlots.remove(ownerId);
    if (owned == null || owned.isEmpty()) {
      return List.of();
    }
    List<Lease> removed = new ArrayList<>(owned.size());
    for (UUID bubbleId : owned) {
      Slot slot = slots.remove(bubbleId);
      if (slot != null) {
        activeQueue.remove(bubbleId);
        removed.add(new Lease(bubbleId, slot.generation));
      }
    }
    return removed;
  }

  synchronized boolean isCurrent(UUID bubbleId, long generation) {
    Slot slot = slots.get(bubbleId);
    return slot != null && slot.active && slot.generation == generation;
  }

  synchronized int activeCount() {
    return slots.size();
  }

  synchronized void clear() {
    slots.clear();
    ownerSlots.clear();
    activeQueue.clear();
  }

  private void removeSlot(UUID bubbleId, Slot slot) {
    slots.remove(bubbleId, slot);
    activeQueue.remove(bubbleId);
    Set<UUID> owned = ownerSlots.get(slot.ownerId);
    if (owned == null) {
      return;
    }
    owned.remove(bubbleId);
    if (owned.isEmpty()) {
      ownerSlots.remove(slot.ownerId, owned);
    }
  }

  record Lease(UUID bubbleId, long generation) {
  }

  private static final class Slot {
    private final UUID ownerId;
    private final long generation;
    private boolean active;

    private Slot(UUID ownerId, long generation) {
      this.ownerId = ownerId;
      this.generation = generation;
    }
  }
}

final class StasisCapacityGate {
  private final int limit;
  private final AtomicInteger count = new AtomicInteger();

  StasisCapacityGate(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Stasis capacity must be positive");
    }
    this.limit = limit;
  }

  boolean tryAcquire() {
    int current = count.get();
    while (current < limit) {
      if (count.compareAndSet(current, current + 1)) {
        return true;
      }
      current = count.get();
    }
    return false;
  }

  void release() {
    int current = count.get();
    while (current > 0 && !count.compareAndSet(current, current - 1)) {
      current = count.get();
    }
  }

  int count() {
    return count.get();
  }

  void reset() {
    count.set(0);
  }
}

final class StasisWindowBudget {
  private final int inspectionLimit;
  private final int handoffLimit;
  private final int outlinePointLimit;
  private final int freezeFxLimit;
  private final int castFxLimit;
  private final int expiryFxLimit;
  private final long windowNanos;
  private final LongSupplier clock;
  private long window = Long.MIN_VALUE;
  private int inspections;
  private int handoffs;
  private int outlinePoints;
  private int freezeFx;
  private int castFx;
  private int expiryFx;

  StasisWindowBudget(int inspectionLimit, int handoffLimit, int outlinePointLimit,
                     int freezeFxLimit, int castFxLimit, int expiryFxLimit,
                     long windowNanos) {
    this(inspectionLimit, handoffLimit, outlinePointLimit, freezeFxLimit,
        castFxLimit, expiryFxLimit, windowNanos, System::nanoTime);
  }

  StasisWindowBudget(int inspectionLimit, int handoffLimit, int outlinePointLimit,
                     int freezeFxLimit, int castFxLimit, int expiryFxLimit,
                     long windowNanos, LongSupplier clock) {
    if (inspectionLimit <= 0 || handoffLimit <= 0 || outlinePointLimit <= 0
        || freezeFxLimit <= 0 || castFxLimit <= 0 || expiryFxLimit <= 0
        || windowNanos <= 0) {
      throw new IllegalArgumentException("Stasis work limits must be positive");
    }
    this.inspectionLimit = inspectionLimit;
    this.handoffLimit = handoffLimit;
    this.outlinePointLimit = outlinePointLimit;
    this.freezeFxLimit = freezeFxLimit;
    this.castFxLimit = castFxLimit;
    this.expiryFxLimit = expiryFxLimit;
    this.windowNanos = windowNanos;
    this.clock = clock;
  }

  synchronized boolean tryInspection() {
    rotate();
    if (inspections >= inspectionLimit) {
      return false;
    }
    inspections++;
    return true;
  }

  synchronized boolean tryEntityHandoff() {
    rotate();
    if (handoffs >= handoffLimit) {
      return false;
    }
    handoffs++;
    return true;
  }

  synchronized boolean tryOutlinePoint() {
    rotate();
    if (outlinePoints >= outlinePointLimit) {
      return false;
    }
    outlinePoints++;
    return true;
  }

  synchronized boolean tryFreezeFx() {
    rotate();
    if (freezeFx >= freezeFxLimit) {
      return false;
    }
    freezeFx++;
    return true;
  }

  synchronized boolean tryCastFx() {
    rotate();
    if (castFx >= castFxLimit) {
      return false;
    }
    castFx++;
    return true;
  }

  synchronized boolean tryExpiryFx() {
    rotate();
    if (expiryFx >= expiryFxLimit) {
      return false;
    }
    expiryFx++;
    return true;
  }

  private void rotate() {
    long currentWindow = Math.floorDiv(clock.getAsLong(), windowNanos);
    if (currentWindow == window) {
      return;
    }
    window = currentWindow;
    inspections = 0;
    handoffs = 0;
    outlinePoints = 0;
    freezeFx = 0;
    castFx = 0;
    expiryFx = 0;
  }
}
