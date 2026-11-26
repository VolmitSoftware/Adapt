package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreBehavior;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemWorkStatus;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class HarvesterCoreBehavior implements GolemCoreBehavior {
  private static final Id BONE_MEAL = Id.minecraft("bone_meal");

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
    int level = GolemancySupport.ownerLevel(core, golem);
    GolemHarvesterCore.Config config = core.config();
    List<ItemRef> carried = golem.carriedItems();
    UUID owner = golem.handle().owner();
    boolean planting = GolemancyRules.unlocked(level, config.tillingLevel) && core.tills(owner);
    boolean tending = GolemancyRules.unlocked(level, config.tendingLevel) && core.tendReady(golem.handle().id(), golem.nowMillis())
        && core.tends(owner);
    if (!planting && !tending) {
      return Optional.empty();
    }
    WorldRef world = golem.world();
    List<ItemRef> stock = planting ? actions.seedStock(golem) : List.of();
    List<Candidate> candidates = candidates(golem, world, level, planting, tending, carried, stock);
    boolean boneMeal = false;
    boolean chestChecked = false;
    for (Candidate candidate : candidates) {
      HarvestSpots.Mark mark = candidate.mark();
      boolean tend = mark.kind() == HarvesterRules.Spot.TEND;
      if (tend && !chestChecked) {
        chestChecked = true;
        boneMeal = boneMealSlot(GolemancySupport.homeChest(golem).orElse(null)) >= 0;
      }
      if ((tend && !boneMeal) || !golem.inWorkRadius(new Loc(world, mark.x() + 0.5D, mark.y() + 0.5D, mark.z() + 0.5D))) {
        continue;
      }
      String type = tend ? HarvesterRules.TEND : HarvesterRules.PLANT;
      GolemTask task = GolemTask.builder(candidate.node(), NodeType.HARVEST, type, GolemTarget.block(world.uid(), mark.x(), mark.y(), mark.z()))
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
    int level = GolemancySupport.ownerLevel(core, golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || !GolemancySupport.covered(record, task)) {
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
    BlockRef farmland = actions.target(golem, task);
    if (HarvesterRules.survey(farmland, plan, actions.owned(golem)) != HarvesterRules.Spot.PLANT) {
      return GolemActResult.FAILED;
    }
    if (!golem.canPlace(farmland.relative(BlockFace.UP).location())) {
      return GolemActResult.BLOCKED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    return actions.plant(golem, farmland, plan) ? GolemActResult.DONE : GolemActResult.FAILED;
  }

  private GolemActResult tend(GolemContext golem, GolemTask task, HarvesterRules.Plan plan) {
    BlockRef crop = actions.target(golem, task);
    if (HarvesterRules.survey(crop, plan, actions.owned(golem)) != HarvesterRules.Spot.TEND) {
      return GolemActResult.FAILED;
    }
    Loc at = crop.location();
    if (!golem.canInteract(at) || !golem.canPlace(at)) {
      return GolemActResult.BLOCKED;
    }
    InventoryRef chest = GolemancySupport.homeChest(golem).orElse(null);
    Loc chestAt = chestLocation(golem);
    int slot = boneMealSlot(chest);
    if (slot < 0) {
      return GolemActResult.FAILED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    ItemRef spent = spend(chest, slot);
    if (spent == null) {
      return GolemActResult.FAILED;
    }
    GolemancySupport.visitHomeChest(golem);
    boolean grown;
    try {
      grown = crop.applyBoneMeal(BlockFace.UP);
    } catch (RuntimeException failure) {
      refund(chest, chestAt, slot, spent);
      throw failure;
    }
    if (!grown) {
      refund(chest, chestAt, slot, spent);
      return GolemActResult.FAILED;
    }
    XpProvenance.recordBonemeal(crop);
    core.tended(golem.handle().id(), golem.nowMillis());
    HarvestFeedback.play(core, actions.center(crop), HarvestFeedback.Kind.TEND);
    return GolemActResult.DONE;
  }

  private List<Candidate> candidates(GolemContext golem, WorldRef world, int level, boolean planting, boolean tending, List<ItemRef> carried, List<ItemRef> stock) {
    Loc here = golem.golem().location();
    List<Candidate> candidates = new ArrayList<>();
    for (NodeIndex.Entry entry : GolemancySupport.ownedNodes(golem, world, NodeType.HARVEST)) {
      HarvesterRules.Plan plan = actions.plan(level, entry.record());
      boolean plants = planting && (plantable(plan, carried) || plantable(plan, stock));
      List<HarvestSpots.Mark> marks = spots.of(entry.key());
      if (planting && !plants && marks.stream().anyMatch(mark -> mark.kind() == HarvesterRules.Spot.PLANT)) {
        golem.waiting(GolemWorkStatus.NEEDS_SEEDS);
      }
      if (!plants && !tending) {
        continue;
      }
      for (HarvestSpots.Mark mark : marks) {
        boolean inArea = NodeArea.covers(entry.key(), entry.record(), mark.x() + 0.5D, mark.y() + 0.5D, mark.z() + 0.5D);
        if (inArea && (mark.kind() == HarvesterRules.Spot.PLANT ? plants : tending)) {
          candidates.add(new Candidate(entry.key(), mark, distanceSquared(here, mark)));
        }
      }
    }
    candidates.sort(Comparator.comparingDouble(Candidate::distanceSquared));
    return candidates;
  }

  private static boolean plantable(HarvesterRules.Plan plan, List<ItemRef> carried) {
    for (ItemRef item : carried) {
      Id crop = plan.crops().farmlandCrop(item.type());
      if (crop != null && HarvesterRules.wanted(plan, crop)) {
        return true;
      }
    }
    return false;
  }

  private static Loc chestLocation(GolemContext golem) {
    GolemAnchor anchor = golem.handle().state().homeChest();
    WorldRef world = golem.world();
    return anchor == null || !anchor.world().equals(world.uid()) ? null : new Loc(world, anchor.x(), anchor.y(), anchor.z());
  }

  private static int boneMealSlot(InventoryRef chest) {
    if (chest == null) {
      return -1;
    }
    List<ItemRef> contents = chest.contents();
    for (int slot = 0; slot < contents.size(); slot++) {
      ItemRef item = contents.get(slot);
      if (item != null && !item.isEmpty() && item.type().equals(BONE_MEAL) && item.amount() > 0) {
        return slot;
      }
    }
    return -1;
  }

  private static ItemRef spend(InventoryRef chest, int slot) {
    ItemRef held = chest.item(slot);
    if (held == null || held.isEmpty() || !held.type().equals(BONE_MEAL) || held.amount() <= 0) {
      return null;
    }
    ItemRef one = held.copyWithAmount(1);
    if (held.amount() == 1) {
      chest.clear(slot);
      return one;
    }
    chest.setItem(slot, held.copyWithAmount(held.amount() - 1));
    return one;
  }

  private static void refund(InventoryRef chest, Loc at, int slot, ItemRef one) {
    ItemRef held = chest.item(slot);
    if (held == null || held.isEmpty()) {
      chest.setItem(slot, one);
      return;
    }
    if (held.isSimilar(one) && held.amount() < held.maxStackSize()) {
      chest.setItem(slot, held.copyWithAmount(held.amount() + 1));
      return;
    }
    for (ItemRef left : chest.addItem(one)) {
      if (at != null) {
        at.world().dropItemNaturally(at.position(), left);
      }
    }
  }

  private static double distanceSquared(Loc here, HarvestSpots.Mark mark) {
    double dx = mark.x() + 0.5D - here.x();
    double dy = mark.y() + 0.5D - here.y();
    double dz = mark.z() + 0.5D - here.z();
    return dx * dx + dy * dy + dz * dz;
  }

  private record Candidate(NodeKey node, HarvestSpots.Mark mark, double distanceSquared) {
  }
}
