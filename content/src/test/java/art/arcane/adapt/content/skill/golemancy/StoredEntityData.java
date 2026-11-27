package art.arcane.adapt.content.skill.golemancy;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.testkit.MemoryData;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class StoredEntityData {
  private StoredEntityData() {
  }

  static <T extends EntityRef> T entity(Class<T> type) {
    T entity = mock(type);
    MemoryData data = mapBacked();
    when(entity.data()).thenReturn(data);
    return entity;
  }

  static MemoryData mapBacked() {
    return new MemoryData();
  }
}
