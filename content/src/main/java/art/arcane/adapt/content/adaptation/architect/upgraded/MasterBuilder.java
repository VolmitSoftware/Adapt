package art.arcane.adapt.content.adaptation.architect.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkFill;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyPayment;
import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;
import art.arcane.adapt.content.item.ChalkWandItem.WandData;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Builder's Wand, Supply Line and Chalk Line fused: wider wand faces, refills from carried containers, every chalk wand,
 * and chalk fills: sneak-right-click with a finished guide and a block in the off hand to lay it along the guide as a
 * line, a ring, a solid disc or a wall, a few blocks a tick, each one a real placement paid from your supplies.
 */
public class MasterBuilder extends SkillUpgrade<MasterBuilder.Config> {
  public static final Id ID = Id.adapt("architect/upgrade/master_builder");
  public static final PlayerPreference<ArchitectChalkFill.Shape> SHAPE = PlayerPreference.of("shape", ArchitectChalkFill.Shape.class, ArchitectChalkFill.Shape.OUTLINE, List.of(
          new PlayerPreference.Choice<>(ArchitectChalkFill.Shape.OUTLINE, Text.key("adapt.upgrade.architect.master_builder.shape_outline"), Id.minecraft("string"), 1),
          new PlayerPreference.Choice<>(ArchitectChalkFill.Shape.DISC, Text.key("adapt.upgrade.architect.master_builder.shape_disc"), Id.minecraft("smooth_stone_slab"), 1),
          new PlayerPreference.Choice<>(ArchitectChalkFill.Shape.WALL, Text.key("adapt.upgrade.architect.master_builder.shape_wall"), Id.minecraft("stone_brick_wall"), 1))).labelled(Text.key("adapt.upgrade.architect.master_builder.shape"));
  private final Map<UUID, Deque<Long>> fills = playerState();

