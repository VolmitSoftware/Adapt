package art.arcane.adapt.content.adaptation.architect.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkFill;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyPayment;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.WandData;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Magic Foundation, Scaffolder and Steady Hands fused, one level lighter: footing while you build. Hands out the
 * Mason's Template, which copies a small build and stamps it elsewhere, turned or mirrored, or repeats it along a
 * chalk guide; every block is a real placement paid from your inventory and carried containers.
 */
public class Skyhook extends SkillUpgrade<Skyhook.Config> {
  public static final Id ID = Id.adapt("architect/upgrade/skyhook");
  public static final PlayerPreference<Gap> GAP = PlayerPreference.of("gap", Gap.class, Gap.ONE, List.of(
          new PlayerPreference.Choice<>(Gap.NONE, Text.key("adapt.upgrade.architect.skyhook.gap_none"), Id.minecraft("stone_bricks"), 1),
          new PlayerPreference.Choice<>(Gap.ONE, Text.key("adapt.upgrade.architect.skyhook.gap_one"), Id.minecraft("oak_fence"), 1),
          new PlayerPreference.Choice<>(Gap.TWO, Text.key("adapt.upgrade.architect.skyhook.gap_two"), Id.minecraft("oak_fence"), 2),
          new PlayerPreference.Choice<>(Gap.FOUR, Text.key("adapt.upgrade.architect.skyhook.gap_four"), Id.minecraft("oak_fence"), 4))).labelled(Text.key("adapt.upgrade.architect.skyhook.gap"));
  private static final Rgb GHOST = new Rgb(170, 230, 255);
  private static final Rgb SHORT = new Rgb(255, 60, 60);
  public static final int MAX_GHOSTS = 256;
  public static final int MAX_SHORT_LISTED = 4;
  private final MasonsTemplate template;
  private final Map<UUID, Preview> previews = playerState();

