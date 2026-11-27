package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/**
 * A block position in a loaded world. The handle is a coordinate, not a snapshot: reads always
 * reflect the current block. Must be used on the thread that owns the block's region (the server
 * thread on modded). Equality is world plus position.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface BlockRef {
  /** World the block is in. */
  WorldRef world();

  /** Block coordinates. */
  BlockPos pos();

  /** Block X coordinate. */
  default int x() {
    return pos().x();
  }

  /** Block Y coordinate. */
  default int y() {
    return pos().y();
  }

  /** Block Z coordinate. */
  default int z() {
    return pos().z();
  }

  /** Corner location (block coordinates, no centring). */
  Loc location();

  /** Centre of the block. */
  default Loc center() {
    return location().add(0.5D, 0.5D, 0.5D);
  }

  /** Block type id, for example {@code minecraft:stone}. */
  Id type();

  /** Current block state, type plus properties. */
  BlockStateRef state();

  /**
   * Replaces the block with {@code state}. With {@code applyPhysics} neighbours update as for a vanilla placement;
   * without it they are left alone.
   */
  void setState(BlockStateRef state, boolean applyPhysics);

  /** Replaces the block with the default state of {@code type}; no-op when the id is not a block. */
  void setType(Id type, boolean applyPhysics);

  /** Neighbouring block on {@code face}. */
  BlockRef relative(BlockFace face);

  /** Block {@code distance} steps away in the direction of {@code face}. */
  BlockRef relative(BlockFace face, int distance);

  /** Block at the given offset. */
  BlockRef relative(int dx, int dy, int dz);

  /** True for air, cave air and void air. */
  boolean isAir();

  /** True for water and lava blocks. */
  boolean isLiquid();

  /** True when entities can move through the block (no collision shape). */
  boolean isPassable();

  /** True when the block counts as solid, as Bukkit's Block#isSolid. */
  boolean isSolid();

  /** True when a placement here would replace the block (air, grass, water, snow layers and similar). */
  boolean isReplaceable();

  /**
   * This block's own inventory: one half of a double chest, or the inventory of any other block that holds items
   * (containers, lecterns, jukeboxes, chiseled bookshelves, decorated pots); null otherwise.
   */
  InventoryRef blockInventory();

  /** Collision boxes in world coordinates; empty for passable blocks. */
  List<Box> collisionBoxes();

  /** Light level at the block, 0 to 15, the brighter of block and sky light. */
  int lightLevel();

  /** Light this block receives from the sky, 0 to 15. */
  int skyLight();

  /** Hardness of the block, which sets mining time; -1 for unbreakable blocks such as bedrock. */
  float hardness();

  /** True when {@code tool} gets this block's drops: no tool needed, or the correct tool. */
  boolean isPreferredTool(ItemRef tool);

  /** Drops this block would produce for the tool and breaker, without breaking it. */
  List<ItemRef> drops(ItemRef tool, EntityRef breaker);

  /** Breaks the block as if mined with the tool, dropping items. Does not fire a break event. */
  boolean breakNaturally(ItemRef tool);

  /** Biome id at the block, for example {@code minecraft:plains}. */
  Id biome();

  /** Biome temperature at this block, adjusted for height like vanilla rain and snow checks. */
  double temperature();

  /** Applies one bone meal use from {@code face}, like a dispenser would; true when the block grew. */
  boolean applyBoneMeal(BlockFace face);

  /** Container slots for chests, barrels, furnaces, brewing stands and similar; null otherwise. */
  InventoryRef container();

  /**
   * The blocks of the container here: both halves of a double chest (left side first, as Bukkit's
   * {@code DoubleChestInventory} names them), this block for any other container, empty for a non-container.
   */
  List<BlockRef> containerHalves();

  /** The opening lid of a chest, barrel or shulker box here; empty for any other block. */
  Optional<LidRef> lid();

  /** Whether the block receives redstone power, directly or indirectly. */
  boolean isRedstonePowered();

  /** The brewing stand at this block, empty for any other block. */
  Optional<BrewingStandRef> brewingStand();

  /** The furnace, blast furnace or smoker at this block, empty for any other block. */
  Optional<FurnaceRef> furnace();

  /** The campfire or soul campfire at this block, empty for any other block. */
  Optional<CampfireRef> campfire();

  /**
   * Data stored on this block position (custom block data on Bukkit, a chunk attachment on modded).
   * The data does not move with pistons; Adapt cancels piston moves for blocks that carry data.
   */
  DataHolder data();

  /** True when {@link #data()} holds anything for this position. */
  boolean hasData();

  /**
   * Data stored inside this block's block entity (the tile state's persistent data on Bukkit), which lives and dies
   * with the block entity. Writes are saved immediately. Null when the block has no block entity.
   */
  DataHolder blockEntityData();

  /**
   * A copy of {@code item} carrying this block's block-entity state (contents, lock, loot table, custom name and any
   * other block-entity data), written like Bukkit's {@code BlockStateMeta#setBlockState}; null when the item cannot hold
   * block state or this block has no block entity.
   */
  ItemRef itemWithBlockState(ItemRef item);

  /**
   * Bounds of the block's outline shape in world coordinates (Bukkit {@code Block#getBoundingBox}); a zero box at the
   * origin when the shape is empty.
   */
  Box boundingBox();
}
