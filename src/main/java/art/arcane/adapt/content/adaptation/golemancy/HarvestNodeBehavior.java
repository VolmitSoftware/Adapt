package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class HarvestNodeBehavior implements NodeBehavior {
  static final double EXTRA_REACH = 3.0D;

  private final GolemHarvesterCore core;
  private final HarvestSpots spots;
  private final HarvestActions actions;

  HarvestNodeBehavior(GolemHarvesterCore core, HarvestSpots spots, HarvestActions actions) {
    this.core = Objects.requireNonNull(core);
    this.spots = Objects.requireNonNull(spots);
    this.actions = Objects.requireNonNull(actions);
  }

  @Override
  public Adaptation<?> policy() {
    return core;
  }

  @Override
  public void scan(NodeContext node, ScanBudget budget) {
    int level = core.ownerLevel(node.owner());
    if (level < 1) {
      return;
    }
    NodeKey key = node.key();
    HarvesterRules.Plan plan = actions.plan(level, node.record());
    Predicate<Block> owned = node::owns;
    Map<Long, GolemTask> open = open(node);
    spots.scanned(key, node.record(), node.nowMillis());
    for (int index = budget.start(); index < budget.end(); index++) {
      Optional<Block> found = node.block(index);
      if (found.isEmpty()) {
        continue;
      }
      Block block = found.get();
      HarvesterRules.Spot spot = HarvesterRules.survey(block, plan, owned);
      spots.record(key, block.getX(), block.getY(), block.getZ(), spot);
      String type = jobType(spot);
      GolemTask existing = open.isEmpty() ? null : open.get(NodeContainers.blockKey(block.getX(), block.getY(), block.getZ()));
      if (existing != null && !existing.type().equals(type)) {
        node.withdraw(existing);
        existing = null;
      }
      if (type != null && existing == null && budget.hasTaskRoom()) {
        node.post(node.task(type, GolemTarget.block(block)).build());
      }
    }
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    return switch (task.type()) {
      case HarvesterRules.HARVEST -> harvest(golem, task);
      case HarvesterRules.TILL -> till(golem, task);
      default -> GolemActResult.FAILED;
    };
  }

  @Override
  public int actIntervalTicks(GolemContext golem, GolemTask task) {
    return actions.intervalTicks(golem);
  }

  private GolemActResult harvest(GolemContext golem, GolemTask task) {
    int level = actions.level(golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !actions.covered(record, task)) {
      return GolemActResult.FAILED;
    }
    HarvesterRules.Plan plan = actions.plan(level, record);
    Predicate<Block> owned = actions.owned(golem);
    Block block = actions.target(golem, task);
    if (HarvesterRules.survey(block, plan, owned) != HarvesterRules.Spot.RIPE) {
      return GolemActResult.FAILED;
    }
    if (!actions.canHarvest(golem, block, plan)) {
      return GolemActResult.BLOCKED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    actions.reap(golem, block, plan);
    int limit = HarvesterRules.cropsPerAction(core.config().cropsPerActionByLevel, level) - 1;
    int harvested = 1 + reapNearby(golem, task, record, plan, owned, limit);
    golem.workXp(core.config().workXpPerCrop * harvested);
    return GolemActResult.DONE;
  }

  private GolemActResult till(GolemContext golem, GolemTask task) {
    int level = actions.level(golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !actions.covered(record, task)) {
      return GolemActResult.FAILED;
    }
    HarvesterRules.Plan plan = actions.plan(level, record);
    Block block = actions.target(golem, task);
    if (HarvesterRules.survey(block, plan, actions.owned(golem)) != HarvesterRules.Spot.TILL) {
      return GolemActResult.FAILED;
    }
    Location at = block.getLocation();
    if (!golem.canBreak(at) || !golem.canPlace(at)) {
      return GolemActResult.BLOCKED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    block.setType(Material.FARMLAND);
    HarvestFeedback.play(core, actions.center(block), HarvestFeedback.Kind.TILL);
    actions.plant(golem, block, plan);
    return GolemActResult.DONE;
  }

  private int reapNearby(GolemContext golem, GolemTask task, NodeRecord record, HarvesterRules.Plan plan, Predicate<Block> owned, int limit) {
    NodeKey key = task.node().orElse(null);
    if (limit <= 0 || key == null) {
      return 0;
    }
    Optional<GolemTaskBoard> board = GolemancyRuntime.boards().map(boards -> boards.board(key.world()));
    if (board.isEmpty()) {
      return 0;
    }
    Location here = golem.golem().getLocation();
    List<GolemTask> nearby = nearby(board.get().tasks(key), task, here, golem.nowMillis());
    int harvested = 0;
    for (GolemTask extra : nearby) {
      if (harvested >= limit || golem.room() <= 0) {
        break;
      }
      Block block = actions.target(golem, extra);
      if (!owned.test(block)) {
        continue;
      }
      if (!actions.covered(record, extra) || HarvesterRules.survey(block, plan, owned) != HarvesterRules.Spot.RIPE) {
        board.get().withdraw(extra);
        continue;
      }
      if (!actions.canHarvest(golem, block, plan)) {
        continue;
      }
      actions.reap(golem, block, plan);
      board.get().withdraw(extra);
      harvested++;
    }
    return harvested;
  }

  private static List<GolemTask> nearby(List<GolemTask> tasks, GolemTask current, Location here, long nowMillis) {
    double reach = EXTRA_REACH * EXTRA_REACH;
    List<GolemTask> found = new ArrayList<>(tasks.size());
    for (GolemTask candidate : tasks) {
      if (candidate != current && HarvesterRules.HARVEST.equals(candidate.type()) && candidate.holder(nowMillis).isEmpty()
          && candidate.target().distanceSquared(here.getX(), here.getY(), here.getZ()) <= reach) {
        found.add(candidate);
      }
    }
    found.sort(Comparator.comparingDouble(candidate -> candidate.target().distanceSquared(here.getX(), here.getY(), here.getZ())));
    return found;
  }

  private static Map<Long, GolemTask> open(NodeContext node) {
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

  private static String jobType(HarvesterRules.Spot spot) {
    return switch (spot) {
      case RIPE -> HarvesterRules.HARVEST;
      case TILL -> HarvesterRules.TILL;
      case NONE, TEND, PLANT -> null;
    };
  }
}
