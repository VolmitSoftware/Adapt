package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Item facts for tests: vanilla tool durabilities built in, block hardness and interactable blocks settable. */
public final class FakeItems implements ItemOps {
  private final Map<Id, Integer> durability = new HashMap<>(Map.of(
      Id.minecraft("wooden_pickaxe"), 59,
      Id.minecraft("stone_pickaxe"), 131,
      Id.minecraft("copper_pickaxe"), 190,
      Id.minecraft("iron_pickaxe"), 250,
      Id.minecraft("golden_pickaxe"), 32,
      Id.minecraft("diamond_pickaxe"), 1561,
      Id.minecraft("netherite_pickaxe"), 2031));
  private final Map<Id, Float> hardness = new HashMap<>();
  private final Map<Id, Float> blastResistance = new HashMap<>();
  private final Map<Id, Float> friction = new HashMap<>();
  private final Map<Id, Set<Id>> unsupportedEnchants = new HashMap<>();
  private final Map<Id, Set<Id>> conflicts = new HashMap<>();
  private final Map<String, List<ItemRef>> storedItems = new ConcurrentHashMap<>();
  private final Set<Id> interactable = new HashSet<>(Set.of(Id.minecraft("chest"), Id.minecraft("oak_door"),
      Id.minecraft("lever"), Id.minecraft("crafting_table")));

  private volatile Predicate<Id> knownItems = (Id type) -> true;

  /** Limits {@link #create} and {@link #isItem} to items a pinned Minecraft version has; others create empty stacks. */
  public void knownItems(Predicate<Id> known) {
    knownItems = known;
  }

  public FakeItems hardness(String vanillaPath, float value, float blast) {
    return hardness(Id.minecraft(vanillaPath), value, blast);
  }

  public FakeItems hardness(Id block, float value, float blast) {
    hardness.put(block, value);
    blastResistance.put(block, blast);
    return this;
  }

  @Override
  public ItemRef create(Id type, int amount) {
    if (!knownItems.test(type)) {
      return empty();
    }
    Integer max = durability.get(type);
    if (max != null) {
      FakeItem tool = FakeItem.tool(type.path(), max);
      tool.setAmount(Math.max(1, amount));
      return tool;
    }
    return new FakeItem(type, Math.max(1, amount));
  }

  @Override
  public ItemRef empty() {
    return FakeItem.empty();
  }

  @Override
  public boolean isItem(Id type) {
    return !type.isAir() && knownItems.test(type);
  }

  @Override
  public boolean isBlock(Id type) {
    return !durability.containsKey(type);
  }

  @Override
  public boolean isEdible(Id type) {
    return false;
  }

  @Override
  public int maxStackSize(Id type) {
    return durability.containsKey(type) ? 1 : 64;
  }

  @Override
  public int maxDurability(Id type) {
    return durability.getOrDefault(type, 0);
  }

  @Override
  public float blockHardness(Id block) {
    return hardness.getOrDefault(block, 0F);
  }

  @Override
  public float blastResistance(Id block) {
    return blastResistance.getOrDefault(block, 0F);
  }

  @Override
  public float blockFriction(Id block) {
    return friction.getOrDefault(block, 0.6F);
  }

  public FakeItems friction(String vanillaPath, float value) {
    friction.put(Id.minecraft(vanillaPath), value);
    return this;
  }

  private final Set<Id> occluding = new HashSet<>(Set.of(Id.minecraft("stone"), Id.minecraft("dirt"),
      Id.minecraft("oak_planks"), Id.minecraft("bricks")));

  public FakeItems occluding(String vanillaPath) {
    occluding.add(Id.minecraft(vanillaPath));
    return this;
  }

  @Override
  public boolean isOccluding(Id block) {
    return occluding.contains(block);
  }

  @Override
  public boolean isInteractable(Id block) {
    return interactable.contains(block);
  }

  @Override
  public BlockStateRef blockState(Id block) {
    return new FakeBlockState(block);
  }