  public MasterBuilder() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bricks"));
    declareOvercharge();
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, SHAPE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    int side = (getFaceReach(level) * 2) + 1;
    statLore(v, TextFormat.GREEN, "", side + "x" + side, 1);
    statLore(v, TextFormat.GREEN, "", getFillBlocks(level), 2);
    statLore(v, TextFormat.GREEN, "", getCircleRadius(level), 3);
    statLore(v, TextFormat.GREEN, "", getWallHeight(level), 4);
    if (level >= getConfig().discLevel) {
      statLore(v, TextFormat.GREEN, "+ ", "", 5);
    }
  }

  @Override
  public int lend(Adaptation<?> original, int level) {
    return level > 0 && ArchitectChalkLine.ID.equals(original.getId()) ? original.getMaxLevel() : super.lend(original, level);
  }

  @Override
  public double strength(Adaptation<?> original, int upgradeLevel) {
    if (ArchitectChalkLine.ID.equals(original.getId())) {
      return getCircleRadius(upgradeLevel);
    }
    return ArchitectPlacement.ID.equals(original.getId()) ? getFaceReach(upgradeLevel) : 1D;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND || (e.action() != InteractAction.RIGHT_CLICK_AIR && e.action() != InteractAction.RIGHT_CLICK_BLOCK)) {
      return;
    }
    PlayerRef player = e.player();
    if (!player.isSneaking()) {
      return;
    }
    WandData wand = ChalkWandItem.read(player.inventory().mainHand());
    ItemRef off = player.inventory().offHand();
    BlockStateRef state = ArchitectBuilds.placedState(off);
    int level = wand == null || wand.plan().isEmpty() || state == null ? 0 : getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    e.setCancelled(true);
    fill(player, wand, state, off.copyWithAmount(1), level);
  }

  void fill(PlayerRef player, WandData wand, BlockStateRef state, ItemRef unit, int level) {
    ArchitectChalkLine chalk = ArchitectBuilds.find(ArchitectChalkLine.ID, ArchitectChalkLine.class);
    if (chalk == null || chalk.getActiveLevel(player) < wand.tool().requiredLevel() || chalk.guide(player, wand).isEmpty()) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.master_builder.nothing").resolve());
      return;
    }
    if (BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.busy").resolve());
      return;
    }
    long now = System.currentTimeMillis();
    long waitMillis = waitMillis(player.uuid(), now);
    if (waitMillis > 0L) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.wait").withMarkup("time", Format.duration(waitMillis, 1)).resolve());
      return;
    }
    ArchitectChalkFill.Shape shape = preference(player, SHAPE);
    if (shape == ArchitectChalkFill.Shape.DISC && level < getConfig().discLevel) {
      shape = ArchitectChalkFill.Shape.OUTLINE;
    }
    List<Point> points = ArchitectChalkFill.fill(wand.tool(), wand.plan(), shape, getWallHeight(level),
        towards(player, wand), chalk.arcRadiusLimit());
    List<BlockRef> open = ArchitectBuilds.openSpots(player.world(), points);
    if (open.isEmpty()) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.master_builder.nothing").resolve());
      return;
    }
    int limit = (int) Math.round(getFillBlocks(level) * overcharge(player));
    List<BlockRef> laid = open.subList(0, Math.min(limit, open.size()));
    BuildBatch.Builder batch = BuildBatch.of(this, player)
        .limit(limit)
        .pace(getConfig().blocksPerTick)
        .payFrom(new ArchitectSupplyPayment(player, ArchitectBuilds.find(ArchitectSupplyLine.ID, ArchitectSupplyLine.class)))
        .undoable(WreckingHand.kept(player))
        .onDone((BuildResult result) -> {
          HudStatus.show(player, ArchitectBuilds.report(result));
          WreckingHand.site(player, result.blocks());
        });
    for (BlockRef block : laid) {
      batch.place(block, state, unit);
    }
    if (BuildBatches.start(batch.build())) {
      fills.computeIfAbsent(player.uuid(), (UUID ignored) -> new ArrayDeque<>()).addLast(now);
      cue(player, MutationCue.WORK);
    }
  }

  /** Milliseconds until the player may start another fill under the fills-per-window cap; 0 when they may now. */
  long waitMillis(UUID player, long now) {
    Deque<Long> recent = fills.get(player);
    if (recent == null) {
      return 0L;
    }
    long window = (long) (getConfig().fillWindowSeconds * 1000D);
    while (!recent.isEmpty() && now - recent.peekFirst() >= window) {
      recent.pollFirst();
    }
    return recent.size() < Math.max(1, getConfig().fillsPerWindow) ? 0L : window - (now - recent.peekFirst());
  }

  /** Which way an upright circle's wall grows: towards the side of the guide the player stands on. */
  private static int towards(PlayerRef player, WandData wand) {
    if (wand.tool() != Tool.COMPASS) {
      return 1;
    }
    Point center = wand.plan().points().getFirst();
    Loc at = player.location();
    double side = wand.plan().plane() == Plane.XY ? at.z() - (center.z() + 0.5D) : at.x() - (center.x() + 0.5D);
    return side < 0D ? -1 : 1;
  }

  int getFaceReach(int level) {
    return Math.max(1, level * getConfig().faceReachPerLevel);
  }

  int getFillBlocks(int level) {
    return Math.max(1, perLevel(level, getConfig().fillBlocksLevel1, getConfig().fillBlocksLevel2, getConfig().fillBlocksLevel3));
  }

  int getCircleRadius(int level) {
    return Math.max(1, perLevel(level, getConfig().circleRadiusLevel1, getConfig().circleRadiusLevel2, getConfig().circleRadiusLevel3));
  }

  int getWallHeight(int level) {
    return Math.max(2, getConfig().wallHeightAtFirstLevel + ((level - 1) * getConfig().wallHeightPerLevel));
  }

  private static int perLevel(int level, int first, int second, int third) {
    return level <= 1 ? first : level == 2 ? second : third;
  }

  @ConfigDescription("Place whole faces at once, refilled from shulkers, guided by chalk.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Blocks the Builder's Wand face reaches out from the block you aim at, per level. Faces stop at 3, a 7x7 face.", impact = "Higher values fill wider faces with each placement.")
    int faceReachPerLevel = 1;
    @ConfigDoc(value = "Most blocks one chalk fill lays at level 1.", impact = "Higher values fill longer guides at once.")
    int fillBlocksLevel1 = 64;
    @ConfigDoc(value = "Most blocks one chalk fill lays at level 2.", impact = "Higher values fill longer guides at once.")
    int fillBlocksLevel2 = 160;
    @ConfigDoc(value = "Most blocks one chalk fill lays at level 3.", impact = "Higher values fill longer guides at once.")
    int fillBlocksLevel3 = 320;
    @ConfigDoc(value = "Largest chalk circle radius at level 1; Chalk Line's own limit applies when it is larger.", impact = "Higher values allow wider circles.")
    int circleRadiusLevel1 = 15;
    @ConfigDoc(value = "Largest chalk circle radius at level 2.", impact = "Higher values allow wider circles.")
    int circleRadiusLevel2 = 24;
    @ConfigDoc(value = "Largest chalk circle radius at level 3.", impact = "Higher values allow wider circles.")
    int circleRadiusLevel3 = 32;
    @ConfigDoc(value = "Layers a wall fill stacks at level 1.", impact = "Higher values build taller walls.")
    int wallHeightAtFirstLevel = 2;
    @ConfigDoc(value = "Wall layers added per level after the first.", impact = "Higher values reward higher levels more.")
    int wallHeightPerLevel = 2;
    @ConfigDoc(value = "Level from which a circle can be filled as a solid disc.", impact = "Lower values unlock discs sooner.")
    int discLevel = 2;
    @ConfigDoc(value = "Fills one player may start within the fill window.", impact = "Higher values allow faster repeated fills.")
    int fillsPerWindow = 3;
    @ConfigDoc(value = "Length of the fill window in seconds.", impact = "Lower values allow faster repeated fills.")
    double fillWindowSeconds = 10;
    @ConfigDoc(value = "Blocks a fill lays per tick, before the server-wide cap.", impact = "Higher values finish sooner but cost more per tick.")
    int blocksPerTick = 4;
  }
}
