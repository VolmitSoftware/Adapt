package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.ranged.HeartseekerGuidance;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedPreferences;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.content.adaptation.ranged.RangedTrajectorySight;
import art.arcane.adapt.content.adaptation.ranged.RicochetHandoff;
import art.arcane.adapt.content.adaptation.ranged.TrajectoryInk;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heartseeker, Ricochet Bolt and Trajectory Sight fused: drawing a bow at a creature locks it and shows the curve the
 * arrow will take; the arrow bends onto the locked target, and a Ricochet Arrow then bounces on to the nearest second
 * target. Holding full draw and sweeping across creatures locks a Volley: the release sends one more arrow per extra
 * lock.
 */
public class Trueflight extends SkillUpgrade<Trueflight.Config> {
  public static final Id ID = Id.adapt("ranged/upgrade/trueflight");

  private static final Id BOW = Id.minecraft("bow");
  private static final Rgb LOCK_RED = new Rgb(255, 30, 60);
  private static final double BOW_FULL_DRAW_SPEED = 3.0D;

  private final Map<UUID, Lock> locks = playerState();
  private final Map<UUID, Flight> flights = new ConcurrentHashMap<>();
  private final Map<UUID, Long> previewedAt = playerState();
  private final Map<UUID, Volley> volleys = playerState();
  private final Map<UUID, Long> volleyReadyAt = playerState();
  private final RicochetHandoff handoff = new RicochetHandoff("Trueflight");
  private final RicochetArrow ricochetArrow;

