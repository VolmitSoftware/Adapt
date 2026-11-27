package art.arcane.adapt.bukkit.platform.item;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.nms.NMS;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

/** Item factory. {@link #encode} uses the plugin's framed item byte format, Base64 encoded. */
public final class BukkitItemOps implements ItemOps {
  private static final String OBJECT_STREAM_PREFIX = "rO0";

  @Override
  public ItemRef create(Id type, int amount) {
    Material material = BukkitResolve.material(type);
    if (material == null || BukkitResolve.isAir(material) || !material.isItem()) {
      return empty();
    }
    return new BukkitItemRef(new ItemStack(material, Math.max(1, amount)));
  }

  @Override
  public ItemRef empty() {
    return new BukkitItemRef(new ItemStack(Material.AIR));
  }

  @Override
  public boolean isItem(Id type) {
    Material material = BukkitResolve.material(type);
    return material != null && material.isItem();
  }

  @Override
  public boolean isBlock(Id type) {
    Material material = BukkitResolve.material(type);
    return material != null && material.isBlock();
  }

  @Override
  public boolean isEdible(Id type) {
    Material material = BukkitResolve.material(type);
    return material != null && material.isEdible();
  }

  @Override
  public int maxStackSize(Id type) {
    Material material = BukkitResolve.material(type);
    return material == null ? 0 : material.getMaxStackSize();
  }

  @Override
  public int maxDurability(Id type) {
    Material material = BukkitResolve.material(type);
    return material == null ? 0 : material.getMaxDurability();
  }

  @Override
  public float blockHardness(Id block) {
    Material material = BukkitResolve.material(block);
    return material == null || !material.isBlock() ? 0F : material.getHardness();
  }

  @Override
  public float blastResistance(Id block) {
    Material material = BukkitResolve.material(block);
    return material == null || !material.isBlock() ? 0F : material.getBlastResistance();
  }

  @Override
  public float blockFriction(Id block) {
    Material material = BukkitResolve.material(block);
    return material == null || !material.isBlock() ? 0.6F : material.getSlipperiness();
  }

  @Override
  public boolean isInteractable(Id block) {
    Material material = BukkitResolve.material(block);
    return material != null && material.isBlock() && material.isInteractable();
  }

  @Override
  public boolean isOccluding(Id block) {
    Material material = BukkitResolve.material(block);
    return material != null && material.isBlock() && material.isOccluding();
  }

  @Override
  public BlockStateRef blockState(Id block) {
    Material material = BukkitResolve.material(block);
    return material == null || !material.isBlock() ? null : BukkitRefs.blockState(material.createBlockData());
  }

  @Override
  public ItemRef spawner(Id spawnedType) {
    ItemStack spawner = new ItemStack(Material.SPAWNER);
    EntityType type = spawnedType == null ? null : BukkitResolve.entityType(spawnedType);
    if (type != null && spawner.getItemMeta() instanceof BlockStateMeta meta
        && meta.getBlockState() instanceof CreatureSpawner target) {
      target.setSpawnedType(type);
      meta.setBlockState(target);
      spawner.setItemMeta(meta);
    }
    return new BukkitItemRef(spawner);
  }

  @Override
  public ItemRef playerHead(PlayerRef owner) {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    Player player = owner == null ? null : BukkitRefs.player(owner);
    if (player != null && head.getItemMeta() instanceof SkullMeta skullMeta) {
      skullMeta.setOwningPlayer(player);
      head.setItemMeta(skullMeta);
    }
    return new BukkitItemRef(head);
  }

  @Override
  public String encode(ItemRef item) {
    if (item == null || item.isEmpty()) {
      return "";
    }
    return NMS.serializeStack(BukkitRefs.item(item));
  }

  @Override
  public ItemRef decode(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      return empty();
    }
    if (encoded.startsWith(OBJECT_STREAM_PREFIX)) {
      return decodeObjectStream(encoded);
    }
    try {
      ItemStack[] items = PaperCompat.deserializeItems(Base64.getDecoder().decode(encoded));
      if (items == null || items.length == 0 || items[0] == null) {
        return empty();
      }
      return new BukkitItemRef(items[0]);
    } catch (RuntimeException invalid) {
      return empty();
    }
  }

  private ItemRef decodeObjectStream(String encoded) {
    try {
      ItemStack item = NMS.deserializeStack(encoded);
      return item == null ? empty() : new BukkitItemRef(item);
    } catch (RuntimeException invalid) {
      return empty();
    }
  }

  @Override
  public byte[] serialize(ItemRef item) {
    return PaperApi.serialize(BukkitRefs.item(item));
  }

  @Override
  public ItemRef deserialize(byte[] data) {
    return new BukkitItemRef(PaperApi.deserialize(data));
  }

  @Override
  public byte[] encodeNbt(ItemRef item) {
    return PaperApi.serialize(BukkitRefs.item(item));
  }

  @Override
  public boolean hasBlockEntity(Id block) {
    Material material = BukkitResolve.material(block);
    return material != null && material.isBlock() && material.createBlockData().createBlockState() instanceof TileState;
  }

  @Override
  public ItemRef enchantWithLevels(ItemRef item, int levels, boolean treasure, Random random) {
    return new BukkitItemRef(BukkitItem120Version.enchantWithLevels(BukkitRefs.item(item).clone(), levels, treasure, random));
  }

  @Override
  public boolean canEnchant(Id enchantment, ItemRef item) {
    Enchantment resolved = BukkitResolve.enchantment(enchantment);
    return resolved != null && item != null && !item.isEmpty() && resolved.canEnchantItem(BukkitRefs.item(item));
  }

  @Override
  public byte[] serializeItems(List<ItemRef> items) {
    ItemStack[] stacks = new ItemStack[items.size()];
    for (int index = 0; index < stacks.length; index++) {
      ItemRef item = items.get(index);
      stacks[index] = item == null || item.isEmpty() ? null : BukkitRefs.item(item);
    }
    return PaperCompat.serializeItems(stacks);
  }

  @Override
  public List<ItemRef> deserializeItems(byte[] data) {
    ItemStack[] stacks = PaperCompat.deserializeItems(data);
    List<ItemRef> items = new ArrayList<>(stacks.length);
    for (ItemStack stack : stacks) {
      items.add(stack == null ? empty() : new BukkitItemRef(stack));
    }
    return items;
  }

  @Override
  public boolean enchantmentsConflict(Id first, Id second) {
    Enchantment a = BukkitResolve.enchantment(first);
    Enchantment b = BukkitResolve.enchantment(second);
    return a != null && b != null && (a.equals(b) || a.conflictsWith(b));
  }

  @Override
  public int enchantmentMinLevel(Id enchantment) {
    Enchantment type = BukkitResolve.enchantment(enchantment);
    return type == null ? 1 : type.getStartLevel();
  }
}
