package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.golem.GolemActResult;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemToolPlan;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.node.NodeArea;
import art.arcane.adapt.content.golem.node.NodeBehavior;
import art.arcane.adapt.content.golem.node.NodeContainers;
import art.arcane.adapt.content.golem.node.NodeContext;
import art.arcane.adapt.content.golem.node.NodeItemTags;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.ScanBudget;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class BreakerNodeBehavior implements NodeBehavior {
  private static final Id WATER = Id.minecraft("water");
  private static final Id AIR = Id.minecraft("air");

  private final GolemBreakerCore core;

  BreakerNodeBehavior(GolemBreakerCore core) {
    this.core = Objects.requireNonNull(core);
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
    NodeRecord record = node.record();
    Map<Long, GolemTask> open = open(node);
    if (!BreakerRules.filtered(record)) {
      return;
    }
    BreakerRules.Plan plan = plan(level, record);
    for (int index : budget.indices()) {
      Optional<BlockRef> found = node.block(index);
      if (found.isEmpty()) {
        continue;
      }
      BlockRef block = found.get();
      boolean breakable = breakable(block, plan);
      node.candidate(index, breakable);
      GolemTask existing = open.isEmpty() ? null : open.get(NodeContainers.blockKey(block.x(), block.y(), block.z()));
      if (existing != null && !breakable) {
        node.withdraw(existing);
      }
      if (breakable && existing == null && budget.hasTaskRoom()) {
        node.post(node.task(BreakerRules.BREAK, GolemTarget.block(block)).build());
      }
    }
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    int level = GolemancySupport.ownerLevel(core, golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !BreakerRules.BREAK.equals(task.type()) || !GolemancySupport.covered(record, task)) {
      return GolemActResult.FAILED;
    }
    GolemBreakerCore.Config config = core.config();
    BreakerRules.Plan plan = plan(level, record);
    GolemTarget target = task.target();
    BlockRef block = golem.world().blockAt(target.blockX(), target.blockY(), target.blockZ());
    if (!golem.owns(block.location()) || !breakable(block, plan)) {
      return GolemActResult.FAILED;
    }
    if (!golem.canBreak(block.location())) {
      return GolemActResult.BLOCKED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    boolean silk = BreakerRules.silk(config, level, record);
    boolean fortune = BreakerRules.fortune(config, level, record);
    ItemRef held = golem.toolInUse().orElse(null);
    mine(golem, block, BreakerRules.dropsTool(held, silk, fortune, core.tool(BreakerRules.freeTool(core.pickaxe(level))), core::tool));
    if (held != null) {
      golem.wearTool();
    }
    golem.workXp(config.workXpPerBlock);
    return GolemActResult.DONE;
  }

  @Override
  public GolemToolPlan tools(GolemContext golem, GolemTask task) {
    int level = GolemancySupport.ownerLevel(core, golem);
    NodeRecord record = golem.node().orElse(null);
    GolemTarget target = task.target();
    WorldRef world = golem.world();
    if (level < 1 || record == null || !BreakerRules.BREAK.equals(task.type())
        || !golem.owns(new Loc(world, target.blockX(), target.blockY(), target.blockZ()))) {
      return GolemToolPlan.NONE;
    }
    BlockRef block = world.blockAt(target.blockX(), target.blockY(), target.blockZ());
    GolemBreakerCore.Config config = core.config();
    return BreakerRules.toolPlan(block::isPreferredTool, core::tool, core.pickaxe(level), BreakerRules.silk(config, level, record),
        BreakerRules.fortune(config, level, record));
  }

  @Override
  public int actIntervalTicks(GolemContext golem, GolemTask task) {
    return BreakerRules.intervalTicks(core.config().intervalTicksByLevel, GolemancySupport.ownerLevel(core, golem));
  }

  private void mine(GolemContext golem, BlockRef block, ItemRef tool) {
    BlockStateRef broken = block.state();
    List<ItemRef> drops = drops(golem, block, broken, tool);
    Loc at = block.center();
    block.setType("true".equals(broken.property("waterlogged")) ? WATER : AIR, true);
    for (ItemRef item : drops) {
      if (item == null || item.isEmpty() || item.amount() <= 0) {
        continue;
      }
      golem.carry(item).ifPresent(left -> block.world().dropItemNaturally(at.position(), left));
    }
    BreakerFeedback.play(core, at, broken);
  }

  private List<ItemRef> drops(GolemContext golem, BlockRef block, BlockStateRef broken, ItemRef tool) {
    List<ItemRef> drops = new ArrayList<>(block.drops(tool, null));
    if (XpProvenance.isPlayerPlaced(block)) {
      return drops;
    }
    Id type = block.type();
    Id placement = broken.placementItem();
    List<ItemRef> kept = new ArrayList<>(drops.size());
    List<ItemRef> eligible = new ArrayList<>(drops.size());
    for (ItemRef item : drops) {
      if (item == null) {
        continue;
      }
      if (item.type().equals(type) || item.type().equals(placement)) {
        kept.add(item);
      } else {
        eligible.add(item);
      }
    }
    if (!eligible.isEmpty()) {
      kept.addAll(golem.withBonusYield(eligible));
    }
    return kept;
  }

  private BreakerRules.Plan plan(int level, NodeRecord record) {
    return BreakerRules.plan(core.config(), core.blacklist(), level, record, NodeItemTags.shared(), core.tool(core.pickaxe(level)));
  }

  private boolean breakable(BlockRef block, BreakerRules.Plan plan) {
    if (!BreakerRules.wanted(plan.node(), block, plan.tags())) {
      return false;
    }
    Id type = block.type();
    return BreakerRules.minable(type, core.hardness(type), plan) && !block.isLiquid() && !core.tileEntity(type)
        && block.isPreferredTool(plan.tool()) && !plinth(block);
  }

  private static boolean plinth(BlockRef block) {
    return GolemancyRuntime.plinths().map(plinths -> plinths.isPlinthBlock(block)).orElse(false);
  }

  private static Map<Long, GolemTask> open(NodeContext node) {
    List<GolemTask> tasks = node.tasks();
    if (tasks.isEmpty()) {
      return Map.of();
    }
    NodeKey key = node.key();
    NodeRecord record = node.record();
    boolean filtered = BreakerRules.filtered(record);
    Map<Long, GolemTask> open = new HashMap<>(tasks.size() * 2);
    for (GolemTask task : tasks) {
      GolemTarget target = task.target();
      if (!filtered || !NodeArea.covers(key, record, target.x(), target.y(), target.z())) {
        node.withdraw(task);
        continue;
      }
      open.put(NodeContainers.blockKey(target.blockX(), target.blockY(), target.blockZ()), task);
    }
    return open;
  }
}
