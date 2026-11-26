package art.arcane.adapt.content.adaptation.excavation.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Landshaper's stakes: the first stake sets the height, the second the far corner, and after a short preview the
 * ground between them is levelled to that height. Soft ground above is dug with the shovel from the hotbar and drops
 * into the inventory; holes below are filled with dirt, sand or gravel from the inventory. Placed blocks are never dug,
 * and columns beyond the height reach are left alone so nothing is left hanging.
 */
public final class GradingStakes extends MutationTool<GradingStakes.Data> {
  static final String ID = "tool-excavation-grading-stakes";
  private static final int HOTBAR = 9;
  private static final Id FARMLAND = Id.minecraft("farmland");
  private static final List<Id> FILL = List.of(Id.minecraft("dirt"), Id.minecraft("sand"), Id.minecraft("gravel"));
  private static final int STAKE_MARKER_TICKS = 1200;
  private static final Rgb OUTLINE = new Rgb(200, 160, 90);

  private final Landshaper landshaper;
  private final Map<UUID, Grade> grades = new ConcurrentHashMap<>();

  GradingStakes(Landshaper landshaper) {
    super(ID, landshaper, Id.minecraft("stick"), 1, Data.class, ExcavationMessages.GRADING_STAKES_NAME);
    this.landshaper = landshaper;
  }

