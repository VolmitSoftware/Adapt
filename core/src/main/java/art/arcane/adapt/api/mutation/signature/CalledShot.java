package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.hunter.upgraded.TrackersCompass;
import art.arcane.adapt.content.adaptation.ranged.HeartseekerGuidance;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Hunter and Ranged, either leading, replacing Convergence: the nearest natural creature of the kind your Tracker's
 * Compass follows glows for you alone far past the compass's reach, and a bow drawn at it locks on from up to 64
 * blocks, so the arrow seeks it the way Trueflight's does.
 */
public final class CalledShot extends Signature<CalledShot.Config> {
  private static final ViewerGlowPort.Layer LAYER = ViewerGlowPort.Layer.HUNTER;
  private static final Id BOW = Id.minecraft("bow");
  private static final Rgb CALL_RED = new Rgb(200, 50, 40);
  private static final double VERTICAL_REACH = 48D;
  private static final double FOLIA_FLOOR = 16D;
  private final Map<UUID, Marked> marked = playerState();
  private final Map<UUID, Lock> locks = playerState();
  private final Map<UUID, ProjectileRef> shots = playerState();

  public CalledShot(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
    setInterval(500);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, (int) glowRange(level), 1);
    statLore(v, (int) lockRange(level), 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer data : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = data.getPlayer();
      if (player != null) {
        Tasks.runEntity(player, () -> mark(player));
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    PlayerRef player = e.player();
    ItemRef item = e.item();
    if (!e.action().isRightClick() || item == null || !BOW.equals(item.type())) {
      return;
    }
    Marked mark = marked.get(player.uuid());
    int level = getActiveLevel(player);
    if (level <= 0 || mark == null || mark.target() == null || !aimedAt(player, mark.target(), lockRange(level))) {
      locks.remove(player.uuid());
      return;
    }
    locks.put(player.uuid(), new Lock(mark.target(), System.currentTimeMillis() + getConfig().lockMillis));
    fx(mark.target(), FxPriority.TRANSITION).dustRing(CALL_RED, 0.7D, 14, 1.0F);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    if (e.isCancelled() || !(e.shooter() instanceof PlayerRef player) || !BOW.equals(e.bow().type())
        || !(e.projectile() instanceof ProjectileRef arrow) || !RangedProjectiles.isArrowLike(arrow)
        || RangedHeartseeker.isSeekingProjectile(arrow)) {
      return;
    }
    Lock lock = locks.remove(player.uuid());
    if (lock == null || lock.until() < System.currentTimeMillis() || !lock.target().isValid() || lock.target().isDead()) {
      return;
    }
    shots.put(player.uuid(), arrow);
    fire(player, lock.target(), null, false);
    shots.remove(player.uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Marked mark = marked.remove(e.player().uuid());
    if (mark != null && mark.target() != null) {
      ViewerGlowPort.current().unset(LAYER, mark.target().uuid(), mark.target().entityId(), e.player());
    }
  }

  @Override
  protected Outcome run(BlockUse use) {
    LivingRef target = use.target();
    ProjectileRef arrow = shots.get(use.player().uuid());
    if (target == null || arrow == null) {
      return Outcome.MISSED;
    }
    Vec3 launch = arrow.velocity();
    Flight flight = new Flight(arrow, target, HeartseekerGuidance.seekSpeed(arrow), arrow.location().position(),
        launch.lengthSquared() <= 1.0E-6 ? use.player().eyeLocation().direction() : launch.normalize(),
        System.currentTimeMillis());
    Tasks.runEntity(arrow, () -> steer(flight), 1);
    return Outcome.lasting(target, 20);
  }

  private void steer(Flight flight) {
    ProjectileRef arrow = flight.arrow();
    LivingRef target = flight.target();
    if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock() || arrow.isOnGround()
        || System.currentTimeMillis() - flight.launchedAt() > getConfig().maxFlightMillis
        || !target.isValid() || target.isDead() || !target.world().uid().equals(arrow.world().uid())
        || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(target))) {
      return;
    }
    Vec3 position = arrow.location().position();
    Vec3 velocity = arrow.velocity();
    Vec3 aim = target.location().add(0D, target.height() * 0.6D, 0D).position();
    HeartseekerGuidance.Turn turn = HeartseekerGuidance.steer(arc(), velocity.lengthSquared() <= 1.0E-6
            ? flight.arcDirection() : velocity.normalize(), position, aim, flight.arcOrigin(), flight.arcDirection(),
        position.distance(flight.arcOrigin()), flight.arcActive());
    flight.arcActive = turn.arcActive();
    HeartseekerGuidance.fly(arrow, turn.direction(), flight.speed());
    Tasks.runEntity(arrow, () -> steer(flight), 1);
  }

