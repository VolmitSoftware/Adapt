package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreBehavior;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskMatch;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ExperienceOrbRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class GathererWork implements GolemCoreBehavior {
  static final String PICKUP = "pickup";
  static final String ABSORB = "absorb";
  static final long AREA_CACHE_MILLIS = 1_500L;
  static final int PICKUPS_PER_TICK = 4;
  static final int PULLS_PER_TICK = 8;
  static final double PULL_SPEED = 0.35D;

  private static final double PULL_HEIGHT = 0.25D;
  private static final double PULL_MIN_DISTANCE = 0.25D;
  private static final List<String> FOREIGN_MARKERS = List.of("NPC", "shopitem", "hologram");
  private static final Id BUNDLE = Id.minecraft("bundle");

  private final GolemGathererCore core;
  private final GatherDeathSites deathSites;
  private final Map<UUID, CachedArea> areas = new ConcurrentHashMap<>();

  GathererWork(GolemGathererCore core, GatherDeathSites deathSites) {
    this.core = Objects.requireNonNull(core);
    this.deathSites = Objects.requireNonNull(deathSites);
  }

  static boolean collectable(ItemRef stack) {
    return stack != null && !stack.isEmpty() && !stack.type().isAir() && stack.amount() > 0 && !ReactSuperStacks.hollow(stack);
  }

  static boolean marked(EntityRef entity) {
    EntityMarkerPort markers = AdaptPorts.get(EntityMarkerPort.class, EntityMarkerPort.NONE);
    for (String marker : FOREIGN_MARKERS) {
      if (markers.marked(entity, marker)) {
        return true;
      }
    }
    return false;
  }

  static <T extends EntityRef> List<T> nearby(Class<T> type, Loc center, double x, double y, double z) {
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, x, z)) {
      return new ArrayList<>();
    }
    List<T> found = new ArrayList<>();
    for (EntityRef entity : center.world().entitiesIn(Box.around(center.position(), x, y, z), type::isInstance)) {
      found.add(type.cast(entity));
    }
    return found;
  }

  @Override
  public Adaptation<?> policy() {
    return core;
  }

  @Override
  public Optional<GolemTask> seek(GolemContext golem) {
    WorldRef world = golem.world();
    GolemHandle handle = golem.handle();
    int level = core.ownerLevel(handle.owner());
    GatherArea area = area(golem, level);
    long now = golem.nowMillis();
    Loc here = golem.golem().location();
    List<Candidate> found = new ArrayList<>();
    if (!area.byNodes() && golem.room() > 0) {
      Loc center = new Loc(world, area.x(), area.y(), area.z());
      for (ItemEntityRef item : nearby(ItemEntityRef.class, center, area.radius(), area.radius(), area.radius())) {
        Loc at = item.location();
        if (area.contains(at) && golem.inWorkRadius(at) && collectable(item.item()) && eligible(item, handle.owner(), now)) {
          found.add(new Candidate(pickupTask(item), at.distanceSquared(here)));
        }
      }
    }
    if (absorbRoom(handle, handle.state().essencePoints(), level) > 0) {
      for (ExperienceOrbRef orb : orbs(world, area)) {
        Loc at = orb.location();
        if (area.contains(at) && golem.inWorkRadius(at) && absorbable(orb, now)) {
          found.add(new Candidate(absorbTask(orb), at.distanceSquared(here)));
        }
      }
    }
    found.sort(Comparator.comparingDouble(Candidate::distanceSquared));
    for (Candidate candidate : found) {
      if (!golem.skips(candidate.task())) {
        return Optional.of(candidate.task());
      }
    }
    return Optional.empty();
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    EntityRef target = golem.targetEntity().orElse(null);
    int level = core.ownerLevel(golem.handle().owner());
    if (PICKUP.equals(task.type()) && target instanceof ItemEntityRef item) {
      return pickUp(golem, item, area(golem, level));
    }
    if (ABSORB.equals(task.type()) && target instanceof ExperienceOrbRef orb) {
      return absorb(golem, orb, area(golem, level), level);
    }
    return GolemActResult.FAILED;
  }

  @Override
  public void tick(GolemContext golem) {
    GolemHandle handle = golem.handle();
    int level = core.ownerLevel(handle.owner());
    GolemGathererCore.Config config = core.config();
    double magnet = GathererRules.unlocked(level, config.magnetLevel) && core.magnets(handle.owner()) ? config.magnetRadius : 0D;
    double instant = GathererRules.unlocked(level, config.instantPickupLevel) ? config.instantPickupRadius : 0D;
    double reach = Math.max(magnet, instant);
    boolean absorbing = instant > 0D && absorbRoom(handle, handle.state().essencePoints(), level) > 0;
    boolean carrying = golem.room() > 0;
    if (reach <= 0D || (!carrying && !absorbing)) {
      return;
    }
    GatherArea area = area(golem, level);
    Loc here = golem.golem().location();
    long now = golem.nowMillis();
    boolean pulling = carrying && magnet > 0D && area.contains(here) && claimAllows(golem, here);
    int attempts = 0;
    int pulled = 0;
    List<ItemEntityRef> items = carrying ? nearby(ItemEntityRef.class, here, reach, reach, reach) : List.of();
    for (ItemEntityRef item : items) {
      Loc at = item.location();
      double distance = at.distanceSquared(here);
      ItemRef stack = item.item();
      if (distance > reach * reach || !collectable(stack) || !collects(area, at, stack) || !golem.inWorkRadius(at)
          || !eligible(item, handle.owner(), now)) {
        continue;
      }
      boolean room = golem.room() > 0;
      if (room && distance <= instant * instant && attempts < PICKUPS_PER_TICK) {
        if (!golem.skips(pickupTask(item))) {
          attempts++;
          pickUp(golem, item, area);
        }
        continue;
      }
      if (room && pulling && distance <= magnet * magnet && pulled < PULLS_PER_TICK && claimAllows(golem, at)) {
        pull(item, at, here);
        pulled++;
      }
    }
    if (absorbing) {
      for (ExperienceOrbRef orb : nearby(ExperienceOrbRef.class, here, instant, instant, instant)) {
        if (orb.location().distanceSquared(here) <= instant * instant && !golem.skips(absorbTask(orb))) {
          absorb(golem, orb, area, level);
        }
      }
    }
  }

  GolemActResult pickUp(GolemContext golem, ItemEntityRef item, GatherArea area) {
    ItemRef stack = item.item();
    Loc at = item.location();
    if (!collectable(stack) || !collects(area, at, stack) || !golem.inWorkRadius(at)
        || !eligible(item, golem.handle().owner(), golem.nowMillis())) {
      return GolemActResult.FAILED;
    }
    if (ReactSuperStacks.isSuperStack(stack)) {
      return unpack(golem, item, stack, area, at);
    }
    int amount = stack.amount();
    int taking = Math.min(golem.room(), amount);
    if (taking <= 0) {
      return GolemActResult.FAILED;
    }
    if (!claimAllows(golem, at) || !ProtectionProbes.attemptEntityItemPickup(golem.golem(), item, amount - taking)) {
      return GolemActResult.BLOCKED;
    }
    ItemRef current = item.item();
    if (!item.isValid() || item.isDead() || current == null || current.isEmpty() || !current.isSimilar(stack) || current.amount() != amount) {
      return GolemActResult.FAILED;
    }
    ItemRef taken = current.copyWithAmount(taking);
    int carried = taking - golem.carry(taken).map(ItemRef::amount).orElse(0);
    if (carried <= 0) {
      return GolemActResult.FAILED;
    }
    if (carried >= amount) {
      item.remove();
    } else {
      item.setItem(current.copyWithAmount(amount - carried));
    }
    golem.workXp(core.config().workXpPerStack);
    return GolemActResult.DONE;
  }

  boolean eligible(ItemEntityRef item, UUID owner, long nowMillis) {
    if (!item.isValid() || item.isDead() || GathererRules.waiting(item.pickupDelay()) || !item.canPlayerPickup()
        || !item.canMobPickup() || marked(item)) {
      return false;
    }
    GolemGathererCore.Config config = core.config();
    if (GathererRules.foreign(item.thrower(), item.owner(), owner, item.ticksLived(), config.foreignItemSeconds * GathererRules.TICKS_PER_SECOND)) {
      return false;
    }
    return !nearDeath(item.location(), nowMillis);
  }

  void sweep(long nowMillis) {
    areas.values().removeIf(cached -> nowMillis >= cached.until());
  }

  boolean pending() {
    return !areas.isEmpty();
  }

  void forget() {
    areas.clear();
  }

  private GolemActResult unpack(GolemContext golem, ItemEntityRef item, ItemRef bundle, GatherArea area, Loc at) {
    if (golem.room() <= 0) {
      return GolemActResult.FAILED;
    }
    if (!claimAllows(golem, at) || !ProtectionProbes.attemptEntityItemPickup(golem.golem(), item, 0)) {
      return GolemActResult.BLOCKED;
    }
    ItemRef current = item.item();
    if (!item.isValid() || item.isDead() || !ReactSuperStacks.isSuperStack(current) || !current.isSimilar(bundle)) {
      return GolemActResult.FAILED;
    }
    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem, current, type -> area.collects(at, type));
    if (unpacked.carried() <= 0) {
      return GolemActResult.FAILED;
    }
    settle(item, current, unpacked.leftovers());
    golem.workXp(core.config().workXpPerStack);
    return GolemActResult.DONE;
  }

  private GolemActResult absorb(GolemContext golem, ExperienceOrbRef orb, GatherArea area, int level) {
    GolemHandle handle = golem.handle();
    int essence = handle.state().essencePoints();
    int room = absorbRoom(handle, essence, level);
    Loc at = orb.location();
    if (room <= 0 || !area.contains(at) || !golem.inWorkRadius(at) || !absorbable(orb, golem.nowMillis())) {
      return GolemActResult.FAILED;
    }
    if (!claimAllows(golem, at)) {
      return GolemActResult.BLOCKED;
    }
    int total = GathererRules.orbTotal(orb.experience(), orb.count());
    int taking = GathererRules.absorbed(total, room);
    if (taking <= 0) {
      return GolemActResult.FAILED;
    }
    handle.setEssence((double) essence + taking);
    if (taking >= total) {
      orb.remove();
    } else {
      orb.setCount(1);
      orb.setExperience(total - taking);
    }
    return GolemActResult.DONE;
  }

  private boolean absorbable(ExperienceOrbRef orb, long nowMillis) {
    return orb.isValid() && !orb.isDead() && !nearDeath(orb.location(), nowMillis);
  }

  private boolean nearDeath(Loc at, long nowMillis) {
    return deathSites.protects(at.world().uid(), at.x(), at.y(), at.z(), core.config().deathSiteRadius, nowMillis);
  }

  private int essenceCap(int level) {
    return GathererRules.essenceCap(core.config().essenceCapByLevel, level);
  }

  private int absorbRoom(GolemHandle handle, int essence, int level) {
    int room = GathererRules.essenceRoom(essence, essenceCap(level));
    return room > 0 && core.absorbsOrbs(handle.owner()) ? room : 0;
  }

  private GatherArea area(GolemContext golem, int level) {
    GolemHandle handle = golem.handle();
    GolemAnchor home = handle.state().home();
    int channels = GolemRunes.channels(handle);
    long now = golem.nowMillis();
    CachedArea cached = areas.get(handle.id());
    if (cached != null && now < cached.until() && cached.level() == level && cached.channels() == channels && Objects.equals(cached.home(), home)) {
      return cached.area();
    }
    GatherArea area = lookup(golem, handle, level, channels);
    areas.put(handle.id(), new CachedArea(home, channels, level, area, now + AREA_CACHE_MILLIS));
    return area;
  }

  private GatherArea lookup(GolemContext golem, GolemHandle handle, int level, int channels) {
    Loc home = golem.home();
    return collectionNodes(handle.owner(), home, golem.workRadius(), channels).orElseGet(() ->
        GatherArea.home(home, GathererRules.pickupRadius(core.config().pickupRadiusByLevel, level) + GolemRunes.effects(handle).radiusBonus()));
  }

  private static Optional<GatherArea> collectionNodes(UUID owner, Loc home, double radius, int channels) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      return Optional.empty();
    }
    WorldRef world = home.world();
    List<NodeIndex.Entry> near = service.get().index().near(world.uid(), home.x(), home.y(), home.z(), radius);
    List<GatherArea.Box> boxes = new ArrayList<>(near.size());
    boolean matched = false;
    for (NodeIndex.Entry entry : near) {
      NodeRecord record = entry.record();
      if (record.type() != NodeType.COLLECTION || !record.owner().equals(owner) || !GolemTaskMatch.channel(record.channel(), channels)) {
        continue;
      }
      matched = true;
      if (GolemancySupport.active(world, entry.key(), record)) {
        boxes.add(GatherArea.Box.of(entry.key(), record));
      }
    }
    return matched ? Optional.of(GatherArea.nodes(world.uid(), boxes)) : Optional.empty();
  }

  private static List<ExperienceOrbRef> orbs(WorldRef world, GatherArea area) {
    if (!area.byNodes()) {
      Loc center = new Loc(world, area.x(), area.y(), area.z());
      return nearby(ExperienceOrbRef.class, center, area.radius(), area.radius(), area.radius());
    }
    Map<UUID, ExperienceOrbRef> found = new LinkedHashMap<>();
    for (GatherArea.Box box : area.nodes()) {
      Loc center = new Loc(world, box.centerX(), box.centerY(), box.centerZ());
      for (ExperienceOrbRef orb : nearby(ExperienceOrbRef.class, center, box.halfX(), box.halfY(), box.halfZ())) {
        found.putIfAbsent(orb.uuid(), orb);
      }
    }
    return List.copyOf(found.values());
  }

  private static boolean collects(GatherArea area, Loc at, ItemRef stack) {
    if (!BUNDLE.equals(stack.type())) {
      return area.collects(at, stack.type());
    }
    return ReactSuperStacks.accepts(stack, type -> area.collects(at, type));
  }

  private static void settle(ItemEntityRef item, ItemRef bundle, List<ItemRef> leftovers) {
    if (leftovers.isEmpty()) {
      item.remove();
      return;
    }
    Optional<ItemRef> rebuilt = ReactSuperStacks.rebuild(bundle, leftovers);
    if (rebuilt.isPresent()) {
      item.setItem(rebuilt.get());
      return;
    }
    WorldRef world = item.world();
    Loc at = item.location();
    item.remove();
    for (ItemRef leftover : leftovers) {
      world.dropItemNaturally(at.position(), leftover);
    }
  }

  private static boolean claimAllows(GolemContext golem, Loc at) {
    Loc block = new Loc(at.world(), at.blockX(), at.blockY(), at.blockZ());
    return golem.canInteract(block) && golem.canBreak(block);
  }

  private static GolemTask pickupTask(ItemEntityRef item) {
    return GolemTask.builder(PICKUP, GolemTarget.entity(item)).build();
  }

  private static GolemTask absorbTask(ExperienceOrbRef orb) {
    return GolemTask.builder(ABSORB, GolemTarget.entity(orb)).build();
  }

  private static void pull(ItemEntityRef item, Loc at, Loc here) {
    Vec3 toward = here.position().add(0D, PULL_HEIGHT, 0D).subtract(at.position());
    double distance = toward.length();
    if (distance < PULL_MIN_DISTANCE) {
      return;
    }
    item.setVelocity(toward.multiply(PULL_SPEED / distance));
  }

  private record Candidate(GolemTask task, double distanceSquared) {
  }

  private record CachedArea(GolemAnchor home, int channels, int level, GatherArea area, long until) {
  }
}
