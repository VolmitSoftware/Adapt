package art.arcane.adapt.bukkit.platform.world;

import org.bukkit.inventory.BlockInventoryHolder;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitPlatform;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.bukkit.platform.data.BlockDataStore;
import art.arcane.adapt.bukkit.platform.data.PdcDataHolder;
import art.arcane.adapt.bukkit.platform.entity.BukkitInventoryRef;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.platform.world.CampfireRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import art.arcane.adapt.platform.world.LidRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.block.Campfire;
import org.bukkit.block.Furnace;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.block.TileState;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Block position handle over a Bukkit block. Block data lives in {@link BlockDataStore} under the plugin's
 * namespace.
 */
public final class BukkitBlockRef implements BlockRef {
  private final Block block;
  private final BlockPos pos;

  public BukkitBlockRef(Block block) {
    this.block = Objects.requireNonNull(block, "block");
    this.pos = BukkitConversions.pos(block);
  }

  public Block block() {
    return block;
  }

  @Override
  public WorldRef world() {
    return BukkitRefs.world(block.getWorld());
  }

  @Override
  public BlockPos pos() {
    return pos;
  }

  @Override
  public Loc location() {
    return new Loc(world(), pos.x(), pos.y(), pos.z());
  }

  @Override
  public Id type() {
    return BukkitResolve.id(block.getType());
  }

  @Override
  public BlockStateRef state() {
    return new BukkitBlockStateRef(block.getBlockData());
  }

  @Override
  public void setState(BlockStateRef state, boolean applyPhysics) {
    block.setBlockData(BukkitRefs.blockData(state), applyPhysics);
  }

  @Override
  public void setType(Id type, boolean applyPhysics) {
    Material material = BukkitResolve.material(type);
    if (material != null && material.isBlock()) {
      block.setType(material, applyPhysics);
    }
  }

  @Override
  public BlockRef relative(BlockFace face) {
    return new BukkitBlockRef(block.getRelative(face.dx(), face.dy(), face.dz()));
  }

  @Override
  public BlockRef relative(BlockFace face, int distance) {
    return new BukkitBlockRef(block.getRelative(face.dx() * distance, face.dy() * distance, face.dz() * distance));
  }

  @Override
  public BlockRef relative(int dx, int dy, int dz) {
    return new BukkitBlockRef(block.getRelative(dx, dy, dz));
  }

  @Override
  public boolean isAir() {
    return BukkitResolve.isAir(block.getType());
  }

  @Override
  public boolean isLiquid() {
    return block.isLiquid();
  }

  @Override
  public boolean isPassable() {
    return block.isPassable();
  }

  @Override
  public boolean isSolid() {
    return block.isSolid();
  }

  @Override
  public boolean isReplaceable() {
    return block.isReplaceable();
  }

  @Override
  public InventoryRef blockInventory() {
    BlockState state = PaperCompat.liveState(block);
    if (state instanceof Chest chest) {
      return new BukkitInventoryRef(chest.getBlockInventory());
    }
    return state instanceof BlockInventoryHolder holder ? new BukkitInventoryRef(holder.getInventory()) : null;
  }

  @Override
  public List<Box> collisionBoxes() {
    Collection<BoundingBox> boxes = block.getCollisionShape().getBoundingBoxes();
    if (boxes.isEmpty()) {
      return List.of();
    }
    List<Box> shifted = new ArrayList<>(boxes.size());
    for (BoundingBox box : boxes) {
      shifted.add(BukkitConversions.box(box).shift(pos.x(), pos.y(), pos.z()));
    }
    return shifted;
  }

  @Override
  public int lightLevel() {
    return block.getLightLevel();
  }

  @Override
  public int skyLight() {
    return block.getLightFromSky();
  }

  @Override
  public float hardness() {
    return block.getType().getHardness();
  }

  @Override
  public boolean isPreferredTool(ItemRef tool) {
    return block.isPreferredTool(BukkitRefs.item(tool));
  }

  @Override
  public List<ItemRef> drops(ItemRef tool, EntityRef breaker) {
    ItemStack stack = tool == null ? null : BukkitRefs.item(tool);
    Entity entity = breaker == null ? null : BukkitRefs.entity(breaker);
    Collection<ItemStack> drops = block.getDrops(stack, entity);
    List<ItemRef> wrapped = new ArrayList<>(drops.size());
    for (ItemStack drop : drops) {
      wrapped.add(BukkitRefs.item(drop));
    }
    return wrapped;
  }

  @Override
  public boolean breakNaturally(ItemRef tool) {
    return tool == null || tool.isEmpty() ? block.breakNaturally() : block.breakNaturally(BukkitRefs.item(tool));
  }

  @Override
  public Id biome() {
    return BukkitResolve.id(block.getBiome());
  }

