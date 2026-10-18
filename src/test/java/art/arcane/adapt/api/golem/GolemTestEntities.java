package art.arcane.adapt.api.golem;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

final class GolemTestEntities {
  private GolemTestEntities() {
  }

  static <T extends Entity> T entity(Class<T> type) {
    T entity = mock(type);
    PersistentDataContainer data = mapBacked();
    UUID entityId = UUID.randomUUID();
    lenient().when(entity.getPersistentDataContainer()).thenReturn(data);
    lenient().when(entity.getUniqueId()).thenReturn(entityId);
    return entity;
  }

  static PersistentDataContainer mapBacked() {
    Map<NamespacedKey, Object> values = new HashMap<>();
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.has(any(NamespacedKey.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().doAnswer(invocation -> {
      values.put(invocation.getArgument(0), invocation.getArgument(2));
      return null;
    }).when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    lenient().doAnswer(invocation -> {
      values.remove(invocation.<NamespacedKey>getArgument(0));
      return null;
    }).when(data).remove(any(NamespacedKey.class));
    return data;
  }
}
