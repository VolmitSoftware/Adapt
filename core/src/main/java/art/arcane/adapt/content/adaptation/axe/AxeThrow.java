package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AbilityDamage;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Quat;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

/**
 * Thrown axes for Throwing Axe and Worldsplitter. A left-click in the air hurls the held axe as a snowball that deals
 * the throw's damage to what it hits. A thrown axe is stamped into the owner's data before it flies, so it returns to
 * the owner or drops where it landed even across ricochets, quits, crashes and shutdowns. {@link #fling} sends any other
 * item the same way (Clod Hurler, Brickbat): a tumbling block rides the snowball, and neither entity is ever saved.
 */
public final class AxeThrow {
  /** Marks the snowballs and tumbling blocks of a fling, so strays from a crash are removed when they load. */
  public static final DataKey<Byte> FLUNG_KEY = DataKey.adapt("flung-block", DataCodecs.BYTE);
  private static final int MAX_IN_FLIGHT = 512;
  private static final float LOOK_SCALE = 0.5F;
  private static final int TUMBLE_STEP_TICKS = 2;
  private static final Id BLOCK_DISPLAY = Id.minecraft("block_display");
  private static final Id ITEM_DISPLAY = Id.minecraft("item_display");
  private static final String RECOVERY_KEY_PREFIX = "throwing_axe_recovery_";
  private static final long TICK_NANOS = 50_000_000L;
  private static final long BLOCK_BREAK_SWING_GUARD_TICKS = 1L;
  private static final long SHUTDOWN_RECOVERY_MILLIS = 2_000L;
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private final Adaptation<?> owner;
  private final Cooldowns cooldowns;
  private final Rules rules;
  private final Map<UUID, ThrownAxe> inFlight = new ConcurrentHashMap<>();
  private final Map<UUID, Flung> flung = new ConcurrentHashMap<>();
  private final Set<AxeReplacementTicket> pendingReplacements = ConcurrentHashMap.newKeySet();
  private final Map<Id, PendingAxeDrop> pendingDrops = new ConcurrentHashMap<>();
  private final AxeRecoveryJournal recoveryJournal = AxeRecoveryJournal.createDefault();
  private final ThreadLocal<Deque<ThrowDamageAttempt>> damageAttempts = new ThreadLocal<>();
  private final AtomicBoolean closing = new AtomicBoolean();
  private final Object lifecycleLock = new Object();
  private final AxeBlockBreakSwingGuard blockBreakSwingGuard =
      new AxeBlockBreakSwingGuard(BLOCK_BREAK_SWING_GUARD_TICKS);

  public AxeThrow(Adaptation<?> owner, Cooldowns cooldowns, Rules rules) {
    this.owner = owner;
    this.cooldowns = cooldowns;
    this.rules = rules;
  }

  /** What each owner decides about its throws. */
  public interface Rules {
    /** The throw a player at {@code level} makes with an axe of type {@code axe}. */
    Toss toss(int level, Id axe);

    /** Whether bounced axes skip passive and neutral mobs; read when each hit lands. */
    boolean ignorePassiveMobs();

    /** Runs on the player's thread after an axe left their hand. */
    void thrown(PlayerRef player);

    /** Runs on the owner's thread after a thrown axe damaged {@code target}. */
    void hit(PlayerRef owner, LivingRef target);

    /** Runs at the impact point when a thrown axe struck a block or an entity. */
    default void landed(PlayerRef owner, Loc impact) {
    }

    /** Effects as the axe leaves the hand at {@code spawn} heading along {@code direction}, and along its flight. */
    void launchFx(Loc spawn, Vec3 direction, ProjectileRef axe, int flightTicks);

    /** Effects where an axe broke on its throw. */
    void breakFx(Loc impact);

    /** Effects on a target a thrown axe damaged. */
    void hitFx(LivingRef target);

    /** Runs on the target's thread after {@code thrown} damaged it; effects by default. */
    default void struck(PlayerRef owner, LivingRef target, ItemRef thrown) {
      hitFx(target);
    }

    /** Whether a throw by {@code ownerId} may damage {@code target}; read on the target's thread before each hit. */
    default boolean strikes(UUID ownerId, LivingRef target) {
      return true;
    }

    /** Effects as a flung block leaves {@code spawn} and along its flight. */
    default void flingFx(Loc spawn, ProjectileRef ball, BlockStateRef block, int flightTicks) {
    }

