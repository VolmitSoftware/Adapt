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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AxeMessages;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AxeThrowingAxe extends SimpleAdaptation<AxeThrowingAxe.Config> {
  private static final int MAX_IN_FLIGHT = 512;
  private static final String RECOVERY_KEY_PREFIX = "throwing_axe_recovery_";
  private static final long TICK_NANOS = 50_000_000L;
  private static final long BLOCK_BREAK_SWING_GUARD_TICKS = 1L;
  private static final long SHUTDOWN_RECOVERY_MILLIS = 2_000L;
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, ThrownAxe> inFlight = new ConcurrentHashMap<>();
  private final Set<AxeReplacementTicket> pendingReplacements = ConcurrentHashMap.newKeySet();
  private final Map<Id, PendingAxeDrop> pendingDrops = new ConcurrentHashMap<>();
  private final AxeRecoveryJournal recoveryJournal = AxeRecoveryJournal.createDefault();
  private final ThreadLocal<Deque<ThrowDamageAttempt>> damageAttempts = new ThreadLocal<>();
  private final AtomicBoolean closing = new AtomicBoolean();
  private final Object lifecycleLock = new Object();
  private final AxeBlockBreakSwingGuard blockBreakSwingGuard =
      new AxeBlockBreakSwingGuard(BLOCK_BREAK_SWING_GUARD_TICKS);

  public AxeThrowingAxe() {
    super("axe-throwing-axe");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_axe"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_axe"))
        .key("challenge_axe_throw_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_axe"))
            .key("challenge_axe_throw_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_throw_500", "axe.throwing-axe.hits", 500, 500);
    registerMilestone("challenge_axe_throw_5k", "axe.throwing-axe.hits", 5000, 1800);
  }

  @Override
  protected void onRuntimeActivated() {
    closing.set(false);
    for (PlayerRef player : host().onlinePlayers()) {
      Tasks.runEntity(player, () -> recoverStampedAxes(player));
    }
  }

  @Override
  public void unregister() {
    List<AxeReplacementTicket> replacements;
    List<PendingAxeDrop> drops;
    List<Map.Entry<UUID, ThrownAxe>> activeThrows;
    synchronized (lifecycleLock) {
      closing.set(true);
      blockBreakSwingGuard.clear();
      replacements = new ArrayList<>(pendingReplacements);
      pendingReplacements.clear();
      drops = new ArrayList<>(pendingDrops.values());
      activeThrows = new ArrayList<>(inFlight.entrySet());
      inFlight.clear();
    }
    for (AxeReplacementTicket replacement : replacements) {
      replacement.cancel();
    }
    for (PendingAxeDrop drop : drops) {
      drop.cancel(null);
    }
    long recoveryDeadline = System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_RECOVERY_MILLIS);
    for (PendingAxeDrop drop : drops) {
      long remainingNanos = recoveryDeadline - System.nanoTime();
      if (remainingNanos <= 0L || !drop.awaitResolution(remainingNanos)) {
        if (!drop.persistFallback()) {
          AdaptLog.error("Failed to journal a reserved thrown axe during shutdown.");
        }
      }
    }
    for (Map.Entry<UUID, ThrownAxe> entry : activeThrows) {
      UUID projectileId = entry.getKey();
      ThrownAxe thrown = entry.getValue();
      ProjectileReplacementRegistry.unregister(projectileId);
      removeProjectile(projectileId);
      recoverRetiredAxe(thrown);
    }
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.SNEAK, AxePreferences.IGNORE_PASSIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getDamageMultiplier(f), 2), 1);
    statLore(v, C.GREEN, "+ ", Form.f(getThrowSpeed(f), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMs(f), 1), 3);
    if (returnsAtLevel(level)) {
      v.addLore(C.AQUA + "" + AdaptLanguage.text(AxeMessages.THROWING_AXE_LORE4));
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (!isThrowInteraction(e.action(), e.hand())) {
      if (e.action() == InteractAction.LEFT_CLICK_BLOCK
          && (e.hand() == null || e.hand() == Hand.MAIN_HAND)) {
        blockBreakSwingGuard.clear(e.player().uuid());
      }
      return;
    }

    PlayerRef p = e.player();
    if (blockBreakSwingGuard.consume(p.uuid(), p.ticksLived())) {
      return;
    }

    if (!preferenceEnabled(p, AxePreferences.SNEAK) || p.isSneaking()) {
      withAdaptedPlayer(p, () -> throwAxe(p));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (!isAxe(p.inventory().mainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    blockBreakSwingGuard.mark(p.uuid(), p.ticksLived());
  }

  private void throwAxe(PlayerRef p) {
    if (closing.get()) {
      return;
    }
    ItemRef hand = p.inventory().mainHand();
    if (!isAxe(hand)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.uuid();
    long cooldown = (long) getCooldownMs(getLevelPercent(level));
    if (!cooldowns.isReady(id, cooldown)) {
      return;
    }

    if (inFlight.size() >= MAX_IN_FLIGHT) {
      return;
    }

    ItemOps items = items();
    ItemRef consumedAxe = hand.copyWithAmount(1);
    ItemRef thrown = consumedAxe.copy();
    boolean broken = applyThrowDurability(thrown, getConfig().durabilityCost);
    double damage = getThrowDamage(hand.type(), getLevelPercent(level));
    boolean returns = returnsAtLevel(level);

    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "throw", items.create(hand.type()), 1, () -> {
      if (hand.amount() > 1) {
        hand.setAmount(hand.amount() - 1);
        p.inventory().setMainHand(hand);
      } else {
        p.inventory().setMainHand(items.empty());
      }

      defaultConsumed.set(true);
      return true;
    })) {
      return;
    }

    boolean recoverable = isRecoverableThrow(defaultConsumed.get(), broken);
    byte[] recoveryData = null;
    if (recoverable) {
      try {
        recoveryData = items.serialize(thrown);
      } catch (Throwable error) {
        deliverAxe(p, consumedAxe);
        playReturnFx(p);
        AdaptLog.warn("Failed to prepare durable recovery for a thrown axe owned by " + p.name() + ".");
        AdaptLog.error(error);
        return;
      }
    }

    Loc eye = p.eyeLocation();
    Vec3 dir = eye.direction().normalize();
    double speed = getThrowSpeed(getLevelPercent(level));
    Loc spawn = eye.add(dir.multiply(0.6D));
    ProjectileRef ball;
    try {
      EntityRef spawned = p.world().spawn(SNOWBALL, spawn, entity -> {
        if (entity instanceof ProjectileRef snowball) {
          snowball.setItem(thrown.copy());
          snowball.setShooter(p);
          snowball.setVelocity(dir.multiply(speed));
        }
      });
      if (!(spawned instanceof ProjectileRef projectile)) {
        throw new IllegalStateException("The thrown axe projectile was not spawned");
      }
      ball = projectile;
    } catch (Throwable error) {
      if (defaultConsumed.get()) {
        deliverAxe(p, consumedAxe);
        playReturnFx(p);
      }
      AdaptLog.error("Failed to spawn a thrown axe for " + p.uuid() + ".");
      AdaptLog.error(error);
      return;
    }

    UUID ballId = ball.uuid();
    Id recoveryKey = recoveryKey(ballId);
    if (recoveryData != null) {
      try {
        p.data().set(recoveryData(recoveryKey), recoveryData);
      } catch (Throwable error) {
        ball.remove();
        deliverAxe(p, consumedAxe);
        playReturnFx(p);
        AdaptLog.warn("Failed to persist durable recovery for a thrown axe owned by " + p.name() + ".");
        AdaptLog.error(error);
        return;
      }
    }
    int flightTicks = Math.max(1, getConfig().maxFlightTicks);
    ThrownAxe state = new ThrownAxe(
        id,
        thrown,
        damage,
        returns,
        defaultConsumed.get() && broken,
        recoverable,
        recoveryKey,
        flightDeadline(System.nanoTime(), flightTicks)
    );
    if (!registerThrow(ball, state)) {
      ball.remove();
      recoverRetiredAxe(state);
      return;
    }
    cooldowns.mark(id, cooldown);
    p.setCooldown(thrown.type(), (int) Math.max(1L, cooldown / 50L));
    addStat(p, "axe.throwing-axe.thrown", 1);

    fx(spawn, FxPriority.GAMEPLAY)
        .trail(Particles.CRIT_MAGIC, dir.x(), dir.y(), dir.z(), 1.3D, 6)
        .chord(Sounds.ITEM_TRIDENT_THROW, 0.8F, 1.2F, Sounds.ITEM_AXE_STRIP, 0.4F, 0.7F);
    timeline(ball)
        .duration(flightTicks)
        .priority(FxPriority.TRAIL)
        .cullRadius(40)
        .frame((f, tick, progress) -> {
          f.particle(Particles.CRIT_MAGIC, 2, 0D, 0D, 0D, 0.05D, 0.0D);
          if ((tick & 1) == 0) {
            f.particle(Particles.SMOKE, 1, 0D, 0D, 0D, 0.02D, 0.0D);
          }
        })
        .start();

    if (!scheduleThrowExpiry(ballId, ball, state)) {
      resolveThrow(ballId, ball, null);
    }
  }

  private boolean registerThrow(ProjectileRef ball, ThrownAxe thrown) {
    synchronized (lifecycleLock) {
      if (closing.get()) {
        return false;
      }
      UUID projectileId = ball.uuid();
      inFlight.put(projectileId, thrown);
      ProjectileReplacementRegistry.register(
          ball,
          source -> beginRicochetReplacement(source, thrown)
      );
      return true;
    }
  }

  private ProjectileReplacementRegistry.Ticket beginRicochetReplacement(
      ProjectileRef source,
      ThrownAxe thrown
  ) {
    synchronized (lifecycleLock) {
      UUID projectileId = source.uuid();
      if (closing.get() || !isSnowball(source)
          || !inFlight.remove(projectileId, thrown)) {
        return null;
      }

      Loc impact = safeLocation(source);
      AxeReplacementTicket replacement = new AxeReplacementTicket(thrown, impact);
      pendingReplacements.add(replacement);
      return replacement;
    }
  }

  private boolean scheduleThrowExpiry(UUID projectileId, ProjectileRef ball, ThrownAxe thrown) {
    long remainingTicks = remainingFlightTicks(thrown.expiresAtNanos(), System.nanoTime());
    if (remainingTicks <= 0L) {
      return false;
    }
    try {
      return Tasks.runEntity(
          ball,
          () -> resolveThrow(projectileId, ball, null),
          (int) Math.min(Integer.MAX_VALUE, remainingTicks)
      );
    } catch (Throwable error) {
      AdaptLog.error("Failed to schedule thrown axe expiry for " + projectileId + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private void retireThrow(UUID ballId, Loc impact) {
    ProjectileReplacementRegistry.unregister(ballId);
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null) {
      return;
    }

    finishThrow(impact, thrown);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    ProjectileRef ball = e.projectile();
    if (!isSnowball(ball) || !inFlight.containsKey(ball.uuid())) {
      return;
    }
    resolveThrow(ball.uuid(), ball, e.hitEntity());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    if (isSnowball(e.entity())) {
      retireThrow(e.entity().uuid(), safeLocation(e.entity()));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      return;
    }
    ThrowDamageAttempt attempt = active.peekLast();
    if (attempt == null || !sameEntity(e.damager(), attempt.owner()) || !sameEntity(e.entity(), attempt.target())) {
      return;
    }
    attempt.resolve(isSuccessfulDamageEvent(true, e.isCancelled(), e.finalDamage()));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      return;
    }
    ThrowDamageAttempt attempt = active.peekLast();
    if (attempt != null && sameEntity(e.entity(), attempt.target())) {
      attempt.markKilled();
    }
  }

  private void resolveThrow(UUID ballId, ProjectileRef ball, EntityRef hit) {
    ProjectileReplacementRegistry.unregister(ballId);
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null) {
      return;
    }

    Loc impact = safeLocation(ball);
    PlayerRef owner = host().player(thrown.ownerId());
    RangedRicochetBolt.RicochetImpact ricochet = RangedRicochetBolt.impactOf(ball);

    if (hit instanceof LivingRef target && owner != null) {
      Tasks.runEntity(target, () -> prepareThrowHit(target, owner, thrown, ricochet));
    }

    if (ball.isValid()) {
      ball.remove();
    }

    finishThrow(impact, thrown);
  }

  private void finishThrow(Loc impact, ThrownAxe thrown) {
    if (thrown.broken()) {
      if (impact != null) {
        fx(impact, FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 6, 0.25D)
            .sound(Sounds.ENTITY_ITEM_BREAK, 0.8F, 0.9F);
      }
      return;
    }

    if (!thrown.recoverable()) {
      return;
    }

    PlayerRef owner = host().player(thrown.ownerId());
    if (owner != null) {
      Tasks.runEntity(owner, () -> completeThrowRecovery(owner, impact, thrown));
    }
  }

  private void prepareThrowHit(
      LivingRef target,
      PlayerRef owner,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!isEligibleThrowTarget(target, thrown.ownerId())
        || !allowsThrowImpact(target, ricochet.count(), getConfig().ignorePassiveMobs)) {
      return;
    }

    Loc targetLocation = target.location();
    boolean playerTarget = target instanceof PlayerRef;
    boolean passive = !AdaptationDamageTargets.allows(target, true);
    Tasks.runEntity(
        owner,
        () -> authorizeThrowHit(owner, target, targetLocation, playerTarget, passive, thrown, ricochet)
    );
  }

  private void authorizeThrowHit(
      PlayerRef owner,
      LivingRef target,
      Loc targetLocation,
      boolean playerTarget,
      boolean passive,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!owner.isOnline() || !hasActiveAdaptation(owner)
        || (ricochet.count() > 0 && passive && preferenceEnabled(owner, AxePreferences.IGNORE_PASSIVE))) {
      return;
    }
    boolean allowed = playerTarget ? canPVP(owner, targetLocation) : canPVE(owner, targetLocation);
    if (allowed) {
      Tasks.runEntity(target, () -> damageThrowTarget(target, owner, thrown, ricochet));
    }
  }

  private void damageThrowTarget(
      LivingRef target,
      PlayerRef owner,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!isEligibleThrowTarget(target, thrown.ownerId())
        || !allowsThrowImpact(target, ricochet.count(), getConfig().ignorePassiveMobs)) {
      return;
    }
    ThrowDamageAttempt attempt = new ThrowDamageAttempt(owner, target);
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      active = new ArrayDeque<>();
      damageAttempts.set(active);
    }
    active.addLast(attempt);
    try {
      target.damage(thrown.damage() + ricochet.bonusDamage(), owner);
    } catch (Throwable error) {
      AdaptLog.error("Thrown axe damage failed for target " + target.uuid() + ".");
      AdaptLog.error(error);
      return;
    } finally {
      active.removeLastOccurrence(attempt);
      if (active.isEmpty()) {
        damageAttempts.remove();
      }
    }
    if (!attempt.successful()) {
      return;
    }
    fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 8, 0.35D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.0F, Sounds.ITEM_AXE_STRIP, 0.4F, 0.8F);
    Tasks.runEntity(owner, () -> rewardHit(owner));
    if (attempt.killed() && ricochet.count() > 0) {
      Loc deathLocation = target.location();
      Tasks.runEntity(
          owner,
          () -> rewardRicochetKill(owner, deathLocation, ricochet.count())
      );
    }
  }

  private void rewardRicochetKill(PlayerRef owner, Loc deathLocation, int ricochetCount) {
    if (!owner.isOnline()) {
      return;
    }
    RangedRicochetBolt.rewardRicochetKill(owner, deathLocation, ricochetCount);
  }

  static boolean allowsThrowImpact(LivingRef target, int ricochetCount, boolean ignorePassiveMobs) {
    return ricochetCount <= 0 || AdaptationDamageTargets.allows(target, ignorePassiveMobs);
  }

  private boolean isEligibleThrowTarget(LivingRef target, UUID ownerId) {
    if (!target.isValid() || target.isDead() || ownerId.equals(target.uuid())
        || isProtectedFriendly(null, target)) {
      return false;
    }
    UUID tamer = target.tameOwner();
    return tamer == null || !ownerId.equals(tamer);
  }

  private void completeThrowRecovery(PlayerRef owner, Loc impact, ThrownAxe thrown) {
    if (!owner.isOnline()) {
      return;
    }
    if (thrown.returns()) {
      recoverStampedAxe(owner, thrown.recoveryKey());
    } else {
      dropAxe(impact, thrown, owner);
    }
  }

  private void rewardHit(PlayerRef owner) {
    if (!owner.isOnline() || !hasActiveAdaptation(owner)) {
      return;
    }
    addStat(owner, "axe.throwing-axe.hits", 1);
    xp(owner, getConfig().xpPerHit);
  }

  private void deliverAxe(PlayerRef owner, ItemRef axe) {
    ItemRef main = owner.inventory().mainHand();
    if (main == null || main.isEmpty()) {
      owner.inventory().setMainHand(axe);
    } else {
      List<ItemRef> overflow = owner.inventory().addItem(axe);
      for (ItemRef item : overflow) {
        owner.world().dropItem(owner.location().position(), item);
      }
    }
  }

  private void playReturnFx(PlayerRef owner) {
    fx(owner.eyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.ENCHANTMENT_TABLE, 8, 0.3D)
        .chord(Sounds.ITEM_TRIDENT_RETURN, 0.7F, 1.1F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
  }

  private void dropAxe(Loc impact, ThrownAxe thrown, PlayerRef owner) {
    if (impact == null || impact.world() == null) {
      return;
    }
    DataHolder data = owner.data();
    byte[] encoded = data.get(recoveryData(thrown.recoveryKey()));
    if (encoded == null) {
      return;
    }

    PendingAxeDrop pending = new PendingAxeDrop(thrown, impact, encoded);
    synchronized (lifecycleLock) {
      if (closing.get() || pendingDrops.putIfAbsent(thrown.recoveryKey(), pending) != null) {
        return;
      }
      try {
        data.remove(thrown.recoveryKey());
      } catch (Throwable error) {
        pendingDrops.remove(thrown.recoveryKey(), pending);
        AdaptLog.error("Failed to reserve a thrown axe impact drop.");
        AdaptLog.error(error);
        return;
      }
    }

    if (!Tasks.runAt(impact, pending::deliver)) {
      pending.cancel(owner);
    }
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerJoinEvent event) {
    recoverStampedAxes(event.player());
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent event) {
    PlayerRef player = event.player();
    UUID playerId = player.uuid();
    blockBreakSwingGuard.clear(playerId);
    for (AxeReplacementTicket replacement : new ArrayList<>(pendingReplacements)) {
      if (replacement.ownerId().equals(playerId)) {
        replacement.cancel();
      }
    }
    for (PendingAxeDrop drop : new ArrayList<>(pendingDrops.values())) {
      if (drop.ownerId().equals(playerId)) {
        drop.cancel(player);
      }
    }
    for (Map.Entry<UUID, ThrownAxe> entry : new ArrayList<>(inFlight.entrySet())) {
      ThrownAxe thrown = entry.getValue();
      if (!thrown.ownerId().equals(playerId) || !inFlight.remove(entry.getKey(), thrown)) {
        continue;
      }
      ProjectileReplacementRegistry.unregister(entry.getKey());
      removeProjectile(entry.getKey());
      if (thrown.recoverable()) {
        recoverStampedAxe(player, thrown.recoveryKey());
      }
    }
  }

  private void recoverStampedAxes(PlayerRef player) {
    if (!player.isOnline()) {
      return;
    }
    for (PendingAxeDrop drop : new ArrayList<>(pendingDrops.values())) {
      if (drop.ownerId().equals(player.uuid())) {
        drop.cancel(player);
      }
    }
    JournalRecoveryBatch journalRecovery = recoverJournalAxes(player);
    if (!journalRecovery.available()) {
      return;
    }
    DataHolder data = player.data();
    for (Id key : new ArrayList<>(data.keys())) {
      if (key.namespace().equals(Id.ADAPT)
          && key.path().startsWith(RECOVERY_KEY_PREFIX)
          && !journalRecovery.keys().contains(key)) {
        recoverStampedAxe(player, key);
      }
    }
  }

  private JournalRecoveryBatch recoverJournalAxes(PlayerRef player) {
    UUID ownerId = player.uuid();
    List<String> recoveryKeys;
    try {
      recoveryKeys = recoveryJournal.keys(ownerId);
    } catch (Throwable error) {
      AdaptLog.error("Failed to list journaled thrown axes for " + ownerId + ".");
      AdaptLog.error(error);
      return new JournalRecoveryBatch(false, Set.of());
    }

    Set<Id> journalKeys = new HashSet<>(recoveryKeys.size());
    for (String recoveryKeyName : recoveryKeys) {
      Id journalKey = Id.adapt(recoveryKeyName);
      journalKeys.add(journalKey);
      Id recoveryKey = stageJournaledAxe(player, recoveryKeyName);
      if (recoveryKey != null) {
        recoverStampedAxe(player, recoveryKey);
      }
    }
    return new JournalRecoveryBatch(true, journalKeys);
  }

  private Id stageJournaledAxe(PlayerRef player, String recoveryKeyName) {
    UUID ownerId = player.uuid();
    try {
      Id recoveryKey = Id.adapt(recoveryKeyName);
      byte[] journaled = recoveryJournal.read(ownerId, recoveryKeyName);
      DataHolder data = player.data();
      byte[] stamped = data.get(recoveryData(recoveryKey));
      if (stamped != null && !Arrays.equals(stamped, journaled)) {
        AdaptLog.error("Conflicting journaled thrown axe for " + ownerId
            + " at " + recoveryKeyName + ".");
        return null;
      }
      if (stamped == null) {
        data.set(recoveryData(recoveryKey), journaled);
      }
      recoveryJournal.delete(ownerId, recoveryKeyName);
      return recoveryKey;
    } catch (Throwable error) {
      AdaptLog.error("Failed to import a journaled thrown axe for " + ownerId
          + " at " + recoveryKeyName + ".");
      AdaptLog.error(error);
      return null;
    }
  }

  private void recoverStampedAxe(PlayerRef player, Id key) {
    DataHolder data = player.data();
    byte[] encoded = data.get(recoveryData(key));
    if (encoded == null) {
      return;
    }

    ItemRef axe;
    try {
      axe = items().deserialize(encoded);
    } catch (Throwable error) {
      AdaptLog.warn("Failed to decode durable thrown-axe recovery for " + player.name() + ".");
      AdaptLog.error(error);
      return;
    }

    try {
      data.remove(key);
      deliverAxe(player, axe);
    } catch (Throwable error) {
      try {
        data.set(recoveryData(key), encoded);
      } catch (Throwable restoreError) {
        error.addSuppressed(restoreError);
      }
      AdaptLog.error("Failed to deliver durable thrown-axe recovery to " + player.name() + ".");
      AdaptLog.error(error);
      return;
    }
    playReturnFx(player);
  }

  private Id recoveryKey(UUID projectileId) {
    return Id.adapt(RECOVERY_KEY_PREFIX + projectileId.toString().replace("-", ""));
  }

  private static DataKey<byte[]> recoveryData(Id recoveryKey) {
    return DataKey.of(recoveryKey, DataCodecs.BYTE_ARRAY);
  }

  private void removeProjectile(UUID projectileId) {
    EntityRef projectile = host().entity(projectileId);
    if (projectile != null) {
      Tasks.runEntity(projectile, () -> {
        if (projectile.isValid()) {
          projectile.remove();
        }
      });
    }
  }

  private Loc safeLocation(EntityRef entity) {
    try {
      return entity.location();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private boolean applyThrowDurability(ItemRef stack, int amount) {
    if (stack.isUnbreakable()) {
      return false;
    }

    int next = stack.damage() + amount;
    if (reachesBreakThreshold(
        stack.damage(),
        amount,
        items().maxDurability(stack.type())
    )) {
      return true;
    }
    stack.setDamage(next);
    return false;
  }

  private boolean returnsAtLevel(int level) {
    return getLevelPercent(level) >= getConfig().returnUnlockLevelPercent;
  }

  private double getThrowSpeed(double factor) {
    return getConfig().throwSpeedBase + (factor * getConfig().throwSpeedFactor);
  }

  private double getDamageMultiplier(double factor) {
    return getConfig().damageMultiplierBase + (factor * getConfig().damageMultiplierFactor);
  }

  private double getThrowDamage(Id axe, double factor) {
    return meleeBaseDamage(axe) * getDamageMultiplier(factor);
  }

  private double getCooldownMs(double factor) {
    return Math.max(250D, getConfig().cooldownMsBase - (factor * getConfig().cooldownMsLevelReduction));
  }

  static double meleeBaseDamage(Id axe) {
    return switch (axe.isMinecraft() ? axe.path() : "") {
      case "netherite_axe" -> 10D;
      case "diamond_axe", "iron_axe", "stone_axe" -> 9D;
      case "wooden_axe", "golden_axe" -> 7D;
      default -> 7D;
    };
  }

  static boolean isThrowInteraction(InteractAction action, Hand hand) {
    return action == InteractAction.LEFT_CLICK_AIR
        && (hand == null || hand == Hand.MAIN_HAND);
  }

  static boolean reachesBreakThreshold(int currentDamage, int addedDamage, int maxDurability) {
    return (long) currentDamage + addedDamage >= maxDurability;
  }

  static long flightDeadline(long nowNanos, long flightTicks) {
    long safeTicks = Math.max(1L, flightTicks);
    long maximumTicks = Long.MAX_VALUE / TICK_NANOS;
    long duration = Math.min(safeTicks, maximumTicks) * TICK_NANOS;
    if (nowNanos > Long.MAX_VALUE - duration) {
      return Long.MAX_VALUE;
    }
    return nowNanos + duration;
  }

  static long remainingFlightTicks(long deadlineNanos, long nowNanos) {
    long remainingNanos = deadlineNanos - nowNanos;
    if (remainingNanos <= 0L) {
      return 0L;
    }
    long completeTicks = remainingNanos / TICK_NANOS;
    return remainingNanos % TICK_NANOS == 0L ? completeTicks : completeTicks + 1L;
  }

  static boolean isSuccessfulDamageEvent(boolean observed, boolean cancelled, double finalDamage) {
    return observed && !cancelled && finalDamage > 0D;
  }

  private void recoverRetiredAxe(ThrownAxe thrown) {
    PlayerRef owner = host().player(thrown.ownerId());
    RecoveryDisposition disposition = recoveryDisposition(thrown.recoverable(), owner != null && owner.isOnline());
    if (disposition == RecoveryDisposition.DESTROYED) {
      return;
    }
    if (disposition == RecoveryDisposition.OWNER) {
      Tasks.runEntity(owner, () -> recoverStampedAxe(owner, thrown.recoveryKey()));
    }
  }

  static boolean isRecoverableThrow(boolean defaultConsumed, boolean broken) {
    return defaultConsumed && !broken;
  }

  static RecoveryDisposition recoveryDisposition(boolean recoverable, boolean ownerOnline) {
    if (!recoverable) {
      return RecoveryDisposition.DESTROYED;
    }
    return ownerOnline ? RecoveryDisposition.OWNER : RecoveryDisposition.PENDING;
  }

  private static boolean isSnowball(EntityRef entity) {
    return entity != null && SNOWBALL.equals(entity.type());
  }

  private static boolean sameEntity(EntityRef first, EntityRef second) {
    return first != null && second != null && first.uuid().equals(second.uuid());
  }

  private static PlatformHost host() {
    return AdaptCore.platform().host();
  }

  private static ItemOps items() {
    return host().items();
  }

  enum RecoveryDisposition {
    DESTROYED,
    OWNER,
    PENDING
  }

  record ThrownAxe(
      UUID ownerId,
      ItemRef axe,
      double damage,
      boolean returns,
      boolean broken,
      boolean recoverable,
      Id recoveryKey,
      long expiresAtNanos
  ) {
    ThrownAxe {
      axe = axe.copy();
    }
  }

  private record JournalRecoveryBatch(boolean available, Set<Id> keys) {
    private JournalRecoveryBatch {
      keys = Set.copyOf(keys);
    }
  }

  private final class PendingAxeDrop {
    private final ThrownAxe thrown;
    private final Loc impact;
    private final byte[] encoded;
    private final CountDownLatch resolution = new CountDownLatch(1);
    private boolean deliveryAllowed = true;
    private boolean resolved;
    private boolean restorationScheduled;

    private PendingAxeDrop(ThrownAxe thrown, Loc impact, byte[] encoded) {
      this.thrown = thrown;
      this.impact = impact;
      this.encoded = encoded.clone();
    }

    private void deliver() {
      Throwable failure = null;
      synchronized (this) {
        if (resolved || !deliveryAllowed) {
          return;
        }
        try {
          impact.world().dropItem(impact.position(), thrown.axe());
          resolved = true;
          pendingDrops.remove(thrown.recoveryKey(), this);
          resolution.countDown();
        } catch (Throwable error) {
          deliveryAllowed = false;
          failure = error;
        }
      }
      if (failure != null) {
        AdaptLog.error("Failed to materialize a thrown axe impact drop.");
        AdaptLog.error(failure);
        cancel(null);
      }
    }

    private void cancel(PlayerRef knownOwner) {
      PlayerRef owner = knownOwner == null ? host().player(thrown.ownerId()) : knownOwner;
      synchronized (this) {
        deliveryAllowed = false;
        if (resolved || restorationScheduled) {
          return;
        }
        if (owner == null) {
          persistFallback();
          return;
        }
        restorationScheduled = true;
      }

      if (Tasks.isOwnedByCurrentRegion(owner)) {
        restoreOwned(owner);
        return;
      }
      try {
        boolean scheduled = Tasks.runEntity(
            owner,
            () -> restoreOwned(owner),
            0,
            this::restoreRetired
        );
        if (!scheduled) {
          restoreRetired();
        }
      } catch (Throwable error) {
        restoreRetired();
        AdaptLog.error("Failed to schedule restoration of a reserved thrown axe drop.");
        AdaptLog.error(error);
      }
    }

    private void restoreOwned(PlayerRef owner) {
      try {
        synchronized (this) {
          if (resolved) {
            return;
          }
          DataHolder data = owner.data();
          byte[] stamped = data.get(recoveryData(thrown.recoveryKey()));
          if (stamped != null && !Arrays.equals(stamped, encoded)) {
            throw new IllegalStateException(
                "Conflicting durable thrown-axe stamp for " + thrown.ownerId()
            );
          }
          if (stamped == null) {
            data.set(recoveryData(thrown.recoveryKey()), encoded.clone());
          }
          resolved = true;
          restorationScheduled = false;
          pendingDrops.remove(thrown.recoveryKey(), this);
          resolution.countDown();
        }
      } catch (Throwable error) {
        persistFallback();
        AdaptLog.error("Failed to restore a reserved thrown axe drop.");
        AdaptLog.error(error);
      }
    }

    private void restoreRetired() {
      synchronized (this) {
        restorationScheduled = false;
      }
      if (!persistFallback()) {
        AdaptLog.error("Failed to journal a retired thrown axe recovery.");
      }
    }

    private synchronized boolean persistFallback() {
      deliveryAllowed = false;
      if (resolved) {
        return true;
      }
      try {
        recoveryJournal.persist(thrown.ownerId(), thrown.recoveryKey(), encoded);
        resolved = true;
        restorationScheduled = false;
        pendingDrops.remove(thrown.recoveryKey(), this);
        resolution.countDown();
        return true;
      } catch (Throwable error) {
        restorationScheduled = false;
        AdaptLog.error("Failed to persist durable thrown-axe recovery for "
            + thrown.ownerId() + ".");
        AdaptLog.error(error);
        return false;
      }
    }

    private boolean awaitResolution(long timeoutNanos) {
      try {
        return resolution.await(Math.max(0L, timeoutNanos), TimeUnit.NANOSECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    private UUID ownerId() {
      return thrown.ownerId();
    }
  }

  private final class AxeReplacementTicket implements ProjectileReplacementRegistry.Ticket {
    private final ThrownAxe thrown;
    private final Loc impact;
    private boolean finished;

    private AxeReplacementTicket(ThrownAxe thrown, Loc impact) {
      this.thrown = thrown;
      this.impact = impact;
    }

    @Override
    public synchronized boolean complete(ProjectileRef replacement) {
      if (finished || !isSnowball(replacement)) {
        return false;
      }

      synchronized (lifecycleLock) {
        UUID projectileId = replacement.uuid();
        if (closing.get()
            || remainingFlightTicks(thrown.expiresAtNanos(), System.nanoTime()) <= 0L
            || inFlight.putIfAbsent(projectileId, thrown) != null) {
          return false;
        }
        ProjectileReplacementRegistry.register(
            replacement,
            source -> beginRicochetReplacement(source, thrown)
        );
        if (!scheduleThrowExpiry(projectileId, replacement, thrown)) {
          ProjectileReplacementRegistry.unregister(projectileId);
          inFlight.remove(projectileId, thrown);
          return false;
        }

        finished = true;
        pendingReplacements.remove(this);
        return true;
      }
    }

    @Override
    public synchronized void cancel() {
      if (finished) {
        return;
      }
      finished = true;
      pendingReplacements.remove(this);
      finishThrow(impact, thrown);
    }

    private UUID ownerId() {
      return thrown.ownerId();
    }
  }

  private static final class ThrowDamageAttempt {
    private final PlayerRef owner;
    private final LivingRef target;
    private boolean successful;
    private boolean killed;

    private ThrowDamageAttempt(PlayerRef owner, LivingRef target) {
      this.owner = owner;
      this.target = target;
    }

    private PlayerRef owner() {
      return owner;
    }

    private LivingRef target() {
      return target;
    }

    private void resolve(boolean accepted) {
      successful = accepted;
    }

    private boolean successful() {
      return successful;
    }

    private void markKilled() {
      killed = true;
    }

    private boolean killed() {
      return killed;
    }
  }

  @ConfigDescription("Left-click air with an axe to hurl it as a spinning projectile that deals its melee damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from thrown axe impacts after a ricochet.", impact = "When enabled, bounced axes cannot damage protected mobs. Direct throws and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of the axe's melee damage dealt on a throw hit.", impact = "Higher values make thrown axes hit harder at every level.")
    double damageMultiplierBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra melee-damage fraction added by leveling this adaptation.", impact = "Higher values reward leveling with stronger throws.")
    double damageMultiplierFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base flight velocity of a thrown axe in blocks per tick.", impact = "Higher values make thrown axes travel faster and flatter.")
    double throwSpeedBase = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra flight velocity added by leveling this adaptation.", impact = "Higher values increase throw speed scaling with level.")
    double throwSpeedFactor = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base throw cooldown in milliseconds before level scaling.", impact = "Higher values slow how often axes can be thrown.")
    double cooldownMsBase = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds of cooldown removed at maximum level.", impact = "Higher values let higher levels throw more frequently.")
    double cooldownMsLevelReduction = 600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Durability spent from the axe on each throw.", impact = "Higher values wear thrown axes out faster.")
    int durabilityCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a thrown axe stays airborne before it is recovered automatically.", impact = "Higher values let throws travel farther before auto-recovery.")
    int maxFlightTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level progress required before thrown axes return to your hand.", impact = "Lower values unlock the auto-return at an earlier level.")
    double returnUnlockLevelPercent = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per thrown-axe hit on an entity.", impact = "Higher values accelerate skill progression from throwing.")
    double xpPerHit = 6;

    public Config() {
      baseCost = 5;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 5;
    }
  }
}
