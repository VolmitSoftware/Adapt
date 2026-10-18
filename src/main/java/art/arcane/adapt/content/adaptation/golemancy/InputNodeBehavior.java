package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemActor;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

final class InputNodeBehavior implements NodeBehavior {
  private static final int LID_TICKS = GolemActor.DEFAULT_ACT_INTERVAL_TICKS + 10;
  private static final double CENTER = 0.5D;

  private final GolemSorterCore core;
  private final SorterNetwork network;
  private final SorterPlans plans;
  private final SorterFetches fetches;
  private final SorterIndex index;
  private final Function<UUID, Player> players;

  InputNodeBehavior(GolemSorterCore core, SorterNetwork network, SorterPlans plans, SorterFetches fetches, SorterIndex index,
                    Function<UUID, Player> players) {
    this.core = Objects.requireNonNull(core);
    this.network = Objects.requireNonNull(network);
    this.plans = Objects.requireNonNull(plans);
    this.fetches = Objects.requireNonNull(fetches);
    this.index = Objects.requireNonNull(index);
    this.players = Objects.requireNonNull(players);
  }

  @Override
  public Adaptation<?> policy() {
    return core;
  }

  @Override
  public boolean deliversBeforeClaiming() {
    return true;
  }

  @Override
  public void scan(NodeContext node, ScanBudget budget) {
    int level = core.ownerLevel(node.owner());
    Optional<SorterSurvey> survey = level < 1 ? Optional.empty()
        : network.survey(SorterAccess.of(node), node.world(), node.key(), node.record(), level, node.nowMillis());
    List<GolemTask> wanted = survey.map(found -> wanted(node, found, level)).orElse(List.of());
    for (GolemTask open : node.tasks()) {
      if (!listed(wanted, open)) {
        node.withdraw(open);
      }
    }
    for (GolemTask task : wanted) {
      if (budget.hasTaskRoom()) {
        node.post(task);
      } else if (!SorterRules.SORT.equals(task.type()) && !listed(node.tasks(), task) && evictSort(node, survey.orElseThrow())) {
        node.post(task);
      }
    }
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    int level = level(golem);
    NodeRecord record = golem.node().orElse(null);
    NodeKey key = task.node().orElse(null);
    if (level < 1 || record == null || key == null) {
      return GolemActResult.FAILED;
    }
    return switch (task.type()) {
      case SorterRules.SORT, SorterRules.RESTOCK, SorterRules.FETCH -> take(golem, task, key, record, level);
      case SorterRules.DROP, SorterRules.STOCK, SorterRules.RETURN -> leave(golem, task, key, record, level);
      case SorterRules.HAND -> hand(golem);
      default -> GolemActResult.FAILED;
    };
  }

  @Override
  public Optional<GolemTask> route(GolemContext golem, NodeKey node, List<ItemStack> carried) {
    int level = level(golem);
    NodeRecord record = golem.node().orElse(null);
    if (level < 1 || record == null || carried.isEmpty()) {
      return Optional.empty();
    }
    SorterSurvey survey = network.survey(SorterAccess.of(golem), golem.world(), node, record, level, golem.nowMillis()).orElse(null);
    if (survey == null) {
      return Optional.empty();
    }
    UUID golemId = golem.handle().id();
    Optional<SorterPlans.Plan> plan = plans.get(golemId, golem.nowMillis());
    if (plan.isPresent()) {
      Optional<GolemTask> planned = planned(golem, node, survey, plan.get(), carried);
      if (planned.isPresent()) {
        return planned;
      }
      plans.forget(golemId);
    }
    Optional<GolemTask> drop = drop(golem, node, survey, carried);
    return drop.isPresent() ? drop : giveBack(golem, node, survey, carried);
  }

