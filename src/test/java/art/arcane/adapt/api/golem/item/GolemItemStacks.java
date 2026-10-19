package art.arcane.adapt.api.golem.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class GolemItemStacks {
  static final NamespacedKey KIND_KEY = new NamespacedKey("adapt", "golem_item");
  static final NamespacedKey DATA_KEY = new NamespacedKey("adapt", "golem_item_data");

  private GolemItemStacks() {
  }

  static void wire(ItemStack item, Material type) {
    FireworkEffectMeta meta = mock(FireworkEffectMeta.class);
    PersistentDataContainer container = container(new HashMap<>());
    when(meta.getPersistentDataContainer()).thenReturn(container);
    trackFlags(meta);
    when(item.getType()).thenReturn(type);
    when(item.hasItemMeta()).thenReturn(true);
    when(item.getItemMeta()).thenReturn(meta);
    when(item.setItemMeta(any())).thenReturn(true);
    when(item.clone()).thenReturn(item);
  }

  static ItemStack tagged(Material type, String kindId, String data) {
    ItemStack item = mock(ItemStack.class);
    wire(item, type);
    PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
    container.set(KIND_KEY, PersistentDataType.STRING, kindId);
    if (data != null) {
      container.set(DATA_KEY, PersistentDataType.STRING, data);
    }
    return item;
  }

  static ItemStack plain(Material type) {
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(type);
    when(item.clone()).thenReturn(item);
    return item;
  }

  static String stored(ItemStack item, NamespacedKey key) {
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  private static void trackFlags(FireworkEffectMeta meta) {
    Set<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);
    doAnswer(call -> {
      for (Object flag : call.getArguments()) {
        flags.add((ItemFlag) flag);
      }
      return null;
    }).when(meta).addItemFlags(any(ItemFlag[].class));
    when(meta.getItemFlags()).thenAnswer(call -> Set.copyOf(flags));
    when(meta.hasItemFlag(any(ItemFlag.class))).thenAnswer(call -> flags.contains(call.<ItemFlag>getArgument(0)));
  }

  @SuppressWarnings("unchecked")
  private static PersistentDataContainer container(Map<NamespacedKey, Object> values) {
    PersistentDataContainer container = mock(PersistentDataContainer.class);
    doAnswer(call -> {
      values.put(call.getArgument(0), call.getArgument(2));
      return null;
    }).when(container).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    doAnswer(call -> values.remove(call.<NamespacedKey>getArgument(0))).when(container).remove(any(NamespacedKey.class));
    when(container.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(call -> values.get(call.<NamespacedKey>getArgument(0)));
    when(container.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(call -> values.containsKey(call.<NamespacedKey>getArgument(0)));
    when(container.has(any(NamespacedKey.class)))
        .thenAnswer(call -> values.containsKey(call.<NamespacedKey>getArgument(0)));
    return container;
  }
}
