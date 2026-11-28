package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Random;
import java.util.Set;

/** Item factory and item-type facts. Callable from any thread. */
@ApiStatus.AvailableSince("1.0.0")
public interface ItemOps {
  /** New stack of the item type with at least one item; an empty ref when the id is not an item. */
  ItemRef create(Id type, int amount);

  /** New stack of one item of the type. */
  default ItemRef create(Id type) {
    return create(type, 1);
  }

  /** The empty stack, as found in an empty slot. */
  ItemRef empty();

  /** True when the id names an item type. */
  boolean isItem(Id type);

  /** True when the id names a block type. */
  boolean isBlock(Id type);

  /** True when the item type can be eaten or drunk as food. */
  boolean isEdible(Id type);

  /** Default stack size limit of the item type; 0 when the id is not an item. */
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

  /**
   * Kinds whose digging action the mod loader says the stack performs (Forge {@code ToolActions}, NeoForge
   * {@code ItemAbilities}); empty where the loader or version has no digging actions.
   */
  @ApiStatus.Experimental
  Set<ToolKind> toolActions(ItemRef item);

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

  /**
   * Gzip-compressed binary NBT compound of one non-empty stack in this Minecraft version's own save form (the
   * ItemStack codec with registries from 1.20.5, {@code ItemStack#save} before), as Paper's {@code serializeAsBytes}
   * writes it; it may carry an extra {@code DataVersion} entry. A client on the same Minecraft version rebuilds the
   * exact stack from it. Throws when the stack cannot be encoded.
   */
  byte[] encodeNbt(ItemRef item);

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
