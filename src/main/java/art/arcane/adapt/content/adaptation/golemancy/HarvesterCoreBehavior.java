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
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class HarvesterCoreBehavior implements GolemCoreBehavior {
  private final GolemHarvesterCore core;
  private final HarvestSpots spots;
  private final HarvestActions actions;

  HarvesterCoreBehavior(GolemHarvesterCore core, HarvestSpots spots, HarvestActions actions) {
    this.core = Objects.requireNonNull(core);
    this.spots = Objects.requireNonNull(spots);
    this.actions = Objects.requireNonNull(actions);
  }

  @Override
  public Adaptation<?> policy() {
    return core;
  }

  @Override
  public Optional<GolemTask> seek(GolemContext golem) {
    int level = actions.level(golem);
    GolemHarvesterCore.Config config = core.config();
    List<ItemStack> carried = golem.carriedItems();
    boolean planting = HarvesterRules.unlocked(level, config.tillingLevel) && carriesSeed(carried);
    boolean tending = HarvesterRules.unlocked(level, config.tendingLevel) && core.tendReady(golem.handle().id(), golem.nowMillis());
    if (!planting && !tending) {
      return Optional.empty();
    }
    World world = golem.world();
    List<Candidate> candidates = candidates(golem, world, level, planting, tending, carried);
    boolean boneMeal = false;
    boolean chestChecked = false;
    for (Candidate candidate : candidates) {
      HarvestSpots.Mark mark = candidate.mark();
      boolean tend = mark.kind() == HarvesterRules.Spot.TEND;
      if (tend && !chestChecked) {
        chestChecked = true;
        boneMeal = boneMealSlot(homeChest(golem).orElse(null)) >= 0;
      }
      if ((tend && !boneMeal) || !golem.inWorkRadius(new Location(world, mark.x() + 0.5D, mark.y() + 0.5D, mark.z() + 0.5D))) {
        continue;
      }
      String type = tend ? HarvesterRules.TEND : HarvesterRules.PLANT;
      GolemTask task = GolemTask.builder(candidate.node(), NodeType.HARVEST, type, GolemTarget.block(world.getUID(), mark.x(), mark.y(), mark.z()))
          .build();
      if (!golem.skips(task)) {
        return Optional.of(task);
      }
    }
    return Optional.empty();
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    GolemActResult result = perform(golem, task);
    if (result == GolemActResult.FAILED) {
      GolemTarget target = task.target();
      task.node().ifPresent(key -> spots.drop(key, target.blockX(), target.blockY(), target.blockZ()));
    }
    return result;
  }

  @Override
  public int actIntervalTicks(GolemContext golem, GolemTask task) {
    return actions.intervalTicks(golem);
  }

  private GolemActResult perform(GolemContext golem, GolemTask task) {
    int level = actions.level(golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !actions.covered(record, task)) {
      return GolemActResult.FAILED;
    }
    HarvesterRules.Plan plan = actions.plan(level, record);
    return switch (task.type()) {
      case HarvesterRules.PLANT -> plant(golem, task, plan);
      case HarvesterRules.TEND -> tend(golem, task, plan);
      default -> GolemActResult.FAILED;
    };
  }

  private GolemActResult plant(GolemContext golem, GolemTask task, HarvesterRules.Plan plan) {
    Block farmland = actions.target(golem, task);
    if (HarvesterRules.survey(farmland, plan, actions.owned(golem)) != HarvesterRules.Spot.PLANT) {
      return GolemActResult.FAILED;
    }
    if (!golem.canPlace(farmland.getRelative(BlockFace.UP).getLocation())) {
      return GolemActResult.BLOCKED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    return actions.plant(golem, farmland, plan) ? GolemActResult.DONE : GolemActResult.FAILED;
  }

  private GolemActResult tend(GolemContext golem, GolemTask task, HarvesterRules.Plan plan) {
    Block crop = actions.target(golem, task);
    if (HarvesterRules.survey(crop, plan, actions.owned(golem)) != HarvesterRules.Spot.TEND) {
      return GolemActResult.FAILED;
    }
    if (!golem.canInteract(crop.getLocation())) {
      return GolemActResult.BLOCKED;
    }
    Inventory chest = homeChest(golem).orElse(null);
    int slot = boneMealSlot(chest);
    if (slot < 0) {
      return GolemActResult.FAILED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    ItemStack spent = spend(chest, slot);
    if (spent == null) {
      return GolemActResult.FAILED;
    }
    boolean grown;
    try {
      grown = crop.applyBoneMeal(BlockFace.UP);
    } catch (RuntimeException failure) {
      refund(chest, slot, spent);
      throw failure;
    }
    if (!grown) {
      refund(chest, slot, spent);
      return GolemActResult.FAILED;
    }
    core.tended(golem.handle().id(), golem.nowMillis());
    HarvestFeedback.play(core, actions.center(crop), HarvestFeedback.Kind.TEND);
    return GolemActResult.DONE;
  }

  private List<Candidate> candidates(GolemContext golem, World world, int level, boolean planting, boolean tending, List<ItemStack> carried) {
    Location here = golem.golem().getLocation();
    List<Candidate> candidates = new ArrayList<>();
    for (NodeIndex.Entry entry : harvestNodes(golem, world)) {
      boolean plants = planting && plantable(actions.plan(level, entry.record()), carried);
      if (!plants && !tending) {
        continue;
      }
      for (HarvestSpots.Mark mark : spots.of(entry.key())) {
        boolean inArea = NodeArea.covers(entry.key(), entry.record(), mark.x() + 0.5D, mark.y() + 0.5D, mark.z() + 0.5D);
        if (inArea && (mark.kind() == HarvesterRules.Spot.PLANT ? plants : tending)) {
          candidates.add(new Candidate(entry.key(), mark, distanceSquared(here, mark)));
        }
      }
    }
    candidates.sort(Comparator.comparingDouble(Candidate::distanceSquared));
    return candidates;
  }

  private boolean carriesSeed(List<ItemStack> carried) {
    HarvestCrops crops = core.crops();
    for (ItemStack item : carried) {
      if (crops.farmlandCrop(item.getType()) != null) {
        return true;
      }
    }
    return false;
  }

  private static boolean plantable(HarvesterRules.Plan plan, List<ItemStack> carried) {
    for (ItemStack item : carried) {
      Material crop = plan.crops().farmlandCrop(item.getType());
      if (crop != null && HarvesterRules.wanted(plan, crop)) {
        return true;
      }
    }
    return false;
  }

  private static List<NodeIndex.Entry> harvestNodes(GolemContext golem, World world) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      return List.of();
    }
    Location home = golem.home();
    List<NodeIndex.Entry> near = service.get().index().near(world.getUID(), home.getX(), home.getY(), home.getZ(), golem.workRadius());
    if (near.isEmpty()) {
      return List.of();
    }
    GolemHandle handle = golem.handle();
    UUID owner = handle.owner();
    int channels = GolemRunes.channels(handle);
    List<NodeIndex.Entry> found = new ArrayList<>(near.size());
    for (NodeIndex.Entry entry : near) {
      NodeRecord record = entry.record();
      if (record.type() == NodeType.HARVEST && record.owner().equals(owner) && GolemTaskMatch.channel(record.channel(), channels)
          && active(golem, world, entry.key(), record)) {
        found.add(entry);
      }
    }
    return found;
  }

  private static boolean active(GolemContext golem, World world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
      return true;
    }
    Location anchor = new Location(world, key.x(), key.y(), key.z());
    return !golem.owns(anchor) || NodeRules.active(record, world.getBlockAt(key.x(), key.y(), key.z()));
  }

  private static Optional<Inventory> homeChest(GolemContext golem) {
    GolemState.Anchor chest = golem.handle().state().homeChest();
    World world = golem.world();
    if (chest == null || !chest.world().equals(world.getUID()) || !world.isChunkLoaded(chest.x() >> 4, chest.z() >> 4)) {
      return Optional.empty();
    }
    return golem.container(world.getBlockAt(chest.x(), chest.y(), chest.z()));
  }

  private static int boneMealSlot(Inventory chest) {
    if (chest == null) {
      return -1;
    }
    ItemStack[] contents = chest.getStorageContents();
    for (int slot = 0; slot < contents.length; slot++) {
      ItemStack item = contents[slot];
      if (item != null && item.getType() == Material.BONE_MEAL && item.getAmount() > 0) {
        return slot;
      }
    }
    return -1;
  }

  private static ItemStack spend(Inventory chest, int slot) {
    ItemStack held = chest.getItem(slot);
    if (held == null || held.getType() != Material.BONE_MEAL || held.getAmount() <= 0) {
      return null;
    }
    ItemStack one = held.clone();
    one.setAmount(1);
    if (held.getAmount() == 1) {
      chest.setItem(slot, null);
      return one;
    }
    ItemStack rest = held.clone();
    rest.setAmount(held.getAmount() - 1);
    chest.setItem(slot, rest);
    return one;
  }

  private static void refund(Inventory chest, int slot, ItemStack one) {
    ItemStack held = chest.getItem(slot);
    if (held == null) {
      chest.setItem(slot, one);
      return;
    }
    if (held.isSimilar(one) && held.getAmount() < held.getMaxStackSize()) {
      ItemStack restored = held.clone();
      restored.setAmount(held.getAmount() + 1);
      chest.setItem(slot, restored);
      return;
    }
    Location at = chest.getLocation();
    for (ItemStack left : chest.addItem(one).values()) {
      if (at != null && at.getWorld() != null) {
        at.getWorld().dropItemNaturally(at, left);
      }
    }
  }

  private static double distanceSquared(Location here, HarvestSpots.Mark mark) {
    double dx = mark.x() + 0.5D - here.getX();
    double dy = mark.y() + 0.5D - here.getY();
    double dz = mark.z() + 0.5D - here.getZ();
    return dx * dx + dy * dy + dz * dz;
  }

  private record Candidate(NodeKey node, HarvestSpots.Mark mark, double distanceSquared) {
  }
}