    /** Effects where a flung block landed. */
    default void impactFx(Loc impact, BlockStateRef block) {
    }

    /** Ticks a returning fling takes to fly back before it reaches the owner; 0 returns it at once. */
    default int returnTicks() {
      return 0;
    }

    /** Shows {@code item} flying from {@code impact} back to {@code owner} over {@code ticks}. */
    default void returnFlight(PlayerRef owner, Loc impact, ItemRef item, int ticks) {
    }

    /** Effects at the owner when an axe comes back. */
    void returnFx(PlayerRef owner);

    /** What a returning axe strikes on its way back to {@code owner}, or null when it strikes nothing. */
    default @Nullable ReturnSweep returnSweep(PlayerRef owner) {
      return null;
    }

    /** Runs on the owner's thread after a returning axe struck {@code hits}, nearest the impact first. */
    default void returned(PlayerRef owner, List<LivingRef> hits) {
    }
  }

  /**
   * The return path: creatures within {@code width} blocks of the line from the impact to the owner's eyes, each struck
   * once for {@code damageRatio} of the throw damage, at most {@code maxTargets}, each costing {@code durabilityPerHit}.
   */
  public record ReturnSweep(int maxTargets, double damageRatio, double width, int durabilityPerHit) {
  }

  /** One throw: its cooldown, damage, speed, whether it flies back, durability cost and flight time. */
  public record Toss(long cooldownMillis, double damage, double speed, boolean returns, int durabilityCost,
                     int flightTicks) {
  }

  /**
   * One fling: {@code carried} rides the flight and comes back, drops where it lands, or is spent when it is not
   * {@code recoverable}; {@code block} tumbles around the snowball, which shows {@code look}.
   */
  public record Fling(ItemRef carried, BlockStateRef block, ItemRef look, Vec3 velocity, double damage, boolean returns,
                      boolean recoverable, int flightTicks) {
  }

  private record Flung(ProjectileRef ball, BlockStateRef block, BlockDisplayRef look) {
  }

  public void activate() {
    closing.set(false);
    for (PlayerRef player : host().onlinePlayers()) {
      Tasks.runEntity(player, () -> recoverStampedAxes(player));
    }
    for (WorldRef world : host().worlds()) {
      sweep(world.entities());
    }
  }

  /** Removes flung snowballs, tumbling blocks and returning items no throw owns, such as any a crash left behind. */
  public void sweep(List<EntityRef> entities) {
    for (EntityRef entity : entities) {
      if (!flung.containsKey(entity.uuid()) && isFlungEntity(entity) && !isLook(entity.uuid())) {
        Tasks.runEntity(entity, () -> {
          if (entity.isValid()) {
            entity.remove();
          }
        });
      }
    }
  }

  private boolean isLook(UUID id) {
    for (Flung throwing : flung.values()) {
      if (throwing.look() != null && throwing.look().uuid().equals(id)) {
        return true;
      }
    }
    return false;
  }

  static boolean isFlungEntity(EntityRef entity) {
    return entity != null && (isSnowball(entity) || BLOCK_DISPLAY.equals(entity.type())
        || ITEM_DISPLAY.equals(entity.type())) && entity.data().has(FLUNG_KEY);
  }

  /** Number of flings in the air. */
  public int flungCount() {
    return flung.size();
  }