  private List<GolemTask> wanted(NodeContext node, SorterSurvey survey, int level) {
    List<GolemTask> wanted = new ArrayList<>();
    if (SorterRules.unlocked(level, core.config().fetchLevel)) {
      for (SorterFetches.Request request : fetches.open(node.key(), node.nowMillis())) {
        ItemStack item = request.item();
        survey.fetchSource(item).ifPresent(source -> wanted.add(node.task(SorterRules.FETCH, GolemTarget.block(source.block()))
            .item(item).build()));
      }
    }
    for (SorterSurvey.RestockNeed need : survey.restockNeeds()) {
      if (deficit(need, survey, node.nowMillis()) > 0) {
        survey.restockSource(need).ifPresent(source -> wanted.add(node.task(SorterRules.RESTOCK, GolemTarget.block(source.block()))
            .item(need.item()).build()));
      }
    }
    GolemTarget input = GolemTarget.block(survey.input().block());
    for (SorterStock.Entry entry : survey.input().stock().entries()) {
      if (survey.choose(entry.prototype()).isPresent()) {
        wanted.add(node.task(SorterRules.SORT, input).item(entry.prototype()).build());
      }
    }
    return wanted;
  }

  private int deficit(SorterSurvey.RestockNeed need, SorterSurvey survey, long nowMillis) {
    ItemStack item = need.item();
    return need.deficit() - plans.inbound(need.target().id(), planned -> survey.matcher().same(item, planned), nowMillis);
  }

  private boolean evictSort(NodeContext node, SorterSurvey survey) {
    GolemTask cheapest = null;
    int cheapestCount = Integer.MAX_VALUE;
    for (GolemTask open : node.tasks()) {
      ItemStack item = open.item().orElse(null);
      if (!SorterRules.SORT.equals(open.type()) || item == null || open.holder(node.nowMillis()).isPresent()) {
        continue;
      }
      int count = survey.input().stock().count(held -> held.isSimilar(item));
      if (count < cheapestCount) {
        cheapest = open;
        cheapestCount = count;
      }
    }
    return cheapest != null && node.withdraw(cheapest);
  }

  private GolemActResult take(GolemContext golem, GolemTask task, NodeKey key, NodeRecord record, int level) {
    Block block = block(golem, task);
    Optional<GolemActResult> problem = problem(golem, block);
    if (problem.isPresent()) {
      return problem.get();
    }
    int allowed = SorterRules.stacksPerTrip(core.config().stacksPerTripByLevel, level) - golem.carriedItems().size();
    if (allowed <= 0) {
      return GolemActResult.FAILED;
    }
    if (golem.actIndex() == 0) {
      golem.interact(block, LID_TICKS);
      return GolemActResult.WORKING;
    }
    Inventory inventory = golem.container(block).orElse(null);
    SorterSurvey survey = network.survey(SorterAccess.of(golem), golem.world(), key, record, level, golem.nowMillis()).orElse(null);
    if (inventory == null || survey == null) {
      return GolemActResult.FAILED;
    }
    ContainerId id = ContainerId.of(block, inventory);
    Withdrawal moved = switch (task.type()) {
      case SorterRules.SORT -> sortFrom(golem, task, inventory, survey, allowed);
      case SorterRules.RESTOCK -> restockFrom(golem, task, inventory, id, survey, allowed);
      default -> fetchFrom(golem, task, key, inventory, allowed);
    };
    index.invalidate(id);
    if (moved.items() <= 0) {
      return GolemActResult.FAILED;
    }
    SorterFeedback.play(core, center(block), SorterFeedback.Kind.TAKE);
    return GolemActResult.DONE;
  }

  private Withdrawal sortFrom(GolemContext golem, GolemTask task, Inventory inventory, SorterSurvey survey, int allowed) {
    ItemStack wanted = task.item().orElse(null);
    Predicate<ItemStack> placeable = item -> survey.choose(item).filter(destination -> reachable(golem, destination)).isPresent();
    Withdrawal first = wanted == null ? Withdrawal.NONE
        : withdraw(golem, inventory, item -> item.isSimilar(wanted) && placeable.test(item), allowed, Integer.MAX_VALUE);
    Withdrawal rest = withdraw(golem, inventory, placeable, allowed - first.stacks(), Integer.MAX_VALUE);
    return first.plus(rest);
  }

