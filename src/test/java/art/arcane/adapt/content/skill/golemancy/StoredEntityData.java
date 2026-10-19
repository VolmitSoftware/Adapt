package art.arcane.adapt.content.skill.golemancy;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class StoredEntityData {
  private StoredEntityData() {
  }

  static <T extends Entity> T entity(Class<T> type) {
    T entity = mock(type);
    PersistentDataContainer data = mapBacked();
    when(entity.getPersistentDataContainer()).thenReturn(data);
    return entity;
  }

  static PersistentDataContainer mapBacked() {
    Map<NamespacedKey, Object> values = new HashMap<>();
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    doAnswer(invocation -> {
      values.put(invocation.getArgument(0), invocation.getArgument(2));
      return null;
    }).when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    doAnswer(invocation -> {
      values.remove(invocation.<NamespacedKey>getArgument(0));
      return null;
    }).when(data).remove(any(NamespacedKey.class));
    return data;
  }
}