  public Skyhook() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("scaffolding"));
    declareOvercharge();
    template = registerTool(new MasonsTemplate(this));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, GAP);
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    int side = getCaptureSize(level);
    statLore(v, TextFormat.GREEN, "", side, 1);
    statLore(v, TextFormat.GREEN, "", side * side * side, 2);
    statLore(v, TextFormat.GREEN, "", getRepeats(level), 3);
    if (level >= getConfig().mirrorLevel) {
      statLore(v, TextFormat.GREEN, "+ ", "", 4);
    }
  }

  @Override
  public int lend(Adaptation<?> original, int level) {
    return level <= 0 ? 0 : Math.max(1, super.lend(original, level) - getConfig().lighterLoanLevels);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    ItemRef held = player.inventory().mainHand();
    if (player.isSneaking() || !template.is(held) || getActiveLevel(player) <= 0) {
      return;
    }
    e.setCancelled(true);
    MasonsTemplate.Data data = template.read(held).orElse(null);
    if (data == null) {
      return;
    }
    WreckingHand wrecking = WreckingHand.find();
    if (!data.replace && (wrecking == null || wrecking.getActiveLevel(player) <= 0)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.architect.masons_template.replace_locked").withMarkup("upgrade", String.valueOf(wrecking == null ? WreckingHand.ID : wrecking.getDisplayName())).resolve());
      return;
    }
    data.replace = !data.replace;
    save(player, held, data);
    HudStatus.show(player, TextFormat.AQUA + (data.replace ? Text.key("adapt.tool.architect.masons_template.mode_replace") : Text.key("adapt.tool.architect.masons_template.mode_stamp")).resolve());
  }

  /** A main-hand use of the template by a holder. */
  public void use(ToolUse use, MasonsTemplate.Data data) {
    PlayerRef player = use.player();
    BlockRef block = use.block();
    if (use.rightClick() && use.sneaking() && block == null) {
      WreckingHand wrecking = WreckingHand.find();
      if (wrecking != null) {
        wrecking.undo(player, Set.of(ID, WreckingHand.ID));
      }
      return;
    }
    if (!data.hasPattern()) {
      if (block != null) {
        corner(use, data, block);
      }
      return;
    }
    if (use.leftClick()) {
      turn(use, data, block);
    } else if (use.rightClick() && block != null && use.face() != null) {
      if (use.sneaking()) {
        repeat(player, data, use.level());
      } else {
        stamp(player, data, block.relative(use.face()), use.level());
      }
    }
  }

  private void corner(ToolUse use, MasonsTemplate.Data data, BlockRef block) {
    PlayerRef player = use.player();
    String world = block.world().uid().toString();
    if (data.corner == null || data.corner.length != 3 || !world.equals(data.cornerWorld)) {
      data.cornerWorld = world;
      data.corner = new int[]{block.x(), block.y(), block.z()};
      save(player, use.item(), data);
      HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.architect.masons_template.corner").resolve());
      return;
    }
    int[] first = data.corner;
    int size = getCaptureSize(use.level());
    if (Math.abs(first[0] - block.x()) >= size || Math.abs(first[1] - block.y()) >= size
        || Math.abs(first[2] - block.z()) >= size) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.architect.masons_template.too_large").withMarkup("size", String.valueOf(size)).resolve());
      return;
    }
    ArchitectPattern pattern = ArchitectPattern.copy(block.world(), first[0], first[1], first[2], block.x(), block.y(), block.z());
    data.corner = null;
    data.cornerWorld = null;
    if (pattern == null) {
      save(player, use.item(), data);
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.architect.masons_template.empty").resolve());
      return;
    }
    data.pattern = pattern;
    data.turns = 0;
    data.mirror = false;
    save(player, use.item(), data);
    cue(player, MutationCue.WORK);
    HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.architect.masons_template.copied").withMarkup("count", String.valueOf(pattern.count())).resolve());
  }

  private void turn(ToolUse use, MasonsTemplate.Data data, BlockRef block) {
    PlayerRef player = use.player();
    if (use.sneaking()) {
      if (block == null) {
        return;
      }
      if (use.level() < getConfig().mirrorLevel) {
        HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.architect.masons_template.mirror_locked").withMarkup("level", String.valueOf(getConfig().mirrorLevel)).resolve());
        return;
      }
      data.mirror = !data.mirror;
      HudStatus.show(player, TextFormat.AQUA + (data.mirror ? Text.key("adapt.tool.architect.masons_template.mirrored") : Text.key("adapt.tool.architect.masons_template.unmirrored")).resolve());
    } else {
      data.turns = Math.floorMod(data.turns + 1, 4);
      HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.architect.masons_template.turned").withMarkup("degrees", String.valueOf(data.turns * 90)).resolve());
    }
    save(player, use.item(), data);
    Preview shown = previews.get(player.uuid());
    BlockRef anchor = shown == null || !shown.isLive() ? null : shown.anchor();
    if (anchor != null) {
      preview(player, data, anchor);
    }
  }

  /** The first right-click on a spot shows the stamp to you alone; the second on the same spot builds it. */
  void stamp(PlayerRef player, MasonsTemplate.Data data, BlockRef anchor, int level) {
    Preview shown = previews.get(player.uuid());
    if (shown == null || !shown.isLive() || !shown.matches(anchor, data)) {
      preview(player, data, anchor);
      return;
    }
    previews.remove(player.uuid());
    displays().clearViewer(getId().toString(), player.uuid());
    List<ArchitectPattern.Placement> placements = data.pattern.placements(anchor, data.turns, data.mirror);
    build(player, placements, getStampBlocks(level));
  }

  private void preview(PlayerRef player, MasonsTemplate.Data data, BlockRef anchor) {
    List<ArchitectPattern.Placement> placements = open(data.pattern.placements(anchor, data.turns, data.mirror));
    previews.put(player.uuid(), new Preview(anchor.world().uid(), anchor.x(), anchor.y(), anchor.z(), data.pattern.fingerprint(), data.turns, data.mirror,
        System.currentTimeMillis() + (long) (getConfig().previewSeconds * 1000D)));
    ViewerDisplayPort displays = displays();
    displays.clearViewer(getId().toString(), player.uuid());
    Map<Id, Integer> needed = new LinkedHashMap<>();
    for (ArchitectPattern.Placement placement : placements) {
      needed.merge(placement.state().placementItem(), 1, Integer::sum);
    }
    ArchitectSupplyLine supply = ArchitectBuilds.find(ArchitectSupplyLine.ID, ArchitectSupplyLine.class);
    Map<Id, Integer> missing = new LinkedHashMap<>();
    for (Map.Entry<Id, Integer> need : needed.entrySet()) {
      ItemRef unit = AdaptApi.platform().host().items().create(need.getKey(), 1);
      int carried = supply == null ? 0 : supply.carried(player, unit);
      if (carried < need.getValue()) {
        missing.put(need.getKey(), need.getValue() - carried);
      }
    }
    int ticks = (int) Math.max(20L, Math.round(getConfig().previewSeconds * 20D));
    for (int index = 0; index < Math.min(MAX_GHOSTS, placements.size()); index++) {
      ArchitectPattern.Placement placement = placements.get(index);
      Rgb glow = missing.containsKey(placement.state().placementItem()) ? SHORT : GHOST;
      displays.showBlock(getId().toString(), "ghost:" + index, player, placement.block().location(), placement.state(), glow, ticks);
    }
    if (missing.isEmpty()) {
      HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.architect.masons_template.ready").withMarkup("count", String.valueOf(placements.size())).resolve());
      return;
    }
    List<String> parts = new ArrayList<>();
    for (Map.Entry<Id, Integer> entry : missing.entrySet()) {
      if (parts.size() == MAX_SHORT_LISTED) {
        parts.add("...");
        break;
      }
      parts.add(entry.getValue() + " " + entry.getKey().path().replace('_', ' '));
    }
    HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.architect.masons_template.short").withMarkup("items", String.join(", ", parts)).resolve());
  }

  /** Stamps the copy at points along the carried chalk guide, spaced by its footprint and the chosen gap. */
  void repeat(PlayerRef player, MasonsTemplate.Data data, int level) {
    WandData wand = ArchitectChalkLine.plannedWand(player);
    ArchitectChalkLine chalk = ArchitectBuilds.find(ArchitectChalkLine.ID, ArchitectChalkLine.class);
    if (wand == null || chalk == null || chalk.guide(player, wand).isEmpty()) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.architect.masons_template.no_guide").resolve());
      return;
    }
    List<Point> path = ArchitectChalkFill.path(wand.tool(), wand.plan(), chalk.arcRadiusLimit());
    int spacing = Math.max(data.pattern.width(), data.pattern.depth()) + preference(player, GAP).blocks();
    if (BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.busy").resolve());
      return;
    }
    List<Point> anchors = anchors(path, spacing, (int) Math.round(getRepeats(level) * overcharge(player)));
    WorldRef world = player.world();
    Map<BlockRef, ArchitectPattern.Placement> placements = new LinkedHashMap<>();
    for (Point anchor : anchors) {
      for (ArchitectPattern.Placement placement : data.pattern.placements(world.blockAt(anchor.x(), anchor.y(), anchor.z()),
          data.turns, data.mirror)) {
        placements.putIfAbsent(placement.block(), placement);
      }
    }
    build(player, new ArrayList<>(placements.values()),
        Math.min(getConfig().maxBlocksPerRepeat, getStampBlocks(level) * Math.max(1, anchors.size())));
  }

  /** Points along the path at least {@code spacing} apart, starting at its first point, at most {@code count}. */
  static List<Point> anchors(List<Point> path, int spacing, int count) {
    List<Point> anchors = new ArrayList<>();
    Point last = null;
    double spacingSquared = (double) spacing * spacing;
    for (Point point : path) {
      if (anchors.size() >= count) {
        break;
      }
      if (last == null || distanceSquared(last, point) >= spacingSquared) {
        anchors.add(point);
        last = point;
      }
    }
    return anchors;
  }

  private void build(PlayerRef player, List<ArchitectPattern.Placement> placements, int limit) {
    if (BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.busy").resolve());
      return;
    }
    List<ArchitectPattern.Placement> open = open(placements);
    if (open.isEmpty()) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.master_builder.nothing").resolve());
      return;
    }
    BuildBatch.Builder batch = BuildBatch.of(this, player)
        .limit(limit)
        .pace(getConfig().blocksPerTick)
        .payFrom(new ArchitectSupplyPayment(player, ArchitectBuilds.find(ArchitectSupplyLine.ID, ArchitectSupplyLine.class)))
        .undoable(WreckingHand.kept(player))
        .onDone((BuildResult result) -> {
          HudStatus.show(player, ArchitectBuilds.report(result));
          WreckingHand.site(player, result.blocks());
        });
    for (ArchitectPattern.Placement placement : open) {
      batch.place(placement.block(), placement.state());
    }
    if (BuildBatches.start(batch.build())) {
      cue(player, MutationCue.WORK);
    }
  }

  private static List<ArchitectPattern.Placement> open(List<ArchitectPattern.Placement> placements) {
    List<ArchitectPattern.Placement> open = new ArrayList<>(placements.size());
    for (ArchitectPattern.Placement placement : placements) {
      BlockRef block = placement.block();
      WorldRef world = block.world();
      if (block.y() >= world.minHeight() && block.y() < world.maxHeight()
          && world.isChunkLoaded(block.x() >> 4, block.z() >> 4) && (block.isAir() || block.isReplaceable())) {
        open.add(placement);
      }
    }
    return open;
  }

  private void save(PlayerRef player, ItemRef held, MasonsTemplate.Data data) {
    template.write(held, data);
    player.inventory().setMainHand(held);
  }

  private static double distanceSquared(Point first, Point second) {
    double x = first.x() - second.x();
    double y = first.y() - second.y();
    double z = first.z() - second.z();
    return (x * x) + (y * y) + (z * z);
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }

  int getCaptureSize(int level) {
    return Math.max(1, getConfig().templateSizeAtFirstLevel + ((level - 1) * getConfig().templateSizePerLevel));
  }

  int getStampBlocks(int level) {
    int side = getCaptureSize(level);
    return side * side * side;
  }

  int getRepeats(int level) {
    return (int) Math.max(1L, Math.round(getConfig().repeatsAtFirstLevel * Math.pow(2D, Math.max(0, level - 1))));
  }

  private record Preview(UUID world, int x, int y, int z, int pattern, int turns, boolean mirror, long until) {
    boolean isLive() {
      return System.currentTimeMillis() <= until;
    }

    BlockRef anchor() {
      WorldRef loaded = AdaptApi.platform().host().world(world);
      return loaded == null ? null : loaded.blockAt(x, y, z);
    }

    boolean matches(BlockRef spot, MasonsTemplate.Data data) {
      return world.equals(spot.world().uid()) && x == spot.x() && y == spot.y() && z == spot.z()
          && pattern == data.pattern.fingerprint() && turns == data.turns && mirror == data.mirror;
    }
  }

  public enum Gap {
    NONE(0), ONE(1), TWO(2), FOUR(4);

    private final int blocks;

    Gap(int blocks) {
      this.blocks = blocks;
    }

    public int blocks() {
      return blocks;
    }
  }

  @ConfigDescription("Footing while you build, and a template that copies, stamps and repeats small builds.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Levels below the usual loan at which Magic Foundation, Scaffolder and Steady Hands work, never below 1.", impact = "Higher values make the footing originals weaker.")
    int lighterLoanLevels = 1;
    @ConfigDoc(value = "Blocks per side a template copies at level 1; one stamp lays at most this many cubed.", impact = "Higher values copy larger builds.")
    int templateSizeAtFirstLevel = 6;
    @ConfigDoc(value = "Blocks per side added per level after the first.", impact = "Higher values reward higher levels more.")
    int templateSizePerLevel = 4;
    @ConfigDoc(value = "Stamps one repeat lays along a chalk guide at level 1; doubles each level.", impact = "Higher values repeat further along a guide.")
    int repeatsAtFirstLevel = 4;
    @ConfigDoc(value = "Most blocks one repeat lays, however many stamps it makes.", impact = "Higher values allow longer repeats at more cost per run.")
    int maxBlocksPerRepeat = 4096;
    @ConfigDoc(value = "Level from which a template can be mirrored.", impact = "Lower values unlock mirroring sooner.")
    int mirrorLevel = 2;
    @ConfigDoc(value = "Seconds a stamp preview stays up waiting for the second right-click.", impact = "Higher values give more time to confirm.")
    double previewSeconds = 30;
    @ConfigDoc(value = "Blocks a stamp lays per tick, before the server-wide cap.", impact = "Higher values finish sooner but cost more per tick.")
    int blocksPerTick = 8;
  }
}
