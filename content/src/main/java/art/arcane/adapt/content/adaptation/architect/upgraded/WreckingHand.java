package art.arcane.adapt.content.adaptation.architect.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyPayment;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.WandData;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Mason's Eraser, Silk-Touch Glass and Smart Shape fused: erase your placements, pick up glass by hand and rotate
 * blocks by punching, undo your last fills, stamps and wand faces, and swap a guide or a build for another block.
 */
public class WreckingHand extends SkillUpgrade<WreckingHand.Config> {
  public static final Id ID = Id.adapt("architect/upgrade/wrecking_hand");
  private static final int MAX_SITES = 5;
  private final Map<UUID, Deque<Site>> sites = playerState();

  public WreckingHand() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_pickaxe"));
    declareOvercharge();
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    statLore(v, TextFormat.GREEN, "", Format.duration(getUndoWindowMillis(level), 0), 2);
    statLore(v, TextFormat.GREEN, "", getUndoBatches(level), 3);
    statLore(v, TextFormat.GREEN, "", getReplaceBlocks(level), 4);
  }

  /** The registered Wrecking Hand, or null. */
  public static @Nullable WreckingHand find() {
    return ArchitectBuilds.find(ID, WreckingHand.class);
  }

  /** How many undoable builds a new build of this player's keeps: Wrecking Hand's count, or one without it. */
  public static int kept(PlayerRef player) {
    WreckingHand hand = find();
    int level = hand == null ? 0 : hand.getLevel(player);
    return level > 0 ? hand.getUndoBatches(level) : 1;
  }

  /** Remembers the blocks a build of this player's laid, so the replace brush can find them again. */
  public static void site(PlayerRef player, Collection<BlockRef> blocks) {
    WreckingHand hand = find();
    if (hand == null || blocks.isEmpty() || hand.getLevel(player) <= 0) {
      return;
    }
    Set<Point> points = new HashSet<>(blocks.size());
    for (BlockRef block : blocks) {
      points.add(new Point(block.x(), block.y(), block.z()));
    }
    Deque<Site> recent = hand.sites.computeIfAbsent(player.uuid(), (UUID ignored) -> new ArrayDeque<>());
    synchronized (recent) {
      recent.addFirst(new Site(blocks.iterator().next().world().uid(), Set.copyOf(points)));
      while (recent.size() > MAX_SITES) {
        recent.removeLast();
      }
    }
  }

  /** Drops undone blocks from the player's sites, so the replace brush never reaches what stands there later. */
  public void forget(PlayerRef player, Collection<BlockRef> blocks) {
    Deque<Site> recent = sites.get(player.uuid());
    if (recent == null || blocks.isEmpty()) {
      return;
    }
    UUID world = blocks.iterator().next().world().uid();
    Set<Point> undone = new HashSet<>(blocks.size());
    for (BlockRef block : blocks) {
      undone.add(new Point(block.x(), block.y(), block.z()));
    }
    synchronized (recent) {
      List<Site> kept = new ArrayList<>(recent.size());
      for (Site site : recent) {
        if (!site.world().equals(world)) {
          kept.add(site);
          continue;
        }
        Set<Point> left = new HashSet<>(site.points());
        left.removeAll(undone);
        if (!left.isEmpty()) {
          kept.add(new Site(site.world(), Set.copyOf(left)));
        }
      }
      recent.clear();
      recent.addAll(kept);
    }
  }

  /**
   * Undoes the player's newest build made by one of {@code owners} inside the undo window, returning exactly what each
   * still-standing block cost. Tells the player when there is nothing to undo; false without Wrecking Hand.
   */
  public boolean undo(PlayerRef player, Set<Id> owners) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return false;
    }
    if (BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.busy").resolve());
      return true;
    }
    boolean started = BuildBatches.undo(player, owners, getUndoWindowMillis(level), getConfig().blocksPerTick, 0L,
        (BuildResult result) -> {
          forget(player, result.blocks());
          HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.upgrade.architect.wrecking_hand.undone").withMarkup("count", String.valueOf(result.undone())).resolve());
          cue(player, MutationCue.WORK);
        });
    if (!started) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.wrecking_hand.nothing_to_undo").resolve());
    }
    return true;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND || !e.player().isSneaking()) {
      return;
    }
    PlayerRef player = e.player();
    if (e.action() == InteractAction.RIGHT_CLICK_AIR) {
      Set<Id> owners = undoOwners(player);
      if (owners != null && getActiveLevel(player) > 0) {
        e.setUseItem(UseResult.DENY);
        undo(player, owners);
      }
      return;
    }
    if (e.action() != InteractAction.LEFT_CLICK_BLOCK || e.clickedBlock() == null || getActiveLevel(player) <= 0) {
      return;
    }
    MasonsTemplate template = MasonsTemplate.find();
    BlockStateRef state = ArchitectBuilds.placedState(player.inventory().offHand());
    if (template == null || state == null || template.is(player.inventory().mainHand()) || !template.armsReplace(player)) {
      return;
    }
    e.setCancelled(true);
    replace(player, e.clickedBlock(), state, player.inventory().offHand().copyWithAmount(1));
  }

  /** The builds a sneak-right-click into the air with the held item undoes: chalk fills with a chalk wand, wand faces with a block. */
  private static @Nullable Set<Id> undoOwners(PlayerRef player) {
    ItemRef main = player.inventory().mainHand();
    if (ChalkWandItem.read(main) != null) {
      return ArchitectBuilds.placedState(player.inventory().offHand()) == null ? Set.of(MasterBuilder.ID) : null;
    }
    return ArchitectBuilds.placedState(main) == null ? null : Set.of(ArchitectPlacement.ID);
  }

  /**
   * Swaps the blocks of the build or chalk guide the clicked block belongs to for {@code state}: each old block is a
   * real break with the held tool, then the new one is placed and paid. Only blocks the held tool gets drops from,
   * never containers, spawners or other players' placements.
   */
  void replace(PlayerRef player, BlockRef clicked, BlockStateRef state, ItemRef unit) {
    int level = getActiveLevel(player);
    if (BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.busy").resolve());
      return;
    }
    WorldRef world = clicked.world();
    Point at = new Point(clicked.x(), clicked.y(), clicked.z());
    Set<Point> own = siteOf(player, world.uid(), at);
    Collection<Point> area = own;
    if (area == null) {
      List<Point> guide = guideOf(player);
      area = guide.contains(at) ? guide : null;
    }
    List<BlockRef> targets = area == null ? List.of() : targets(player, world, area, own != null, state);
    if (targets.isEmpty()) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.wrecking_hand.nothing_to_replace").resolve());
      return;
    }
    int allowed = (int) Math.round(getReplaceBlocks(level) * overcharge(player));
    List<BlockRef> swapped = targets.subList(0, Math.min(allowed, targets.size()));
    BuildBatch.Builder batch = BuildBatch.of(this, player)
        .limit(swapped.size() * 2)
        .pace(getConfig().blocksPerTick)
        .payFrom(new ArchitectSupplyPayment(player, ArchitectBuilds.find(ArchitectSupplyLine.ID, ArchitectSupplyLine.class)))
        .undoable(kept(player))
        .onDone((BuildResult result) -> {
          HudStatus.show(player, ArchitectBuilds.report(result));
          site(player, result.blocks());
        });
    for (BlockRef target : swapped) {
      batch.breakWith(target, getConfig().durabilityPerBlock, target::isPreferredTool);
      batch.place(target, state, unit);
    }
    if (BuildBatches.start(batch.build())) {
      cue(player, MutationCue.WORK);
    }
  }

  private List<BlockRef> targets(PlayerRef player, WorldRef world, Collection<Point> area, boolean ownBuild,
                                 BlockStateRef state) {
    ItemRef tool = player.inventory().mainHand();
    List<BlockRef> targets = new ArrayList<>();
    for (Point point : area) {
      if (point.y() < world.minHeight() || point.y() >= world.maxHeight()
          || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
        continue;
      }
      BlockRef block = world.blockAt(point.x(), point.y(), point.z());
      if (block.isAir() || block.isLiquid() || block.isReplaceable() || block.type().equals(state.type())
          || block.hardness() < 0F || block.container() != null || isKeptBlock(block.type())
          || !block.isPreferredTool(tool) || (!ownBuild && XpProvenance.isPlayerPlaced(block))
          || !canBlockBreak(player, block.location())) {
        continue;
      }
      targets.add(block);
    }
    return targets;
  }

  private static boolean isKeptBlock(Id type) {
    String path = type.path();
    return path.equals("spawner") || path.equals("trial_spawner") || path.endsWith("_sign") || path.endsWith("_banner")
        || path.equals("bedrock") || path.contains("command_block") || path.equals("structure_block")
        || path.equals("jigsaw") || path.equals("end_portal_frame") || path.equals("reinforced_deepslate");
  }

  private @Nullable Set<Point> siteOf(PlayerRef player, UUID world, Point at) {
    Deque<Site> recent = sites.get(player.uuid());
    if (recent == null) {
      return null;
    }
    synchronized (recent) {
      for (Site site : recent) {
        if (site.world().equals(world) && site.points().contains(at)) {
          return site.points();
        }
      }
    }
    return null;
  }

  private static List<Point> guideOf(PlayerRef player) {
    WandData wand = ArchitectChalkLine.plannedWand(player);
    ArchitectChalkLine chalk = ArchitectBuilds.find(ArchitectChalkLine.ID, ArchitectChalkLine.class);
    return wand == null || chalk == null ? List.of() : chalk.guide(player, wand);
  }

  long getUndoWindowMillis(int level) {
    return (long) (getConfig().undoWindowSeconds * Math.pow(2D, Math.max(0, level - 1)) * 1000D);
  }

  int getUndoBatches(int level) {
    return Math.max(1, getConfig().undoBuildsAtFirstLevel + ((level - 1) * getConfig().undoBuildsPerLevel));
  }

  int getReplaceBlocks(int level) {
    return Math.max(1, switch (Math.max(1, Math.min(3, level))) {
      case 1 -> getConfig().replaceBlocksLevel1;
      case 2 -> getConfig().replaceBlocksLevel2;
      default -> getConfig().replaceBlocksLevel3;
    });
  }

  private record Site(UUID world, Set<Point> points) {
  }

  @ConfigDescription("Erase, pick up and rotate your placements, undo your last builds, and replace a guide or build with another block.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Seconds after a build during which you can undo it at level 1; the window doubles each level.", impact = "Higher values let you undo older builds.")
    double undoWindowSeconds = 60;
    @ConfigDoc(value = "Builds you can undo, newest first, at level 1.", impact = "Higher values let you step further back.")
    int undoBuildsAtFirstLevel = 1;
    @ConfigDoc(value = "Undoable builds added per level after the first.", impact = "Higher values reward higher levels more.")
    int undoBuildsPerLevel = 2;
    @ConfigDoc(value = "Blocks one replace swaps at level 1.", impact = "Higher values swap larger areas at once.")
    int replaceBlocksLevel1 = 64;
    @ConfigDoc(value = "Blocks one replace swaps at level 2.", impact = "Higher values swap larger areas at once.")
    int replaceBlocksLevel2 = 160;
    @ConfigDoc(value = "Blocks one replace swaps at level 3.", impact = "Higher values swap larger areas at once.")
    int replaceBlocksLevel3 = 320;
    @ConfigDoc(value = "Durability a replace takes from the held tool per block it breaks.", impact = "Higher values wear tools faster.")
    int durabilityPerBlock = 1;
    @ConfigDoc(value = "Blocks an undo or replace changes per tick, before the server-wide cap.", impact = "Higher values finish sooner but cost more per tick.")
    int blocksPerTick = 8;
  }
}
