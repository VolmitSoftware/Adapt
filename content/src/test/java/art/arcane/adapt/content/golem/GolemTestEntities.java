package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.MemoryData;

import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

final class GolemTestEntities {
  private GolemTestEntities() {
  }

  static <T extends EntityRef> T entity(Class<T> type, Id entityType) {
    T entity = mock(type);
    DataHolder data = mapBacked();
    UUID entityId = UUID.randomUUID();
    lenient().when(entity.data()).thenReturn(data);
    lenient().when(entity.uuid()).thenReturn(entityId);
    lenient().when(entity.type()).thenReturn(entityType);
    return entity;
  }

  static MobRef golem(GolemBody body) {
    return entity(MobRef.class, body.entityType());
  }

  static MobRef mob(String vanillaType) {
    return entity(MobRef.class, Id.minecraft(vanillaType));
  }

  static DataHolder mapBacked() {
    return new MemoryData();
  }
}
