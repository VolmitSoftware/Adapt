package art.arcane.adapt.bukkit.packet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Equipment packets for entity ids the server does not know (virtual players). Bukkit only sends equipment for
 * real living entities, so this builds {@code ClientboundSetEquipmentPacket} reflectively against the
 * Mojang-mapped server, the way VolmLib's virtual players do.
 */
public final class EquipmentPackets {
  private static volatile EquipmentPackets instance;
  private static volatile boolean unavailable;

  private final Constructor<?> packet;
  private final Method pairOf;
  private final Method asNmsCopy;
  private final Class<?> slotClass;
  private final Method getHandle;
  private final Field connection;
  private final Method send;

  private EquipmentPackets(Player sample) throws ReflectiveOperationException {
    ClassLoader loader = sample.getClass().getClassLoader();
    String craftPackage = sample.getServer().getClass().getPackage().getName();
    Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket", true, loader);
    Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair", true, loader);
    Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet", true, loader);
    this.slotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot", true, loader);
    this.packet = packetClass.getConstructor(int.class, List.class);
    this.pairOf = pairClass.getMethod("of", Object.class, Object.class);
    this.asNmsCopy = Class.forName(craftPackage + ".inventory.CraftItemStack", true, loader).getMethod("asNMSCopy", ItemStack.class);
    this.getHandle = sample.getClass().getMethod("getHandle");
    Object handle = getHandle.invoke(sample);
    this.connection = field(handle.getClass(), "connection");
    this.send = connection.getType().getMethod("send", packetInterface);
  }

  public static boolean send(Player viewer, int entityId, Map<EquipmentSlot, ItemStack> items) {
    EquipmentPackets packets = resolve(viewer);
    if (packets == null || items.isEmpty()) {
      return false;
    }
    try {
      List<Object> slots = new ArrayList<>(items.size());
      for (Map.Entry<EquipmentSlot, ItemStack> entry : items.entrySet()) {
        Object slot = slot(packets.slotClass, entry.getKey());
        if (slot != null) {
          slots.add(packets.pairOf.invoke(null, slot, packets.asNmsCopy.invoke(null, entry.getValue())));
        }
      }
      Object built = packets.packet.newInstance(entityId, slots);
      Object target = packets.connection.get(packets.getHandle.invoke(viewer));
      if (target == null) {
        return false;
      }
      packets.send.invoke(target, built);
      return true;
    } catch (ReflectiveOperationException | RuntimeException failure) {
      return false;
    }
  }

  private static EquipmentPackets resolve(Player viewer) {
    EquipmentPackets current = instance;
    if (current != null || unavailable) {
      return current;
    }
    synchronized (EquipmentPackets.class) {
      if (instance == null && !unavailable) {
        try {
          instance = new EquipmentPackets(viewer);
        } catch (ReflectiveOperationException | RuntimeException failure) {
          unavailable = true;
        }
      }
      return instance;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object slot(Class<?> slotClass, EquipmentSlot slot) {
    String name = switch (slot) {
      case HAND -> "MAINHAND";
      case OFF_HAND -> "OFFHAND";
      default -> slot.name();
    };
    try {
      return Enum.valueOf((Class<Enum>) slotClass, name);
    } catch (IllegalArgumentException missing) {
      return null;
    }
  }

  private static Field field(Class<?> type, String name) throws NoSuchFieldException {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      try {
        Field field = current.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException next) {
        continue;
      }
    }
    throw new NoSuchFieldException(name);
  }
}
