package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskMatch;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GolemancySupport {
  private GolemancySupport() {
  }

  static void registerRecipe(SimpleAdaptation<?> adaptation, ConfiguredRecipeParser parser, String name, ConfiguredShapedRecipe spec, int level) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, name);
    parser.builder(key, spec).ifPresent(builder -> adaptation.registerRecipe(builder.requiredLevel(level).build()));
    if (spec.isEnabled()) {
      GolemItems.craftCounter().track(key);
    }
  }

  static int ownerLevel(SimpleAdaptation<?> core, GolemContext golem) {
    Player owner = golem.owner();
    return owner == null ? 0 : core.ownerLevel(owner.getUniqueId());
  }

  static void recordOwnerLevel(String adaptation, UUID owner, int level) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return;
    }
    for (GolemHandle handle : registry.get().loadedOf(owner)) {
      J.runEntity(handle.entity(), () -> {
        if (!handle.isRetired()) {
          handle.recordOwnerLevel(adaptation, level);
        }
      });
    }
  }

  static Optional<Inventory> homeChest(GolemContext golem) {
    GolemState.Anchor chest = golem.handle().state().homeChest();
    World world = golem.world();
    if (chest == null || !chest.world().equals(world.getUID()) || !world.isChunkLoaded(chest.x() >> 4, chest.z() >> 4)) {
      return Optional.empty();
    }
    return golem.container(world.getBlockAt(chest.x(), chest.y(), chest.z()));
  }

  static List<NodeIndex.Entry> ownedNodes(GolemContext golem, World world, NodeType type) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      return new ArrayList<>();
    }
    Location home = golem.home();
    List<NodeIndex.Entry> near = service.get().index().near(world.getUID(), home.getX(), home.getY(), home.getZ(), golem.workRadius());
    GolemHandle handle = golem.handle();
    UUID owner = handle.owner();
    int channels = GolemRunes.channels(handle);
    List<NodeIndex.Entry> found = new ArrayList<>(near.size());
    for (NodeIndex.Entry entry : near) {
      NodeRecord record = entry.record();
      if (record.type() == type && record.owner().equals(owner) && GolemTaskMatch.channel(record.channel(), channels)
          && active(golem, world, entry.key(), record)) {
        found.add(entry);
      }
    }
    return found;
  }

  static boolean active(World world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !world.isChunkLoaded(key.chunkX(), key.chunkZ())
        || !J.isOwnedByCurrentRegion(new Location(world, key.x(), key.y(), key.z()))) {
      return true;
    }
    return NodeRules.active(record, world.getBlockAt(key.x(), key.y(), key.z()));
  }

  static boolean covered(NodeRecord record, GolemTask task) {
    GolemTarget target = task.target();
    return task.node().filter(key -> NodeArea.covers(key, record, target.x(), target.y(), target.z())).isPresent();
  }

  static Map<Long, GolemTask> open(NodeContext node) {
    List<GolemTask> tasks = node.tasks();
    if (tasks.isEmpty()) {
      return Map.of();
    }
    NodeKey key = node.key();
    NodeRecord record = node.record();
    Map<Long, GolemTask> open = new HashMap<>(tasks.size() * 2);
    for (GolemTask task : tasks) {
      GolemTarget target = task.target();
      if (!NodeArea.covers(key, record, target.x(), target.y(), target.z())) {
        node.withdraw(task);
        continue;
      }
      open.put(NodeContainers.blockKey(target.blockX(), target.blockY(), target.blockZ()), task);
    }
    return open;
  }

  private static boolean active(GolemContext golem, World world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
      return true;
    }
    Location anchor = new Location(world, key.x(), key.y(), key.z());
    return !golem.owns(anchor) || NodeRules.active(record, world.getBlockAt(key.x(), key.y(), key.z()));
  }
}
