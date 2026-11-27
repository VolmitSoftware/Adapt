package art.arcane.adapt.content.adaptation.excavation.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
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


/**
 * Landshaper's stakes: the first stake sets the height, the second the far corner, and after a short preview the
 * ground between them is levelled to that height. Soft ground above is dug with the shovel from the hotbar and drops
 * into the inventory; holes below are filled with dirt, sand or gravel from the inventory. Placed blocks are never dug,
 * and columns beyond the height reach are left alone so nothing is left hanging.
 */
public final class GradingStakes extends MutationTool<GradingStakes.Data> {
  static final Id ID = Id.adapt("excavation/tool/grading_stakes");
  private static final int HOTBAR = 9;
  private static final Id FARMLAND = Id.minecraft("farmland");
  private static final List<Id> FILL = List.of(Id.minecraft("dirt"), Id.minecraft("sand"), Id.minecraft("gravel"));
  private static final int STAKE_MARKER_TICKS = 1200;
  private static final Rgb OUTLINE = new Rgb(200, 160, 90);

  private final Landshaper landshaper;
  private final Map<UUID, Grade> grades = new ConcurrentHashMap<>();

  GradingStakes(Landshaper landshaper) {
    super(ToolItems.builder(ID, Id.minecraft("stick"), Data.class, new Data()).build(), landshaper, 1);
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
    return List.of(RecipeSpec.shapeless(ID, List.of(Ingredient.of(Id.minecraft("stick")),
        Ingredient.of(Id.minecraft("stick")), Ingredient.of(Id.minecraft("string")),
        Ingredient.of(Id.minecraft("clay_ball"))), create(new Data())));
  }

  @Override
  public void applyLore(Data data, List<String> lines) {
    if (data.world != null) {
      lines.add(TextFormat.GRAY + Text.key("adapt.tool.excavation.grading_stakes.stake").withMarkup("x", String.valueOf(data.x)).withMarkup("y", String.valueOf(data.y)).withMarkup("z", String.valueOf(data.z)).resolve());
    }
  }

  @Override
  public void use(ToolUse use) {
    if (!use.rightClick()) {
      return;
    }
    PlayerRef player = use.player();
    ItemRef item = use.item();
    if (use.sneaking()) {
      store(player, item, new Data());
      Grade pending = grades.remove(player.uuid());
      if (pending != null && pending.started().get()) {
        BuildBatches.cancel(player);
      }
      displays().clearViewer(landshaper.getId().toString(), player.uuid());
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.tool.excavation.grading_stakes.pulled").resolve());
      return;
    }

    BlockRef block = use.block();
    if (block == null) {
      return;
    }
    Data data = read(item).orElse(null);
    if (data == null || data.world == null || !data.world.equals(block.world().uid().toString())) {
      Data first = new Data();
      first.world = block.world().uid().toString();
      first.x = block.x();
      first.y = block.y();
      first.z = block.z();
      store(player, item, first);
      displays().showBlock(landshaper.getId().toString(), "stake", player, block.location(), block.state(), OUTLINE,
          STAKE_MARKER_TICKS);
      return;
    }

    if (grades.containsKey(player.uuid()) || BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.excavation.grading_stakes.busy").resolve());
      return;
    }
    int side = landshaper.getGradeSide(use.level());
    int minX = Math.min(data.x, block.x());
    int maxX = Math.max(data.x, block.x());
    int minZ = Math.min(data.z, block.z());
    int maxZ = Math.max(data.z, block.z());
    if (maxX - minX + 1 > side || maxZ - minZ + 1 > side) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.excavation.grading_stakes.too_wide").withMarkup("size", String.valueOf(side)).resolve());
      return;
    }

    store(player, item, new Data());
    Grade grade = new Grade(block.world(), minX, maxX, minZ, maxZ, data.y, new AtomicBoolean(),
        ConcurrentHashMap.newKeySet());
    grades.put(player.uuid(), grade);
    int preview = Math.max(1, landshaper.getConfig().gradePreviewTicks);
    BlockStateRef ghost = AdaptApi.platform().host().items().blockState(Id.minecraft("glass"));
    if (ghost != null) {
      displays().showCuboid(landshaper.getId().toString(), "plane", player, new Loc(block.world(), minX, data.y + 1, minZ),
          new Vec3(maxX - minX + 1, 0.1D, maxZ - minZ + 1), ghost, OUTLINE, false, preview);
    }
    if (!AdaptApi.scheduler().runEntity(player, () -> run(player, grade), preview, () -> grades.remove(player.uuid(), grade))) {
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
    displays().clearViewer(landshaper.getId().toString(), player.uuid());
    int level = level(player);
    int slot = shovelSlot(player.inventory());
    if (level <= 0 || !player.isOnline() || !player.world().uid().equals(grade.world().uid())) {
      grades.remove(player.uuid(), grade);
      return;
    }
    if (slot < 0) {
      grades.remove(player.uuid(), grade);
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.excavation.grading_stakes.no_shovel").resolve());
      return;
    }
    player.inventory().setHeldSlot(slot);

    int reach = landshaper.getGradeHeight(level);
    List<BlockRef> cuts = new ArrayList<>();
    List<BlockRef> fills = new ArrayList<>();
    for (int x = grade.minX(); x <= grade.maxX(); x++) {
      for (int z = grade.minZ(); z <= grade.maxZ(); z++) {
        BlockRef surface = grade.world().blockAt(x, grade.height(), z);
        if (!AdaptApi.scheduler().owns(surface.location())) {
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
      landshaper.gradeDone(player, List.of(), BuildResult.Stop.DONE, 0, 0);
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
          landshaper.gradeDone(player, dug(cuts), result.stop(), result.broken(), result.placed());
        });
    int durability = Math.max(0, landshaper.getConfig().durabilityCostPerBlock);
    for (BlockRef cut : cuts) {
      grade.digging().add(cut.pos().asLong());
      batch.breakWith(cut, durability, ItemKinds::isShovel);
    }
    Map<BlockRef, Id> units = assignFill(player.inventory(), cuts, fills);
    for (Map.Entry<BlockRef, Id> fill : units.entrySet()) {
      BlockStateRef state = AdaptApi.platform().host().items().blockState(fill.getValue());
      if (state != null) {
        batch.place(fill.getKey(), state);
      }
    }
    grade.started().set(true);
    if (!BuildBatches.start(batch.build())) {
      grades.remove(player.uuid(), grade);
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.excavation.grading_stakes.busy").resolve());
      return;
    }
    watch[0] = AdaptApi.scheduler().repeatEntity(player, () -> {
      if (!ItemKinds.isShovel(player.inventory().mainHand())) {
        BuildBatches.cancel(player);
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
      if (ExcavationBlocks.isShovelable(type) && !FARMLAND.equals(type) && !Mutations.farmed(block)
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
    ItemRef unit = AdaptApi.platform().host().items().create(type, 1);
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
    if (ItemKinds.isShovel(inventory.mainHand())) {
      return inventory.heldSlot();
    }
    for (int slot = 0; slot < HOTBAR; slot++) {
      if (ItemKinds.isShovel(inventory.item(slot))) {
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
    return ViewerDisplayPort.current();
  }

  private record Grade(WorldRef world, int minX, int maxX, int minZ, int maxZ, int height, AtomicBoolean started,
                       Set<Long> digging) {
  }
}