  /**
   * Throws {@code fling.carried()} for {@code p} from {@code spawn}. True when it flies: from then on the throw owns the
   * item and every end returns it, drops it or spends it exactly once. False leaves the item with the caller.
   */
  public boolean fling(PlayerRef p, Loc spawn, Fling fling, int maxFlung) {
    if (closing.get() || inFlight.size() >= MAX_IN_FLIGHT || flung.size() >= maxFlung) {
      return false;
    }
    ItemRef carried = fling.carried().copy();
    byte[] recoveryData = null;
    if (fling.recoverable()) {
      try {
        recoveryData = items().serialize(carried);
      } catch (Throwable error) {
        AdaptLog.error(error);
        return false;
      }
    }
    ProjectileRef ball;
    try {
      EntityRef spawned = p.world().spawn(SNOWBALL, spawn, (EntityRef entity) -> {
        entity.setPersistent(false);
        entity.data().set(FLUNG_KEY, (byte) 1);
        if (entity instanceof ProjectileRef snowball) {
          snowball.setItem(fling.look().copy());
          snowball.setShooter(p);
          snowball.setVelocity(fling.velocity());
        }
      });
      if (!(spawned instanceof ProjectileRef projectile)) {
        if (spawned != null) {
          spawned.remove();
        }
        return false;
      }
      ball = projectile;
    } catch (Throwable error) {
      AdaptLog.error("Failed to spawn a flung block for " + p.uuid() + ".");
      AdaptLog.error(error);
      return false;
    }
    UUID ballId = ball.uuid();
    Id recoveryKey = recoveryKey(ballId);
    if (recoveryData != null) {
      try {
        p.data().set(recoveryData(recoveryKey), recoveryData);
      } catch (Throwable error) {
        ball.remove();
        AdaptLog.error(error);
        return false;
      }
    }
    int flightTicks = Math.max(1, fling.flightTicks());
    ThrownAxe state = new ThrownAxe(p.uuid(), carried, fling.damage(), fling.returns(), false, fling.recoverable(),
        recoveryKey, flightDeadline(System.nanoTime(), flightTicks));
    synchronized (lifecycleLock) {
      if (closing.get()) {
        ball.remove();
        if (recoveryData != null) {
          p.data().remove(recoveryKey);
        }
        return false;
      }
      inFlight.put(ballId, state);
      flung.put(ballId, new Flung(ball, fling.block(), spawnLook(ball, fling.block(), flightTicks)));
    }
    rules.flingFx(spawn, ball, fling.block(), flightTicks);
    if (!scheduleThrowExpiry(ballId, ball, state)) {
      resolveThrow(ballId, ball, null, false);
    }
    return true;
  }

  private BlockDisplayRef spawnLook(ProjectileRef ball, BlockStateRef block, int flightTicks) {
    if (block == null) {
      return null;
    }
    BlockDisplayRef look;
    try {
      look = ball.world().spawnBlockDisplay(ball.location(), block, (BlockDisplayRef display) -> {
        display.setPersistent(false);
        display.data().set(FLUNG_KEY, (byte) 1);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setShadowRadius(0F);
        display.setInterpolationDuration(TUMBLE_STEP_TICKS);
        display.setTransform(tumble(0D));
      });
    } catch (Throwable error) {
      AdaptLog.error(error);
      return null;
    }
    if (look == null) {
      return null;
    }
    if (!ball.addPassenger(look)) {
      look.remove();
      return null;
    }
    FxTimeline.follow(owner, ball)
        .duration(flightTicks)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(64)
        .frame((FxEmitter f, int tick, double progress) -> {
          if (tick % TUMBLE_STEP_TICKS == 0 && look.isValid()) {
            look.setTransform(tumble(tick + TUMBLE_STEP_TICKS));
            look.setInterpolationDelay(0);
          }
        })
        .start();
    return look;
  }

  /** The tumbling block's pose {@code tick} ticks into its flight: a half-size cube spinning about its own centre. */
  static Transform tumble(double tick) {
    float angle = (float) (tick * 0.45D);
    Quat spin = Quat.axisAngle(0.6F, 0.8F, 0.0F, angle);
    Vec3 centre = rotate(spin, new Vec3(-LOOK_SCALE / 2D, -LOOK_SCALE / 2D, -LOOK_SCALE / 2D));
    return new Transform(centre.add(0D, -LOOK_SCALE / 2D, 0D), spin, new Vec3(LOOK_SCALE, LOOK_SCALE, LOOK_SCALE), Quat.IDENTITY);
  }

  static Vec3 rotate(Quat q, Vec3 v) {
    Vec3 axis = new Vec3(q.x(), q.y(), q.z());
    Vec3 t = axis.cross(v).multiply(2D);
    return v.add(t.multiply(q.w())).add(axis.cross(t));
  }

  private void endFling(UUID ballId, Loc impact, boolean landed) {
    Flung ended = flung.remove(ballId);
    if (ended == null) {
      return;
    }
    for (EntityRef part : new EntityRef[]{ended.look(), ended.ball()}) {
      if (part != null && part.isValid()) {
        Tasks.runEntity(part, () -> {
          if (part.isValid()) {
            part.remove();
          }
        });
      }
    }
    if (landed && impact != null && ended.block() != null) {
      rules.impactFx(impact, ended.block());
    }
  }