  /**
   * Keeps the compass's kind marked: the nearest natural one within the glow range. A found quarry, or finding none, is
   * kept for a few pulses before the area is searched again.
   */
  private void mark(PlayerRef player) {
    int level = getActiveLevel(player);
    Id kind = level > 0 && usable() ? TrackersCompass.trackedKind(player) : null;
    Marked previous = marked.get(player.uuid());
    LivingRef before = previous == null ? null : previous.target();
    boolean kept = previous != null && kind != null && kind.equals(previous.kind()) && previous.pulsesLeft() > 0
        && (before == null || natural(player, before, glowRange(level)));
    LivingRef next = kept ? before : kind == null ? null : nearest(player, kind, glowRange(level));
    ViewerGlowPort glow = ViewerGlowPort.current();
    if (before != null && (next == null || !next.uuid().equals(before.uuid()))) {
      glow.unset(LAYER, before.uuid(), before.entityId(), player);
    }
    if (kind == null) {
      marked.remove(player.uuid());
      return;
    }
    if (next != null && (before == null || !next.uuid().equals(before.uuid()))) {
      glow.set(LAYER, next, player, GlowColor.GOLD);
    }
    marked.put(player.uuid(), new Marked(kind, next, kept ? previous.pulsesLeft() - 1 : getConfig().rescanPulses));
  }

  private @Nullable LivingRef nearest(PlayerRef player, Id kind, double glowRange) {
    WorldRef world = player.world();
    Loc from = player.location();
    double range = searchRange(from, glowRange);
    if (range <= 0D) {
      return null;
    }
    LivingRef best = null;
    double bestSquared = Double.MAX_VALUE;
    for (EntityRef entity : world.entitiesIn(Box.around(from.position(), range, Math.min(range, VERTICAL_REACH), range),
        (EntityRef candidate) -> kind.equals(candidate.type()))) {
      if (entity instanceof LivingRef living && natural(player, living, range)) {
        double squared = from.distanceSquared(living.location());
        if (squared < bestSquared) {
          best = living;
          bestSquared = squared;
        }
      }
    }
    return best;
  }

  /** On Folia, the widest part of {@code range} whose chunks this region owns, down to a floor; 0 when none is. */
  private double searchRange(Loc from, double range) {
    if (!Tasks.isFoliaThreading()) {
      return range;
    }
    double owned = range;
    while (owned > FOLIA_FLOOR && !Tasks.isOwnedByCurrentRegion(from, owned, owned)) {
      owned /= 2D;
    }
    return Tasks.isOwnedByCurrentRegion(from, owned, owned) ? owned : 0D;
  }