  @Override
  public Set<ToolKind> toolActions(ItemRef item) {
    return item instanceof FakeItem fake ? fake.toolActions() : Set.of();
  }

  @Override
  public ItemRef spawner(Id spawnedType) {
    return FakeItem.of("spawner").withSpawnedType(spawnedType);
  }

  @Override
  public ItemRef playerHead(PlayerRef owner) {
    return FakeItem.of("player_head");
  }

  @Override
  public String encode(ItemRef item) {
    return item.type().toString() + "*" + item.amount();
  }

  @Override
  public ItemRef decode(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      return empty();
    }
    int split = encoded.lastIndexOf('*');
    return create(Id.parse(encoded.substring(0, split)), Integer.parseInt(encoded.substring(split + 1)));
  }

  @Override
  public byte[] serialize(ItemRef item) {
    return encode(item).getBytes(StandardCharsets.UTF_8);
  }

  /** Every field a test can tell stacks apart by; real platforms write gzip NBT instead. */
  @Override
  public byte[] encodeNbt(ItemRef item) {
    if (item == null || item.isEmpty()) {
      throw new IllegalArgumentException("Cannot encode an empty item stack");
    }
    StringBuilder encoded = new StringBuilder(item.type().toString()).append('|').append(item.displayName())
        .append('|').append(item.lore()).append('|').append(item.enchantments()).append('|').append(item.itemModel());
    item.data().keys().stream().map(Id::toString).sorted().forEach(key -> encoded.append('|').append(key));
    return encoded.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public ItemRef deserialize(byte[] data) {
    String encoded = new String(data, StandardCharsets.UTF_8);
    if (encoded.lastIndexOf('*') < 0) {
      throw new IllegalArgumentException("Unreadable item data");
    }
    return decode(encoded);
  }

  @Override
  public boolean hasBlockEntity(Id block) {
    String path = block.path();
    return path.contains("chest") || path.equals("barrel") || path.endsWith("furnace") || path.equals("hopper")
        || path.endsWith("sign") || path.equals("spawner");
  }

  @Override
  public ItemRef enchantWithLevels(ItemRef item, int levels, boolean treasure, Random random) {
    return item.copy();
  }

  /** Marks {@code item} as unsupported by {@code enchantment}; every other pair is supported. */
  public FakeItems unsupported(Id enchantment, Id item) {
    unsupportedEnchants.computeIfAbsent(enchantment, key -> new HashSet<>()).add(item);
    return this;
  }

  @Override
  public boolean canEnchant(Id enchantment, ItemRef item) {
    return item != null && !item.isEmpty() && !unsupportedEnchants.getOrDefault(enchantment, Set.of()).contains(item.type());
  }

  @Override
  public byte[] serializeItems(List<ItemRef> items) {
    List<ItemRef> copies = new ArrayList<>(items.size());
    for (ItemRef item : items) {
      copies.add(item == null || item.isEmpty() ? empty() : item.copy());
    }
    String token = "items:" + storedItems.size();
    storedItems.put(token, copies);
    return token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public List<ItemRef> deserializeItems(byte[] data) {
    List<ItemRef> stored = storedItems.get(new String(data, StandardCharsets.UTF_8));
    if (stored == null) {
      throw new IllegalStateException("Unknown item payload");
    }
    List<ItemRef> copies = new ArrayList<>(stored.size());
    for (ItemRef item : stored) {
      copies.add(item.copy());
    }
    return copies;
  }

  /** Marks two enchantments as conflicting; an enchantment always conflicts with itself. */
  public FakeItems conflicting(Id first, Id second) {
    conflicts.computeIfAbsent(first, key -> new HashSet<>()).add(second);
    conflicts.computeIfAbsent(second, key -> new HashSet<>()).add(first);
    return this;
  }

  @Override
  public boolean enchantmentsConflict(Id first, Id second) {
    return first.equals(second) || conflicts.getOrDefault(first, Set.of()).contains(second);
  }

  @Override
  public int enchantmentMinLevel(Id enchantment) {
    return 1;
  }
}
