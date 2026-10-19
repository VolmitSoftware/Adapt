package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeStatus;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;

final class FishingNodeBehavior implements NodeBehavior {
  private static final double CENTER = 0.5D;
  private static final double SURFACE = 1.0D;

  private final GolemAnglerCore core;
  private final AnglerWaters waters;
  private final DoubleSupplier random;

  FishingNodeBehavior(GolemAnglerCore core, AnglerWaters waters, DoubleSupplier random) {
    this.core = Objects.requireNonNull(core);
    this.waters = Objects.requireNonNull(waters);
    this.random = Objects.requireNonNull(random);
  }

  @Override
  public Adaptation<?> policy() {
    return core;
  }

  @Override
  public void scan(NodeContext node, ScanBudget budget) {
    if (core.ownerLevel(node.owner()) < 1) {
      return;
    }
    NodeKey key = node.key();
    long now = node.nowMillis();
    if (!waters.survey(key, now, () -> survey(node)).open()) {
      for (GolemTask task : node.tasks()) {
        node.withdraw(task);
      }
      node.status(NodeStatus.BLOCKED);
      return;
    }
    GolemAnglerCore.Config config = core.config();
    int seats = AnglerRules.seats(config.maxAnglersPerChunk);
    boolean[] taken = takenSeats(node, key, seats);
    List<GolemTask> chunk = chunkTasks(key);
    int cap = AnglerRules.atLeastOne(config.maxAnglersPerChunk);
    int claimed = AnglerRules.fishers(chunk, null, now) + AnglerRules.openSeats(chunk, now);
    if (keepOneWaiting(node, now, claimed <= cap) || claimed >= cap || !budget.hasTaskRoom()) {
      return;
    }
    for (int seat = 0; seat < seats; seat++) {
      if (!taken[seat]) {
        int[] at = AnglerWater.seat(key, seat);
        node.post(node.task(AnglerRules.FISH, GolemTarget.block(key.world(), at[0], at[1], at[2])).build());
        return;
      }
    }
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    int level = GolemancySupport.ownerLevel(core, golem);
    NodeRecord record = golem.node().orElse(null);
    NodeKey key = task.node().orElse(null);
    if (level < 1 || record == null || key == null || !AnglerRules.FISH.equals(task.type())) {
      return GolemActResult.FAILED;
    }
    GolemAnglerCore.Config config = core.config();
    AnglerWater.Survey water = waters.get(key).orElse(null);
    int seat = AnglerRules.seatOf(task, key, AnglerRules.seats(config.maxAnglersPerChunk));
    if (water == null || !water.open() || seat < 0 || (golem.actIndex() == 0 && chunkFull(golem, key, config))) {
      return GolemActResult.FAILED;
    }
    World world = golem.world();
    GolemTarget target = task.target();
    if (!golem.canInteract(new Location(world, target.blockX(), target.blockY(), target.blockZ()))) {
      return GolemActResult.BLOCKED;
    }
    Location splash = splash(golem, key, water, seat, target);
    if (golem.actIndex() == 0) {
      AnglerFeedback.play(core, splash, AnglerFeedback.Kind.CAST);
      return GolemActResult.WORKING;
    }
    int caught = reel(golem, config, level, AnglerRules.discardJunk(config, level, record));
    AnglerFeedback.play(core, splash, AnglerFeedback.Kind.CATCH);
    if (caught > 0) {
      golem.workXp(config.workXpPerCatch * caught);
    }
    return golem.room() <= 0 ? GolemActResult.DONE : GolemActResult.WORKING;
  }

  @Override
  public int actIntervalTicks(GolemContext golem, GolemTask task) {
    GolemAnglerCore.Config config = core.config();
    int level = GolemancySupport.ownerLevel(core, golem);
    boolean rain = GolemancyRules.unlocked(level, config.rainLevel)
        && task.node().flatMap(waters::get).map(AnglerWater.Survey::raining).orElse(false);
    return AnglerRules.intervalTicks(AnglerRules.catchSeconds(config.catchIntervalSecondsByLevel, level), rain ? config.rainIntervalMultiplier : 1D);
  }

  private int reel(GolemContext golem, GolemAnglerCore.Config config, int level, boolean discardJunk) {
    int catches = GolemancyRules.unlocked(level, config.doubleCatchLevel) && random.getAsDouble() < config.doubleCatchChance ? 2 : 1;
    AnglerLoot.Tables tables = core.loot();
    AnglerLoot.Weights weights = AnglerRules.weights(config);
    int luck = AnglerRules.luck(config.luckByLevel, level);
    int caught = 0;
    for (int index = 0; index < catches; index++) {
      Optional<AnglerLoot.Catch> roll = AnglerLoot.roll(tables, weights, luck, random);
      if (roll.isEmpty()) {
        continue;
      }
      caught++;
      if (discardJunk && roll.get().category() == AnglerLoot.Category.JUNK) {
        continue;
      }
      for (ItemStack item : golem.withBonusYield(List.of(core.item(roll.get())))) {
        golem.carry(item).ifPresent(left -> golem.world().dropItemNaturally(golem.golem().getLocation(), left));
      }
    }
    return caught;
  }

  private boolean[] takenSeats(NodeContext node, NodeKey key, int seats) {
    boolean[] taken = new boolean[seats];
    for (GolemTask task : node.tasks()) {
      int seat = AnglerRules.FISH.equals(task.type()) ? AnglerRules.seatOf(task, key, seats) : -1;
      if (seat < 0) {
        node.withdraw(task);
        continue;
      }
      taken[seat] = true;
    }
    return taken;
  }

  private static boolean chunkFull(GolemContext golem, NodeKey key, GolemAnglerCore.Config config) {
    return AnglerRules.fishers(chunkTasks(key), golem.handle().id(), golem.nowMillis()) >= AnglerRules.atLeastOne(config.maxAnglersPerChunk);
  }

  private boolean keepOneWaiting(NodeContext node, long nowMillis, boolean room) {
    boolean waiting = false;
    for (GolemTask task : node.tasks()) {
      if (task.holder(nowMillis).isPresent()) {
        continue;
      }
      if (!room || waiting) {
        node.withdraw(task);
        continue;
      }
      waiting = true;
    }
    return waiting;
  }

  private AnglerWater.Survey survey(NodeContext node) {
    World world = node.world();
    AnglerWater.Survey survey = AnglerWater.survey(new WorldWaterView(world, node::owns), node.key());
    if (!survey.open() || !world.hasStorm()) {
      return survey;
    }
    int highest = world.getHighestBlockYAt(survey.x(), survey.z(), HeightMap.MOTION_BLOCKING);
    double temperature = world.getBlockAt(survey.x(), survey.surface() + 1, survey.z()).getTemperature();
    return survey.raining(AnglerWater.raining(true, highest, survey.surface(), temperature));
  }

  private Location splash(GolemContext golem, NodeKey key, AnglerWater.Survey water, int seat, GolemTarget target) {
    int[] at = AnglerWater.bobber(key, water, seat);
    Location bobber = new Location(golem.world(), at[0] + CENTER, at[1] + SURFACE, at[2] + CENTER);
    return golem.owns(bobber) ? bobber : new Location(golem.world(), target.blockX() + CENTER, target.blockY() + SURFACE, target.blockZ() + CENTER);
  }

  private static List<GolemTask> chunkTasks(NodeKey key) {
    return GolemancyRuntime.boards().map(boards -> boards.board(key.world()).chunkTasks(key.chunkKey())).orElse(List.of());
  }
}
