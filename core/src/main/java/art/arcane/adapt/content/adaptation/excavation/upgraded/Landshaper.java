package art.arcane.adapt.content.adaptation.excavation.upgraded;

import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.batch.Payments;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.content.adaptation.excavation.ExcavationEarthMover;
import art.arcane.adapt.content.adaptation.excavation.ExcavationHeave;
import art.arcane.adapt.content.adaptation.excavation.ExcavationHasteBoost;
import art.arcane.adapt.content.adaptation.excavation.ExcavationPlane;
import art.arcane.adapt.content.adaptation.excavation.ExcavationShaft;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Tunneler, Burrow and Hasty Excavator fused: every soft block dug with a shovel carves the plane around it, or a
 * shaft straight down while sneaking, and digging keeps haste up. Haste needs the block break speed attribute and
 * stays off where the version lacks it. Sneak-right-clicking the ground slams a line of earth forward that throws
 * mobs up, and Grading Stakes level the ground between two corners.
 */
public class Landshaper extends SkillUpgrade<Landshaper.Config> {
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final int MAX_SLAM_TARGETS = 12;

  private final boolean hasteSupported =
      PlatformSupport.check(ExcavationHasteBoost.NEEDS).usable();
  private final ExcavationPlane plane = new ExcavationPlane(this, (PlayerRef player, List<BlockRef> carved) -> {
    addStat(player, "excavation.landshaper.blocks-carved", carved.size());
    xpOnBlocks(player, carved, getConfig().xpPerBlock);
    cue(player, MutationCue.WORK);
  });
  private final ExcavationShaft shaft = new ExcavationShaft(this, (PlayerRef player, BlockRef dug) -> {
    addStat(player, "excavation.landshaper.blocks-carved", 1);
    xpOn(player, dug, getConfig().xpPerBlock);
  });
  private final ExcavationHasteBoost haste = new ExcavationHasteBoost(this, playerState(), playerState());
  private final Cooldowns shaftCooldowns = cooldowns();
  private final Cooldowns slamCooldowns = cooldowns();
  private final Map<UUID, Long> slamClicks = playerState();
  private final GradingStakes stakes;

  public Landshaper() {
    super("upgraded-excavation-landshaper");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_shovel"));
    stakes = registerTool(new GradingStakes(this));
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (getActiveLevel(player.getPlayer()) <= 0) {
      AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getPlaneBlocks(level), 1);
    statLore(v, getShaftDepth(level), 2);
    statLore(v, C.GREEN, "+", Form.pc(ExcavationHasteBoost.hasteAmount(level), 0), 3);
    statLore(v, getGradeSide(level) + "x" + getGradeSide(level), 4);
    statLore(v, getGradeHeight(level), 5);
    statLore(v, Form.f(getSlamDamage(level), 1), 6);
    statLore(v, C.YELLOW, "* ", Form.duration(getSlamCooldownMillis(level), 1), 7);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (BonusBlockBreaks.isActive(p) || !isShovel(p.inventory().mainHand())
        || !ExcavationBlocks.isShovelable(e.block().type())) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    if (p.isSneaking()) {
      sinkShaft(p, e.block(), context.level());
      return;
    }
    plane.carve(p, e.block(), getPlaneBlocks(context.level()), getConfig().durabilityCostPerBlock, (Id type) -> true);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    if (!hasteSupported || !isShovel(p.inventory().mainHand()) || !ExcavationBlocks.isShovelable(e.block().type())) {
      return;
    }

    BlockActionContext context = resolveInteractContext(p, e.block().location());
    if (context == null) {
      return;
    }

    if (haste.apply(p, e.block(), context.level(), getHasteDurationTicks())) {
      cue(p, MutationCue.BOON);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    int level = slamLevel(e);
    if (level <= 0) {
      return;
    }
    PlayerRef p = e.player();
    slamCooldowns.mark(p.uuid(), getSlamCooldownMillis(level));
    slamClicks.put(p.uuid(), e.dispatchId());
    e.setCancelled(true);
    damageHand(p, Math.max(0, getConfig().slamDurability));
    slam(p, level);
  }

  /** Whether this click is, or just was, a slam, so the kept Earth Mover leaves it alone. */
  public boolean slams(PlayerInteractEvent e) {
    Long slammed = slamClicks.get(e.player().uuid());
    return (slammed != null && slammed == e.dispatchId()) || slamLevel(e) > 0;
  }

  /** The level a sneak-right-click on top of a block slams at, or 0 when it does not: cooling down or unpaid. */
  private int slamLevel(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || (e.hand() != null && e.hand() != Hand.MAIN_HAND)
        || e.clickedFace() != BlockFace.UP) {
      return 0;
    }
    PlayerRef p = e.player();
    BlockRef ground = e.clickedBlock();
    ItemRef hand = p.inventory().mainHand();
    if (!p.isSneaking() || ground == null || !isShovel(hand)) {
      return 0;
    }

    BlockActionContext context = resolveInteractContext(p, ground.location());
    if (context == null || !slamCooldowns.isReady(p.uuid(), getSlamCooldownMillis(context.level()))
        || !BonusBlockBreaks.canCharge(hand, Math.max(0, getConfig().slamDurability))) {
      return 0;
    }
    return context.level();
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (!BonusBlockBreaks.isActive(p) || !stakes.collects(p, e.block())) {
      return;
    }
    List<ItemRef> collected = new ArrayList<>(e.items().size());
    for (ItemEntityRef item : e.items()) {
      collected.add(item.item());
      item.remove();
    }
    e.items().clear();
    Payments.give(p, collected);
  }

