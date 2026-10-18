package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class BreakerNodeBehavior implements NodeBehavior {
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
    BreakerRules.Plan plan = plan(level, record, false, false);
    for (int index = budget.start(); index < budget.end(); index++) {
      Optional<Block> found = node.block(index);
      if (found.isEmpty()) {
        continue;
      }
      Block block = found.get();
      boolean breakable = breakable(block, plan);
      GolemTask existing = open.isEmpty() ? null : open.get(NodeContainers.blockKey(block.getX(), block.getY(), block.getZ()));
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
    int level = level(golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !BreakerRules.BREAK.equals(task.type()) || !covered(record, task)) {
      return GolemActResult.FAILED;
    }
    GolemBreakerCore.Config config = core.config();
    BreakerRules.Plan plan = plan(level, record, BreakerRules.silk(config, level, record), BreakerRules.fortune(config, level, record));
    GolemTarget target = task.target();
    Block block = golem.world().getBlockAt(target.blockX(), target.blockY(), target.blockZ());
    if (!golem.owns(block.getLocation()) || !breakable(block, plan)) {
      return GolemActResult.FAILED;
    }
    if (!golem.canBreak(block.getLocation())) {
      return GolemActResult.BLOCKED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    mine(golem, block, plan.tool());
    golem.workXp(config.workXpPerBlock);
    return GolemActResult.DONE;
  }

  @Override
  public int actIntervalTicks(GolemContext golem, GolemTask task) {
    return BreakerRules.intervalTicks(core.config().intervalTicksByLevel, level(golem));
  }

  private void mine(GolemContext golem, Block block, ItemStack tool) {
    List<ItemStack> drops = new ArrayList<>(golem.withBonusYield(new ArrayList<>(block.getDrops(tool))));
    BlockData broken = block.getBlockData();
    Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);
    block.setType(Material.AIR);
    for (ItemStack item : drops) {
      if (item == null || item.getAmount() <= 0) {
        continue;
      }
      golem.carry(item).ifPresent(left -> block.getWorld().dropItemNaturally(at, left));
    }
    BreakerFeedback.play(core, at, broken);
  }

  private BreakerRules.Plan plan(int level, NodeRecord record, boolean silk, boolean fortune) {
    GolemBreakerCore.Config config = core.config();
    ItemStack tool = core.tool(BreakerRules.tool(config.toolByLevel, level), silk, fortune);
    return BreakerRules.plan(config, core.blacklist(), level, record, NodeItemTags.shared(), tool);
  }

  private boolean breakable(Block block, BreakerRules.Plan plan) {
    if (!BreakerRules.wanted(plan.node(), block, plan.tags())) {
      return false;
    }
    Material type = block.getType();
    return BreakerRules.minable(type, core.hardness(type), plan) && !block.isLiquid() && !core.tileEntity(block)
        && block.isPreferredTool(plan.tool()) && !anchored(block);
  }

  private int level(GolemContext golem) {
    Player owner = golem.owner();
    return owner == null ? 0 : core.ownerLevel(owner.getUniqueId());
  }

  private static boolean anchored(Block block) {
    return GolemancyRuntime.nodes().map(nodes -> nodes.index().mayAnchor(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()))
        .orElse(true);
  }

  private static boolean covered(NodeRecord record, GolemTask task) {
    GolemTarget target = task.target();
    return task.node().filter(key -> NodeArea.covers(key, record, target.x(), target.y(), target.z())).isPresent();
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