  public void close() {
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
      endFling(projectileId, null, false);
      recoverRetiredAxe(thrown);
    }
  }

  /** Throws on a left-click in the air, unless that click was the swing that just broke a block. */
  public void interact(PlayerInteractEvent e) {
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

    if (!owner.preferenceEnabled(p, AxePreferences.SNEAK) || p.isSneaking()) {
      owner.withAdaptedPlayer(p, () -> throwAxe(p));
    }
  }

  /** True once for the swing that just broke a block, so that swing does not also throw. */
  public boolean consumeBreakSwing(PlayerRef p) {
    return blockBreakSwingGuard.consume(p.uuid(), p.ticksLived());
  }

  /** Remembers that the player's swing broke a block, so the same swing does not also throw. */
  public void blockBroken(PlayerRef p) {
    blockBreakSwingGuard.mark(p.uuid(), p.ticksLived());
  }

  private void throwAxe(PlayerRef p) {
    if (closing.get()) {
      return;
    }
    ItemRef hand = p.inventory().mainHand();
    if (!owner.isAxe(hand)) {
      return;
    }

    int level = owner.getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.uuid();
    Toss toss = rules.toss(level, hand.type());
    long cooldown = toss.cooldownMillis();
    if (!cooldowns.isReady(id, cooldown)) {
      return;
    }

    if (inFlight.size() >= MAX_IN_FLIGHT) {
      return;
    }

    ItemOps items = items();
    ItemRef consumedAxe = hand.copyWithAmount(1);
    ItemRef thrown = consumedAxe.copy();
    boolean broken = applyThrowDurability(thrown, toss.durabilityCost());

    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!owner.payItemCost(p, "throw", items.create(hand.type()), 1, () -> {
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
        rules.returnFx(p);
        AdaptLog.warn("Failed to prepare durable recovery for a thrown axe owned by " + p.name() + ".");
        AdaptLog.error(error);
        return;
      }
    }

    Loc eye = p.eyeLocation();
    Vec3 dir = eye.direction().normalize();
    double speed = toss.speed();
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
        rules.returnFx(p);
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
        rules.returnFx(p);
        AdaptLog.warn("Failed to persist durable recovery for a thrown axe owned by " + p.name() + ".");
        AdaptLog.error(error);
        return;
      }
    }
    int flightTicks = Math.max(1, toss.flightTicks());
    ThrownAxe state = new ThrownAxe(
        id,
        thrown,
        toss.damage(),
        toss.returns(),
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
    rules.thrown(p);

    rules.launchFx(spawn, dir, ball, flightTicks);

    if (!scheduleThrowExpiry(ballId, ball, state)) {
      resolveThrow(ballId, ball, null, false);
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
          () -> resolveThrow(projectileId, ball, null, false),
          (int) Math.min(Integer.MAX_VALUE, remainingTicks)
      );
    } catch (Throwable error) {
      AdaptLog.error("Failed to schedule thrown axe expiry for " + projectileId + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private void retireThrow(UUID ballId, Loc impact, EntityRemoveEvent.Cause cause) {
    ProjectileReplacementRegistry.unregister(ballId);
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null) {
      return;
    }

    boolean wasFlung = flung.containsKey(ballId);
    endFling(ballId, impact, false);
    if (wasFlung && cause == EntityRemoveEvent.Cause.UNLOAD) {
      recoverRetiredAxe(thrown);
      return;
    }
    finishThrow(impact, thrown);
  }

  public void projectileHit(ProjectileHitEvent e) {
    ProjectileRef ball = e.projectile();
    if (!isSnowball(ball) || !inFlight.containsKey(ball.uuid())) {
      return;
    }
    resolveThrow(ball.uuid(), ball, e.hitEntity(), true);
  }

  public void entityRemoved(EntityRemoveEvent e) {
    if (isSnowball(e.entity())) {
      retireThrow(e.entity().uuid(), safeLocation(e.entity()), e.cause());
    }
  }

  public void entityDamaged(EntityDamageByEntityEvent e) {
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

  public void entityDied(EntityDeathEvent e) {
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      return;
    }
    ThrowDamageAttempt attempt = active.peekLast();
    if (attempt != null && sameEntity(e.entity(), attempt.target())) {
      attempt.markKilled();
    }
  }

  private void resolveThrow(UUID ballId, ProjectileRef ball, EntityRef hit, boolean landed) {
    ProjectileReplacementRegistry.unregister(ballId);
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null) {
      return;
    }

    Loc impact = safeLocation(ball);
    PlayerRef thrower = host().player(thrown.ownerId());
    RangedRicochetBolt.RicochetImpact ricochet = RangedRicochetBolt.impactOf(ball);

    if (hit instanceof LivingRef target && thrower != null) {
      Tasks.runEntity(target, () -> prepareThrowHit(target, thrower, thrown, ricochet));
    }
    if (landed && thrower != null && impact != null) {
      Tasks.runAt(impact, () -> rules.landed(thrower, impact));
    }
    endFling(ballId, impact, landed);

    if (ball.isValid()) {
      ball.remove();
    }

    finishThrow(impact, thrown);
  }

  private void finishThrow(Loc impact, ThrownAxe thrown) {
    if (thrown.broken()) {
      if (impact != null) {
        rules.breakFx(impact);
      }
      return;
    }

    if (!thrown.recoverable()) {
      return;
    }

    PlayerRef thrower = host().player(thrown.ownerId());
    if (thrower != null) {
      Tasks.runEntity(thrower, () -> completeThrowRecovery(thrower, impact, thrown));
    }
  }

  private void prepareThrowHit(
      LivingRef target,
      PlayerRef thrower,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!isEligibleThrowTarget(target, thrown.ownerId())
        || !allowsThrowImpact(target, ricochet.count(), rules.ignorePassiveMobs())) {
      return;
    }

    Loc targetLocation = target.location();
    boolean playerTarget = target instanceof PlayerRef;
    boolean passive = !AdaptationDamageTargets.allows(target, true);
    Tasks.runEntity(
        thrower,
        () -> authorizeThrowHit(thrower, target, targetLocation, playerTarget, passive, thrown, ricochet)
    );
  }

  private void authorizeThrowHit(
      PlayerRef thrower,
      LivingRef target,
      Loc targetLocation,
      boolean playerTarget,
      boolean passive,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!thrower.isOnline() || !owner.hasActiveAdaptation(thrower)
        || (ricochet.count() > 0 && passive && owner.preferenceEnabled(thrower, AxePreferences.IGNORE_PASSIVE))) {
      return;
    }
    boolean allowed = playerTarget ? owner.canPVP(thrower, targetLocation) : owner.canPVE(thrower, targetLocation);
    if (allowed) {
      Tasks.runEntity(target, () -> damageThrowTarget(target, thrower, thrown, ricochet));
    }
  }

  private void damageThrowTarget(
      LivingRef target,
      PlayerRef thrower,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!isEligibleThrowTarget(target, thrown.ownerId())
        || !allowsThrowImpact(target, ricochet.count(), rules.ignorePassiveMobs())) {
      return;
    }
    ThrowDamageAttempt attempt = new ThrowDamageAttempt(thrower, target);
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      active = new ArrayDeque<>();
      damageAttempts.set(active);
    }
    active.addLast(attempt);
    try {
      AbilityDamage.deal(target, thrown.damage() + ricochet.bonusDamage(), thrower);
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
    rules.struck(thrower, target, thrown.axe());
    Tasks.runEntity(thrower, () -> rewardHit(thrower, target));
    if (attempt.killed() && ricochet.count() > 0) {
      Loc deathLocation = target.location();
      Tasks.runEntity(
          thrower,
          () -> rewardRicochetKill(thrower, deathLocation, ricochet.count())
      );
    }
  }

  private void rewardRicochetKill(PlayerRef thrower, Loc deathLocation, int ricochetCount) {
    if (!thrower.isOnline()) {
      return;
    }
    RangedRicochetBolt.rewardRicochetKill(thrower, deathLocation, ricochetCount);
  }

  static boolean allowsThrowImpact(LivingRef target, int ricochetCount, boolean ignorePassiveMobs) {
    return ricochetCount <= 0 || AdaptationDamageTargets.allows(target, ignorePassiveMobs);
  }

  private boolean isEligibleThrowTarget(LivingRef target, UUID ownerId) {
    if (!target.isValid() || target.isDead() || ownerId.equals(target.uuid())
        || owner.isProtectedFriendly(null, target)) {
      return false;
    }
    UUID tamer = target.tameOwner();
    return (tamer == null || !ownerId.equals(tamer)) && rules.strikes(ownerId, target);
  }

  private void completeThrowRecovery(PlayerRef thrower, Loc impact, ThrownAxe thrown) {
    if (!thrower.isOnline()) {
      return;
    }
    int returnTicks = rules.returnTicks();
    if (thrown.returns() && returnTicks > 0 && impact != null) {
      rules.returnFlight(thrower, impact, thrown.axe(), returnTicks);
      if (!Tasks.runEntity(thrower, () -> arrive(thrower, impact, thrown), returnTicks)) {
        arrive(thrower, impact, thrown);
      }
    } else if (thrown.returns()) {
      sweepHome(thrower, impact, thrown);
      recoverStampedAxe(thrower, thrown.recoveryKey());
    } else {
      dropAxe(impact, thrown, thrower);
    }
  }

  /** A returning fling reaches its owner: into the inventory, or down at the impact when they died or left the world. */
  private void arrive(PlayerRef thrower, Loc impact, ThrownAxe thrown) {
    if (!thrower.isOnline()) {
      return;
    }
    if (returnLandsAtImpact(thrower.isDead(), impact.world(), thrower.world())) {
      dropAxe(impact, thrown, thrower);
    } else {
      recoverStampedAxe(thrower, thrown.recoveryKey());
    }
  }

  static boolean returnLandsAtImpact(boolean ownerDead, WorldRef impactWorld, WorldRef ownerWorld) {
    return ownerDead || impactWorld == null || ownerWorld == null || !impactWorld.uid().equals(ownerWorld.uid());
  }

  private void sweepHome(PlayerRef thrower, Loc impact, ThrownAxe thrown) {
    ReturnSweep sweep = rules.returnSweep(thrower);
    if (sweep == null || sweep.maxTargets() <= 0 || impact == null || impact.world() == null
        || !impact.world().uid().equals(thrower.world().uid())) {
      return;
    }
    DataHolder data = thrower.data();
    DataKey<byte[]> key = recoveryData(thrown.recoveryKey());
    byte[] encoded = data.get(key);
    if (encoded == null) {
      return;
    }
    ItemRef axe;
    try {
      axe = items().deserialize(encoded);
    } catch (Throwable error) {
      return;
    }
    ItemRef flown = thrown.axe();
    int hitsAllowed = Math.min(sweep.maxTargets(), returnHitsBeforeBreaking(flown.isUnbreakable(), flown.damage(),
        maxDurability(flown), sweep.durabilityPerHit()));
    double damage = thrown.damage() * sweep.damageRatio();
    boolean ignorePassive = rules.ignorePassiveMobs() || owner.preferenceEnabled(thrower, AxePreferences.IGNORE_PASSIVE);
    List<LivingRef> struck = new ArrayList<>();
    for (LivingRef target : returnPath(thrower, impact.position(), thrower.eyeLocation().position(), sweep.width())) {
      if (struck.size() >= hitsAllowed) {
        break;
      }
      if (strikeOnReturn(thrower, target, damage, ignorePassive)) {
        struck.add(target);
      }
    }
    if (struck.isEmpty()) {
      return;
    }
    if (!axe.isUnbreakable() && sweep.durabilityPerHit() > 0) {
      axe.setDamage(axe.damage() + (struck.size() * sweep.durabilityPerHit()));
      try {
        data.set(key, items().serialize(axe));
      } catch (Throwable error) {
        AdaptLog.error("Failed to wear a returning thrown axe owned by " + thrower.name() + ".");
        AdaptLog.error(error);
      }
    }
    rules.returned(thrower, struck);
  }

  /** How many return hits an axe can pay for and still not break. */
  static int returnHitsBeforeBreaking(boolean unbreakable, int damage, int maxDurability, int perHit) {
    if (unbreakable || maxDurability <= 0 || perHit <= 0) {
      return Integer.MAX_VALUE;
    }
    return Math.max(0, (maxDurability - 1 - damage) / perHit);
  }

  private List<LivingRef> returnPath(PlayerRef thrower, Vec3 from, Vec3 to, double width) {
    Vec3 line = to.subtract(from);
    double lengthSquared = line.lengthSquared();
    Box box = new Box(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()), Math.min(from.z(), to.z()),
        Math.max(from.x(), to.x()), Math.max(from.y(), to.y()), Math.max(from.z(), to.z())).expand(width + 1D);
    List<LivingRef> found = new ArrayList<>();
    for (EntityRef entity : thrower.world().entitiesIn(box, (EntityRef candidate) -> candidate instanceof LivingRef)) {
      LivingRef target = (LivingRef) entity;
      if (isEligibleThrowTarget(target, thrower.uuid())
          && distanceToLine(target.location().position().add(0D, target.height() * 0.5D, 0D), from, line,
          lengthSquared) <= width) {
        found.add(target);
      }
    }
    found.sort(Comparator.comparingDouble((LivingRef target) -> target.location().position().distanceSquared(from)));
    return found;
  }

  static double distanceToLine(Vec3 point, Vec3 from, Vec3 line, double lengthSquared) {
    double along = lengthSquared <= 0D ? 0D : Math.max(0D, Math.min(1D, point.subtract(from).dot(line) / lengthSquared));
    return point.distance(from.add(line.multiply(along)));
  }

  private boolean strikeOnReturn(PlayerRef thrower, LivingRef target, double damage, boolean ignorePassive) {
    Loc at = target.location();
    if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(at))
        || !AdaptationDamageTargets.allows(target, ignorePassive)) {
      return false;
    }
    if (!owner.canDamageTarget(thrower, target)) {
      return false;
    }
    double before = target.health();
    ThrowDamageAttempt attempt = new ThrowDamageAttempt(thrower, target);
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      active = new ArrayDeque<>();
      damageAttempts.set(active);
    }
    active.addLast(attempt);
    try {
      AbilityDamage.deal(target, damage, thrower);
    } catch (Throwable error) {
      AdaptLog.error("Returning axe damage failed for target " + target.uuid() + ".");
      AdaptLog.error(error);
      return false;
    } finally {
      active.removeLastOccurrence(attempt);
      if (active.isEmpty()) {
        damageAttempts.remove();
      }
    }
    boolean landed = attempt.successful() || target.isDead() || target.health() < before;
    if (landed) {
      rules.hitFx(target);
    }
    return landed;
  }

  private void rewardHit(PlayerRef thrower, LivingRef target) {
    if (!thrower.isOnline() || !owner.hasActiveAdaptation(thrower)) {
      return;
    }
    rules.hit(thrower, target);
  }

  private void deliverAxe(PlayerRef thrower, ItemRef axe) {
    ItemRef main = thrower.inventory().mainHand();
    if (main == null || main.isEmpty()) {
      thrower.inventory().setMainHand(axe);
    } else {
      List<ItemRef> overflow = thrower.inventory().addItem(axe);
      for (ItemRef item : overflow) {
        thrower.world().dropItem(thrower.location().position(), item);
      }
    }
  }

  private void dropAxe(Loc impact, ThrownAxe thrown, PlayerRef thrower) {
    if (impact == null || impact.world() == null) {
      return;
    }
    DataHolder data = thrower.data();
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
      pending.cancel(thrower);
    }
  }

  public void joined(PlayerRef player) {
    recoverStampedAxes(player);
  }

  public void quit(PlayerRef player) {
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
      endFling(entry.getKey(), null, false);
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
    rules.returnFx(player);
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
        maxDurability(stack)
    )) {
      return true;
    }
    stack.setDamage(next);
    return false;
  }

  private static int maxDurability(ItemRef stack) {
    return stack.maxDamage() > 0 ? stack.maxDamage() : items().maxDurability(stack.type());
  }

  /** The vanilla melee damage of an axe type, which throws scale. */
  public static double meleeBaseDamage(Id axe) {
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
    PlayerRef thrower = host().player(thrown.ownerId());
    RecoveryDisposition disposition = recoveryDisposition(thrown.recoverable(), thrower != null && thrower.isOnline());
    if (disposition == RecoveryDisposition.DESTROYED) {
      return;
    }
    if (disposition == RecoveryDisposition.OWNER) {
      Tasks.runEntity(thrower, () -> recoverStampedAxe(thrower, thrown.recoveryKey()));
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
      PlayerRef thrower = knownOwner == null ? host().player(thrown.ownerId()) : knownOwner;
      synchronized (this) {
        deliveryAllowed = false;
        if (resolved || restorationScheduled) {
          return;
        }
        if (thrower == null) {
          persistFallback();
          return;
        }
        restorationScheduled = true;
      }

      if (Tasks.isOwnedByCurrentRegion(thrower)) {
        restoreOwned(thrower);
        return;
      }
      try {
        boolean scheduled = Tasks.runEntity(
            thrower,
            () -> restoreOwned(thrower),
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

    private void restoreOwned(PlayerRef thrower) {
      try {
        synchronized (this) {
          if (resolved) {
            return;
          }
          DataHolder data = thrower.data();
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
}