  private Withdrawal restockFrom(GolemContext golem, GolemTask task, Inventory inventory, ContainerId source, SorterSurvey survey, int allowed) {
    ItemStack wanted = task.item().orElse(null);
    if (wanted == null) {
      return Withdrawal.NONE;
    }
    SorterMatcher matcher = survey.matcher();
    SorterSurvey.RestockNeed need = null;
    for (SorterSurvey.RestockNeed candidate : survey.restockNeeds()) {
      if (matcher.same(candidate.item(), wanted) && !candidate.target().id().equals(source) && reachable(golem, candidate.target())) {
        need = candidate;
        break;
      }
    }
    int deficit = need == null ? 0 : deficit(need, survey, golem.nowMillis());
    if (deficit <= 0) {
      return Withdrawal.NONE;
    }
    Withdrawal moved = withdraw(golem, inventory, item -> matcher.same(wanted, item), allowed, deficit);
    if (moved.items() > 0) {
      plans.restock(golem.handle().id(), need.target().id(), wanted, moved.items(), golem.nowMillis());
    }
    return moved;
  }

  private Withdrawal fetchFrom(GolemContext golem, GolemTask task, NodeKey key, Inventory inventory, int allowed) {
    ItemStack wanted = task.item().orElse(null);
    SorterFetches.Request request = wanted == null ? null : fetches.next(key, wanted, golem.nowMillis()).orElse(null);
    if (request == null) {
      return Withdrawal.NONE;
    }
    int reserved = request.reserve(golem.room());
    if (reserved <= 0) {
      return Withdrawal.NONE;
    }
    Withdrawal moved = withdraw(golem, inventory, item -> item.isSimilar(wanted), allowed, reserved);
    request.release(reserved - moved.items());
    if (moved.items() > 0) {
      plans.fetch(golem.handle().id(), request, wanted, moved.items(), golem.nowMillis());
    }
    return moved;
  }

  private GolemActResult leave(GolemContext golem, GolemTask task, NodeKey key, NodeRecord record, int level) {
    Block block = block(golem, task);
    Optional<GolemActResult> problem = problem(golem, block);
    if (problem.isPresent()) {
      return problem.get();
    }
    if (golem.actIndex() == 0) {
      golem.interact(block, LID_TICKS);
      return GolemActResult.WORKING;
    }
    Inventory inventory = golem.container(block).orElse(null);
    SorterSurvey survey = network.survey(SorterAccess.of(golem), golem.world(), key, record, level, golem.nowMillis()).orElse(null);
    if (inventory == null || survey == null) {
      return GolemActResult.FAILED;
    }
    ContainerId id = ContainerId.of(block, inventory);
    GolemActResult result = switch (task.type()) {
      case SorterRules.DROP -> dropInto(golem, block, inventory, id, survey);
      case SorterRules.STOCK -> stockInto(golem, block, inventory, id, survey);
      default -> returnInto(golem, block, inventory, id, survey);
    };
    index.invalidate(id);
    return result;
  }

  private GolemActResult dropInto(GolemContext golem, Block block, Inventory inventory, ContainerId id, SorterSurvey survey) {
    SorterDestination destination = survey.destination(id).filter(found -> found.kind() != SorterDestination.Kind.INPUT).orElse(null);
    if (destination == null) {
      return GolemActResult.FAILED;
    }
    Caps caps = new Caps(survey, destination);
    int[] stacks = new int[1];
    int moved = golem.deliverUpTo(inventory, item -> {
      boolean belongs = survey.choose(item).map(best -> best.id().equals(id)).orElse(false);
      int amount = belongs ? caps.take(item) : 0;
      if (amount > 0) {
        stacks[0]++;
      }
      return amount;
    });
    return delivered(golem, block, moved, stacks[0]);
  }

