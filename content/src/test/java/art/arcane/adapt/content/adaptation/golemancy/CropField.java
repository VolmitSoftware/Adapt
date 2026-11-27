package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
  static final Id AIR = Id.minecraft("air");
  private static final Id WATER = Id.minecraft("water");
  private static final Id LAVA = Id.minecraft("lava");
  private static final List<Id> PICKAXE_TIERS = List.of(Id.minecraft("wooden_pickaxe"), Id.minecraft("golden_pickaxe"),
      Id.minecraft("stone_pickaxe"), Id.minecraft("copper_pickaxe"), Id.minecraft("iron_pickaxe"), Id.minecraft("diamond_pickaxe"),
      Id.minecraft("netherite_pickaxe"));
  private static final int[] TIER_OF = {0, 0, 1, 1, 2, 3, 4};

  final WorldRef world = mock(WorldRef.class, withSettings().strictness(Strictness.LENIENT));
  final List<String> physicsFree = new ArrayList<>();
  private final Map<String, Cell> cells = new HashMap<>();
  private final Map<String, BlockRef> blocks = new HashMap<>();
  private final Map<Id, Supplier<List<ItemRef>>> drops = new HashMap<>();
  private final Map<Id, Function<ItemRef, List<ItemRef>>> toolDrops = new HashMap<>();
  private final Set<Id> unharvestable = new HashSet<>();
  private final Map<Id, Id> tiers = new HashMap<>();

  CropField() {
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    lenient().when(world.blockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    lenient().when(world.blockAt(any(Loc.class))).thenAnswer(invocation -> {
      Loc at = invocation.getArgument(0);
      return block(at.blockX(), at.blockY(), at.blockZ());
    });
    lenient().when(world.blockAt(any(BlockPos.class))).thenAnswer(invocation -> {
      BlockPos at = invocation.getArgument(0);
      return block(at.x(), at.y(), at.z());
    });
  }

  static BlockStateRef ageable(int age, int max) {
    return CropState.of(max).with("age", Integer.toString(age));
  }

  static BlockStateRef pitcher(int age, String half) {
    return CropState.of(4).with("age", Integer.toString(age)).with("half", half);
  }

  static BlockStateRef vines(boolean berries) {
    return CropState.of(-1).with("berries", Boolean.toString(berries));
  }

  static BlockStateRef leaves(boolean persistent, int distance) {
    return CropState.of(-1).with("persistent", Boolean.toString(persistent)).with("distance", Integer.toString(distance));
  }

  static BlockStateRef facing(BlockFace face) {
    return CropState.of(-1).with("facing", face.name().toLowerCase(Locale.ROOT));
  }

  CropField set(int x, int y, int z, Id type) {
    return set(x, y, z, type, null);
  }

  CropField set(int x, int y, int z, Id type, BlockStateRef data) {
    cells.put(key(x, y, z), new Cell(type, data));
    return this;
  }

  CropField drops(Id type, Supplier<List<ItemRef>> rolled) {
    drops.put(type, rolled);
    return this;
  }

  CropField toolDrops(Id type, Function<ItemRef, List<ItemRef>> rolled) {
    toolDrops.put(type, rolled);
    return this;
  }

  CropField unharvestable(Id type) {
    unharvestable.add(type);
    return this;
  }

  CropField needsTier(Id type, Id pickaxe) {
    tiers.put(type, pickaxe);
    return this;
  }

  private boolean harvests(Id type, ItemRef tool) {
    if (unharvestable.contains(type)) {
      return false;
    }
    Id needed = tiers.get(type);
    return needed == null || tier(tool.type()) >= tier(needed);
  }

  private static int tier(Id pickaxe) {
    int at = PICKAXE_TIERS.indexOf(pickaxe);
    return at < 0 ? -1 : TIER_OF[at];
  }

  Id type(int x, int y, int z) {
    Cell cell = cells.get(key(x, y, z));
    return cell == null ? AIR : cell.type;
  }

  BlockStateRef data(int x, int y, int z) {
    Cell cell = cells.get(key(x, y, z));
    return cell == null || cell.data == null ? null : cell.data instanceof CropState crop ? crop.withType(cell.type) : cell.data;
  }

  int age(int x, int y, int z) {
    return data(x, y, z).age();
  }

  Loc at(int x, int y, int z) {
    return new Loc(world, x, y, z);
  }

  BlockRef block(int x, int y, int z) {
    return blocks.computeIfAbsent(key(x, y, z), ignored -> mockBlock(x, y, z));
  }

  static List<ItemRef> stacks(Id type, int amount) {
    List<ItemRef> list = new ArrayList<>(1);
    list.add(GolemTestItems.stack(type, amount));
    return list;
  }

  static List<ItemRef> stacks(Id first, int firstAmount, Id second, int secondAmount) {
    List<ItemRef> list = stacks(first, firstAmount);
    list.add(GolemTestItems.stack(second, secondAmount));
    return list;
  }

  private BlockRef mockBlock(int x, int y, int z) {
    BlockRef block = mock(BlockRef.class, withSettings().strictness(Strictness.LENIENT));
    String key = key(x, y, z);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.y()).thenReturn(y);
    lenient().when(block.z()).thenReturn(z);
    lenient().when(block.pos()).thenReturn(new BlockPos(x, y, z));
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.location()).thenAnswer(invocation -> new Loc(world, x, y, z));
    lenient().when(block.center()).thenAnswer(invocation -> new Loc(world, x + 0.5D, y + 0.5D, z + 0.5D));
    lenient().when(block.type()).thenAnswer(invocation -> type(x, y, z));
    lenient().when(block.isAir()).thenAnswer(invocation -> type(x, y, z).isAir());
    lenient().when(block.state()).thenAnswer(invocation -> {
      BlockStateRef data = data(x, y, z);
      return data == null ? CropState.of(-1).withType(type(x, y, z)) : data;
    });
    lenient().when(block.relative(any(BlockFace.class))).thenAnswer(invocation -> {
      BlockFace face = invocation.getArgument(0);
      return block(x + face.dx(), y + face.dy(), z + face.dz());
    });
    lenient().doAnswer(invocation -> {
      cells.put(key, new Cell(invocation.getArgument(0), null));
      if (!invocation.<Boolean>getArgument(1)) {
        physicsFree.add(key);
      }
      return null;
    }).when(block).setType(any(Id.class), anyBoolean());
    lenient().doAnswer(invocation -> {
      cells.put(key, new Cell(type(x, y, z), invocation.getArgument(0)));
      return null;
    }).when(block).setState(any(BlockStateRef.class), anyBoolean());
    lenient().when(block.drops(any(), any())).thenAnswer(invocation -> {
      ItemRef tool = invocation.getArgument(0);
      if (tool == null) {
        Supplier<List<ItemRef>> rolled = drops.get(type(x, y, z));
        return rolled == null ? List.of() : rolled.get();
      }
      Function<ItemRef, List<ItemRef>> rolled = toolDrops.get(type(x, y, z));
      return rolled == null ? List.of() : rolled.apply(tool);
    });
    lenient().when(block.isPreferredTool(any(ItemRef.class))).thenAnswer(invocation -> harvests(type(x, y, z), invocation.getArgument(0)));
    lenient().when(block.isLiquid()).thenAnswer(invocation -> type(x, y, z).equals(WATER) || type(x, y, z).equals(LAVA));
    lenient().when(block.applyBoneMeal(any(BlockFace.class))).thenAnswer(invocation -> {
      BlockStateRef crop = data(x, y, z);
      if (crop == null || crop.age() < 0 || crop.age() >= crop.maxAge()) {
        return false;
      }
      cells.put(key, new Cell(type(x, y, z), crop.with("age", Integer.toString(Math.min(crop.maxAge(), crop.age() + 2)))));
      return true;
    });
    return block;
  }

  private static String key(int x, int y, int z) {
    return x + "," + y + "," + z;
  }

  private record Cell(Id type, BlockStateRef data) {
  }

  private record CropState(Id type, int max, Map<String, String> properties) implements BlockStateRef {
    static CropState of(int max) {
      return new CropState(AIR, max, Map.of());
    }

    CropState withType(Id next) {
      return new CropState(next, max, properties);
    }

    @Override
    public boolean isAir() {
      return type.isAir();
    }

    @Override
    public String property(String name) {
      return properties.get(name);
    }

    @Override
    public CropState with(String name, String value) {
      Map<String, String> next = new LinkedHashMap<>(properties);
      next.put(name, value);
      return new CropState(type, max, Map.copyOf(next));
    }

    @Override
    public int age() {
      String age = properties.get("age");
      return age == null ? -1 : Integer.parseInt(age);
    }

    @Override
    public int maxAge() {
      return properties.containsKey("age") ? max : -1;
    }

    @Override
    public Id placementItem() {
      return type;
    }

    @Override
    public Id breakSound() {
      return Id.minecraft("block.stone.break");
    }

    @Override
    public boolean isPreferredTool(ItemRef tool) {
      return true;
    }

    @Override
    public boolean isSolid() {
      return !type.isAir();
    }

    @Override
    public String asString() {
      return type + properties.toString();
    }

    @Override
    public Object unwrap() {
      return this;
    }
  }
}