  private void slam(PlayerRef p, int level) {
    Loc from = p.location();
    double yaw = Math.toRadians(from.yaw());
    Vec3 direction = new Vec3(-Math.sin(yaw), 0D, Math.cos(yaw));
    int length = Math.max(1, getConfig().slamLength);
    Vec3 start = from.position();
    Vec3 middle = start.add(direction.multiply(length / 2D));
    double reach = (length / 2D) + getConfig().slamWidth;
    Vec3 push = direction.multiply(getConfig().slamPush).withY(getConfig().slamLift);
    List<LivingRef> heaved = new ArrayList<>();
    for (EntityRef entity : p.world().nearbyEntities(middle, reach, getConfig().slamHeight, reach)) {
      if (heaved.size() >= MAX_SLAM_TARGETS) {
        break;
      }
      if (entity instanceof LivingRef target && !target.uuid().equals(p.uuid())
          && onLine(start, direction, length, target.location().position()) && slamTarget(p, target)
          && Tasks.isOwnedByCurrentRegion(target)
          && ExcavationHeave.heave(p, target, getSlamDamage(level), push, SLOWNESS, getConfig().slamSlowTicks,
          getConfig().slamSlowAmplifier)) {
        heaved.add(target);
      }
    }

    slamFx(p.world(), from, direction, length);
    cue(p, MutationCue.FIGHT);
    addStat(p, "excavation.landshaper.slams", 1);
    xpOnMobs(p, heaved, getConfig().slamXpPerMob);
  }

  private boolean onLine(Vec3 start, Vec3 direction, int length, Vec3 at) {
    double dx = at.x() - start.x();
    double dz = at.z() - start.z();
    double along = (dx * direction.x()) + (dz * direction.z());
    double across = Math.abs((dx * direction.z()) - (dz * direction.x()));
    return along >= 0D && along <= length + 0.5D && across <= getConfig().slamWidth
        && Math.abs(at.y() - start.y()) <= getConfig().slamHeight;
  }

  private boolean slamTarget(PlayerRef p, LivingRef target) {
    if (isProtectedFriendly(p, target)) {
      return false;
    }
    if (target instanceof PlayerRef victim) {
      return canPVP(p, victim.location());
    }
    return ExcavationEarthMover.isEarthMoverTarget(target) && AdaptationDamageTargets.allows(target, false)
        && canPVE(p, target);
  }

  private void slamFx(WorldRef world, Loc from, Vec3 direction, int length) {
    for (int step = 1; step <= length; step++) {
      Loc at = from.add(direction.x() * step, 0D, direction.z() * step);
      BlockRef below = world.blockAt(at.blockX(), at.blockY() - 1, at.blockZ());
      if (!below.isAir()) {
        fx(at, FxPriority.COMBAT).particle(Particles.blockCrack(below.state()), 8, 0, 0.1D, 0, 0.4D, 0.05D);
      }
    }
    fx(from, FxPriority.COMBAT).sound(Sounds.BLOCK_ROOTED_DIRT_BREAK, 1.0F, 0.6F);
  }