  private GolemActResult stockInto(GolemContext golem, Block block, Inventory inventory, ContainerId id, SorterSurvey survey) {
    UUID golemId = golem.handle().id();
    SorterPlans.Plan plan = plans.get(golemId, golem.nowMillis()).orElse(null);
    plans.forget(golemId);
    SorterDestination destination = survey.destination(id).orElse(null);
    if (!(plan instanceof SorterPlans.Restock restock) || !restock.target().equals(id) || destination == null
        || SorterRules.nests(destination, restock.item())) {
      return GolemActResult.DONE;
    }
    ItemStack wanted = restock.item();
    SorterMatcher matcher = survey.matcher();
    int[] left = {survey.cap(destination, wanted)};
    if (left[0] == Integer.MAX_VALUE) {
      return GolemActResult.DONE;
    }
    int[] stacks = new int[1];
    int moved = golem.deliverUpTo(inventory, item -> {
      if (!matcher.same(wanted, item) || left[0] <= 0) {
        return 0;
      }
      int amount = Math.min(item.getAmount(), left[0]);
      left[0] -= amount;
      stacks[0]++;
      return amount;
    });
    return moved > 0 ? delivered(golem, block, moved, stacks[0]) : GolemActResult.DONE;
  }

  private GolemActResult returnInto(GolemContext golem, Block block, Inventory inventory, ContainerId id, SorterSurvey survey) {
    if (!id.equals(survey.input().id())) {
      return GolemActResult.FAILED;
    }
    plans.forget(golem.handle().id());
    SorterDestination input = survey.input();
    int moved = golem.deliverUpTo(inventory, item -> SorterRules.nests(input, item) ? 0 : item.getAmount());
    if (moved <= 0) {
      return GolemActResult.FAILED;
    }
    SorterFeedback.play(core, center(block), SorterFeedback.Kind.DROP);
    return GolemActResult.DONE;
  }

  private GolemActResult delivered(GolemContext golem, Block block, int moved, int stacks) {
    if (moved <= 0) {
      return GolemActResult.FAILED;
    }
    golem.workXp(stacks * core.config().workXpPerStack);
    SorterFeedback.play(core, center(block), SorterFeedback.Kind.DROP);
    return GolemActResult.DONE;
  }

  private GolemActResult hand(GolemContext golem) {
    UUID golemId = golem.handle().id();
    SorterPlans.Plan plan = plans.get(golemId, golem.nowMillis()).orElse(null);
    if (!(plan instanceof SorterPlans.Fetch fetch)) {
      return GolemActResult.DONE;
    }
    UUID playerId = fetch.request().player();
    Player player = golem.targetEntity().filter(Player.class::isInstance).map(Player.class::cast)
        .filter(found -> found.getUniqueId().equals(playerId)).orElse(null);
    if (player == null) {
      if (reachableLater(golem, playerId)) {
        return GolemActResult.WORKING;
      }
      plans.forget(golemId);
      return GolemActResult.DONE;
    }
    ItemStack item = fetch.item();
    int taken = golem.take(held -> held.isSimilar(item), fetch.amount());
    plans.finish(golemId, taken);
    if (taken <= 0) {
      return GolemActResult.DONE;
    }
    give(player, item, taken);
    int stackSize = Math.max(1, item.getMaxStackSize());
    golem.workXp((taken + stackSize - 1) / stackSize * core.config().workXpPerStack);
    SorterFeedback.delivered(core, player, item, taken);
    return GolemActResult.DONE;
  }

  private boolean reachableLater(GolemContext golem, UUID playerId) {
    Player online = players.apply(playerId);
    return online != null && online.isOnline() && golem.ownedEntity(playerId).isEmpty();
  }

  private Optional<GolemTask> planned(GolemContext golem, NodeKey node, SorterSurvey survey, SorterPlans.Plan plan, List<ItemStack> carried) {
    return switch (plan) {
      case SorterPlans.Fetch fetch -> handTask(golem, node, fetch, carried);
      case SorterPlans.Restock restock -> stockTask(golem, node, survey, restock, carried);
    };
  }

