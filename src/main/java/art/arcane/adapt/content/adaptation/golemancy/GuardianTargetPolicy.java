package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTargetPolicy;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

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
  public LivingEntity select(GolemHandle golem, Player owner) {
    Watch watch = watch(golem, owner);
    if (watch == null) {
      return null;
    }
    LivingEntity attacker = avenge(watch);
    LivingEntity target = attacker == null ? nearest(watch) : attacker;
    if (target != null) {
      markEngaged(watch, target.getLocation());
    }
    return target;
  }

  @Override
  public boolean holds(GolemHandle golem, Player owner, LivingEntity target) {
    if (target == null || !target.isValid() || target.isDead() || !J.isOwnedByCurrentRegion(target)) {
      return false;
    }
    Watch watch = watch(golem, owner);
    if (watch == null) {
      return false;
    }
    Location at = target.getLocation();
    if (!covers(watch, at) || !inRange(watch, at) || reason(watch, target) == GuardianRules.Reason.NONE) {
      return false;
    }
    markEngaged(watch, at);
    return true;
  }

  @Override
  public void tick(GolemHandle golem, Player owner) {
    if (owner == null || !owner.isOnline()) {
      return;
    }
    long now = clock.getAsLong();
    Long due = nextIntercepts.get(golem.id());
    if (due != null && now < due) {
      return;
    }
    GolemGuardianCore.Config config = core.config();
    if (!GuardianRules.unlocked(core.ownerLevel(golem), config.interceptLevel)) {
      return;
    }
    Watch watch = watch(golem, owner);
    if (watch == null) {
      return;
    }
    nextIntercepts.put(golem.id(), now + Math.max(0L, config.interceptIntervalMillis));
    UUID ownerId = owner.getUniqueId();
    for (Mob candidate : candidates(watch)) {
      if (candidate != watch.body() && targetOf(candidate, ownerId) && reason(watch, candidate) != GuardianRules.Reason.NONE
          && core.canEngage(owner, candidate)) {
        candidate.setTarget(watch.body());
      }
    }
  }

  boolean pending() {
    return !nextIntercepts.isEmpty();
  }

  void sweep(long nowMillis) {
    nextIntercepts.values().removeIf(due -> nowMillis >= due);
  }

  void forget() {
    nextIntercepts.clear();
  }

  private Watch watch(GolemHandle golem, Player owner) {
    if (!GolemancyRuntime.settings().allows(golem.body(), GolemCoreType.GUARDIAN)) {
      return null;
    }
    Mob body = golem.entity();
    Location here = body.getLocation();
    World world = here.getWorld();
    if (world == null) {
      return null;
    }
    GolemGuardianCore.Config config = core.config();
    long now = clock.getAsLong();
    Player online = owner != null && owner.isOnline() ? owner : null;
    int level = online == null ? GuardianRules.OFFLINE_LEVEL : core.ownerLevel(golem);
    GolemState state = golem.state();
    GolemMode mode = state.mode();
    Location home = home(state.home(), world);
    Location anchor = switch (mode) {
      case FOLLOW -> ownerAnchor(online, world, here);
      case STAY -> here;
      case HOME, WORK -> home == null ? here : home;
    };
    List<NodeIndex.Entry> nodes = mode == GolemMode.WORK && home != null ? guard.nodesFor(golem, world, home) : List.of();
    boolean retaliate = online != null && GuardianRules.unlocked(level, config.retaliateLevel);
    LivingEntity attacker = retaliate ? core.recentAttacker(online.getUniqueId(), now) : null;
    double sentry = golem.body() == GolemBody.FROST ? config.sentryRange : 0D;
    GuardianRules.Options options = new GuardianRules.Options(online != null, retaliate, config.targetCreepers, config.ignoreNamedMobs);
    return new Watch(body, here, golem.owner(), online, anchor, GuardianRules.perception(config.perceptionByLevel, level), sentry, nodes,
        attacker, options, now);
  }

  private LivingEntity avenge(Watch watch) {
    LivingEntity attacker = watch.attacker();
    if (attacker == null || attacker == watch.body() || !J.isOwnedByCurrentRegion(attacker) || !attacker.isValid() || attacker.isDead()) {
      return null;
    }
    Location at = attacker.getLocation();
    boolean eligible = covers(watch, at) && inRange(watch, at) && reason(watch, attacker) == GuardianRules.Reason.RETALIATE;
    return eligible && engageable(watch, attacker) ? attacker : null;
  }

  private LivingEntity nearest(Watch watch) {
    Mob nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Mob candidate : candidates(watch)) {
      if (candidate == watch.body()) {
        continue;
      }
      Location at = candidate.getLocation();
      double distance = at.distanceSquared(watch.here());
      if (distance >= nearestDistance || !covers(watch, at) || !inRange(watch, at)
          || reason(watch, candidate) == GuardianRules.Reason.NONE || !engageable(watch, candidate)) {
        continue;
      }
      nearest = candidate;
      nearestDistance = distance;
    }
    return nearest;
  }

  private List<Mob> candidates(Watch watch) {
    List<Mob> found = PaperCompat.nearbyEntitiesByType(Mob.class, watch.anchor(), watch.perception());
    if (watch.nodes().isEmpty()) {
      return found;
    }
    Set<UUID> seen = new HashSet<>(found.size() * 2);
    for (Mob mob : found) {
      seen.add(mob.getUniqueId());
    }
    World world = watch.anchor().getWorld();
    for (NodeIndex.Entry node : watch.nodes()) {
      NodeRecord record = node.record();
      int[] center = NodeArea.center(node.key());
      Location middle = new Location(world, center[0] + BLOCK_HALF, center[1] + BLOCK_HALF, center[2] + BLOCK_HALF);
      for (Mob mob : PaperCompat.nearbyEntitiesByType(Mob.class, middle, record.radiusX() + BLOCK_HALF, record.radiusY() + BLOCK_HALF,
          record.radiusZ() + BLOCK_HALF)) {
        if (seen.add(mob.getUniqueId())) {
          found.add(mob);
        }
      }
    }
    return found;
  }

  private GuardianRules.Reason reason(Watch watch, LivingEntity candidate) {
    MinionTargeting.Candidate captured = MinionTargeting.captureOwned(watch.ownerId(), candidate);
    LivingEntity attacker = watch.attacker();
    GuardianRules.Candidate facts = new GuardianRules.Candidate(
        captured == null || captured.protectedFriendly(),
        candidate instanceof Player,
        candidate instanceof Enemy,
        candidate instanceof Creeper,
        candidate.customName() != null,
        targetOf(candidate, watch.ownerId()) || targetOf(candidate, watch.body().getUniqueId()),
        attacker != null && attacker.getUniqueId().equals(candidate.getUniqueId()));
    return GuardianRules.reason(facts, watch.options());
  }

  private boolean engageable(Watch watch, LivingEntity candidate) {
    return watch.owner() == null || core.canEngage(watch.owner(), candidate);
  }

  private void markEngaged(Watch watch, Location at) {
    NodeKey node = nodeAt(watch, at);
    if (node != null) {
      guard.engaged(node, watch.now());
    }
  }

  private static boolean covers(Watch watch, Location at) {
    if (!Objects.equals(at.getWorld(), watch.anchor().getWorld())) {
      return false;
    }
    double perception = watch.perception();
    return at.distanceSquared(watch.anchor()) <= perception * perception || nodeAt(watch, at) != null;
  }

  private static boolean inRange(Watch watch, Location at) {
    double sentry = watch.sentry();
    return sentry <= 0D || at.distanceSquared(watch.here()) <= sentry * sentry;
  }

  private static NodeKey nodeAt(Watch watch, Location at) {
    for (NodeIndex.Entry node : watch.nodes()) {
      if (GuardNodeBehavior.covers(node.key(), node.record(), at.getX(), at.getY(), at.getZ())) {
        return node.key();
      }
    }
    return null;
  }

  private static boolean targetOf(LivingEntity candidate, UUID target) {
    if (!(candidate instanceof Mob mob)) {
      return false;
    }
    LivingEntity current = mob.getTarget();
    return current != null && current.getUniqueId().equals(target);
  }

  private static Location ownerAnchor(Player online, World world, Location here) {
    return online != null && J.isOwnedByCurrentRegion(online) && world.equals(online.getWorld()) ? online.getLocation() : here;
  }

  private static Location home(GolemState.Anchor anchor, World world) {
    if (anchor == null || !world.getUID().equals(anchor.world())) {
      return null;
    }
    return new Location(world, anchor.x() + BLOCK_HALF, anchor.y(), anchor.z() + BLOCK_HALF);
  }

  private record Watch(Mob body, Location here, UUID ownerId, Player owner, Location anchor, double perception, double sentry,
                       List<NodeIndex.Entry> nodes, LivingEntity attacker, GuardianRules.Options options, long now) {
  }
}