  public Trueflight() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("target"));
    setInterval(50);
    ricochetArrow = registerTool(new RicochetArrow(this));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.TRAIL, RangedPreferences.IMPACT);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getLockRange(level), 0), 1);
    statLore(v, Format.f(getBounceRadius(level), 0), 2);
    statLore(v, Format.f(getBounceDamage(level), 1), 3);
    statLore(v, getVolleyLocks(level), 4);
    statLore(v, Format.duration(getConfig().volleyCooldownMillis, 1), 5);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    PlayerRef player = e.player();
    ItemRef item = e.item();
    if (!e.action().isRightClick() || item == null || !item.type().equals(BOW) || player.hasCooldown(BOW)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    long now = System.currentTimeMillis();
    LivingRef target = HeartseekerGuidance.lockCandidate(player, getLockRange(level));
    boolean lockable = target != null && canStrike(player, target);
    if (volleyReadyAt.getOrDefault(player.uuid(), 0L) <= now) {
      volleys.put(player.uuid(), new Volley(player, now, lockable ? target : null));
    }
    if (!lockable) {
      return;
    }

    locks.put(player.uuid(), new Lock(player, target, now + getConfig().lockMillis));
    fx(target, FxPriority.TRANSITION).dustRing(LOCK_RED, 0.7D, 14, 1.0F);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    if (!(e.shooter() instanceof PlayerRef player) || !e.bow().type().equals(BOW)
        || !(e.projectile() instanceof ProjectileRef arrow) || !RangedProjectiles.isArrowLike(arrow)
        || arrow.type().equals(RangedProjectiles.TRIDENT) || RangedHeartseeker.isSeekingProjectile(arrow)) {
      return;
    }

    long now = System.currentTimeMillis();
    Lock lock = locks.remove(player.uuid());
    Volley volley = volleys.remove(player.uuid());
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    List<LivingRef> volleyTargets = volley == null || e.force() < 1F ? List.of() : volley.liveTargets(this);
    LivingRef target = volleyTargets.size() >= 2 ? volleyTargets.getFirst()
        : lock != null && lock.live(now) && canStrike(player, lock.target()) ? lock.target() : null;
    if (target == null) {
      return;
    }

    boolean ricochet = ricochetArrow.spentBy(e) > 0;
    Vec3 launch = arrow.velocity();
    flights.put(arrow.uuid(), new Flight(arrow, player, target, level, HeartseekerGuidance.seekSpeed(arrow),
        arrow.location().position(), launch.lengthSquared() <= 0.000001D ? player.eyeLocation().direction() : launch,
        false, ricochet, now));
    if (volleyTargets.size() >= 2) {
      fireVolley(player, arrow, volleyTargets.subList(1, volleyTargets.size()), level, now);
    }
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    locks.values().removeIf((Lock lock) -> !lock.live(now));
    for (Volley volley : volleys.values()) {
      PlayerRef owner = volley.owner();
      if (!AdaptApi.scheduler().runEntity(owner, () -> sweep(volley, now))) {
        volleys.remove(owner.uuid(), volley);
      }
    }
    for (Flight flight : flights.values()) {
      ProjectileRef arrow = flight.arrow();
      if (!AdaptApi.scheduler().runEntity(arrow, () -> steer(arrow, flight, now))) {
        flights.remove(arrow.uuid(), flight);
      }
    }
    for (Lock lock : locks.values()) {
      UUID ownerId = lock.owner().uuid();
      if (now - previewedAt.getOrDefault(ownerId, 0L) >= getConfig().previewIntervalMillis) {
        previewedAt.put(ownerId, now);
        AdaptApi.scheduler().runEntity(lock.owner(), () -> preview(lock));
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void bonus(EntityDamageByEntityEvent e) {
    Flight flight = flights.get(e.damager().uuid());
    if (flight != null && flight.bounced() && e.damage() > 0D && e.entity().uuid().equals(flight.target().uuid())) {
      e.setDamage(e.damage() + getBounceDamage(flight.level()));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef arrow) || !(e.entity() instanceof LivingRef victim)) {
      return;
    }

    Flight flight = flights.remove(arrow.uuid());
    if (flight == null || e.finalDamage() <= 0D) {
      return;
    }

    PlayerRef owner = flight.owner();
    AdaptApi.scheduler().runEntity(owner, () -> rewardHit(owner, victim));
    if (flight.bounced() || !flight.ricochet()) {
      return;
    }

    LivingRef next = nextTarget(owner, victim, getBounceRadius(flight.level()));
    if (next == null) {
      return;
    }

    Loc from = victim.location().add(0D, victim.height() * 0.6D, 0D);
    Vec3 toNext = aimPoint(next).position().subtract(from.position());
    if (toNext.lengthSquared() <= 0.000001D) {
      return;
    }

    Vec3 direction = toNext.normalize();
    double speed = flight.speed() * (1D + getConfig().bounceSpeedBonus);
    Loc exit = from.add(direction.multiply(Math.max(0.6D, victim.width() + 0.4D)));
    Loc aim = aimPoint(next);
    ProjectileRef bounced = handoff.relaunch(arrow, exit, direction.multiply(speed), owner,
        (ProjectileRef copy) -> {
          copy.setPickupRule(ArrowPickup.DISALLOWED);
          Stormshot.carryOver(arrow, copy);
          flights.put(copy.uuid(), new Flight(copy, owner, next, flight.level(), speed, exit.position(), direction, true,
              false, System.currentTimeMillis()));
        });
    if (bounced == null) {
      return;
    }

    AdaptApi.scheduler().runEntity(arrow, arrow::remove, 1);
    fx(from, FxPriority.COMBAT)
        .line(Particles.ELECTRIC_SPARK, aim.x(), aim.y(), aim.z(), 8)
        .dustRing(new Rgb(180, 210, 255), 0.5D, 12, 1.0F);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    flights.remove(e.entity().uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    locks.remove(id);
    volleys.remove(id);
    flights.values().removeIf((Flight flight) -> flight.owner().uuid().equals(id));
  }

  @Override
  public void unregister() {
    flights.clear();
    super.unregister();
  }

  private void sweep(Volley volley, long now) {
    PlayerRef owner = volley.owner();
    ItemRef active = owner.activeItem();
    int level = getActiveLevel(owner);
    if (level <= 0 || !owner.isOnline() || !owner.isHandRaised() || active == null || !active.type().equals(BOW)) {
      volleys.remove(owner.uuid(), volley);
      return;
    }
    if (now - volley.drawnAt() < getConfig().fullDrawMillis || volley.targets().size() >= getVolleyLocks(level)) {
      return;
    }
    LivingRef target = HeartseekerGuidance.lockCandidate(owner, getLockRange(level));
    if (target != null && canStrike(owner, target) && volley.add(target)) {
      fx(target, FxPriority.TRANSITION).dustRing(LOCK_RED, 0.7D, 14, 1.0F);
      cue(owner, MutationCue.BOON);
    }
  }

  private void fireVolley(PlayerRef player, ProjectileRef first, List<LivingRef> targets, int level, long now) {
    double speed = Math.max(first.velocity().length(), BOW_FULL_DRAW_SPEED);
    int fired = 0;
    for (LivingRef target : targets) {
      Loc eye = player.eyeLocation();
      Vec3 toTarget = aimPoint(target).position().subtract(eye.position());
      if (toTarget.lengthSquared() <= 0.000001D || !payArrow(player)) {
        continue;
      }
      Vec3 direction = toTarget.normalize();
      ProjectileRef arrow = player.launchProjectile(RangedProjectiles.ARROW, (ProjectileRef launched) -> {
        launched.setVelocity(direction.multiply(speed));
        launched.setPickupRule(ArrowPickup.ALLOWED);
      });
      if (arrow == null) {
        continue;
      }
      fired++;
      flights.put(arrow.uuid(), new Flight(arrow, player, target, level, HeartseekerGuidance.seekSpeed(arrow),
          eye.position(), direction, false, false, now));
    }
    if (fired > 0) {
      volleyReadyAt.put(player.uuid(), now + getConfig().volleyCooldownMillis);
      addStat(player, "ranged.trueflight.volleys", 1);
      cue(player, MutationCue.FIGHT);
    }
  }

  private boolean payArrow(PlayerRef player) {
    ItemRef unit = AdaptApi.platform().host().items().create(RangedProjectiles.ARROW, 1);
    return player.inventory().containsAtLeast(unit, 1) && payItemCost(player, "volley", unit, 1,
        () -> player.inventory().removeSimilar(unit, 1) >= 1);
  }

  private void steer(ProjectileRef arrow, Flight flight, long now) {
    LivingRef target = flight.target();
    if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock() || arrow.isOnGround()
        || now - flight.launchedAt() > getConfig().maxFlightMillis
        || !target.isValid() || target.isDead() || !target.world().uid().equals(arrow.world().uid())
        || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(target))) {
      flights.remove(arrow.uuid(), flight);
      return;
    }

    Vec3 position = arrow.location().position();
    Vec3 velocity = arrow.velocity();
    HeartseekerGuidance.Turn turn = HeartseekerGuidance.steer(getArc(), velocity.lengthSquared() <= 0.000001D
            ? flight.arcDirection() : velocity.normalize(), position, aimPoint(target).position(),
        flight.arcOrigin(), flight.arcDirection(), position.distance(flight.arcOrigin()), flight.arcActive());
    flight.arcActive(turn.arcActive());
    HeartseekerGuidance.fly(arrow, turn.direction(), flight.speed());
  }

  private void preview(Lock lock) {
    PlayerRef player = lock.owner();
    ItemRef active = player.activeItem();
    TrajectoryInk.Style style = inkStyle();
    if (style == null || !player.isOnline() || !player.isHandRaised() || active == null || !active.type().equals(BOW)
        || !lock.target().isValid() || !areParticlesEnabled()
        || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(lock.target()))) {
      return;
    }

    TrajectoryInk ink = new TrajectoryInk(player, style,
        preferenceEnabled(player, RangedPreferences.TRAIL), preferenceEnabled(player, RangedPreferences.IMPACT));
    ink.seekingPath(getConfig().previewSegments, player.eyeLocation().direction().normalize()
        .multiply(BOW_FULL_DRAW_SPEED), aimPoint(lock.target()), getArc(), () -> true);
  }

  private TrajectoryInk.Style inkStyle() {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof RangedTrajectorySight sight) {
        return sight.inkStyle();
      }
    }
    return null;
  }

  private LivingRef nextTarget(PlayerRef owner, LivingRef victim, double radius) {
    Loc center = victim.location();
    LivingRef nearest = null;
    double best = Double.MAX_VALUE;
    for (EntityRef entity : victim.nearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof LivingRef candidate) || candidate.uuid().equals(victim.uuid())
          || candidate.isDead() || !canStrike(owner, candidate)) {
        continue;
      }
      double distance = candidate.location().distanceSquared(center);
      if (distance < best) {
        best = distance;
        nearest = candidate;
      }
    }
    return nearest;
  }

  private boolean canStrike(PlayerRef owner, LivingRef target) {
    if (target.uuid().equals(owner.uuid()) || target.isInvisible() || isProtectedFriendly(owner, target)
        || !AdaptationDamageTargets.allows(target, getConfig().ignorePassiveMobs)) {
      return false;
    }
    return target instanceof PlayerRef ? canPVP(owner, target.location()) : canPVE(owner, target.location());
  }

  private void rewardHit(PlayerRef owner, LivingRef victim) {
    if (!owner.isOnline()) {
      return;
    }
    xpOn(owner, victim, getConfig().xpPerHit);
    addStat(owner, "ranged.trueflight.hits", 1);
    cue(owner, MutationCue.FIGHT);
  }

  private static Loc aimPoint(LivingRef target) {
    return target.location().add(0D, target.height() * 0.6D, 0D);
  }

  private HeartseekerGuidance.Arc getArc() {
    return new HeartseekerGuidance.Arc(getConfig().initialArcDistance, getConfig().initialArcControlDistance,
        getConfig().lungeRadius, getConfig().turnDegreesPerTick, getConfig().lungeTurnDegreesPerTick);
  }

  double getLockRange(int level) {
    return getConfig().lockRangeBase + (level * getConfig().lockRangePerLevel);
  }

  double getBounceRadius(int level) {
    return getConfig().bounceRadiusBase + (level * getConfig().bounceRadiusPerLevel);
  }

  int getVolleyLocks(int level) {
    return Math.max(1, getConfig().volleyLocksBase + level);
  }

  double getBounceDamage(int level) {
    return getConfig().bounceDamageBase + (level * getConfig().bounceDamagePerLevel);
  }

  private static final class Volley {
    private final PlayerRef owner;
    private final long drawnAt;
    private final List<LivingRef> targets = new ArrayList<>(4);

    private Volley(PlayerRef owner, long drawnAt, LivingRef first) {
      this.owner = owner;
      this.drawnAt = drawnAt;
      if (first != null) {
        targets.add(first);
      }
    }

    PlayerRef owner() {
      return owner;
    }

    long drawnAt() {
      return drawnAt;
    }

    synchronized List<LivingRef> targets() {
      return List.copyOf(targets);
    }

    synchronized boolean add(LivingRef target) {
      for (LivingRef locked : targets) {
        if (locked.uuid().equals(target.uuid())) {
          return false;
        }
      }
      return targets.add(target);
    }

    synchronized List<LivingRef> liveTargets(Trueflight trueflight) {
      List<LivingRef> live = new ArrayList<>(targets.size());
      for (LivingRef target : targets) {
        if (target.isValid() && !target.isDead() && trueflight.canStrike(owner, target)) {
          live.add(target);
        }
      }
      return live;
    }
  }

  private record Lock(PlayerRef owner, LivingRef target, long expiresAt) {
    boolean live(long now) {
      return expiresAt > now && target.isValid() && !target.isDead();
    }
  }

  private static final class Flight {
    private final ProjectileRef arrow;
    private final PlayerRef owner;
    private final LivingRef target;
    private final int level;
    private final double speed;
    private final Vec3 arcOrigin;
    private final Vec3 arcDirection;
    private final boolean bounced;
    private final boolean ricochet;
    private final long launchedAt;
    private volatile boolean arcActive;

    private Flight(ProjectileRef arrow, PlayerRef owner, LivingRef target, int level, double speed, Vec3 arcOrigin,
                   Vec3 arcDirection, boolean bounced, boolean ricochet, long launchedAt) {
      this.arrow = arrow;
      this.owner = owner;
      this.target = target;
      this.level = level;
      this.speed = speed;
      this.arcOrigin = arcOrigin;
      this.arcDirection = arcDirection;
      this.bounced = bounced;
      this.ricochet = ricochet;
      this.launchedAt = launchedAt;
      this.arcActive = !bounced;
    }

    ProjectileRef arrow() {
      return arrow;
    }

    PlayerRef owner() {
      return owner;
    }

    LivingRef target() {
      return target;
    }

    int level() {
      return level;
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

    boolean bounced() {
      return bounced;
    }

    boolean ricochet() {
      return ricochet;
    }

    long launchedAt() {
      return launchedAt;
    }

    boolean arcActive() {
      return arcActive;
    }

    void arcActive(boolean active) {
      arcActive = active;
    }
  }

  @ConfigDescription("Locked arrows curve onto their target and bounce on to the nearest second one.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Lock range before level scaling, in blocks.", impact = "Higher values lock targets from farther away.")
    double lockRangeBase = 24;
    @ConfigDoc(value = "Lock range added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double lockRangePerLevel = 8;
    @ConfigDoc(value = "How long a lock waits for the shot, in milliseconds.", impact = "Higher values allow slower draws.")
    long lockMillis = 4000;
    @ConfigDoc(value = "Bounce search radius around the first target before level scaling, in blocks.", impact = "Higher values find a second target farther away.")
    double bounceRadiusBase = 6;
    @ConfigDoc(value = "Bounce search radius added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double bounceRadiusPerLevel = 2;
    @ConfigDoc(value = "Extra damage on the bounced hit before level scaling.", impact = "Higher values make the second hit stronger.")
    double bounceDamageBase = 1;
    @ConfigDoc(value = "Extra bounced damage added per level.", impact = "Higher values reward higher levels more.")
    double bounceDamagePerLevel = 1;
    @ConfigDoc(value = "Speed gained by the arrow when it bounces.", impact = "Higher values make the second leg faster.")
    double bounceSpeedBonus = 0.2;
    @ConfigDoc(value = "Leave passive and neutral mobs out of locks and bounces.", impact = "When enabled, only hostile mobs and players are chosen.")
    boolean ignorePassiveMobs = true;
    @ConfigDoc(value = "Distance the opening curve lasts, in blocks.", impact = "Higher values make a wider opening curve.")
    double initialArcDistance = 12;
    @ConfigDoc(value = "How far ahead the opening curve bends from, in blocks.", impact = "Higher values make the curve leave straighter.")
    double initialArcControlDistance = 8;
    @ConfigDoc(value = "Distance from the target where the arrow turns hardest, in blocks.", impact = "Higher values start the final turn earlier.")
    double lungeRadius = 2.5;
    @ConfigDoc(value = "Steering per tick in flight, in degrees.", impact = "Higher values let arrows turn tighter.")
    double turnDegreesPerTick = 10;
    @ConfigDoc(value = "Steering per tick near the target, in degrees.", impact = "Higher values make final turns sharper.")
    double lungeTurnDegreesPerTick = 18;
    @ConfigDoc(value = "Longest a locked arrow steers, in milliseconds.", impact = "Higher values let arrows chase longer.")
    long maxFlightMillis = 6000;
    @ConfigDoc(value = "Time between redraws of the curve while drawing, in milliseconds.", impact = "Lower values redraw the curve more often.")
    long previewIntervalMillis = 150;
    @ConfigDoc(value = "Points sampled when drawing the curve.", impact = "Higher values draw longer, smoother curves.")
    int previewSegments = 24;
    @ConfigDoc(value = "Skill XP for each locked hit.", impact = "Higher values reward locked shots.")
    double xpPerHit = 6;
    @ConfigDoc(value = "Volley locks before adding the level.", impact = "Higher values let one release hit more targets.")
    int volleyLocksBase = 1;
    @ConfigDoc(value = "Draw time before sweeping adds Volley locks, in milliseconds.", impact = "Lower values start locking sooner.")
    long fullDrawMillis = 1000;
    @ConfigDoc(value = "Time between Volleys, in milliseconds.", impact = "Lower values let you fire Volleys more often.")
    long volleyCooldownMillis = 6000;

  }
}
