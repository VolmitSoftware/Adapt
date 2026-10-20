package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTestItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.block.data.type.PitcherCrop;
import org.bukkit.inventory.ItemStack;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

final class CropField {
  static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000022a1");

  final World world = mock(World.class, withSettings().strictness(Strictness.LENIENT));
  final List<String> physicsFree = new ArrayList<>();
  private final Map<String, Cell> cells = new HashMap<>();
  private final Map<String, Block> blocks = new HashMap<>();
  private final Map<Material, Supplier<List<ItemStack>>> drops = new EnumMap<>(Material.class);
  private final Map<Material, Function<ItemStack, List<ItemStack>>> toolDrops = new EnumMap<>(Material.class);
  private final Set<Material> unharvestable = EnumSet.noneOf(Material.class);
  private final Map<Material, Material> tiers = new EnumMap<>(Material.class);

  CropField() {
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    lenient().when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    lenient().when(world.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
      Location at = invocation.getArgument(0);
      return block(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    });
  }

  static Ageable ageable(int age, int max) {
    Ageable data = mock(Ageable.class, withSettings().strictness(Strictness.LENIENT));
    wireAge(data, age, max);
    lenient().when(data.clone()).thenAnswer(invocation -> ageable(data.getAge(), max));
    return data;
  }

  static PitcherCrop pitcher(int age, Bisected.Half half) {
    PitcherCrop data = mock(PitcherCrop.class, withSettings().strictness(Strictness.LENIENT));
    wireAge(data, age, 4);
    lenient().when(data.getHalf()).thenReturn(half);
    lenient().when(data.clone()).thenAnswer(invocation -> pitcher(data.getAge(), half));
    return data;
  }

  static CaveVinesPlant vines(boolean berries) {
    CaveVinesPlant data = mock(CaveVinesPlant.class, withSettings().strictness(Strictness.LENIENT));
    AtomicBoolean state = new AtomicBoolean(berries);
    lenient().when(data.hasBerries()).thenAnswer(invocation -> state.get());
    lenient().doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setBerries(anyBoolean());
    lenient().when(data.clone()).thenAnswer(invocation -> vines(state.get()));
    return data;
  }

