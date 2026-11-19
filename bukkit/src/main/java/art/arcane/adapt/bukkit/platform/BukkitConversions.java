package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Value and enum conversions between the SPI and Bukkit. Allocation-free where the target is an enum. */
public final class BukkitConversions {
  private BukkitConversions() {
  }

  public static Id id(NamespacedKey key) {
    return key == null ? null : new Id(key.getNamespace(), key.getKey());
  }

  public static NamespacedKey key(Id id) {
    return id == null ? null : new NamespacedKey(id.namespace(), id.path());
  }

  public static Vec3 vec(Vector vector) {
    return new Vec3(vector.getX(), vector.getY(), vector.getZ());
  }

  public static Vector vector(Vec3 vec) {
    return new Vector(vec.x(), vec.y(), vec.z());
  }

  public static Box box(BoundingBox box) {
    return new Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
  }

  public static BoundingBox boundingBox(Box box) {
    return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
  }

  public static BlockPos pos(Block block) {
    return new BlockPos(block.getX(), block.getY(), block.getZ());
  }

  public static GameMode gameMode(org.bukkit.GameMode mode) {
    return switch (mode) {
      case CREATIVE -> GameMode.CREATIVE;
      case ADVENTURE -> GameMode.ADVENTURE;
      case SPECTATOR -> GameMode.SPECTATOR;
      default -> GameMode.SURVIVAL;
    };
  }

  public static org.bukkit.GameMode gameMode(GameMode mode) {
    return switch (mode) {
      case SURVIVAL -> org.bukkit.GameMode.SURVIVAL;
      case CREATIVE -> org.bukkit.GameMode.CREATIVE;
      case ADVENTURE -> org.bukkit.GameMode.ADVENTURE;
      case SPECTATOR -> org.bukkit.GameMode.SPECTATOR;
    };
  }

  public static org.bukkit.inventory.EquipmentSlot slot(EquipmentSlot slot) {
    return BukkitConversionsVersion.slot(slot);
  }

  public static EquipmentSlot slot(org.bukkit.inventory.EquipmentSlot slot) {
    return BukkitConversionsVersion.slot(slot);
  }

  public static org.bukkit.inventory.EquipmentSlot hand(Hand hand) {
    return hand == Hand.MAIN_HAND ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND;
  }

  public static Hand hand(org.bukkit.inventory.EquipmentSlot slot) {
    return slot == org.bukkit.inventory.EquipmentSlot.OFF_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
  }

  public static PlayerTeleportEvent.TeleportCause cause(TeleportCause cause) {
    return switch (cause) {
      case PLUGIN -> PlayerTeleportEvent.TeleportCause.PLUGIN;
      case COMMAND -> PlayerTeleportEvent.TeleportCause.COMMAND;
      case ENDER_PEARL -> PlayerTeleportEvent.TeleportCause.ENDER_PEARL;
      case CHORUS_FRUIT -> PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT;
      case NETHER_PORTAL -> PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;
      case END_PORTAL -> PlayerTeleportEvent.TeleportCause.END_PORTAL;
      case END_GATEWAY -> PlayerTeleportEvent.TeleportCause.END_GATEWAY;
      case SPECTATE -> PlayerTeleportEvent.TeleportCause.SPECTATE;
      case DISMOUNT -> PlayerTeleportEvent.TeleportCause.DISMOUNT;
      case EXIT_BED -> PlayerTeleportEvent.TeleportCause.EXIT_BED;
      case UNKNOWN -> PlayerTeleportEvent.TeleportCause.UNKNOWN;
    };
  }

  public static TeleportCause cause(PlayerTeleportEvent.TeleportCause cause) {
    return BukkitConversionsVersion.cause(cause);
  }

  public static AttributeModifier.Operation operation(AttributeOperation operation) {
    return switch (operation) {
      case ADD_VALUE -> AttributeModifier.Operation.ADD_NUMBER;
      case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_SCALAR;
      case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
    };
  }

  public static AttributeOperation operation(AttributeModifier.Operation operation) {
    return switch (operation) {
      case ADD_NUMBER -> AttributeOperation.ADD_VALUE;
      case ADD_SCALAR -> AttributeOperation.ADD_MULTIPLIED_BASE;
      case MULTIPLY_SCALAR_1 -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
    };
  }

