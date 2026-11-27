package art.arcane.adapt.engine.data;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeCodec;
import art.arcane.adapt.content.golem.node.NodeFaces;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.engine.addon.EngineBlockSlice;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.engine.addon.GolemData;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Addon block slices in the Bukkit mantle: no limit on their number, and a reinstall reads with its new codec. */
class MantleSlicesTest extends AdaptTestBase {
  private static final int ADDONS = 12;
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

  private final List<WorldData> opened = new ArrayList<>();
  private World world;

  @BeforeAll
  static void registerSlices() {
    AdaptSlices.register();
    GolemData.declare();
  }

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000c2"));
    lenient().when(world.getKey()).thenReturn(new NamespacedKey("minecraft", "overworld"));
    lenient().when(world.getName()).thenReturn("world");
    lenient().when(world.getMinHeight()).thenReturn(-64);
    lenient().when(world.getMaxHeight()).thenReturn(320);
    lenient().when(plugin.getDataFolder(any(String[].class))).thenAnswer(invocation -> {
      File folder = dataFolder;
      for (Object argument : invocation.getArguments()) {
        folder = new File(folder, String.valueOf(argument));
      }
      folder.mkdirs();
      return folder;
    });
  }

  @AfterEach
  void tearDown() {
    for (WorldData data : opened) {
      data.unregister();
    }
  }

  @Test
  void twelveAddonsKeepSlicesOnOneBlockAndAReinstallReadsThemWithANewCodec() {
    WorldData data = open();
    for (int addon = 0; addon < ADDONS; addon++) {
      MantleSlices.set(data, 5, 70, -9, marks(addon), "mark " + addon);
      MantleSlices.set(data, 5, 70, -9, counts(addon), addon * 10);
    }
    assertThat(data.savePlate(5, -9)).isTrue();
    close(data);

    WorldData reopened = open();
    for (int addon = 0; addon < ADDONS; addon++) {
      assertThat(MantleSlices.get(reopened, 5, 70, -9, marks(addon))).isEqualTo("mark " + addon);
      assertThat(MantleSlices.get(reopened, 5, 70, -9, counts(addon))).isEqualTo(addon * 10);
    }
    assertThat(MantleSlices.get(reopened, 5, 71, -9, marks(0))).isNull();
  }

  @Test
  void removingOneSliceLeavesTheOthersAndAChunkVisitSeesOnlyItsOwnValues() {
    WorldData data = open();
    MantleSlices.set(data, 1, 64, 1, marks(0), "kept");
    MantleSlices.set(data, 1, 64, 1, marks(1), "dropped");
    MantleSlices.set(data, 2, 64, 1, marks(1), "elsewhere");

    MantleSlices.remove(data, 1, 64, 1, marks(1));
    List<String> visited = new ArrayList<>();
    MantleSlices.iterateChunk(data, 0, 0, marks(1), (int x, int y, int z, String value) -> visited.add(x + ":" + value));

    assertThat(MantleSlices.get(data, 1, 64, 1, marks(0))).isEqualTo("kept");
    assertThat(MantleSlices.get(data, 1, 64, 1, marks(1))).isNull();
    assertThat(visited).containsExactly("2:elsewhere");
    MantleSlices.remove(data, 1, 64, 1, marks(0));
    assertThat(data.get(1, 64, 1, AddonSliceValues.class)).isNull();
  }

  @Test
  void aValueOfASliceNoAddonDeclaresSurvivesWritesOfOthersOnTheSameBlock() {
    WorldData data = open();
    MantleSlices.set(data, 3, 64, 3, slice("gone", "marks", new TextCodec()), "orphan");
    assertThat(data.savePlate(3, 3)).isTrue();
    close(data);

    WorldData reopened = open();
    MantleSlices.set(reopened, 3, 64, 3, marks(4), "new");
    assertThat(reopened.savePlate(3, 3)).isTrue();
    close(reopened);

    WorldData last = open();
    assertThat(MantleSlices.get(last, 3, 64, 3, slice("gone", "marks", new TextCodec()))).isEqualTo("orphan");
    assertThat(MantleSlices.get(last, 3, 64, 3, marks(4))).isEqualTo("new");
  }

  @Test
  void theBuiltInNodeSliceKeepsItsOwnMantleSlice() {
    WorldData data = open();
    NodeFaces faces = NodeFaces.EMPTY.with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build());

    MantleSlices.set(data, 7, 64, 7, NodeCodec.slice(), faces);

    assertThat(data.get(7, 64, 7, NodeFaces.class)).isEqualTo(faces);
    assertThat(data.get(7, 64, 7, AddonSliceValues.class)).isNull();
    assertThat(MantleSlices.get(data, 7, 64, 7, NodeCodec.slice())).isEqualTo(faces);
  }

  private WorldData open() {
    WorldData data = new WorldData(world);
    opened.add(data);
    return data;
  }

  private void close(WorldData data) {
    data.unregister();
    opened.remove(data);
  }

  private static BlockSlice<String> marks(int addon) {
    return slice("addon" + addon, "marks", new TextCodec());
  }

  private static BlockSlice<Integer> counts(int addon) {
    return new EngineBlockSlice<>(Id.of("addon" + addon, "counts"), Integer.class, new CountCodec());
  }

  private static BlockSlice<String> slice(String namespace, String path, BlockSlice.Codec<String> codec) {
    return new EngineBlockSlice<>(Id.of(namespace, path), String.class, codec);
  }

  private static final class TextCodec implements BlockSlice.Codec<String> {
    @Override
    public void write(String value, DataOutputStream out) throws IOException {
      out.writeUTF(value);
    }

    @Override
    public String read(DataInputStream in) throws IOException {
      return in.readUTF();
    }
  }

  private static final class CountCodec implements BlockSlice.Codec<Integer> {
    @Override
    public void write(Integer value, DataOutputStream out) throws IOException {
      out.writeInt(value);
    }

    @Override
    public Integer read(DataInputStream in) throws IOException {
      return in.readInt();
    }
  }
}
