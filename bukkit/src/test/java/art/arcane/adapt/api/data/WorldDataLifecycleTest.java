package art.arcane.adapt.api.data;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorldDataLifecycleTest extends AdaptTestBase {
  @AfterEach
  void tearDown() throws ReflectiveOperationException {
    mantles().clear();
  }

  @Test
  void stopClosesEveryWorldEvenWhenOneOfThemFails() throws ReflectiveOperationException {
    WorldData first = failing("first");
    WorldData second = failing("second");

    assertThatCode(WorldData::stop).doesNotThrowAnyException();

    verify(first).unregister();
    verify(second).unregister();
    assertThat(mantles()).isEmpty();
  }

  private WorldData failing(String name) throws ReflectiveOperationException {
    World world = mock(World.class);
    lenient().when(world.getName()).thenReturn(name);
    WorldData data = mock(WorldData.class);
    doThrow(new IllegalStateException("close failed for " + name)).when(data).unregister();
    mantles().put(world, data);
    return data;
  }

  @SuppressWarnings("unchecked")
  private static Map<World, WorldData> mantles() throws ReflectiveOperationException {
    Field field = WorldData.class.getDeclaredField("mantles");
    field.setAccessible(true);
    return (Map<World, WorldData>) field.get(null);
  }
}
