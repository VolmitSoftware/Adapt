package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTargetPolicy;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class GuardianTargetPolicy implements GolemTargetPolicy {
  private static final double BLOCK_HALF = 0.5D;
  private static final Id CREEPER = Id.minecraft("creeper");

  private final GolemGuardianCore core;
  private final GuardNodeBehavior guard;
  private final LongSupplier clock;
  private final Map<UUID, Long> nextIntercepts = new ConcurrentHashMap<>();

  GuardianTargetPolicy(GolemGuardianCore core, GuardNodeBehavior guard, LongSupplier clock) {
    this.core = Objects.requireNonNull(core);
    this.guard = Objects.requireNonNull(guard);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public LivingRef select(GolemHandle golem, PlayerRef owner) {
    Watch watch = watch(golem, owner);
    if (watch == null) {
      return null;
    }
    LivingRef attacker = avenge(watch);
    LivingRef target = attacker == null ? nearest(watch) : attacker;
    if (target != null) {
      markEngaged(watch, target.location());
    }
    return target;
  }

  @Override
  public boolean holds(GolemHandle golem, PlayerRef owner, LivingRef target) {
    if (target == null || !Tasks.isOwnedByCurrentRegion(target) || !target.isValid() || target.isDead()) {
      return false;
    }
    Watch watch = watch(golem, owner);
    if (watch == null) {
      return false;
    }
    Loc at = target.location();
    if (!covers(watch, at) || !inRange(watch, at)) {
      return false;
    }
    MinionTargeting.Candidate captured = MinionTargeting.captureOwned(watch.ownerId(), target);
    if (reason(watch, target, captured) == GuardianRules.Reason.NONE || !engageable(watch, captured)) {
      return false;
    }
    LivingRef attacker = watch.attacker();
    if (attacker != null && !attacker.uuid().equals(target.uuid()) && avenge(watch) != null) {
      return false;
    }
    markEngaged(watch, at);
    return true;
  }

  @Override
  public void tick(GolemHandle golem, PlayerRef owner) {
    if (owner == null || !owner.isOnline()) {
      return;
    }
    long now = clock.getAsLong();
    Long due = nextIntercepts.get(golem.id());
    if (due != null && now < due) {
      return;
    }
    GolemGuardianCore.Config config = core.config();
    if (!GolemancyRules.unlocked(core.ownerLevel(golem), config.interceptLevel) || !core.intercepts(owner.uuid())) {
      return;
    }
    Watch watch = watch(golem, owner);
    if (watch == null) {
      return;
    }
    nextIntercepts.put(golem.id(), now + config.interceptIntervalMillis);
    UUID ownerId = owner.uuid();
    UUID golemId = watch.body().uuid();
    for (MobRef candidate : candidates(watch)) {
      if (candidate.uuid().equals(golemId) || !targetOf(candidate, ownerId) || !covers(watch, candidate.location())) {
        continue;
      }
      MinionTargeting.Candidate captured = MinionTargeting.captureOwned(ownerId, candidate);
      if (reason(watch, candidate, captured) != GuardianRules.Reason.NONE && engageable(watch, captured)) {
        candidate.setTarget(watch.body());
      }
    }
  }

  @Override
  public boolean idleBackoff() {
    return true;
  }

  boolean pending() {
    return !nextIntercepts.isEmpty() || guard.pending();
  }

  void sweep(long nowMillis) {
    nextIntercepts.values().removeIf(due -> nowMillis >= due);
    guard.sweep(nowMillis);
  }

  void forget() {
    nextIntercepts.clear();
    guard.forget();
  }

  private Watch watch(GolemHandle golem, PlayerRef owner) {
    if (!GolemancyRuntime.settings().allows(golem.body(), GolemCoreType.GUARDIAN)) {
      return null;
    }
    MobRef body = golem.entity();
    Loc here = body.location();
    WorldRef world = here.world();
    if (world == null) {
      return null;
    }
    GolemGuardianCore.Config config = core.config();
    long now = clock.getAsLong();
    PlayerRef online = owner != null && owner.isOnline() ? owner : null;
    int level = online == null ? GuardianRules.OFFLINE_LEVEL : core.ownerLevel(golem);
    GolemState state = golem.state();
    GolemMode mode = state.mode();
    Loc home = home(state.home(), world);
    Loc anchor = switch (mode) {
      case FOLLOW -> ownerAnchor(online, world, here);
      case STAY -> stayAnchor(body, world, here);
      case HOME, WORK -> home == null ? here : home;
    };
    List<NodeIndex.Entry> nodes = mode == GolemMode.WORK && home != null ? guard.nodesFor(golem, world, home, now) : List.of();
    boolean retaliate = online != null && GolemancyRules.unlocked(level, config.retaliateLevel);
    LivingRef attacker = retaliate ? core.recentAttacker(online.uuid(), now) : null;
    double sentry = golem.body() == GolemBody.FROST ? config.sentryRange : 0D;
    double leash = GolemancyRuntime.brainSettings().leashRadius();
    boolean retaliatePlayers = retaliate && core.retaliatesAgainstPlayers(online.uuid());
    GuardianRules.Options options = new GuardianRules.Options(online != null, retaliate, retaliatePlayers, config.targetCreepers, config.ignoreNamedMobs);
    double perception = GuardianRules.perception(config.perceptionByLevel, level, GolemRunes.effects(golem).radiusBonus(), leash);
    return new Watch(body, here, golem.owner(), online, anchor, perception, sentry, home, leash, nodes, attacker, options, now);
  }

  private LivingRef avenge(Watch watch) {
    LivingRef attacker = watch.attacker();
    if (attacker == null || attacker.uuid().equals(watch.body().uuid()) || !Tasks.isOwnedByCurrentRegion(attacker) || !attacker.isValid()
        || attacker.isDead()) {
      return null;
    }
    Loc at = attacker.location();
    if (!covers(watch, at) || !inRange(watch, at)) {
      return null;
    }
    MinionTargeting.Candidate captured = MinionTargeting.captureOwned(watch.ownerId(), attacker);
    return reason(watch, attacker, captured) == GuardianRules.Reason.RETALIATE && engageable(watch, captured) ? attacker : null;
  }

  private LivingRef nearest(Watch watch) {
    MobRef nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    UUID golemId = watch.body().uuid();
    for (MobRef candidate : candidates(watch)) {
      if (candidate.uuid().equals(golemId) || !prospect(watch, candidate, golemId)) {
        continue;
      }
      Loc at = candidate.location();
      double distance = at.distanceSquared(watch.here());
      if (distance >= nearestDistance || !covers(watch, at) || !inRange(watch, at)) {
        continue;
      }
      MinionTargeting.Candidate captured = MinionTargeting.captureOwned(watch.ownerId(), candidate);
      if (reason(watch, candidate, captured) == GuardianRules.Reason.NONE || !engageable(watch, captured)) {
        continue;
      }
      nearest = candidate;
      nearestDistance = distance;
    }
    return nearest;
  }

  private List<MobRef> candidates(Watch watch) {
    double perception = watch.perception();
    List<MobRef> found = mobsNear(watch.anchor(), perception, perception, perception);
    if (watch.nodes().isEmpty()) {
      return found;
    }
    Set<UUID> seen = new HashSet<>(found.size() * 2);
    for (MobRef mob : found) {
      seen.add(mob.uuid());
    }
    WorldRef world = watch.anchor().world();
    Box leash = Box.around(watch.home().position(), watch.leash(), watch.leash(), watch.leash());
    for (NodeIndex.Entry node : watch.nodes()) {
      Box query = nodeBox(node);
      if (!query.overlaps(leash)) {
        continue;
      }
      Box clipped = new Box(Math.max(query.minX(), leash.minX()), Math.max(query.minY(), leash.minY()), Math.max(query.minZ(), leash.minZ()),
          Math.min(query.maxX(), leash.maxX()), Math.min(query.maxY(), leash.maxY()), Math.min(query.maxZ(), leash.maxZ()));
      Loc middle = new Loc(world, (clipped.minX() + clipped.maxX()) / 2D, (clipped.minY() + clipped.maxY()) / 2D,
          (clipped.minZ() + clipped.maxZ()) / 2D, 0F, 0F);
      for (MobRef mob : mobsNear(middle, clipped.widthX() / 2D, clipped.height() / 2D, clipped.widthZ() / 2D)) {
        if (seen.add(mob.uuid())) {
          found.add(mob);
        }
      }
    }
    return found;
  }

  private static List<MobRef> mobsNear(Loc center, double x, double y, double z) {
    WorldRef world = center.world();
    List<MobRef> found = new ArrayList<>();
    if (world == null || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, x, z))) {
      return found;
    }
    for (EntityRef entity : world.entitiesIn(Box.around(center.position(), x, y, z), candidate -> candidate instanceof MobRef)) {
      found.add((MobRef) entity);
    }
    return found;
  }

  private GuardianRules.Reason reason(Watch watch, LivingRef candidate, MinionTargeting.Candidate captured) {
    LivingRef attacker = watch.attacker();
    GuardianRules.Candidate facts = new GuardianRules.Candidate(
        captured == null || captured.protectedFriendly(),
        candidate instanceof PlayerRef,
        candidate.has(EntityTrait.ENEMY),
        CREEPER.equals(candidate.type()),
        candidate.customName() != null,
        targetOf(candidate, watch.ownerId()) || targetOf(candidate, watch.body().uuid()),
        attacker != null && attacker.uuid().equals(candidate.uuid()));
    return GuardianRules.reason(facts, watch.options());
  }

  private boolean engageable(Watch watch, MinionTargeting.Candidate captured) {
    return captured != null && (watch.owner() == null || core.canEngage(watch.owner(), captured));
  }

  private void markEngaged(Watch watch, Loc at) {
    NodeKey node = nodeAt(watch, at);
    if (node != null) {
      guard.engaged(node, watch.now());
    }
  }

  private static boolean prospect(Watch watch, MobRef candidate, UUID golemId) {
    return candidate.has(EntityTrait.ENEMY) || targetOf(candidate, watch.ownerId()) || targetOf(candidate, golemId);
  }

  private static boolean covers(Watch watch, Loc at) {
    if (!Objects.equals(at.world(), watch.anchor().world())) {
      return false;
    }
    double perception = watch.perception();
    return at.distanceSquared(watch.anchor()) <= perception * perception || (withinLeash(watch, at) && nodeAt(watch, at) != null);
  }

  private static boolean withinLeash(Watch watch, Loc at) {
    Loc home = watch.home();
    double leash = watch.leash();
    return home != null && Objects.equals(at.world(), home.world()) && at.distanceSquared(home) <= leash * leash;
  }

  private static boolean inRange(Watch watch, Loc at) {
    double sentry = watch.sentry();
    return sentry <= 0D || at.distanceSquared(watch.here()) <= sentry * sentry;
  }

  private static NodeKey nodeAt(Watch watch, Loc at) {
    for (NodeIndex.Entry node : watch.nodes()) {
      if (NodeArea.covers(node.key(), node.record(), at.x(), at.y(), at.z())) {
        return node.key();
      }
    }
    return null;
  }

  private static Box nodeBox(NodeIndex.Entry node) {
    NodeCuboid area = node.record().area();
    int[] center = NodeArea.center(node.key());
    return new Box(center[0] + area.minX(), center[1] + area.minY(), center[2] + area.minZ(),
        center[0] + area.maxX() + 1D, center[1] + area.maxY() + 1D, center[2] + area.maxZ() + 1D);
  }

  private static boolean targetOf(LivingRef candidate, UUID target) {
    if (!(candidate instanceof MobRef mob)) {
      return false;
    }
    LivingRef current = mob.target();
    return current != null && current.uuid().equals(target);
  }

  private static Loc stayAnchor(MobRef body, WorldRef world, Loc here) {
    return GolemancyRuntime.brain(body)
        .flatMap(GolemBrain::stayAnchor)
        .filter(anchor -> world.equals(anchor.world()))
        .orElse(here);
  }

  private static Loc ownerAnchor(PlayerRef online, WorldRef world, Loc here) {
    return online != null && Tasks.isOwnedByCurrentRegion(online) && world.equals(online.world()) ? online.location() : here;
  }

  private static Loc home(GolemAnchor anchor, WorldRef world) {
    if (anchor == null || !world.uid().equals(anchor.world())) {
      return null;
    }
    return new Loc(world, anchor.x() + BLOCK_HALF, anchor.y(), anchor.z() + BLOCK_HALF, 0F, 0F);
  }

  private record Watch(MobRef body, Loc here, UUID ownerId, PlayerRef owner, Loc anchor, double perception, double sentry,
                       Loc home, double leash, List<NodeIndex.Entry> nodes, LivingRef attacker, GuardianRules.Options options,
                       long now) {
  }
}