  private Optional<GolemTask> handTask(GolemContext golem, NodeKey node, SorterPlans.Fetch fetch, List<ItemStack> carried) {
    UUID playerId = fetch.request().player();
    Player online = players.apply(playerId);
    ItemStack item = fetch.item();
    if (online == null || !online.isOnline() || !holds(carried, item::isSimilar)) {
      return Optional.empty();
    }
    Entity owned = golem.ownedEntity(playerId).orElse(null);
    if (owned == null) {
      Location here = golem.golem().getLocation();
      GolemTarget waiting = new GolemTarget(GolemTarget.Kind.ENTITY, golem.world().getUID(), here.getX(), here.getY(), here.getZ(), playerId);
      return unskipped(golem, GolemTask.builder(node, NodeType.INPUT, SorterRules.HAND, waiting).item(item).build());
    }
    if (!golem.inWorkRadius(owned.getLocation())) {
      return Optional.empty();
    }
    return unskipped(golem, GolemTask.builder(node, NodeType.INPUT, SorterRules.HAND, GolemTarget.entity(owned)).item(item).build());
  }

  private Optional<GolemTask> stockTask(GolemContext golem, NodeKey node, SorterSurvey survey, SorterPlans.Restock restock, List<ItemStack> carried) {
    ItemStack item = restock.item();
    SorterDestination destination = survey.destination(restock.target()).orElse(null);
    if (destination == null || SorterRules.nests(destination, item) || !holds(carried, held -> survey.matcher().same(item, held))
        || survey.cap(destination, item) <= 0 || destination.stock().room(item) <= 0) {
      return Optional.empty();
    }
    return unskipped(golem, GolemTask.builder(node, NodeType.INPUT, SorterRules.STOCK, GolemTarget.block(destination.block())).item(item).build());
  }

  private Optional<GolemTask> drop(GolemContext golem, NodeKey node, SorterSurvey survey, List<ItemStack> carried) {
    Location here = golem.golem().getLocation();
    List<SorterDestination> stops = new ArrayList<>();
    for (ItemStack item : carried) {
      survey.choose(item).filter(found -> !stops.contains(found) && reachable(golem, found)).ifPresent(stops::add);
    }
    stops.sort(Comparator.comparingDouble(stop -> stop.id().distanceSquared(here.getX(), here.getY(), here.getZ())));
    for (SorterDestination stop : stops) {
      Optional<GolemTask> task = unskipped(golem, GolemTask.builder(node, NodeType.INPUT, SorterRules.DROP, GolemTarget.block(stop.block())).build());
      if (task.isPresent()) {
        return task;
      }
    }
    return Optional.empty();
  }

  private Optional<GolemTask> giveBack(GolemContext golem, NodeKey node, SorterSurvey survey, List<ItemStack> carried) {
    SorterDestination input = survey.input();
    if (!holds(carried, item -> !SorterRules.nests(input, item) && input.stock().room(item) > 0)) {
      return Optional.empty();
    }
    return unskipped(golem, GolemTask.builder(node, NodeType.INPUT, SorterRules.RETURN, GolemTarget.block(input.block())).build());
  }

  private int level(GolemContext golem) {
    Player owner = golem.owner();
    return owner == null ? 0 : core.ownerLevel(owner.getUniqueId());
  }

  private static boolean reachable(GolemContext golem, SorterDestination destination) {
    return golem.inWorkRadius(center(destination.block()));
  }

  private static Optional<GolemTask> unskipped(GolemContext golem, GolemTask task) {
    return golem.skips(task) ? Optional.empty() : Optional.of(task);
  }

  private static Optional<GolemActResult> problem(GolemContext golem, Block block) {
    Location at = block.getLocation();
    if (!golem.owns(at)) {
      return Optional.of(GolemActResult.FAILED);
    }
    if (!golem.canAccessChest(at)) {
      return Optional.of(GolemActResult.BLOCKED);
    }
    return golem.container(block).isPresent() ? Optional.empty() : Optional.of(GolemActResult.FAILED);
  }

