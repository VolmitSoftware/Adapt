package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthPulses;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneShrines;
import art.arcane.adapt.content.adaptation.seaborrne.upgraded.Leviathan;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Seaborne and Herbalism, either leading, replacing Convergence: while you swim, Wildgrowth's pulse grows the kelp, sea
 * pickles and coral fans around you, and a hoe on kelp or sea pickles harvests the sea bed as Harvest Moon sweeps a
 * field, leaving each kelp root and one pickle to grow back. Your Tide Shrines near you are fields too: the pulse
 * tends the sea bed around each of them, wet or not.
 */
public final class KelpGarden extends Signature<KelpGarden.Config> {
  private static final Id KELP = Id.minecraft("kelp");
  private static final Id KELP_PLANT = Id.minecraft("kelp_plant");
  private static final Id SEA_PICKLE = Id.minecraft("sea_pickle");
  private static final Id WATER = Id.minecraft("water");
  private static final int KELP_MAX_AGE = 25;
  private static final int MAX_PICKLES = 4;
  private static final int COLUMN_REACH = 3;

  public KelpGarden(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("kelp"));
    setInterval(1000);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.f(growthRadius(level), 0), 1);
    statLore(v, samples(level), 2);
    statLore(v, Format.f(fieldRadius(level), 0), 3);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().harvestCooldownMillis);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (PlayerProfile learner : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef p = learner.player();
      if (p != null) {
        withPlayerThread(p, () -> tend(p, learner));
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND || e.useBlock() == UseResult.DENY) {
      return;
    }
    BlockRef clicked = e.clickedBlock();
    PlayerRef p = e.player();
    if (clicked == null || !seaCrop(clicked.type()) || !ItemKinds.isHoe(p.inventory().mainHand()) || getActiveLevel(p) <= 0) {
      return;
    }
    fire(p, null, clicked.location(), false);
  }

  @Override
  public Outcome run(BlockUse use) {
    Loc at = use.at();
    PlayerRef p = use.player();
    if (at == null) {
      return Outcome.MISSED;
    }
    int radius = (int) Math.round(fieldRadius(use.level()));
    BuildBatch.Builder batch = BuildBatch.of(this, p).limit(getConfig().maxHarvestedColumns).pace(8);
    int columns = 0;
    int pickles = 0;
    BlockRef origin = at.world().blockAt(at);
    for (int dx = -radius; dx <= radius && columns < getConfig().maxHarvestedColumns; dx++) {
      for (int dz = -radius; dz <= radius && columns < getConfig().maxHarvestedColumns; dz++) {
        for (int dy = -COLUMN_REACH; dy <= COLUMN_REACH; dy++) {
          BlockRef block = origin.relative(dx, dy, dz);
          Id type = block.type();
          if (KELP_PLANT.equals(type) && !kelp(block.relative(0, -1, 0).type()) && kelp(block.relative(0, 1, 0).type())
              && canBlockBreak(p, block.location())) {
            batch.breakBlock(block.relative(0, 1, 0), 0);
            columns++;
            break;
          }
          if (SEA_PICKLE.equals(type) && canBlockBreak(p, block.location())
              && ProtectionProbes.attemptBlockBreakProbe(p, block)) {
            pickles += thin(block);
          }
        }
      }
    }
    if (columns <= 0 && pickles <= 0) {
      return Outcome.MISSED;
    }
    if (columns > 0) {
      BuildBatches.start(batch.build());
    }
    fx(at.add(0.5D, 0.5D, 0.5D), FxPriority.TRANSITION).ring(Particles.BUBBLE, radius, 24, 0D);
    return Outcome.INSTANT;
  }

  private void tend(PlayerRef p, PlayerProfile learner) {
    int level = getActiveLevel(p);
    if (level <= 0 || !usable()) {
      return;
    }
    if (p.isInWater() || p.isSwimming()) {
      pulse(p, learner, level, p.location());
    }
    Leviathan leviathan = leviathan();
    if (leviathan == null || leviathan.getActiveLevel(p) <= 0) {
      return;
    }
    Loc at = p.location();
    double reach = getConfig().shrineReach;
    for (SeaborneShrines.Shrine shrine : leviathan.shrinesOf(p)) {
      WorldRef world = at.world();
      int chunkX = shrine.x() >> 4;
      int chunkZ = shrine.z() >> 4;
      if (shrine.covers(at, reach) && world.isChunkLoaded(chunkX, chunkZ)
          && AdaptApi.scheduler().owns(world, chunkX, chunkZ)) {
        pulse(p, learner, level, new Loc(world, shrine.x() + 0.5D, shrine.y() + 0.5D, shrine.z() + 0.5D));
      }
    }
  }

  private static Leviathan leviathan() {
    Adaptation<?> found = AdaptApi.isRunning() ? AdaptApi.content().adaptation(Leviathan.ID).orElse(null) : null;
    return found instanceof Leviathan leviathan ? leviathan : null;
  }

  private void pulse(PlayerRef p, PlayerProfile learner, int level, Loc center) {
    double radius = growthRadius(level);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int sample = 0; sample < samples(level); sample++) {
      double angle = random.nextDouble(Math.PI * 2D);
      double distance = random.nextDouble(Math.max(0.5D, radius));
      BlockRef spot = center.world().blockAt(center.add(Math.sin(angle) * distance, 0D, Math.cos(angle) * distance));
      for (int dy = -COLUMN_REACH; dy <= COLUMN_REACH; dy++) {
        BlockRef block = spot.relative(0, dy, 0);
        if (grow(p, learner, block)) {
          break;
        }
      }
    }
  }

  private boolean grow(PlayerRef p, PlayerProfile learner, BlockRef block) {
    Id type = block.type();
    if (KELP.equals(type) || KELP_PLANT.equals(type)) {
      BlockRef top = block;
      for (int step = 0; step < KELP_MAX_AGE && KELP_PLANT.equals(top.type()); step++) {
        top = top.relative(0, 1, 0);
      }
      BlockRef above = top.relative(0, 1, 0);
      BlockStateRef head = top.state();
      if (!KELP.equals(head.type()) || head.age() >= KELP_MAX_AGE || !WATER.equals(above.type())
          || !"0".equals(above.state().property("level")) || !allowed(p, learner, above)) {
        return KELP.equals(head.type());
      }
      top.setType(KELP_PLANT, false);
      above.setType(KELP, false);
      above.setState(above.state().with("age", Integer.toString(Math.min(KELP_MAX_AGE, head.age() + 1))), true);
      grown(above);
      return true;
    }
    if (SEA_PICKLE.equals(type)) {
      int count = pickles(block.state());
      if (count < MAX_PICKLES && "true".equals(block.state().property("waterlogged"))
          && coralBlock(block.relative(0, -1, 0).type()) && allowed(p, learner, block)) {
        block.setState(block.state().with("pickles", Integer.toString(count + 1)), true);
        grown(block);
      }
      return true;
    }
    if (coralFan(type)) {
      BlockRef floor = block.relative(0, -1, 0);
      for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        BlockRef side = block.relative(offset[0], 0, offset[1]);
        if (WATER.equals(side.type()) && "0".equals(side.state().property("level"))
            && side.relative(0, -1, 0).isSolid() && floor.isSolid() && allowed(p, learner, side)) {
          side.setType(floorFan(type), false);
          side.setState(side.state().with("waterlogged", "true"), true);
          grown(side);
          return true;
        }
      }
      return true;
    }
    return false;
  }

  private boolean allowed(PlayerRef p, PlayerProfile learner, BlockRef block) {
    return !AdaptApi.settings().isGrowthBlacklisted(block.type())
        && HerbalismGrowthPulses.mayGrow(this, p, block)
        && payHungerCost(p, "hunger", 1, () -> learner.consumeFood(getConfig().foodCost, getConfig().foodFloor));
  }

  private void grown(BlockRef block) {
    XpProvenance.recordBonemeal(block);
    fx(block.center(), FxPriority.TRAIL).burst(Particles.BUBBLE, 3, 0.3D);
  }

  /** Leaves one pickle and drops the rest; returns how many were taken. */
  private static int thin(BlockRef block) {
    int count = pickles(block.state());
    if (count <= 1) {
      return 0;
    }
    block.setState(block.state().with("pickles", "1"), true);
    ItemRef taken = AdaptApi.platform().host().items().create(SEA_PICKLE, count - 1);
    block.world().dropItemNaturally(block.center().position(), taken);
    return count - 1;
  }

  private static int pickles(BlockStateRef state) {
    try {
      return Integer.parseInt(state.property("pickles"));
    } catch (NumberFormatException | NullPointerException ignored) {
      return MAX_PICKLES;
    }
  }

  private static boolean kelp(Id type) {
    return KELP.equals(type) || KELP_PLANT.equals(type);
  }

  private static boolean seaCrop(Id type) {
    return kelp(type) || SEA_PICKLE.equals(type);
  }

  static boolean coralFan(Id type) {
    String path = type.path();
    return "minecraft".equals(type.namespace()) && (path.endsWith("_coral_fan") || path.endsWith("_coral_wall_fan"))
        && !path.startsWith("dead_");
  }

  private static boolean coralBlock(Id type) {
    return "minecraft".equals(type.namespace()) && type.path().endsWith("_coral_block") && !type.path().startsWith("dead_");
  }

  private static Id floorFan(Id fan) {
    return Id.minecraft(fan.path().replace("_coral_wall_fan", "_coral_fan"));
  }

  double growthRadius(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().growthRadiusAtFirstLevel, getConfig().growthRadiusAtTopLevel);
  }

  int samples(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().samplesAtFirstLevel, getConfig().samplesAtTopLevel));
  }

  double fieldRadius(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().fieldRadiusAtFirstLevel, getConfig().fieldRadiusAtTopLevel);
  }

  @ConfigDescription("Wildgrowth and Harvest Moon work underwater on kelp, sea pickles and coral fans.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Radius of the underwater growth pulse at level 1, in blocks.", impact = "Higher values tend a wider sea bed.")
    double growthRadiusAtFirstLevel = 4;
    @ConfigDoc(value = "Radius of the underwater growth pulse at the top level, in blocks.", impact = "Higher values tend a wider sea bed.")
    double growthRadiusAtTopLevel = 8;
    @ConfigDoc(value = "Spots tried each second at level 1.", impact = "Higher values grow faster.")
    double samplesAtFirstLevel = 3;
    @ConfigDoc(value = "Spots tried each second at the top level.", impact = "Higher values grow faster.")
    double samplesAtTopLevel = 8;
    @ConfigDoc(value = "Hunger spent per growth stage, as Wildgrowth.", impact = "Higher values make growth cost more food.")
    double foodCost = 0.15;
    @ConfigDoc(value = "Food level growth never takes you under.", impact = "Higher values stop growth sooner when hungry.")
    int foodFloor = 6;
    @ConfigDoc(value = "How near you must be for a Tide Shrine to count as a field, in blocks.", impact = "Higher values tend shrines further away.")
    double shrineReach = 48;
    @ConfigDoc(value = "Radius of a sea bed harvest at level 1, in blocks.", impact = "Higher values sweep a wider field.")
    double fieldRadiusAtFirstLevel = 3;
    @ConfigDoc(value = "Radius of a sea bed harvest at the top level, in blocks.", impact = "Higher values sweep a wider field.")
    double fieldRadiusAtTopLevel = 6;
    @ConfigDoc(value = "Most kelp stands one harvest cuts.", impact = "Lower values cap work per harvest.")
    int maxHarvestedColumns = 48;
    @ConfigDoc(value = "Time between harvests, in milliseconds; Harvest Moon's hoe cooldown.", impact = "Higher values make harvests rarer.")
    long harvestCooldownMillis = 3000;
  }
}
