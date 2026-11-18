package art.arcane.adapt.testkit;

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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Live view of one position in a {@link FakeWorld}; equal when world and position match. */
public final class FakeBlock implements BlockRef {
  private static final Set<Id> FURNACES = Set.of(Id.minecraft("furnace"), Id.minecraft("blast_furnace"), Id.minecraft("smoker"));
  private static final Set<Id> CAMPFIRES = Set.of(Id.minecraft("campfire"), Id.minecraft("soul_campfire"));
  private static final Set<String> REPLACEABLE = Set.of("short_grass", "tall_grass", "fern", "snow", "dead_bush");
  private final FakeWorld world;
  private final BlockPos pos;

  FakeBlock(FakeWorld world, BlockPos pos) {
    this.world = world;
    this.pos = pos;
  }

  @Override
  public WorldRef world() {
    return world;
  }

  @Override
  public BlockPos pos() {
    return pos;
  }

  @Override
  public Loc location() {
    return Loc.of(world, pos);
  }

  @Override
  public Id type() {
    return state().type();
  }

  @Override
  public BlockStateRef state() {
    return world.stateAt(pos);
  }

  @Override
  public void setState(BlockStateRef state, boolean applyPhysics) {
    world.set(pos, state);
  }

  @Override
  public void setType(Id type, boolean applyPhysics) {
    world.set(pos, new FakeBlockState(type));
  }

  @Override
  public BlockRef relative(BlockFace face) {
    return relative(face, 1);
  }

  @Override
  public BlockRef relative(BlockFace face, int distance) {
    return world.blockAt(pos.relative(face, distance));
  }

  @Override
  public BlockRef relative(int dx, int dy, int dz) {
    return world.blockAt(pos.offset(dx, dy, dz));
  }

  @Override
  public boolean isAir() {
    return type().isAir();
  }

  @Override
  public boolean isLiquid() {
    return type().path().equals("water") || type().path().equals("lava");
  }

  @Override
  public boolean isPassable() {
    return isAir() || isLiquid();
  }

  @Override
  public boolean isReplaceable() {
    return isAir() || isLiquid() || REPLACEABLE.contains(type().path());
  }

  @Override
  public InventoryRef blockInventory() {
    return world.blockInventory(pos);
  }

  @Override
  public boolean isSolid() {
    return !isPassable();
  }

  @Override
  public Box boundingBox() {
    return isSolid() ? new Box(pos.x(), pos.y(), pos.z(), pos.x() + 1D, pos.y() + 1D, pos.z() + 1D) : new Box(0D, 0D, 0D, 0D, 0D, 0D);
  }

  @Override
  public List<Box> collisionBoxes() {
    return isSolid() ? List.of(new Box(pos.x(), pos.y(), pos.z(), pos.x() + 1D, pos.y() + 1D, pos.z() + 1D)) : List.of();
  }

  @Override
  public int lightLevel() {
    return 15;
  }

  @Override
  public int skyLight() {
    return 15;
  }

  @Override
  public float hardness() {
    return isAir() ? 0F : 1.5F;
  }

  @Override
  public boolean isPreferredTool(ItemRef tool) {
    return state().isPreferredTool(tool);
  }

  @Override
  public List<ItemRef> drops(ItemRef tool, EntityRef breaker) {
    return isAir() ? List.of() : List.of(new FakeItem(type(), 1));
  }

  @Override
  public boolean breakNaturally(ItemRef tool) {
    if (isAir()) {
      return false;
    }
    for (ItemRef drop : drops(tool, null)) {
      world.dropItemNaturally(location().add(0.5D, 0.5D, 0.5D).position(), drop);
    }
    world.set(pos, FakeBlockState.of("air"));
    return true;
  }

  @Override
  public Id biome() {
    return Id.minecraft("plains");
  }

  @Override
  public double temperature() {
    return 0.8D;
  }

  @Override
  public boolean applyBoneMeal(BlockFace face) {
    BlockStateRef current = state();
    if (current.age() < 0 || current.age() >= current.maxAge()) {
      return false;
    }
    setState(current.with("age", Integer.toString(current.age() + 1)), true);
    return true;
  }

  @Override
  public InventoryRef container() {
    return null;
  }

  @Override
  public List<BlockRef> containerHalves() {
    return container() == null ? List.of() : List.of(this);
  }

  @Override
  public Optional<LidRef> lid() {
    return Optional.empty();
  }

  @Override
  public boolean isRedstonePowered() {
    return false;
  }

  @Override
  public Optional<BrewingStandRef> brewingStand() {
    return type().equals(Id.minecraft("brewing_stand")) ? Optional.of(world.brewingStand(pos)) : Optional.empty();
  }

  @Override
  public Optional<FurnaceRef> furnace() {
    return FURNACES.contains(type()) ? Optional.of(world.furnace(pos)) : Optional.empty();
  }

  @Override
  public Optional<CampfireRef> campfire() {
    return CAMPFIRES.contains(type()) ? Optional.of(world.campfire(pos)) : Optional.empty();
  }

  @Override
  public DataHolder data() {
    return world.blockData(pos);
  }

  @Override
  public boolean hasData() {
    return !world.blockData(pos).isEmpty();
  }

  @Override
  public DataHolder blockEntityData() {
    return world.blockEntityData(pos);
  }

  /** Holder items (those with container contents) take the block inventory's contents and the block entity's name. */
  @Override
  public ItemRef itemWithBlockState(ItemRef item) {
    if (world.blockEntityData(pos) == null && world.blockInventory(pos) == null || item.containerContents().isEmpty()) {
      return null;
    }
    ItemRef copy = item.copy();
    InventoryRef inventory = world.blockInventory(pos);
    copy.setContainerContents(inventory == null ? List.of() : inventory.contents());
    String name = world.blockEntityName(pos);
    if (name != null) {
      copy.setDisplayName(name);
    }
    return copy;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BlockRef that && pos.equals(that.pos()) && world.uid().equals(that.world().uid());
  }

  @Override
  public int hashCode() {
    return Objects.hash(world.uid(), pos);
  }

  @Override
  public String toString() {
    return type() + "@" + pos;
  }
}
