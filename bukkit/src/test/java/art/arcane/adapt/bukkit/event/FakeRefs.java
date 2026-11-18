package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Static stand-in for BukkitRefs: stable, reversible handles for Bukkit mocks. */
final class FakeRefs implements AutoCloseable {
  private final Map<Class<?>, Map<Object, Object>> wrapped = new HashMap<>();
  private final Map<Object, Object> unwrapped = new IdentityHashMap<>();
  private final MockedStatic<BukkitRefs> refs;

  FakeRefs() {
    refs = Mockito.mockStatic(BukkitRefs.class);
    refs.when(() -> BukkitRefs.player(any(Player.class))).thenAnswer(call -> wrap(call.getArgument(0), PlayerRef.class));
    refs.when(() -> BukkitRefs.entity(any(Entity.class))).thenAnswer(call -> wrap(call.getArgument(0), EntityRef.class));
    refs.when(() -> BukkitRefs.living(any(LivingEntity.class))).thenAnswer(call -> wrap(call.getArgument(0), LivingRef.class));
    refs.when(() -> BukkitRefs.itemEntity(any(Item.class))).thenAnswer(call -> wrap(call.getArgument(0), ItemEntityRef.class));
    refs.when(() -> BukkitRefs.block(any(Block.class))).thenAnswer(call -> wrap(call.getArgument(0), BlockRef.class));
    refs.when(() -> BukkitRefs.item(any(ItemStack.class))).thenAnswer(call -> wrap(call.getArgument(0), ItemRef.class));
    refs.when(() -> BukkitRefs.world(any(World.class))).thenAnswer(call -> wrap(call.getArgument(0), WorldRef.class));
    refs.when(() -> BukkitRefs.id(any(NamespacedKey.class))).thenAnswer(call -> Id.parse(call.getArgument(0).toString()));
    refs.when(() -> BukkitRefs.loc(any(Location.class))).thenAnswer(call -> {
      Location location = call.getArgument(0);
      return new Loc(wrap(location.getWorld(), WorldRef.class), location.getX(), location.getY(), location.getZ(),
          location.getYaw(), location.getPitch());
    });
    refs.when(() -> BukkitRefs.player(any(PlayerRef.class))).thenAnswer(call -> unwrapped.get(call.getArgument(0)));
    refs.when(() -> BukkitRefs.entity(any(EntityRef.class))).thenAnswer(call -> unwrapped.get(call.getArgument(0)));
    refs.when(() -> BukkitRefs.block(any(BlockRef.class))).thenAnswer(call -> unwrapped.get(call.getArgument(0)));
    refs.when(() -> BukkitRefs.item(any(ItemRef.class))).thenAnswer(call -> unwrapped.get(call.getArgument(0)));
    refs.when(() -> BukkitRefs.location(any(Loc.class))).thenAnswer(call -> {
      Loc loc = call.getArgument(0);
      return new Location((World) unwrapped.get(loc.world()), loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
    });
  }

  @SuppressWarnings("unchecked")
  <T> T wrap(Object handle, Class<T> type) {
    if (handle == null) {
      return null;
    }
    Map<Object, Object> byHandle = wrapped.computeIfAbsent(type, ignored -> new IdentityHashMap<>());
    Object existing = byHandle.get(handle);
    if (existing != null) {
      return (T) existing;
    }
    T ref = mock(type);
    if (EntityRef.class.isAssignableFrom(type)) {
      UUID id = handle instanceof Entity entity && entity.getUniqueId() != null ? entity.getUniqueId() : UUID.randomUUID();
      lenient().when(((EntityRef) ref).uuid()).thenReturn(id);
    }
    byHandle.put(handle, ref);
    unwrapped.put(ref, handle);
    return ref;
  }

  @Override
  public void close() {
    refs.close();
  }
}
