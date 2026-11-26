package art.arcane.adapt.content.adaptation.golemancy.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.content.adaptation.discovery.KeenEyeScan;
import art.arcane.adapt.content.adaptation.golemancy.GolemLastJobs;
import art.arcane.adapt.content.adaptation.golemancy.OwnedGolems;
import art.arcane.adapt.content.adaptation.golemancy.TreeBlocks;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHarvest;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The nearest golem does one more of a job. Set off by mining, digging or harvesting, the job is the player's: the golem
 * takes the same block type next to where they worked and drops it where it stood. Set off by anything else, it is the
 * golem's own: it does the next open task like the one it finished last, whatever its kind. A golem that has finished
 * nothing since it loaded falls back to its core: a harvester reaps the next mature crop around it and a lumber golem
 * cuts the next log. Player-placed blocks and bone-mealed crops are never taken. The golem only takes a block within its
 * reach that it can see, and its drops roll with what the golem holds, never the player's tool.
 */
public class GolemancyWorkStrike extends StrikeBlock<GolemancyWorkStrike.Config> {
  private static final Set<String> PLAYER_JOBS = Set.of("pickaxe", "excavation", "herbalism");
  private static final Id AIR = Id.minecraft("air");

  public GolemancyWorkStrike() {
    super("block-golemancy-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_pickaxe"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    GolemHandle golem = OwnedGolems.nearest(player, player.location(), getConfig().golemRadius,
        (GolemHandle handle) -> true);
    if (golem == null) {
      return Outcome.MISSED;
    }
    Loc origin;
    Predicate<BlockRef> matches;
    if (PLAYER_JOBS.contains(use.initiator())) {
      origin = use.center();
      BlockRef worked = origin.world().blockAt(origin);
      matches = sameJob(worked);
    } else {
      GolemTask last = GolemLastJobs.last(golem);
      if (last != null) {
        return repeat(player, golem, last);
      }
      origin = golem.entity().location();
      matches = ownJob(golem.core());
    }
    if (matches == null) {
      return Outcome.MISSED;
    }
    double radius = use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
    MobRef body = golem.entity();
    BlockRef next = nearest(player, origin, radius, matches.and((BlockRef block) -> reaches(body, block)));
    if (next == null || !take(player, body, next)) {
      return Outcome.MISSED;
    }
    Tasks.runEntity(body, () -> body.swingHand(Hand.MAIN_HAND));
    return worked(player);
  }

  private Outcome repeat(PlayerRef player, GolemHandle golem, GolemTask last) {
    MobRef body = golem.entity();
    if (!Tasks.isOwnedByCurrentRegion(body)) {
      return Outcome.MISSED;
    }
    GolemBrain brain = GolemancyRuntime.brain(body).orElse(null);
    if (brain == null || !brain.repeat(last)) {
      return Outcome.MISSED;
    }
    body.swingHand(Hand.MAIN_HAND);
    return worked(player);
  }

  private Outcome worked(PlayerRef player) {
    addStat(player, "golemancy.mutated.jobs", 1);
    return Outcome.INSTANT;
  }

  private static @Nullable Predicate<BlockRef> sameJob(BlockRef worked) {
    Id type = worked.type();
    if (type.isAir()) {
      return null;
    }
    if (HerbalismGrowables.isCrop(type)) {
      return (BlockRef block) -> type.equals(block.type()) && !same(worked, block) && HerbalismHarvest.isMature(block)
          && !MutationPayouts.farmedHarvest(block);
    }
    return (BlockRef block) -> type.equals(block.type()) && !same(worked, block) && !MutationPayouts.farmed(block);
  }

  private static @Nullable Predicate<BlockRef> ownJob(GolemCoreType core) {
    return switch (core) {
      case HARVESTER -> (BlockRef block) -> HerbalismGrowables.isCrop(block.type()) && HerbalismHarvest.isMature(block)
          && !MutationPayouts.farmedHarvest(block);
      case LUMBER -> (BlockRef block) -> TreeBlocks.trunk(block.type(), true) && !MutationPayouts.farmed(block);
      default -> null;
    };
  }

  private static boolean same(BlockRef a, BlockRef b) {
    return a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
  }

  private @Nullable BlockRef nearest(PlayerRef player, Loc origin, double radius, Predicate<BlockRef> matches) {
    WorldRef world = origin.world();
    int reach = (int) Math.ceil(radius);
    BlockRef nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    int inspected = 0;
    for (int x = -reach; x <= reach; x++) {
      for (int y = -reach; y <= reach; y++) {
        for (int z = -reach; z <= reach && inspected < getConfig().blockBudget; z++) {
          double distance = (x * x) + (y * y) + (z * z);
          if (distance > radius * radius || distance >= nearestDistance) {
            continue;
          }
          inspected++;
          Loc at = new Loc(world, origin.blockX() + x, origin.blockY() + y, origin.blockZ() + z);
          if (!Tasks.isOwnedByCurrentRegion(at)) {
            continue;
          }
          BlockRef block = world.blockAt(at);
          if (matches.test(block) && canBlockBreak(player, at)) {
            nearest = block;
            nearestDistance = distance;
          }
        }
      }
    }
    return nearest;
  }

  /** The golem stands within reach of the block and sees it, so it never works through a wall. */
  private boolean reaches(MobRef body, BlockRef block) {
    Loc eye = body.eyeLocation();
    double reach = getConfig().golemReach;
    return eye.sameWorld(block.location()) && eye.distanceSquared(block.center()) <= reach * reach
        && KeenEyeScan.seesBlock(eye, block);
  }

  /** Breaks the block with what the golem holds, or bare-handed; the player's tool never rolls the drops. */
  private boolean take(PlayerRef player, MobRef body, BlockRef block) {
    if (!ProtectionProbes.attemptBlockBreakProbe(player, block)) {
      return false;
    }
    ItemRef held = body.equipment(EquipmentSlot.MAIN_HAND);
    List<ItemRef> drops = block.drops(held == null || held.isEmpty() ? null : held, null);
    block.setType(AIR, true);
    Loc center = block.center();
    for (ItemRef drop : drops) {
      if (isItem(drop) && drop.amount() > 0) {
        center.world().dropItemNaturally(center.position(), drop);
      }
    }
    fx(center, FxPriority.TRANSITION).particle(Particles.CRIT, 8, 0D, 0D, 0D, 0.3D, 0.05D);
    return true;
  }

  @ConfigDescription("Your nearest golem repeats the job you just did on the next matching block.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How far from you a golem can be to answer, in blocks.", impact = "Higher values call golems from farther away.")
    double golemRadius = 24D;
    @ConfigDoc(value = "How far from the job the next matching block may be at level 1, in blocks.", impact = "Higher values find work farther away.")
    double radiusAtFirstLevel = 6D;
    @ConfigDoc(value = "How far from the job the next matching block may be at the top level, in blocks.", impact = "Higher values find work farther away.")
    double radiusAtTopLevel = 10D;
    @ConfigDoc(value = "Most blocks one strike inspects.", impact = "Lower values cap the search of one strike.")
    int blockBudget = 4_096;
    @ConfigDoc(value = "How far from the golem's eyes the block may be, in blocks.", impact = "Higher values let a golem work blocks farther from where it stands.")
    double golemReach = 5D;
  }
}
