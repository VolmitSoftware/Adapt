package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.List;
import java.util.Random;

/** Item factory and item-type facts. Callable from any thread. */
public interface ItemOps {
  ItemRef create(Id type, int amount);

  default ItemRef create(Id type) {
    return create(type, 1);
  }

  ItemRef empty();

  boolean isItem(Id type);

  boolean isBlock(Id type);

  boolean isEdible(Id type);

  int maxStackSize(Id type);

  /** Default durability of the item type; 0 when it takes no damage. */
  int maxDurability(Id type);

  /** Default hardness of the block type; 0 for non-blocks. */
  float blockHardness(Id block);

  /** Blast resistance of the block type; 0 for non-blocks. */
  float blastResistance(Id block);

  /** Ground friction (slipperiness) of the block type: 0.6 for ordinary blocks, 0.98 for ice; 0.6 for non-blocks. */
  float blockFriction(Id block);

  /** True when right-clicking the block type does something by itself (doors, chests, buttons, levers). */
  boolean isInteractable(Id block);

  /** True when the block type fully blocks light and conducts redstone, as Bukkit's Material#isOccluding. */
  boolean isOccluding(Id block);

  /** Default state of the block type, or null for non-blocks. */
  BlockStateRef blockState(Id block);

  /** A spawner item that places a spawner of {@code spawnedType}; a plain spawner when it is null. */
  ItemRef spawner(Id spawnedType);

  /** A player head showing {@code owner}'s skin. */
  ItemRef playerHead(PlayerRef owner);

  /** Platform-native string form, used for golem cargo and similar per-platform storage. */
  String encode(ItemRef item);

  /** Decodes {@link #encode} output; returns an empty ref for unreadable input. */
  ItemRef decode(String encoded);

  /**
   * Platform-native binary form of one non-empty stack (Paper's {@code serializeAsBytes} on Bukkit), for item
   * snapshots kept in entity data or files on the same platform. Throws when the stack cannot be serialized.
   */
  byte[] serialize(ItemRef item);

  /** Decodes {@link #serialize} output; throws {@link IllegalArgumentException} for unreadable input. */
  ItemRef deserialize(byte[] data);

  /** True when placing {@code block} creates a block entity (chests, furnaces, signs...). */
  boolean hasBlockEntity(Id block);

  /** Enchants a copy of {@code item} like an enchanting table at {@code levels}; books become enchanted books. */
  ItemRef enchantWithLevels(ItemRef item, int levels, boolean treasure, Random random);

  /** True when {@code enchantment} supports {@code item} (Bukkit {@code Enchantment#canEnchantItem}); conflicts are not checked. */
  boolean canEnchant(Id enchantment, ItemRef item);

  /**
   * Platform-native binary form of a stack array keeping empty entries in place (Paper's
   * {@code serializeItemsAsBytes} framing on Bukkit). Null or empty entries are written as empty slots.
   */
  byte[] serializeItems(List<ItemRef> items);

  /**
   * Decodes {@link #serializeItems} output with empty refs for empty slots; throws {@link IllegalStateException} for
   * a blob this platform cannot read, so callers keep it instead of treating it as empty.
   */
  List<ItemRef> deserializeItems(byte[] data);

  /** True when the two enchantments cannot share a stack, including an enchantment with itself. */
  boolean enchantmentsConflict(Id first, Id second);

  /** Lowest level of the enchantment (Bukkit {@code getStartLevel}). */
  int enchantmentMinLevel(Id enchantment);
}
