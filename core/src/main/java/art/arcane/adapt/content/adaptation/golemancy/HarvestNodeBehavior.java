package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class HarvestNodeBehavior implements NodeBehavior {
  static final double EXTRA_REACH = 3.0D;

  private static final Id FARMLAND = Id.minecraft("farmland");

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
    node.surveyCriteria(plan.criteria(level));
    Predicate<BlockRef> owned = node::owns;
    Map<Long, GolemTask> open = GolemancySupport.open(node);
    spots.scanned(key, node.record(), node.nowMillis());
    for (int index : budget.indices()) {
      Optional<BlockRef> found = node.block(index);
      if (found.isEmpty()) {
        continue;
      }
      BlockRef block = found.get();
      HarvesterRules.Spot spot = HarvesterRules.survey(block, plan, owned);
      if (spot == HarvesterRules.Spot.UNAVAILABLE) {
        node.candidate(index, true);
        continue;
      }
      node.candidate(index, spot != HarvesterRules.Spot.NONE);
      spots.record(key, block.x(), block.y(), block.z(), spot);
      String type = jobType(spot);
      GolemTask existing = open.isEmpty() ? null : open.get(NodeContainers.blockKey(block.x(), block.y(), block.z()));
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
    int level = GolemancySupport.ownerLevel(core, golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !GolemancySupport.covered(record, task)) {
      return GolemActResult.FAILED;
    }
    HarvesterRules.Plan plan = actions.plan(level, record);
    Predicate<BlockRef> owned = actions.owned(golem);
    BlockRef block = actions.target(golem, task);
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
    int level = GolemancySupport.ownerLevel(core, golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !GolemancySupport.covered(record, task)) {
      return GolemActResult.FAILED;
    }
    HarvesterRules.Plan plan = actions.plan(level, record);
    BlockRef block = actions.target(golem, task);
    if (HarvesterRules.survey(block, plan, actions.owned(golem)) != HarvesterRules.Spot.TILL) {
      return GolemActResult.FAILED;
    }
    Loc at = block.location();
    if (!golem.canBreak(at) || !golem.canPlace(at)) {
      return GolemActResult.BLOCKED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    block.setType(FARMLAND, true);
    HarvestFeedback.play(core, actions.center(block), HarvestFeedback.Kind.TILL);
    actions.plant(golem, block, plan);
    return GolemActResult.DONE;
  }

  private int reapNearby(GolemContext golem, GolemTask task, NodeRecord record, HarvesterRules.Plan plan, Predicate<BlockRef> owned, int limit) {
    NodeKey key = task.node().orElse(null);
    if (limit <= 0 || key == null) {
      return 0;
    }
    Optional<GolemTaskBoard> board = GolemancyRuntime.boards().map(boards -> boards.board(key.world()));
    if (board.isEmpty()) {
      return 0;
    }
    Loc here = golem.golem().location();
    List<GolemTask> nearby = nearby(board.get().tasks(key), task, here, golem.nowMillis());
    int harvested = 0;
    for (GolemTask extra : nearby) {
      if (harvested >= limit || golem.room() <= 0) {
        break;
      }
      BlockRef block = actions.target(golem, extra);
      if (!owned.test(block)) {
        continue;
      }
      if (!GolemancySupport.covered(record, extra) || HarvesterRules.survey(block, plan, owned) != HarvesterRules.Spot.RIPE) {
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

  private static List<GolemTask> nearby(List<GolemTask> tasks, GolemTask current, Loc here, long nowMillis) {
    double reach = EXTRA_REACH * EXTRA_REACH;
    List<GolemTask> found = new ArrayList<>(tasks.size());
    for (GolemTask candidate : tasks) {
      if (candidate != current && HarvesterRules.HARVEST.equals(candidate.type()) && candidate.holder(nowMillis).isEmpty()
          && candidate.target().distanceSquared(here.x(), here.y(), here.z()) <= reach) {
        found.add(candidate);
      }
    }
    found.sort(Comparator.comparingDouble(candidate -> candidate.target().distanceSquared(here.x(), here.y(), here.z())));
    return found;
  }

  private static String jobType(HarvesterRules.Spot spot) {
    return switch (spot) {
      case RIPE -> HarvesterRules.HARVEST;
      case TILL -> HarvesterRules.TILL;
      case NONE, UNAVAILABLE, TEND, PLANT -> null;
    };
  }
}
