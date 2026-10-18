package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class SorterNetwork {
  static final long SERVED_MILLIS = 10_000L;
  static final long ACCESS_MILLIS = 5_000L;
  private static final double CENTER = 0.5D;
  private static final double NODE_SLACK = 2D;

  private final GolemSorterCore core;
  private final SorterSpace space;
  private final SorterIndex index;
  private final NodeTagLookup tags;
  private final Function<ItemStack, String> variants;
  private final LongSupplier scanMillis;
  private final Map<ServedKey, Served> served = new ConcurrentHashMap<>();
  private final Map<AccessKey, Access> access = new ConcurrentHashMap<>();

  SorterNetwork(GolemSorterCore core, SorterSpace space, SorterIndex index, NodeTagLookup tags, Function<ItemStack, String> variants,
                LongSupplier scanMillis) {
    this.core = Objects.requireNonNull(core);
    this.space = Objects.requireNonNull(space);
    this.index = Objects.requireNonNull(index);
    this.tags = Objects.requireNonNull(tags);
    this.variants = Objects.requireNonNull(variants);
    this.scanMillis = Objects.requireNonNull(scanMillis);
  }

  Optional<SorterSurvey> survey(SorterAccess viewer, World world, NodeKey input, NodeRecord record, int level, long nowMillis) {
    GolemSorterCore.Config config = core.config();
    Player owner = viewer.owner();
    Block inputBlock = world.getBlockAt(input.x(), input.y(), input.z());
    Optional<Inventory> inputInventory = container(viewer, owner, inputBlock, nowMillis);
    if (inputInventory.isEmpty()) {
      return Optional.empty();
    }
    ContainerId inputId = ContainerId.of(inputBlock, inputInventory.get());
    SorterDestination inputDestination = new SorterDestination(inputId, inputBlock, SorterStock.read(inputInventory.get(), nowMillis),
        SorterDestination.Kind.INPUT, null, 0D, SorterRules.shulker(inputBlock.getType()));
    int radius = SorterRules.radius(record, SorterRules.destinationRadius(config.destinationRadiusByLevel, level));
    Set<ContainerId> excluded = new HashSet<>();
    excluded.add(inputId);
    Map<ContainerId, NodeRecord> filters = new HashMap<>();
    classifyNodes(viewer, world, input, record, level, radius, excluded, filters);
    long maxAge = SorterRules.unlocked(level, config.indexLevel) ? config.indexRefreshSeconds * 1_000L : Math.max(1L, scanMillis.getAsLong());
    int limit = Math.max(1, config.maxServedContainers);
    List<SorterDestination> destinations = new ArrayList<>();
    for (Block block : candidates(world, input, radius, nowMillis)) {
      if (destinations.size() >= limit) {
        break;
      }
      Optional<Inventory> inventory = container(viewer, owner, block, nowMillis);
      if (inventory.isEmpty()) {
        continue;
      }
      ContainerId id = ContainerId.of(block, inventory.get());
      if (!excluded.add(id)) {
        continue;
      }
      NodeRecord filter = filters.get(id);
      SorterDestination.Kind kind = filter == null ? SorterDestination.Kind.PLAIN
          : SorterRules.overflow(filter) ? SorterDestination.Kind.OVERFLOW : SorterDestination.Kind.FILTER;
      double distance = id.distanceSquared(input.x() + CENTER, input.y() + CENTER, input.z() + CENTER);
      destinations.add(new SorterDestination(id, block, stock(id, inventory.get(), nowMillis, maxAge), kind, filter, distance,
          SorterRules.shulker(block.getType())));
    }
    SorterRules.Match mode = SorterRules.match(record, config, level);
    Map<Material, String> groups = mode == SorterRules.Match.EXACT ? Map.of() : core.groups();
    return Optional.of(new SorterSurvey(new SorterMatcher(mode, groups, tags, variants), config, level, inputDestination, destinations));
  }

  void sweep(long nowMillis) {
    served.values().removeIf(entry -> nowMillis - entry.at() >= SERVED_MILLIS);
    access.values().removeIf(entry -> nowMillis - entry.at() >= ACCESS_MILLIS);
  }

  boolean isEmpty() {
    return served.isEmpty() && access.isEmpty();
  }

  void clear() {
    served.clear();
    access.clear();
  }

  private List<Block> candidates(World world, NodeKey input, int radius, long nowMillis) {
    ServedKey key = new ServedKey(input, radius);
    Served cached = served.get(key);
    if (cached != null && nowMillis - cached.at() < SERVED_MILLIS) {
      return cached.blocks();
    }
    List<Block> blocks = new ArrayList<>(space.containers(world, input.x(), input.y(), input.z(), radius));
    double x = input.x() + CENTER;
    double y = input.y() + CENTER;
    double z = input.z() + CENTER;
    blocks.sort(Comparator.comparingDouble(block -> distanceSquared(block, x, y, z)));
    List<Block> sorted = List.copyOf(blocks);
    served.put(key, new Served(sorted, nowMillis));
    return sorted;
  }

  private Optional<Inventory> container(SorterAccess viewer, Player owner, Block block, long nowMillis) {
    if (owner == null || !viewer.owns(block) || !permits(owner, block, nowMillis)) {
      return Optional.empty();
    }
    return NodeContainers.inventory(block);
  }

  private boolean permits(Player owner, Block block, long nowMillis) {
    AccessKey key = new AccessKey(owner.getUniqueId(), block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    Access known = access.get(key);
    if (known != null && nowMillis - known.at() < ACCESS_MILLIS) {
      return known.allowed();
    }
    boolean allowed = core.canAccessChest(owner, block.getLocation());
    access.put(key, new Access(allowed, nowMillis));
    return allowed;
  }

  private void classifyNodes(SorterAccess viewer, World world, NodeKey input, NodeRecord record, int level, int radius,
                             Set<ContainerId> excluded, Map<ContainerId, NodeRecord> filters) {
    boolean filtering = SorterRules.unlocked(level, core.config().filterLevel);
    List<NodeIndex.Entry> nearby = space.nodes(input.world(), input.x() + CENTER, input.y() + CENTER, input.z() + CENTER, radius + NODE_SLACK);
    for (NodeIndex.Entry entry : nearby) {
      NodeRecord node = entry.record();
      if (node.type() != NodeType.INPUT && node.type() != NodeType.FILTER) {
        continue;
      }
      NodeKey key = entry.key();
      Block anchor = world.getBlockAt(key.x(), key.y(), key.z());
      if (!viewer.owns(anchor)) {
        continue;
      }
      Optional<Inventory> inventory = NodeContainers.inventory(anchor);
      if (inventory.isEmpty()) {
        continue;
      }
      ContainerId id = ContainerId.of(anchor, inventory.get());
      if (node.type() == NodeType.FILTER && filtering && serves(node, record) && NodeRules.active(node, anchor)) {
        filters.putIfAbsent(id, node);
      } else {
        excluded.add(id);
      }
    }
    for (Map.Entry<ContainerId, NodeRecord> filter : List.copyOf(filters.entrySet())) {
      if (excluded.contains(filter.getKey())) {
        filters.remove(filter.getKey());
      }
    }
  }

  private SorterStock stock(ContainerId id, Inventory inventory, long nowMillis, long maxAgeMillis) {
    Optional<SorterStock> cached = index.fresh(id, nowMillis, maxAgeMillis);
    if (cached.isPresent()) {
      return cached.get();
    }
    SorterStock read = SorterStock.read(inventory, nowMillis);
    index.put(id, read);
    return read;
  }

  private static boolean serves(NodeRecord filter, NodeRecord input) {
    return filter.owner().equals(input.owner()) && (filter.channel() == null || filter.channel() == input.channel());
  }

  private static double distanceSquared(Block block, double x, double y, double z) {
    double dx = block.getX() + CENTER - x;
    double dy = block.getY() + CENTER - y;
    double dz = block.getZ() + CENTER - z;
    return dx * dx + dy * dy + dz * dz;
  }

  private record ServedKey(NodeKey input, int radius) {
  }

  private record Served(List<Block> blocks, long at) {
  }

  private record AccessKey(UUID owner, UUID world, int x, int y, int z) {
  }

  private record Access(boolean allowed, long at) {
  }
}
