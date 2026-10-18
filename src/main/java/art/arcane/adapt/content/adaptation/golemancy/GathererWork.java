package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreBehavior;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskMatch;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

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

  private final GolemGathererCore core;
  private final GatherDeathSites deathSites;
  private final Map<UUID, CachedArea> areas = new ConcurrentHashMap<>();

  GathererWork(GolemGathererCore core, GatherDeathSites deathSites) {
    this.core = Objects.requireNonNull(core);
    this.deathSites = Objects.requireNonNull(deathSites);
  }

  static boolean collectable(ItemStack stack) {
    return stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0;
  }

  @Override
  public Adaptation<?> policy() {
    return core;
  }

  @Override
  public Optional<GolemTask> seek(GolemContext golem) {
    World world = golem.world();
    if (!ownerIn(golem, world)) {
      return Optional.empty();
    }
    GolemHandle handle = golem.handle();
    int level = core.ownerLevel(handle.owner());
    GatherArea area = area(golem, level);
    long now = golem.nowMillis();
    Location here = golem.golem().getLocation();
    List<Candidate> found = new ArrayList<>();
    if (!area.byNodes() && golem.room() > 0) {
      Location center = new Location(world, area.x(), area.y(), area.z());
      for (Item item : PaperCompat.nearbyEntitiesByType(Item.class, center, area.radius(), area.radius(), area.radius())) {
        Location at = item.getLocation();
        if (area.contains(at) && golem.inWorkRadius(at) && collectable(item.getItemStack()) && eligible(item, handle.owner(), now)) {
          found.add(new Candidate(GolemTask.builder(PICKUP, GolemTarget.entity(item)).build(), at.distanceSquared(here)));
        }
      }
    }
    if (GathererRules.essenceRoom(handle.state().essencePoints(), essenceCap(level)) > 0) {
      for (ExperienceOrb orb : orbs(world, area)) {
        Location at = orb.getLocation();
        if (area.contains(at) && golem.inWorkRadius(at) && absorbable(orb, now)) {
          found.add(new Candidate(GolemTask.builder(ABSORB, GolemTarget.entity(orb)).build(), at.distanceSquared(here)));
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
    Entity target = golem.targetEntity().orElse(null);
    int level = core.ownerLevel(golem.handle().owner());
    if (PICKUP.equals(task.type()) && target instanceof Item item) {
      return pickUp(golem, item, area(golem, level));
    }
    if (ABSORB.equals(task.type()) && target instanceof ExperienceOrb orb) {
      return absorb(golem, orb, area(golem, level), level);
    }
    return GolemActResult.FAILED;
  }

  @Override
  public void tick(GolemContext golem) {
    GolemHandle handle = golem.handle();
    int level = core.ownerLevel(handle.owner());
    GolemGathererCore.Config config = core.config();
    double magnet = GathererRules.unlocked(level, config.magnetLevel) ? config.magnetRadius : 0D;
    double instant = GathererRules.unlocked(level, config.instantPickupLevel) ? config.instantPickupRadius : 0D;
    double reach = Math.max(magnet, instant);
    World world = golem.world();
    boolean absorbing = GathererRules.essenceRoom(handle.state().essencePoints(), essenceCap(level)) > 0;
    if (reach <= 0D || (golem.room() <= 0 && !absorbing) || !ownerIn(golem, world)) {
      return;
    }
    GatherArea area = area(golem, level);
    Location here = golem.golem().getLocation();
    long now = golem.nowMillis();
    int picked = 0;
    int pulled = 0;
    for (Item item : PaperCompat.nearbyEntitiesByType(Item.class, here, reach, reach, reach)) {
      Location at = item.getLocation();
      double distance = at.distanceSquared(here);
      if (distance > reach * reach || !area.contains(at) || !golem.inWorkRadius(at) || !collectable(item.getItemStack())
          || !eligible(item, handle.owner(), now)) {
        continue;
      }
      boolean room = golem.room() > 0;
      if (room && distance <= instant * instant && picked < PICKUPS_PER_TICK) {
        if (pickUp(golem, item, area) == GolemActResult.DONE) {
          picked++;
        }
        continue;
      }
      if (room && distance <= magnet * magnet && pulled < PULLS_PER_TICK && golem.canInteract(at)) {
        pull(item, at, here);
        pulled++;
      }
    }
    if (instant > 0D && absorbing) {
      for (ExperienceOrb orb : PaperCompat.nearbyEntitiesByType(ExperienceOrb.class, here, instant, instant, instant)) {
        if (orb.getLocation().distanceSquared(here) <= instant * instant) {
          absorb(golem, orb, area, level);
        }
      }
    }
  }

  GolemActResult pickUp(GolemContext golem, Item item, GatherArea area) {
    ItemStack stack = item.getItemStack();
    Location at = item.getLocation();
    if (!collectable(stack) || !area.contains(at) || !golem.inWorkRadius(at) || !eligible(item, golem.handle().owner(), golem.nowMillis())) {
      return GolemActResult.FAILED;
    }
    int amount = stack.getAmount();
    int taking = Math.min(golem.room(), amount);
    if (taking <= 0) {
      return GolemActResult.FAILED;
    }
    Player owner = golem.owner();
    if (owner == null || !golem.canInteract(at) || !ProtectionEventProbe.attemptItemPickup(owner, item, amount - taking)) {
      return GolemActResult.BLOCKED;
    }
    ItemStack current = item.getItemStack();
    if (!item.isValid() || item.isDead() || current == null || !current.isSimilar(stack) || current.getAmount() != amount) {
      return GolemActResult.FAILED;
    }
    ItemStack taken = current.clone();
    taken.setAmount(taking);
    int carried = taking - golem.carry(taken).map(ItemStack::getAmount).orElse(0);
    if (carried <= 0) {
      return GolemActResult.FAILED;
    }
    if (carried >= amount) {
      item.remove();
    } else {
      ItemStack rest = current.clone();
      rest.setAmount(amount - carried);
      item.setItemStack(rest);
    }
    golem.workXp(core.config().workXpPerStack);
    return GolemActResult.DONE;
  }

  boolean eligible(Item item, UUID owner, long nowMillis) {
    if (!item.isValid() || item.isDead() || GathererRules.waiting(item.getPickupDelay()) || !PaperCompat.canPlayerPickup(item)) {
      return false;
    }
    for (String marker : FOREIGN_MARKERS) {
      if (item.hasMetadata(marker)) {
        return false;
      }
    }
    GolemGathererCore.Config config = core.config();
    if (GathererRules.foreign(item.getThrower(), item.getOwner(), owner, item.getTicksLived(), config.foreignItemSeconds * GathererRules.TICKS_PER_SECOND)) {
      return false;
    }
    return !nearDeath(item.getLocation(), nowMillis);
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

  private GolemActResult absorb(GolemContext golem, ExperienceOrb orb, GatherArea area, int level) {
    GolemHandle handle = golem.handle();
    int essence = handle.state().essencePoints();
    int room = GathererRules.essenceRoom(essence, essenceCap(level));
    Location at = orb.getLocation();
    if (room <= 0 || !area.contains(at) || !golem.inWorkRadius(at) || !absorbable(orb, golem.nowMillis())) {
      return GolemActResult.FAILED;
    }
    if (!golem.canInteract(at)) {
      return GolemActResult.BLOCKED;
    }
    int total = GathererRules.orbTotal(orb.getExperience(), PaperCompat.experienceOrbCount(orb));
    int taking = GathererRules.absorbed(total, room);
    if (taking <= 0) {
      return GolemActResult.FAILED;
    }
    handle.setEssence((double) essence + taking);
    if (taking >= total) {
      orb.remove();
    } else {
      PaperCompat.setExperienceOrbTotal(orb, total - taking);
    }
    return GolemActResult.DONE;
  }

  private boolean absorbable(ExperienceOrb orb, long nowMillis) {
    return orb.isValid() && !orb.isDead() && !nearDeath(orb.getLocation(), nowMillis);
  }

  private boolean nearDeath(Location at, long nowMillis) {
    World world = at.getWorld();
    return world != null && deathSites.protects(world.getUID(), at.getX(), at.getY(), at.getZ(), core.config().deathSiteRadius, nowMillis);
  }

  private int essenceCap(int level) {
    return GathererRules.essenceCap(core.config().essenceCapByLevel, level);
  }

  private GatherArea area(GolemContext golem, int level) {
    GolemHandle handle = golem.handle();
    GolemState.Anchor home = handle.state().home();
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
    Location home = golem.home();
    World world = home.getWorld();
    List<GatherArea.Box> boxes = collectionNodes(handle.owner(), world, home, golem.workRadius(), channels);
    if (!boxes.isEmpty()) {
      return GatherArea.nodes(world.getUID(), boxes);
    }
    double radius = GathererRules.pickupRadius(core.config().pickupRadiusByLevel, level) + GolemRunes.effects(handle).radiusBonus();
    return GatherArea.home(home, radius);
  }

  private static List<GatherArea.Box> collectionNodes(UUID owner, World world, Location home, double radius, int channels) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      return List.of();
    }
    List<NodeIndex.Entry> near = service.get().index().near(world.getUID(), home.getX(), home.getY(), home.getZ(), radius);
    List<GatherArea.Box> boxes = new ArrayList<>(near.size());
    for (NodeIndex.Entry entry : near) {
      NodeRecord record = entry.record();
      if (record.type() == NodeType.COLLECTION && record.owner().equals(owner) && GolemTaskMatch.channel(record.channel(), channels)
          && active(world, entry.key(), record)) {
        boxes.add(GatherArea.Box.of(entry.key(), record));
      }
    }
    return boxes;
  }

  private static boolean active(World world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !world.isChunkLoaded(key.chunkX(), key.chunkZ())
        || !J.isOwnedByCurrentRegion(new Location(world, key.x(), key.y(), key.z()))) {
      return true;
    }
    return NodeRules.active(record, world.getBlockAt(key.x(), key.y(), key.z()));
  }

  private static List<ExperienceOrb> orbs(World world, GatherArea area) {
    if (!area.byNodes()) {
      Location center = new Location(world, area.x(), area.y(), area.z());
      return PaperCompat.nearbyEntitiesByType(ExperienceOrb.class, center, area.radius(), area.radius(), area.radius());
    }
    Map<UUID, ExperienceOrb> found = new LinkedHashMap<>();
    for (GatherArea.Box box : area.nodes()) {
      Location center = new Location(world, box.centerX(), box.centerY(), box.centerZ());
      for (ExperienceOrb orb : PaperCompat.nearbyEntitiesByType(ExperienceOrb.class, center, box.halfX(), box.halfY(), box.halfZ())) {
        found.putIfAbsent(orb.getUniqueId(), orb);
      }
    }
    return List.copyOf(found.values());
  }

  private static boolean ownerIn(GolemContext golem, World world) {
    Player owner = golem.owner();
    World ownerWorld = owner == null ? null : owner.getWorld();
    return ownerWorld != null && world != null && ownerWorld.getUID().equals(world.getUID());
  }

  private static void pull(Item item, Location at, Location here) {
    Vector toward = here.toVector().add(new Vector(0D, PULL_HEIGHT, 0D)).subtract(at.toVector());
    double distance = toward.length();
    if (distance < PULL_MIN_DISTANCE) {
      return;
    }
    item.setVelocity(toward.multiply(PULL_SPEED / distance));
  }

  private record Candidate(GolemTask task, double distanceSquared) {
  }

  private record CachedArea(GolemState.Anchor home, int channels, int level, GatherArea area, long until) {
  }
}