  public static org.bukkit.SoundCategory category(SoundCategory category) {
    return switch (category) {
      case MASTER -> org.bukkit.SoundCategory.MASTER;
      case MUSIC -> org.bukkit.SoundCategory.MUSIC;
      case RECORDS -> org.bukkit.SoundCategory.RECORDS;
      case WEATHER -> org.bukkit.SoundCategory.WEATHER;
      case BLOCKS -> org.bukkit.SoundCategory.BLOCKS;
      case HOSTILE -> org.bukkit.SoundCategory.HOSTILE;
      case NEUTRAL -> org.bukkit.SoundCategory.NEUTRAL;
      case PLAYERS -> org.bukkit.SoundCategory.PLAYERS;
      case AMBIENT -> org.bukkit.SoundCategory.AMBIENT;
      case VOICE -> org.bukkit.SoundCategory.VOICE;
    };
  }

  public static org.bukkit.block.BlockFace face(BlockFace face) {
    return switch (face) {
      case DOWN -> org.bukkit.block.BlockFace.DOWN;
      case UP -> org.bukkit.block.BlockFace.UP;
      case NORTH -> org.bukkit.block.BlockFace.NORTH;
      case SOUTH -> org.bukkit.block.BlockFace.SOUTH;
      case WEST -> org.bukkit.block.BlockFace.WEST;
      case EAST -> org.bukkit.block.BlockFace.EAST;
      case NORTH_EAST -> org.bukkit.block.BlockFace.NORTH_EAST;
      case NORTH_WEST -> org.bukkit.block.BlockFace.NORTH_WEST;
      case SOUTH_EAST -> org.bukkit.block.BlockFace.SOUTH_EAST;
      case SOUTH_WEST -> org.bukkit.block.BlockFace.SOUTH_WEST;
      case WEST_NORTH_WEST -> org.bukkit.block.BlockFace.WEST_NORTH_WEST;
      case NORTH_NORTH_WEST -> org.bukkit.block.BlockFace.NORTH_NORTH_WEST;
      case NORTH_NORTH_EAST -> org.bukkit.block.BlockFace.NORTH_NORTH_EAST;
      case EAST_NORTH_EAST -> org.bukkit.block.BlockFace.EAST_NORTH_EAST;
      case EAST_SOUTH_EAST -> org.bukkit.block.BlockFace.EAST_SOUTH_EAST;
      case SOUTH_SOUTH_EAST -> org.bukkit.block.BlockFace.SOUTH_SOUTH_EAST;
      case SOUTH_SOUTH_WEST -> org.bukkit.block.BlockFace.SOUTH_SOUTH_WEST;
      case WEST_SOUTH_WEST -> org.bukkit.block.BlockFace.WEST_SOUTH_WEST;
      case SELF -> org.bukkit.block.BlockFace.SELF;
    };
  }

  /** The core face of the same name; null maps to {@link BlockFace#SELF}. */
  public static BlockFace face(org.bukkit.block.BlockFace face) {
    if (face == null) {
      return BlockFace.SELF;
    }
    return switch (face) {
      case DOWN -> BlockFace.DOWN;
      case UP -> BlockFace.UP;
      case NORTH -> BlockFace.NORTH;
      case SOUTH -> BlockFace.SOUTH;
      case WEST -> BlockFace.WEST;
      case EAST -> BlockFace.EAST;
      case NORTH_EAST -> BlockFace.NORTH_EAST;
      case NORTH_WEST -> BlockFace.NORTH_WEST;
      case SOUTH_EAST -> BlockFace.SOUTH_EAST;
      case SOUTH_WEST -> BlockFace.SOUTH_WEST;
      case WEST_NORTH_WEST -> BlockFace.WEST_NORTH_WEST;
      case NORTH_NORTH_WEST -> BlockFace.NORTH_NORTH_WEST;
      case NORTH_NORTH_EAST -> BlockFace.NORTH_NORTH_EAST;
      case EAST_NORTH_EAST -> BlockFace.EAST_NORTH_EAST;
      case EAST_SOUTH_EAST -> BlockFace.EAST_SOUTH_EAST;
      case SOUTH_SOUTH_EAST -> BlockFace.SOUTH_SOUTH_EAST;
      case SOUTH_SOUTH_WEST -> BlockFace.SOUTH_SOUTH_WEST;
      case WEST_SOUTH_WEST -> BlockFace.WEST_SOUTH_WEST;
      case SELF -> BlockFace.SELF;
    };
  }
}
