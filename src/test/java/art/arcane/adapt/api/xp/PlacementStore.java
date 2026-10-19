package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import org.bukkit.block.Block;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public final class PlacementStore implements AutoCloseable {
  private final Map<String, PlacementStamp> stamps = new HashMap<>();
  private final MockedStatic<WorldData> worlds;

  public PlacementStore() {
    WorldData data = mock(WorldData.class);
    when(data.get(any(Block.class), eq(PlacementStamp.class))).thenAnswer(invocation -> stamps.get(key(invocation.getArgument(0))));
    doAnswer(invocation -> {
      stamps.put(key(invocation.getArgument(0)), invocation.getArgument(1));
      return null;
    }).when(data).set(any(Block.class), any(PlacementStamp.class));
    doAnswer(invocation -> {
      stamps.remove(key(invocation.getArgument(0)));
      return null;
    }).when(data).remove(any(Block.class), eq(PlacementStamp.class));
    worlds = mockStatic(WorldData.class);
    worlds.when(() -> WorldData.of(any())).thenReturn(data);
  }

  public PlacementStamp get(Block block) {
    return stamps.get(key(block));
  }

  public void put(Block block, PlacementStamp stamp) {
    stamps.put(key(block), stamp);
  }

  @Override
  public void close() {
    worlds.close();
  }

  private static String key(Block block) {
    return block.getX() + "," + block.getY() + "," + block.getZ();
  }
}