  /** The first stake, once planted. */
  public static final class Data {
    @Nullable String world;
    int x;
    int y;
    int z;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shapeless(Id.adapt(ID), List.of(Ingredient.of(Id.minecraft("stick")),
        Ingredient.of(Id.minecraft("stick")), Ingredient.of(Id.minecraft("string")),
        Ingredient.of(Id.minecraft("clay_ball"))), create(new Data())));
  }

  @Override
  protected void applyLore(Data data, List<String> lines) {
    if (data.world != null) {
      lines.add(C.GRAY + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_STAKE, trusted("x", String.valueOf(data.x)),
          trusted("y", String.valueOf(data.y)), trusted("z", String.valueOf(data.z))));
    }
  }

  @Override
  protected void use(ToolUse use) {
    if (!use.rightClick()) {
      return;
    }
    PlayerRef player = use.player();
    ItemRef item = use.item();
    if (use.sneaking()) {
      store(player, item, new Data());
      Grade pending = grades.remove(player.uuid());
      if (pending != null && pending.started().get()) {
        BuildBatches.get().cancel(player);
      }
      displays().clearViewer(landshaper.getName(), player.uuid());
      AdaptHud.actionBar(player, C.GRAY + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_PULLED));
      return;
    }

    BlockRef block = use.block();
    if (block == null) {
      return;
    }
    Data data = read(item);
    if (data == null || data.world == null || !data.world.equals(block.world().uid().toString())) {
      Data first = new Data();
      first.world = block.world().uid().toString();
      first.x = block.x();
      first.y = block.y();
      first.z = block.z();
      store(player, item, first);
      displays().showBlock(landshaper.getName(), "stake", player, block.location(), block.state(), OUTLINE,
          STAKE_MARKER_TICKS);
      return;
    }

    if (grades.containsKey(player.uuid()) || BuildBatches.get().isRunning(player)) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_BUSY));
      return;
    }
    int side = landshaper.getGradeSide(use.level());
    int minX = Math.min(data.x, block.x());
    int maxX = Math.max(data.x, block.x());
    int minZ = Math.min(data.z, block.z());
    int maxZ = Math.max(data.z, block.z());
    if (maxX - minX + 1 > side || maxZ - minZ + 1 > side) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_TOO_WIDE,
          trusted("size", String.valueOf(side))));
      return;
    }

    store(player, item, new Data());
    Grade grade = new Grade(block.world(), minX, maxX, minZ, maxZ, data.y, new AtomicBoolean(),
        ConcurrentHashMap.newKeySet());
    grades.put(player.uuid(), grade);
    int preview = Math.max(1, landshaper.getConfig().gradePreviewTicks);
    BlockStateRef ghost = AdaptCore.platform().host().items().blockState(Id.minecraft("glass"));
    if (ghost != null) {
      displays().showCuboid(landshaper.getName(), "plane", player, new Loc(block.world(), minX, data.y + 1, minZ),
          new Vec3(maxX - minX + 1, 0.1D, maxZ - minZ + 1), ghost, OUTLINE, false, preview);
    }
    if (!Tasks.runEntity(player, () -> run(player, grade), preview, () -> grades.remove(player.uuid(), grade))) {
      grades.remove(player.uuid(), grade);
    }
  }

  /** Whether {@code block}, broken by {@code player}'s running grade, sends its drops to the inventory. */
  boolean collects(PlayerRef player, BlockRef block) {
    Grade grade = grades.get(player.uuid());
    return grade != null && grade.digging().contains(block.pos().asLong());
  }

  private void run(PlayerRef player, Grade grade) {
    if (grades.get(player.uuid()) != grade) {
      return;
    }
    displays().clearViewer(landshaper.getName(), player.uuid());
    int level = level(player);
    int slot = shovelSlot(player.inventory());
    if (level <= 0 || !player.isOnline() || !player.world().uid().equals(grade.world().uid())) {
      grades.remove(player.uuid(), grade);
      return;
    }
    if (slot < 0) {
      grades.remove(player.uuid(), grade);
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_NO_SHOVEL));
      return;
    }
    player.inventory().setHeldSlot(slot);

    int reach = landshaper.getGradeHeight(level);
    List<BlockRef> cuts = new ArrayList<>();
    List<BlockRef> fills = new ArrayList<>();
    for (int x = grade.minX(); x <= grade.maxX(); x++) {
      for (int z = grade.minZ(); z <= grade.maxZ(); z++) {
        BlockRef surface = grade.world().blockAt(x, grade.height(), z);
        if (!Tasks.isOwnedByCurrentRegion(surface.location())) {
          continue;
        }
        cut(player, surface, reach, cuts);
        fill(player, surface, reach, fills);
      }
    }
    cuts.sort(Comparator.comparingInt((BlockRef block) -> -block.y()));
    fills.sort(Comparator.comparingInt(BlockRef::y));
    if (cuts.isEmpty() && fills.isEmpty()) {
      grades.remove(player.uuid(), grade);
      landshaper.gradeDone(player, List.of(), new BuildResult(0, 0, 0, 0, BuildResult.Stop.DONE));
      return;
    }

    TaskHandle[] watch = new TaskHandle[1];
    BuildBatch.Builder batch = BuildBatch.of(landshaper, player)
        .limit(Math.max(1, landshaper.getConfig().gradeMaxBlocks))
        .pace(Math.max(1, landshaper.getConfig().gradeBlocksPerTick))
        .onDone((BuildResult result) -> {
          if (watch[0] != null) {
            watch[0].cancel();
          }
          grades.remove(player.uuid(), grade);
          landshaper.gradeDone(player, dug(cuts), result);
        });
    int durability = Math.max(0, landshaper.getConfig().durabilityCostPerBlock);
    for (BlockRef cut : cuts) {
      grade.digging().add(cut.pos().asLong());
      batch.breakWith(cut, durability, landshaper::isShovel);
    }
    Map<BlockRef, Id> units = assignFill(player.inventory(), cuts, fills);
    for (Map.Entry<BlockRef, Id> fill : units.entrySet()) {
      BlockStateRef state = AdaptCore.platform().host().items().blockState(fill.getValue());
      if (state != null) {
        batch.place(fill.getKey(), state);
      }
    }
    grade.started().set(true);
    if (!BuildBatches.get().start(batch.build())) {
      grades.remove(player.uuid(), grade);
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_BUSY));
      return;
    }
    watch[0] = Tasks.repeatEntity(player, () -> {
      if (!landshaper.isShovel(player.inventory().mainHand())) {
        BuildBatches.get().cancel(player);
      }
    }, 1, 1, () -> {
    });
    landshaper.gradeFx(player, grade.world(), grade.minX(), grade.maxX(), grade.minZ(), grade.maxZ(), grade.height());
  }

  /**
   * Soft ground above the plane in this column, top down, when the column's ground ends within reach. The walk stops at
   * the first block that stays (placed, farmland, protected, not soft, or holding something up), so nothing above it is
   * left floating.
   */
  private void cut(PlayerRef player, BlockRef surface, int reach, List<BlockRef> cuts) {
    if (ExcavationBlocks.isShovelable(surface.relative(0, reach + 1, 0).type())) {
      return;
    }
    for (int dy = reach; dy >= 1; dy--) {
      BlockRef block = surface.relative(0, dy, 0);
      Id type = block.type();
      if (ExcavationBlocks.isShovelable(type) && !FARMLAND.equals(type) && !MutationPayouts.farmed(block)
          && holdsNothing(block.relative(0, 1, 0)) && landshaper.canBlockBreak(player, block.location())) {
        cuts.add(block);
      } else if (!block.isAir() && !block.isReplaceable()) {
        return;
      }
    }
  }

  /** The open cells from the plane down to solid ground, when that ground lies within reach. */
  private void fill(PlayerRef player, BlockRef surface, int reach, List<BlockRef> fills) {
    List<BlockRef> hole = new ArrayList<>(reach);
    for (int dy = 0; dy < reach; dy++) {
      BlockRef block = surface.relative(0, -dy, 0);
      if (!block.isAir() && !block.isReplaceable()) {
        break;
      }
      hole.add(block);
    }
    if (hole.isEmpty()) {
      return;
    }
    BlockRef floor = hole.getLast().relative(0, -1, 0);
    if (floor.isAir() || floor.isReplaceable()) {
      return;
    }
    for (BlockRef cell : hole) {
      if (!landshaper.canBlockPlace(player, cell.location())) {
        return;
      }
    }
    fills.addAll(hole);
  }

  /** Air, a soft block or replaceable growth above: nothing that would be left floating or broken by the dig. */
  private static boolean holdsNothing(BlockRef above) {
    return above.isAir() || above.isReplaceable() || ExcavationBlocks.isShovelable(above.type());
  }

  /**
   * One fill item per cell, lowest first: what the inventory holds now plus what the cuts will drop, dirt, then sand,
   * then gravel. Cells beyond that supply ask for dirt, and the batch stops when none is left.
   */
  private static Map<BlockRef, Id> assignFill(PlayerInventoryRef inventory, List<BlockRef> cuts, List<BlockRef> fills) {
    Map<Id, Integer> supply = new LinkedHashMap<>();
    for (Id type : FILL) {
      supply.put(type, count(inventory, type));
    }
    for (BlockRef cut : cuts) {
      Id drop = fillDrop(cut.type());
      if (drop != null) {
        supply.merge(drop, 1, Integer::sum);
      }
    }
    Map<BlockRef, Id> units = new LinkedHashMap<>();
    for (BlockRef cell : fills) {
      Id unit = FILL.getFirst();
      for (Id type : FILL) {
        int left = supply.get(type);
        if (left > 0) {
          supply.put(type, left - 1);
          unit = type;
          break;
        }
      }
      units.put(cell, unit);
    }
    return units;
  }

  private static @Nullable Id fillDrop(Id cut) {
    if (!cut.isMinecraft()) {
      return null;
    }
    return switch (cut.path()) {
      case "dirt", "coarse_dirt", "rooted_dirt", "grass_block", "dirt_path", "mycelium", "podzol" -> FILL.getFirst();
      case "sand" -> FILL.get(1);
      case "gravel" -> FILL.get(2);
      default -> null;
    };
  }

  private static int count(PlayerInventoryRef inventory, Id type) {
    ItemRef unit = AdaptCore.platform().host().items().create(type, 1);
    int total = 0;
    for (ItemRef item : inventory.contents()) {
      if (item != null && !item.isEmpty() && item.isSimilar(unit)) {
        total += item.amount();
      }
    }
    return total;
  }

  private void store(PlayerRef player, ItemRef item, Data data) {
    write(item, data);
    player.inventory().setMainHand(item);
  }

  private int shovelSlot(PlayerInventoryRef inventory) {
    if (landshaper.isShovel(inventory.mainHand())) {
      return inventory.heldSlot();
    }
    for (int slot = 0; slot < HOTBAR; slot++) {
      if (landshaper.isShovel(inventory.item(slot))) {
        return slot;
      }
    }
    return -1;
  }

  private static List<BlockRef> dug(List<BlockRef> cuts) {
    List<BlockRef> dug = new ArrayList<>(cuts.size());
    for (BlockRef cut : cuts) {
      if (cut.isAir()) {
        dug.add(cut);
      }
    }
    return dug;
  }

  private static ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
  }

  private record Grade(WorldRef world, int minX, int maxX, int minZ, int maxZ, int height, AtomicBoolean started,
                       Set<Long> digging) {
  }
}