  /** Pays for the natural ground a grade dug and reports how its batch ended. */
  void gradeDone(PlayerRef p, List<BlockRef> dug, BuildResult result) {
    if (!dug.isEmpty()) {
      xpOnBlocks(p, dug, getConfig().xpPerBlock);
      addStat(p, "excavation.landshaper.blocks-graded", dug.size());
      cue(p, MutationCue.WORK);
    }
    String message = switch (result.stop()) {
      case NO_ITEMS -> C.RED + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_NO_FILL);
      case TOOL_WORN -> C.RED + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_WORN);
      case PROTECTED, OUT_OF_REACH -> C.RED + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_PROTECTED);
      default -> C.GRAY + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_DONE,
          trusted("dug", String.valueOf(result.broken())), trusted("filled", String.valueOf(result.placed())));
    };
    AdaptHud.actionBar(p, message);
  }

  void gradeFx(PlayerRef p, WorldRef world, int minX, int maxX, int minZ, int maxZ, int height) {
    for (int[] corner : new int[][]{{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}}) {
      fx(new Loc(world, corner[0] + 0.5D, height + 1.1D, corner[1] + 0.5D), FxPriority.GAMEPLAY)
          .particle(Particles.CLOUD, 6, 0, 0.2D, 0, 0.1D, 0.02D);
    }
    fx(p.location(), FxPriority.GAMEPLAY).sound(Sounds.BLOCK_GRAVEL_BREAK, 0.8F, 0.8F);
  }

  private void sinkShaft(PlayerRef p, BlockRef dug, int level) {
    long cooldownMillis = (long) getConfig().shaftCooldownMillis;
    if (!shaftCooldowns.isReady(p.uuid(), cooldownMillis)) {
      return;
    }

    List<BlockRef> plan = shaft.plan(p, dug.relative(BlockFace.DOWN), getShaftDepth(level), getConfig().safeFloorMargin);
    if (plan.isEmpty()) {
      return;
    }

    int cost = getConfig().durabilityCostPerBlock;
    ItemRef hand = p.inventory().mainHand();
    if (!ExcavationShaft.canApplyDurability(hand, plan.size() * cost)) {
      return;
    }

    shaftCooldowns.mark(p.uuid(), cooldownMillis);
    BlockStateRef floorData = plan.get(plan.size() - 1).state();
    int ticksPerBlock = Math.max(1, getConfig().ticksPerBlock);
    Tasks.runEntity(p, () -> {
      if (shaft.dig(p, plan.get(0), 1.1F, cost)) {
        shaft.schedule(p, plan, floorData, ticksPerBlock, cost);
      }
    }, 1);
    cue(p, MutationCue.WORK);
    LingeringSound lingering = linger(p);
    Tasks.runEntity(p, lingering::stop, 1 + ((plan.size() + 1) * ticksPerBlock));
  }

  int getPlaneBlocks(int level) {
    return Math.max(1, Math.min(8, getConfig().planeBlocksBase + (level * getConfig().planeBlocksPerLevel)));
  }

  int getShaftDepth(int level) {
    return Math.max(2, getConfig().shaftDepthBase + (level * getConfig().shaftDepthPerLevel));
  }

  int getGradeSide(int level) {
    return Math.max(1, getConfig().gradeSideBase + (level * getConfig().gradeSidePerLevel));
  }

  int getGradeHeight(int level) {
    return Math.max(1, (int) Math.floor(getConfig().gradeHeightAtFirstLevel
        + ((Math.max(1, level) - 1) * getConfig().gradeHeightPerLevel)));
  }

  double getSlamDamage(int level) {
    return Math.max(0D, getConfig().slamDamageBase + (level * getConfig().slamDamagePerLevel));
  }

  long getSlamCooldownMillis(int level) {
    return Math.max(500L, (long) (getConfig().slamCooldownMillisBase - (level * getConfig().slamCooldownMillisPerLevel)));
  }

  private int getHasteDurationTicks() {
    return Math.max(40, Math.min(20 * 30, getConfig().hasteDurationTicks));
  }

  @ConfigDescription("Dig a plane of soft ground at once, or a shaft down while sneaking, with haste while you dig; slam the ground and level it with Grading Stakes.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Blocks carved around each dug block before level scaling.", impact = "Higher values clear more of the plane at low levels; the plane holds at most 8.")
    int planeBlocksBase = 2;
    @ConfigDoc(value = "Plane blocks added per level.", impact = "Higher values reward higher levels more.")
    int planeBlocksPerLevel = 2;
    @ConfigDoc(value = "Shaft depth in blocks before level scaling.", impact = "Higher values dig deeper shafts at every level.")
    int shaftDepthBase = 4;
    @ConfigDoc(value = "Shaft depth added per level, in blocks.", impact = "Higher values reward higher levels more.")
    int shaftDepthPerLevel = 4;
    @ConfigDoc(value = "Time between two shafts, in milliseconds.", impact = "Lower values allow more frequent shafts.")
    double shaftCooldownMillis = 6000;
    @ConfigDoc(value = "Ticks between two shaft blocks.", impact = "Higher values dig the shaft more slowly.")
    int ticksPerBlock = 2;
    @ConfigDoc(value = "Blocks above the world floor where shafts stop.", impact = "Higher values keep shafts farther from the bottom of the world.")
    int safeFloorMargin = 16;
    @ConfigDoc(value = "Shovel durability spent on each extra block.", impact = "Higher values wear the shovel faster.")
    int durabilityCostPerBlock = 1;
    @ConfigDoc(value = "How long haste lasts after each dig, in ticks.", impact = "Higher values keep haste through slower digs; clamped between 40 and 600.")
    int hasteDurationTicks = 100;
    @ConfigDoc(value = "Skill XP for each extra block carved.", impact = "Higher values reward carving more.")
    double xpPerBlock = 1.5;
    @ConfigDoc(value = "Side of the area Grading Stakes level, in blocks, before level scaling.", impact = "Higher values level larger areas at every level.")
    int gradeSideBase = 4;
    @ConfigDoc(value = "Side added to the graded area per level, in blocks.", impact = "Higher values reward higher levels more.")
    int gradeSidePerLevel = 4;
    @ConfigDoc(value = "Most blocks the ground is raised or lowered at level 1.", impact = "Higher values cut and fill deeper at every level.")
    double gradeHeightAtFirstLevel = 3;
    @ConfigDoc(value = "Height change added per level after the first, rounded down.", impact = "Higher values reward higher levels more.")
    double gradeHeightPerLevel = 2.5;
    @ConfigDoc(value = "Ticks the outline shows before the ground is levelled.", impact = "Higher values give more time to pull the stakes.")
    int gradePreviewTicks = 60;
    @ConfigDoc(value = "Blocks dug or filled per tick while grading.", impact = "Higher values grade faster but cost more block updates per tick.")
    int gradeBlocksPerTick = 8;
    @ConfigDoc(value = "Most blocks one grade digs and fills together.", impact = "Higher values allow deeper grades of the largest areas.")
    int gradeMaxBlocks = 2048;
    @ConfigDoc(value = "Length of the slam line in blocks.", impact = "Higher values reach mobs farther ahead.")
    int slamLength = 6;
    @ConfigDoc(value = "Blocks to either side of the slam line that are still hit.", impact = "Higher values make the line wider.")
    double slamWidth = 1.25;
    @ConfigDoc(value = "Blocks above or below you that the slam still hits.", impact = "Higher values hit mobs on steeper ground.")
    double slamHeight = 2;
    @ConfigDoc(value = "Slam damage before level scaling.", impact = "Higher values hit harder at every level.")
    double slamDamageBase = 1;
    @ConfigDoc(value = "Slam damage added per level.", impact = "Higher values reward higher levels more.")
    double slamDamagePerLevel = 2;
    @ConfigDoc(value = "Time between slams before level scaling, in milliseconds.", impact = "Lower values allow more frequent slams.")
    double slamCooldownMillisBase = 12000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values shorten the cooldown more at higher levels.")
    double slamCooldownMillisPerLevel = 2000;
    @ConfigDoc(value = "Shovel durability each slam costs.", impact = "Higher values wear the shovel faster; a shovel that cannot pay does not slam.")
    int slamDurability = 4;
    @ConfigDoc(value = "Upward speed given to each mob the slam hits.", impact = "Higher values throw mobs higher.")
    double slamLift = 0.6;
    @ConfigDoc(value = "Forward speed given to each mob the slam hits.", impact = "Higher values push mobs farther away.")
    double slamPush = 0.3;
    @ConfigDoc(value = "How long slammed mobs are slowed, in ticks.", impact = "Higher values keep them slowed longer.")
    int slamSlowTicks = 60;
    @ConfigDoc(value = "Slowness amplifier on slammed mobs (0 is Slowness I).", impact = "Higher values slow them more.")
    int slamSlowAmplifier = 1;
    @ConfigDoc(value = "Skill XP for each natural mob the slam hits.", impact = "Higher values reward slamming groups.")
    double slamXpPerMob = 2;
  }
}