  @Override
  public double temperature() {
    return block.getTemperature();
  }

  @Override
  public boolean applyBoneMeal(BlockFace face) {
    return block.applyBoneMeal(BukkitConversions.face(face));
  }

  @Override
  public InventoryRef container() {
    BlockState state = PaperCompat.liveState(block);
    return state instanceof Container container ? new BukkitInventoryRef(container.getInventory()) : null;
  }

  @Override
  public List<BlockRef> containerHalves() {
    BlockState state = PaperCompat.liveState(block);
    if (!(state instanceof Container)) {
      return List.of();
    }
    if (state instanceof Chest chest && chest.getInventory() instanceof DoubleChestInventory pair) {
      Block left = blockOf(pair.getLeftSide());
      Block right = blockOf(pair.getRightSide());
      if (left != null && right != null) {
        return List.of(new BukkitBlockRef(left), new BukkitBlockRef(right));
      }
    }
    return List.of(this);
  }

  @Override
  public Optional<LidRef> lid() {
    BlockState state = PaperCompat.liveState(block);
    return state instanceof Lidded lidded ? Optional.of(new BukkitLid(state, lidded)) : Optional.empty();
  }

  @Override
  public boolean isRedstonePowered() {
    return block.isBlockPowered() || block.isBlockIndirectlyPowered();
  }

  private static Block blockOf(Inventory half) {
    Location at = half.getLocation();
    return at == null || at.getWorld() == null ? null : at.getBlock();
  }

  @Override
  public Optional<BrewingStandRef> brewingStand() {
    return block.getType() == Material.BREWING_STAND ? Optional.of(new BukkitBrewingStandRef(block)) : Optional.empty();
  }

  @Override
  public Optional<FurnaceRef> furnace() {
    return block.getState() instanceof Furnace ? Optional.of(new BukkitFurnaceRef(block)) : Optional.empty();
  }

  @Override
  public Optional<CampfireRef> campfire() {
    return block.getState() instanceof Campfire ? Optional.of(new BukkitCampfireRef(block)) : Optional.empty();
  }

  @Override
  public DataHolder data() {
    return new PdcDataHolder(new BlockStore(block));
  }

  @Override
  public boolean hasData() {
    Plugin plugin = BukkitPlatform.plugin();
    return plugin != null && BlockDataStore.has(plugin, block);
  }

  @Override
  public DataHolder blockEntityData() {
    return block.getState() instanceof TileState ? new PdcDataHolder(new TileStore(block)) : null;
  }

  @Override
  public ItemRef itemWithBlockState(ItemRef item) {
    BlockState state = block.getState();
    ItemStack copy = BukkitRefs.item(item).clone();
    if (!(state instanceof TileState) || !(copy.getItemMeta() instanceof BlockStateMeta meta)) {
      return null;
    }
    meta.setBlockState(state);
    copy.setItemMeta(meta);
    return BukkitRefs.item(copy);
  }

  @Override
  public Box boundingBox() {
    return BukkitConversions.box(block.getBoundingBox());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BlockRef that && pos.equals(that.pos()) && world().equals(that.world());
  }

  @Override
  public int hashCode() {
    return block.getWorld().getUID().hashCode() * 31 + pos.hashCode();
  }

  @Override
  public String toString() {
    return "BukkitBlockRef[" + block.getWorld().getName() + " " + pos.x() + "," + pos.y() + "," + pos.z() + "]";
  }

  private record TileStore(Block block) implements PdcDataHolder.Store {
    @Override
    public PersistentDataContainer read() {
      return block.getState() instanceof TileState tile ? tile.getPersistentDataContainer() : null;
    }

    @Override
    public void write(Consumer<PersistentDataContainer> edit) {
      if (block.getState() instanceof TileState tile) {
        edit.accept(tile.getPersistentDataContainer());
        tile.update(false, false);
      }
    }
  }

  private record BlockStore(Block block) implements PdcDataHolder.Store {
    @Override
    public PersistentDataContainer read() {
      Plugin plugin = BukkitPlatform.plugin();
      return plugin == null ? null : BlockDataStore.read(plugin, block);
    }

    @Override
    public void write(Consumer<PersistentDataContainer> edit) {
      Plugin plugin = BukkitPlatform.plugin();
      if (plugin != null) {
        BlockDataStore.edit(plugin, block, edit);
      }
    }
  }

  private record BukkitLid(BlockState state, Lidded lidded) implements LidRef {
    @Override
    public boolean viewed() {
      return state instanceof Container container && !container.getInventory().getViewers().isEmpty();
    }

    @Override
    public boolean isOpen() {
      return lidded.isOpen();
    }

    @Override
    public void open() {
      lidded.open();
    }

    @Override
    public void close() {
      lidded.close();
    }
  }
}