  static Directional facing(BlockFace face) {
    Directional data = mock(Directional.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(data.getFacing()).thenReturn(face);
    return data;
  }

  CropField set(int x, int y, int z, Material type) {
    return set(x, y, z, type, null);
  }

  CropField set(int x, int y, int z, Material type, BlockData data) {
    cells.put(key(x, y, z), new Cell(type, data));
    return this;
  }

  CropField drops(Material type, Supplier<List<ItemStack>> rolled) {
    drops.put(type, rolled);
    return this;
  }

  CropField toolDrops(Material type, Function<ItemStack, List<ItemStack>> rolled) {
    toolDrops.put(type, rolled);
    return this;
  }

  CropField unharvestable(Material type) {
    unharvestable.add(type);
    return this;
  }

  CropField needsTier(Material type, Material pickaxe) {
    tiers.put(type, pickaxe);
    return this;
  }

  private boolean harvests(Material type, ItemStack tool) {
    if (unharvestable.contains(type)) {
      return false;
    }
    Material needed = tiers.get(type);
    return needed == null || tier(tool.getType()) >= tier(needed);
  }

  private static int tier(Material pickaxe) {
    return switch (pickaxe) {
      case WOODEN_PICKAXE, GOLDEN_PICKAXE -> 0;
      case STONE_PICKAXE, COPPER_PICKAXE -> 1;
      case IRON_PICKAXE -> 2;
      case DIAMOND_PICKAXE -> 3;
      case NETHERITE_PICKAXE -> 4;
      default -> -1;
    };
  }

  Material type(int x, int y, int z) {
    Cell cell = cells.get(key(x, y, z));
    return cell == null ? Material.AIR : cell.type;
  }

  BlockData data(int x, int y, int z) {
    Cell cell = cells.get(key(x, y, z));
    return cell == null ? null : cell.data;
  }

  int age(int x, int y, int z) {
    return ((Ageable) data(x, y, z)).getAge();
  }

  Location at(int x, int y, int z) {
    return new Location(world, x, y, z);
  }

  Block block(int x, int y, int z) {
    return blocks.computeIfAbsent(key(x, y, z), ignored -> mockBlock(x, y, z));
  }

  static List<ItemStack> stacks(Material type, int amount) {
    List<ItemStack> list = new ArrayList<>(1);
    list.add(GolemTestItems.stack(type, amount));
    return list;
  }

  static List<ItemStack> stacks(Material first, int firstAmount, Material second, int secondAmount) {
    List<ItemStack> list = stacks(first, firstAmount);
    list.add(GolemTestItems.stack(second, secondAmount));
    return list;
  }

  private Block mockBlock(int x, int y, int z) {
    Block block = mock(Block.class, withSettings().strictness(Strictness.LENIENT));
    String key = key(x, y, z);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getLocation()).thenAnswer(invocation -> new Location(world, x, y, z));
    lenient().when(block.getType()).thenAnswer(invocation -> type(x, y, z));
    lenient().when(block.getBlockData()).thenAnswer(invocation -> data(x, y, z));
    lenient().when(block.getRelative(any(BlockFace.class))).thenAnswer(invocation -> {
      BlockFace face = invocation.getArgument(0);
      return block(x + face.getModX(), y + face.getModY(), z + face.getModZ());
    });
    lenient().doAnswer(invocation -> {
      cells.put(key, new Cell(invocation.getArgument(0), null));
      return null;
    }).when(block).setType(any(Material.class));
    lenient().doAnswer(invocation -> {
      cells.put(key, new Cell(invocation.getArgument(0), null));
      if (!invocation.<Boolean>getArgument(1)) {
        physicsFree.add(key);
      }
      return null;
    }).when(block).setType(any(Material.class), anyBoolean());
    lenient().doAnswer(invocation -> {
      cells.put(key, new Cell(type(x, y, z), invocation.getArgument(0)));
      return null;
    }).when(block).setBlockData(any(BlockData.class));
    lenient().when(block.getDrops()).thenAnswer(invocation -> {
      Supplier<List<ItemStack>> rolled = drops.get(type(x, y, z));
      return rolled == null ? List.of() : rolled.get();
    });
    lenient().when(block.getDrops(any(ItemStack.class))).thenAnswer(invocation -> {
      Function<ItemStack, List<ItemStack>> rolled = toolDrops.get(type(x, y, z));
      return rolled == null ? List.of() : rolled.apply(invocation.getArgument(0));
    });
    lenient().when(block.isPreferredTool(any(ItemStack.class))).thenAnswer(invocation -> harvests(type(x, y, z), invocation.getArgument(0)));
    lenient().when(block.isLiquid()).thenAnswer(invocation -> type(x, y, z) == Material.WATER || type(x, y, z) == Material.LAVA);
    lenient().when(block.applyBoneMeal(any(BlockFace.class))).thenAnswer(invocation -> {
      if (!(data(x, y, z) instanceof Ageable crop) || crop.getAge() >= crop.getMaximumAge()) {
        return false;
      }
      crop.setAge(Math.min(crop.getMaximumAge(), crop.getAge() + 2));
      return true;
    });
    return block;
  }

  private static void wireAge(Ageable data, int age, int max) {
    AtomicInteger state = new AtomicInteger(age);
    lenient().when(data.getAge()).thenAnswer(invocation -> state.get());
    lenient().when(data.getMaximumAge()).thenReturn(max);
    lenient().doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setAge(anyInt());
  }

  private static String key(int x, int y, int z) {
    return x + "," + y + "," + z;
  }

  private record Cell(Material type, BlockData data) {
  }
}
