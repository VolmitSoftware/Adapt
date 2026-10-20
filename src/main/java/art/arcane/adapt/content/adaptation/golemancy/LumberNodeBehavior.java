package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class LumberNodeBehavior implements NodeBehavior {
  static final String FELL = "fell";
  static final int ACT_INTERVAL_TICKS = 20;
  static final long SETTLE_MILLIS = 2_000L;

  private static final Set<LumberTrees.Verdict> REMEMBERED = EnumSet.of(LumberTrees.Verdict.TOO_BIG, LumberTrees.Verdict.NO_LEAVES,
      LumberTrees.Verdict.BUILT, LumberTrees.Verdict.PLACED, LumberTrees.Verdict.TOO_WIDE, LumberTrees.Verdict.UNOWNED);

  private final GolemLumberCore core;
  private final LumberFellings fellings;
  private final LumberRejections rejections;
  private final LumberActions actions;

  LumberNodeBehavior(GolemLumberCore core, LumberFellings fellings, LumberRejections rejections, LumberActions actions) {
    this.core = Objects.requireNonNull(core);
    this.fellings = Objects.requireNonNull(fellings);
    this.rejections = Objects.requireNonNull(rejections);
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
    LumberRules.Plan plan = actions.plan(level);
    NodeKey key = node.key();
    NodeRecord record = node.record();
    UUID world = key.world();
    long now = node.nowMillis();
    fellings.keep(world, base -> NodeArea.covers(key, record, base.x(), base.y(), base.z()), now);
    Map<Long, GolemTask> open = GolemancySupport.open(node);
    TreeView view = actions.view(node.world(), node::owns);
    for (int index = budget.start(); index < budget.end(); index++) {
      Optional<Block> found = node.block(index);
      if (found.isEmpty()) {
        continue;
      }
      Block block = found.get();
      int x = block.getX();
      int y = block.getY();
      int z = block.getZ();
      GolemTask existing = open.isEmpty() ? null : open.get(NodeContainers.blockKey(x, y, z));
      Optional<LumberFellings.Felling> felling = fellings.get(world, x, y, z);
      if (felling.isPresent()) {
        keepOrDrop(node, block, plan, felling.get(), existing, budget);
        continue;
      }
      if (!LumberTrees.candidate(view, x, y, z, plan.nether())) {
        if (existing != null) {
          node.withdraw(existing);
        }
        continue;
      }
      if (existing != null || !budget.hasTaskRoom() || rejections.rejected(key, x, y, z, now)) {
        continue;
      }
      LumberTrees.Survey survey = LumberTrees.survey(view, x, y, z, plan.reach(plan.largestCap()));
      if (survey.verdict() == LumberTrees.Verdict.NATURAL && at(survey.tree().base(), x, y, z)) {
        post(node, block, LumberRules.bodies(core.config(), level, survey.tree().logs()));
      } else if (REMEMBERED.contains(survey.verdict()) || survey.verdict() == LumberTrees.Verdict.NATURAL) {
        rejections.reject(key, x, y, z, now);
      }
    }
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    if (!FELL.equals(task.type())) {
      return GolemActResult.FAILED;
    }
    int level = GolemancySupport.ownerLevel(core, golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !GolemancySupport.covered(record, task)) {
      return GolemActResult.FAILED;
    }
    LumberRules.Plan plan = actions.plan(level);
    int cap = LumberRules.cap(core.config(), golem.handle().body(), level);
    GolemTarget target = task.target();
    UUID world = target.world();
    TreeView view = actions.view(golem);
    LumberFellings.Felling felling = fellings.get(world, target.blockX(), target.blockY(), target.blockZ()).orElse(null);
    if (felling == null) {
      LumberTrees.Survey survey = LumberTrees.survey(view, target.blockX(), target.blockY(), target.blockZ(), plan.reach(cap));
      if (survey.verdict() != LumberTrees.Verdict.NATURAL || !at(survey.tree().base(), target.blockX(), target.blockY(), target.blockZ())) {
        return GolemActResult.FAILED;
      }
      felling = fellings.start(world, survey.tree(), golem.nowMillis());
    } else if (!reachable(felling.tree(), plan, cap)) {
      fellings.finish(world, felling);
      return GolemActResult.FAILED;
    }
    felling.touch(golem.nowMillis());
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    return felling.settling() ? settle(golem, view, felling, plan, world) : fell(golem, view, felling, plan, world);
  }

  @Override
  public int actIntervalTicks(GolemContext golem, GolemTask task) {
    return ACT_INTERVAL_TICKS;
  }

  private GolemActResult fell(GolemContext golem, TreeView view, LumberFellings.Felling felling, LumberRules.Plan plan, UUID world) {
    Batch batch = batch(view, felling, LumberRules.logsThisAct(plan.logsPerSecond(), golem.actIndex()), golem.room());
    if (batch == null) {
      fellings.finish(world, felling);
      return GolemActResult.FAILED;
    }
    for (LumberTrees.Part part : batch.parts()) {
      if (!golem.canBreak(actions.location(golem, part))) {
        return GolemActResult.BLOCKED;
      }
    }
    if (!batch.parts().isEmpty()) {
      actions.fell(golem, felling.tree(), batch.parts());
    }
    if (batch.logs() > 0) {
      golem.workXp(core.config().workXpPerLog * batch.logs());
    }
    felling.advance(batch.next());
    if (!felling.felled()) {
      return GolemActResult.WORKING;
    }
    if (plan.sweep() && core.sweepsLeaves(golem.handle().owner())) {
      List<LumberTrees.Part> leaves = LumberTrees.leafCandidates(view, felling.tree().logParts());
      if (!leaves.isEmpty()) {
        felling.settle(golem.nowMillis() + SETTLE_MILLIS, leaves);
        return GolemActResult.WORKING;
      }
    }
    return complete(golem, view, felling, plan, world);
  }

  private GolemActResult settle(GolemContext golem, TreeView view, LumberFellings.Felling felling, LumberRules.Plan plan, UUID world) {
    if (golem.nowMillis() < felling.settleUntil()) {
      return GolemActResult.WORKING;
    }
    felling.sweptTo(actions.sweep(golem, view, felling.tree(), felling.leaves(), felling.swept()));
    if (!felling.sweptAll()) {
      return GolemActResult.WORKING;
    }
    return complete(golem, view, felling, plan, world);
  }

  private GolemActResult complete(GolemContext golem, TreeView view, LumberFellings.Felling felling, LumberRules.Plan plan, UUID world) {
    if (plan.replant()) {
      actions.replant(golem, view, felling.tree());
    }
    fellings.finish(world, felling);
    return GolemActResult.DONE;
  }

  private void keepOrDrop(NodeContext node, Block block, LumberRules.Plan plan, LumberFellings.Felling felling, GolemTask existing, ScanBudget budget) {
    Set<GolemBody> bodies = LumberRules.bodies(core.config(), plan.level(), felling.tree().logs());
    if (bodies.isEmpty() || !reachable(felling.tree(), plan, plan.largestCap())) {
      fellings.finish(node.key().world(), felling);
      if (existing != null) {
        node.withdraw(existing);
      }
      return;
    }
    if (existing == null && budget.hasTaskRoom()) {
      post(node, block, bodies);
    }
  }

  private static void post(NodeContext node, Block block, Set<GolemBody> bodies) {
    if (!bodies.isEmpty()) {
      node.post(node.task(FELL, GolemTarget.block(block)).bodies(bodies).build());
    }
  }

  private static Batch batch(TreeView view, LumberFellings.Felling felling, int allowance, int room) {
    LumberTrees.Tree tree = felling.tree();
    List<LumberTrees.Part> parts = tree.parts();
    List<LumberTrees.Part> taken = new ArrayList<>(Math.max(1, Math.min(room, allowance)));
    int logs = 0;
    int index = felling.cursor();
    while (index < parts.size() && taken.size() < room) {
      LumberTrees.Part part = parts.get(index);
      boolean log = !TreeBlocks.cap(part.type());
      if (log && logs >= allowance) {
        break;
      }
      if (!view.owned(part.x(), part.y(), part.z())) {
        return null;
      }
      if (view.type(part.x(), part.y(), part.z()) == part.type()) {
        if (log && LumberTrees.intact(view, part) != LumberTrees.Verdict.NATURAL) {
          return null;
        }
        taken.add(part);
        if (log) {
          logs++;
        }
      }
      index++;
    }
    return new Batch(taken, index, logs);
  }

  private static boolean reachable(LumberTrees.Tree tree, LumberRules.Plan plan, int cap) {
    return tree.logs() <= cap && (!tree.nether() || plan.nether()) && (!tree.mega() || plan.mega());
  }

  private static boolean at(LumberTrees.Part part, int x, int y, int z) {
    return part.x() == x && part.y() == y && part.z() == z;
  }

  private record Batch(List<LumberTrees.Part> parts, int next, int logs) {
  }
}
