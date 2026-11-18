package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreBehavior;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class RancherBehavior implements NodeBehavior, GolemCoreBehavior {
  private final GolemRancherCore core;
  private final RancherActions actions;

  RancherBehavior(GolemRancherCore core, RancherActions actions) {
    this.core = Objects.requireNonNull(core);
    this.actions = Objects.requireNonNull(actions);
  }

  @Override
  public Adaptation<?> policy() {
    return core;
  }

  @Override
  public void scan(NodeContext node, ScanBudget budget) {
  }

  @Override
  public Optional<GolemTask> seek(GolemContext golem) {
    int level = GolemancySupport.ownerLevel(core, golem);
    if (level < 1) {
      return Optional.empty();
    }
    WorldRef world = golem.world();
    Loc here = golem.golem().location();
    List<NodeIndex.Entry> nodes = GolemancySupport.ownedNodes(golem, world, NodeType.PASTURE);
    nodes.sort(Comparator.<NodeIndex.Entry>comparingInt(entry -> entry.record().priority()).reversed()
        .thenComparingDouble(entry -> entry.key().distanceSquared(here.x(), here.y(), here.z())));
    RancherRules.Levels levels = actions.levels();
    RancherActions.Stock stock = actions.stock(golem);
    for (NodeIndex.Entry entry : nodes) {
      Set<RancherRules.Action> allowed = RancherRules.actions(entry.record(), level, golem.handle().body(), levels);
      if (allowed.isEmpty()) {
        continue;
      }
      Optional<GolemTask> found = seekAt(golem, world, entry, allowed, here, stock);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    RancherRules.Action action = RancherRules.Action.of(task.type()).orElse(null);
    NodeKey key = task.node().orElse(null);
    NodeRecord record = golem.node().orElse(null);
    int level = GolemancySupport.ownerLevel(core, golem);
    if (action == null || key == null || record == null || level < 1
        || !RancherRules.actions(record, level, golem.handle().body(), actions.levels()).contains(action)) {
      return GolemActResult.FAILED;
    }
    MobRef animal = golem.targetEntity().flatMap(RancherActions::animal).orElse(null);
    if (animal == null || !actions.tends(animal, key, record)) {
      return GolemActResult.FAILED;
    }
    UUID owner = golem.handle().owner();
    RancherRules.Beast beast = actions.beast(animal, animal.location(), owner, action == RancherRules.Action.CULL);
    if (!RancherRules.fits(action, beast, census(actions.herd(golem.world(), key, record), animal, owner), keep(record))) {
      return GolemActResult.FAILED;
    }
    if (!permitted(golem, action, animal)) {
      return GolemActResult.BLOCKED;
    }
    if (!stocked(golem, action, animal, beast.milk())) {
      return GolemActResult.FAILED;
    }
    if (golem.actIndex() == 0) {
      return GolemActResult.WORKING;
    }
    return switch (action) {
      case BREED -> breed(golem, animal, level);
      case SHEAR -> shear(golem, animal);
      case MILK -> milk(golem, animal, beast.milk());
      case CULL -> cull(golem, animal, level);
    };
  }

  @Override
  public int actIntervalTicks(GolemContext golem, GolemTask task) {
    return actions.intervalTicks();
  }

  private Optional<GolemTask> seekAt(GolemContext golem, WorldRef world, NodeIndex.Entry entry, Set<RancherRules.Action> allowed, Loc here,
                                     RancherActions.Stock stock) {
    NodeKey key = entry.key();
    NodeRecord record = entry.record();
    NodeService nodes = GolemancyRuntime.nodes().orElse(null);
    long now = golem.nowMillis();
    if (nodes != null && !nodes.queryDue(key, now)) {
      return Optional.empty();
    }
    List<MobRef> herd = actions.herd(world, key, record);
    if (herd.isEmpty()) {
      if (nodes != null && actions.surveyable(world, key, record)) {
        nodes.emptyQuery(key, record, now);
      }
      return Optional.empty();
    }
    UUID owner = golem.handle().owner();
    boolean cullFacts = allowed.contains(RancherRules.Action.CULL);
    Map<UUID, MobRef> byId = new HashMap<>(herd.size() * 2);
    List<RancherRules.Beast> beasts = new ArrayList<>(herd.size());
    for (MobRef animal : herd) {
      byId.put(animal.uuid(), animal);
      stock.sample(animal);
      beasts.add(actions.beast(animal, here, owner, cullFacts));
    }
    for (RancherRules.Choice choice : RancherRules.choose(beasts, keep(record), allowed, stock)) {
      MobRef animal = byId.get(choice.beast().id());
      if (!golem.inWorkRadius(animal.location())) {
        continue;
      }
      GolemTask task = GolemTask.builder(key, NodeType.PASTURE, choice.action().id(), GolemTarget.entity(animal)).build();
      if (!golem.skips(task)) {
        return Optional.of(task);
      }
    }
    return Optional.empty();
  }

  private GolemActResult breed(GolemContext golem, MobRef animal, int level) {
    if (!actions.takeFood(golem, animal)) {
      return GolemActResult.FAILED;
    }
    GolemRancherCore.Config config = core.config();
    actions.feed(golem, animal, GolemancyRules.unlocked(level, config.husbandryLevel));
    RancherFeedback.play(core, animal.location(), RancherFeedback.Kind.BREED);
    golem.workXp(config.workXpPerPair / 2D);
    return GolemActResult.DONE;
  }

  private GolemActResult shear(GolemContext golem, MobRef animal) {
    if (!animal.type().equals(RancherActions.SHEEP)) {
      return GolemActResult.FAILED;
    }
    actions.shear(golem, animal);
    RancherFeedback.play(core, animal.location(), RancherFeedback.Kind.SHEAR);
    golem.workXp(core.config().workXpPerShear);
    return GolemActResult.DONE;
  }

  private GolemActResult milk(GolemContext golem, MobRef animal, RancherRules.Milk milk) {
    Id container = actions.takeContainer(golem, milk).orElse(null);
    if (container == null) {
      return GolemActResult.FAILED;
    }
    actions.fill(golem, animal, container);
    RancherFeedback.Kind kind = container.equals(RancherActions.BOWL) ? RancherFeedback.Kind.STEW
        : animal.type().equals(RancherActions.GOAT) ? RancherFeedback.Kind.GOAT_MILK : RancherFeedback.Kind.MILK;
    RancherFeedback.play(core, animal.location(), kind);
    golem.workXp(core.config().workXpPerMilk);
    return GolemActResult.DONE;
  }

  private GolemActResult cull(GolemContext golem, MobRef animal, int level) {
    GolemRancherCore.Config config = core.config();
    int extraRolls = GolemancyRules.unlocked(level, config.husbandryLevel) ? config.husbandryExtraLootRolls : 0;
    return switch (actions.cull(golem, animal, extraRolls)) {
      case KILLED -> {
        RancherFeedback.play(core, animal.location(), RancherFeedback.Kind.CULL);
        golem.workXp(config.workXpPerCull);
        yield GolemActResult.DONE;
      }
      case HURT -> GolemActResult.WORKING;
      case REFUSED -> GolemActResult.BLOCKED;
    };
  }

  private boolean stocked(GolemContext golem, RancherRules.Action action, MobRef animal, RancherRules.Milk milk) {
    return switch (action) {
      case BREED -> {
        RancherActions.Stock stock = actions.stock(golem);
        stock.sample(animal);
        yield stock.food(animal.type()) > 0;
      }
      case MILK -> {
        RancherActions.Stock stock = actions.stock(golem);
        yield stock.buckets() > 0 || (milk == RancherRules.Milk.BUCKET_OR_BOWL && stock.bowls() > 0);
      }
      case SHEAR, CULL -> true;
    };
  }

  private int keep(NodeRecord record) {
    return RancherRules.keep(record, core.config().defaultKeep);
  }

  private static boolean permitted(GolemContext golem, RancherRules.Action action, MobRef animal) {
    return action == RancherRules.Action.CULL ? golem.canDamage(animal) : golem.canHandle(animal);
  }

  private RancherRules.Census census(List<MobRef> herd, MobRef target, UUID owner) {
    List<RancherRules.Beast> kin = new ArrayList<>(herd.size());
    for (MobRef animal : herd) {
      if (animal.type().equals(target.type())) {
        kin.add(actions.beast(animal, animal.location(), owner, false));
      }
    }
    return RancherRules.census(kin);
  }
}