  private static Withdrawal withdraw(GolemContext golem, Inventory inventory, Predicate<ItemStack> wants, int maxStacks, int maxItems) {
    ItemStack[] contents = inventory.getStorageContents();
    int items = 0;
    int stacks = 0;
    for (int slot = 0; slot < contents.length && stacks < maxStacks && items < maxItems; slot++) {
      ItemStack held = contents[slot];
      if (held == null || held.getType() == Material.AIR || held.getAmount() <= 0 || !wants.test(held)) {
        continue;
      }
      int room = golem.room();
      if (room <= 0) {
        break;
      }
      int taking = Math.min(held.getAmount(), Math.min(room, maxItems - items));
      ItemStack original = held.clone();
      ItemStack moving = held.clone();
      moving.setAmount(taking);
      inventory.setItem(slot, remainder(held, taking));
      int left;
      try {
        left = golem.carry(moving).map(ItemStack::getAmount).orElse(0);
      } catch (RuntimeException failure) {
        inventory.setItem(slot, original);
        throw failure;
      }
      if (left > 0) {
        inventory.setItem(slot, remainder(held, taking - left));
      }
      if (taking > left) {
        items += taking - left;
        stacks++;
      }
    }
    return new Withdrawal(items, stacks);
  }

  private static ItemStack remainder(ItemStack held, int taken) {
    int rest = held.getAmount() - taken;
    if (rest <= 0) {
      return null;
    }
    ItemStack kept = held.clone();
    kept.setAmount(rest);
    return kept;
  }

  private static void give(Player player, ItemStack item, int amount) {
    int stackSize = Math.max(1, item.getMaxStackSize());
    List<ItemStack> stacks = new ArrayList<>(amount / stackSize + 1);
    int left = amount;
    while (left > 0) {
      ItemStack part = item.clone();
      part.setAmount(Math.min(left, stackSize));
      stacks.add(part);
      left -= part.getAmount();
    }
    for (ItemStack leftover : player.getInventory().addItem(stacks.toArray(new ItemStack[0])).values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
  }

  private static boolean holds(List<ItemStack> carried, Predicate<ItemStack> matches) {
    for (ItemStack item : carried) {
      if (matches.test(item)) {
        return true;
      }
    }
    return false;
  }

  private static boolean listed(List<GolemTask> wanted, GolemTask open) {
    for (GolemTask task : wanted) {
      if (task.type().equals(open.type()) && sameBlock(task.target(), open.target()) && sameItem(task, open)) {
        return true;
      }
    }
    return false;
  }

  private static boolean sameBlock(GolemTarget first, GolemTarget second) {
    return first.blockX() == second.blockX() && first.blockY() == second.blockY() && first.blockZ() == second.blockZ();
  }

  private static boolean sameItem(GolemTask first, GolemTask second) {
    ItemStack item = first.item().orElse(null);
    ItemStack other = second.item().orElse(null);
    return item == null ? other == null : other != null && item.isSimilar(other);
  }

  private static Block block(GolemContext golem, GolemTask task) {
    GolemTarget target = task.target();
    return golem.world().getBlockAt(target.blockX(), target.blockY(), target.blockZ());
  }

  private static Location center(Block block) {
    return block.getLocation().add(CENTER, CENTER, CENTER);
  }

  private record Withdrawal(int items, int stacks) {
    private static final Withdrawal NONE = new Withdrawal(0, 0);

    private Withdrawal plus(Withdrawal other) {
      return new Withdrawal(items + other.items, stacks + other.stacks);
    }
  }

  private static final class Caps {
    private final SorterSurvey survey;
    private final SorterDestination destination;
    private final List<ItemStack> kinds = new ArrayList<>();
    private final List<int[]> left = new ArrayList<>();

    private Caps(SorterSurvey survey, SorterDestination destination) {
      this.survey = survey;
      this.destination = destination;
    }

    private int take(ItemStack item) {
      int index = -1;
      for (int candidate = 0; candidate < kinds.size(); candidate++) {
        if (survey.matcher().same(kinds.get(candidate), item)) {
          index = candidate;
          break;
        }
      }
      if (index < 0) {
        kinds.add(item);
        left.add(new int[]{survey.cap(destination, item)});
        index = kinds.size() - 1;
      }
      int[] remaining = left.get(index);
      int amount = Math.min(item.getAmount(), remaining[0]);
      remaining[0] -= amount;
      return amount;
    }
  }
}