  /** Alive, in reach and natural: no players, pets, named mobs, bosses or spawner, egg and bred mobs. */
  private boolean natural(PlayerRef player, LivingRef target, double range) {
    if (target instanceof PlayerRef || !target.isValid() || target.isDead()
        || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(target)) || target.tameOwner() != null
        || target.has(EntityTrait.BOSS) || (target.customName() != null && !target.customName().isBlank())
        || MutationPayouts.farmed(player, target)) {
      return false;
    }
    Loc at = target.location();
    Loc from = player.location();
    return at.sameWorld(from) && Math.abs(at.y() - from.y()) <= VERTICAL_REACH && at.distanceSquared(from) <= range * range;
  }

  /** Within the lock range and the aim cone, with nothing else under the crosshair. */
  private boolean aimedAt(PlayerRef player, LivingRef target, double range) {
    Loc eye = player.eyeLocation();
    Vec3 toTarget = target.location().add(0D, target.height() * 0.6D, 0D).position().subtract(eye.position());
    double distance = toTarget.length();
    if (!target.isValid() || target.isDead() || !eye.sameWorld(target.location()) || distance > range || distance < 1.0E-3) {
      return false;
    }
    double cosine = toTarget.multiply(1D / distance).dot(eye.direction().normalize());
    if (cosine < Math.cos(Math.toRadians(getConfig().aimConeDegrees))) {
      return false;
    }
    LivingRef crosshair = HeartseekerGuidance.lockCandidate(player, range);
    return crosshair == null || crosshair.uuid().equals(target.uuid());
  }

  private HeartseekerGuidance.Arc arc() {
    return new HeartseekerGuidance.Arc(getConfig().initialArcDistance, getConfig().initialArcControlDistance,
        getConfig().lungeRadius, getConfig().turnDegreesPerTick, getConfig().lungeTurnDegreesPerTick);
  }

  double glowRange(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().glowRangeAtFirstLevel, getConfig().glowRangeAtTopLevel);
  }

  double lockRange(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().lockRangeAtFirstLevel, getConfig().lockRangeAtTopLevel);
  }

  private record Marked(Id kind, @Nullable LivingRef target, int pulsesLeft) {
  }

  private record Lock(LivingRef target, long until) {
  }

  private static final class Flight {
    private final ProjectileRef arrow;
    private final LivingRef target;
    private final double speed;
    private final Vec3 arcOrigin;
    private final Vec3 arcDirection;
    private final long launchedAt;
    private volatile boolean arcActive = true;

    private Flight(ProjectileRef arrow, LivingRef target, double speed, Vec3 arcOrigin, Vec3 arcDirection, long launchedAt) {
      this.arrow = arrow;
      this.target = target;
      this.speed = speed;
      this.arcOrigin = arcOrigin;
      this.arcDirection = arcDirection;
      this.launchedAt = launchedAt;
    }

    ProjectileRef arrow() {
      return arrow;
    }

    LivingRef target() {
      return target;
    }

    double speed() {
      return speed;
    }

    Vec3 arcOrigin() {
      return arcOrigin;
    }

    Vec3 arcDirection() {
      return arcDirection;
    }

    long launchedAt() {
      return launchedAt;
    }

    boolean arcActive() {
      return arcActive;
    }
  }

  @ConfigDescription("Your Tracker's Compass quarry glows for you far past the compass's reach, and a bow drawn at it locks on from far away.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "How far the quarry glows for you at level 1, in blocks.", impact = "Higher values reveal the quarry farther away but search a wider area.")
    double glowRangeAtFirstLevel = 96;
    @ConfigDoc(value = "How far the quarry glows for you at the top level, in blocks.", impact = "Higher values reveal the quarry farther away but search a wider area.")
    double glowRangeAtTopLevel = 128;
    @ConfigDoc(value = "Farthest a drawn bow locks on at level 1, in blocks.", impact = "Higher values lock from farther away.")
    double lockRangeAtFirstLevel = 40;
    @ConfigDoc(value = "Farthest a drawn bow locks on at the top level, in blocks.", impact = "Higher values lock from farther away.")
    double lockRangeAtTopLevel = 64;
    @ConfigDoc(value = "Widest angle between your aim and the quarry that still locks, in degrees.", impact = "Higher values forgive looser aim.")
    double aimConeDegrees = 12;
    @ConfigDoc(value = "Glow pulses a found quarry, or finding none, is kept before the area is searched again.", impact = "Higher values search less often.")
    int rescanPulses = 6;
    @ConfigDoc(value = "How long a lock waits for the shot, in milliseconds.", impact = "Higher values allow slower draws.")
    long lockMillis = 4000;
    @ConfigDoc(value = "Longest a called arrow steers, in milliseconds.", impact = "Higher values let arrows chase longer.")
    long maxFlightMillis = 6000;
    @ConfigDoc(value = "Distance the opening curve lasts, in blocks.", impact = "Higher values make a wider opening curve.")
    double initialArcDistance = 12;
    @ConfigDoc(value = "How far ahead the opening curve bends from, in blocks.", impact = "Higher values make the curve leave straighter.")
    double initialArcControlDistance = 8;
    @ConfigDoc(value = "Distance from the quarry where the arrow turns hardest, in blocks.", impact = "Higher values start the final turn earlier.")
    double lungeRadius = 2.5;
    @ConfigDoc(value = "Steering per tick in flight, in degrees.", impact = "Higher values let arrows turn tighter.")
    double turnDegreesPerTick = 10;
    @ConfigDoc(value = "Steering per tick near the quarry, in degrees.", impact = "Higher values make final turns sharper.")
    double lungeTurnDegreesPerTick = 18;
    @ConfigDoc(value = "Cooldown after a called shot, in milliseconds.", impact = "Higher values space called shots further apart.")
    long cooldownMillis = 0;
  }
}
