package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

final class SorterYard {
  static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000026d1");
  private static final Id CHEST = Id.minecraft("chest");
  private static final Id STONE = Id.minecraft("stone");

  final SorterItems items = new SorterItems();
  final WorldRef world = mock(WorldRef.class, withSettings().strictness(Strictness.LENIENT));
  final List<NodeIndex.Entry> nodes = new ArrayList<>();
  final Set<BlockRef> denied = new HashSet<>();
  final Set<BlockRef> foreign = new HashSet<>();
  final PlayerRef owner = mock(PlayerRef.class, withSettings().strictness(Strictness.LENIENT));
  private final Map<String, BlockRef> blocks = new HashMap<>();
  private final Map<BlockRef, InventoryRef> inventories = new HashMap<>();
  private final List<BlockRef> containers = new ArrayList<>();

  SorterYard() {
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(owner.uuid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000026d2"));
    lenient().when(world.blockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
  }

  BlockRef block(int x, int y, int z) {
    return blocks.computeIfAbsent(x + "," + y + "," + z, ignored -> {
      BlockRef block = mock(BlockRef.class, withSettings().strictness(Strictness.LENIENT));
      lenient().when(block.world()).thenReturn(world);
      lenient().when(block.pos()).thenReturn(new BlockPos(x, y, z));
      lenient().when(block.x()).thenReturn(x);
      lenient().when(block.y()).thenReturn(y);
      lenient().when(block.z()).thenReturn(z);
      lenient().when(block.location()).thenAnswer(invocation -> new Loc(world, x, y, z));
      lenient().when(block.center()).thenAnswer(invocation -> new Loc(world, x + 0.5D, y + 0.5D, z + 0.5D));
      lenient().when(block.type()).thenReturn(STONE);
      lenient().when(block.toString()).thenReturn("block " + x + "," + y + "," + z);
      return block;
    });
  }

  InventoryRef chest(int x, int y, int z, ItemRef... contents) {
    BlockRef block = block(x, y, z);
    InventoryRef inventory = items.inventory(27, contents);
    lenient().when(block.containerHalves()).thenReturn(List.of(block));
    return place(block, inventory);
  }

  InventoryRef doubleChest(int leftX, int rightX, int y, int z, ItemRef... contents) {
    InventoryRef inventory = items.inventory(54, contents);
    BlockRef left = block(leftX, y, z);
    BlockRef right = block(rightX, y, z);
    lenient().when(left.containerHalves()).thenReturn(List.of(left, right));
    lenient().when(right.containerHalves()).thenReturn(List.of(left, right));
    place(left, inventory);
    return place(right, inventory);
  }

  InventoryRef inventory(int x, int y, int z) {
    return inventories.get(block(x, y, z));
  }

  void node(int x, int y, int z, NodeRecord record) {
    nodes.add(new NodeIndex.Entry(new NodeKey(WORLD, x, y, z, record.face()), record));
  }

  SorterSpace space() {
    return new SorterSpace() {
      @Override
      public List<BlockRef> containers(WorldRef at, int x, int y, int z, int radius) {
        List<BlockRef> found = new ArrayList<>();
        for (BlockRef block : containers) {
          double dx = block.x() - x;
          double dy = block.y() - y;
          double dz = block.z() - z;
          if (dx * dx + dy * dy + dz * dz <= (double) radius * radius) {
            found.add(block);
          }
        }
        return found;
      }

      @Override
      public List<NodeIndex.Entry> nodes(UUID at, double x, double y, double z, double range) {
        List<NodeIndex.Entry> found = new ArrayList<>();
        for (NodeIndex.Entry entry : nodes) {
          if (entry.key().distanceSquared(x, y, z) <= range * range) {
            found.add(entry);
          }
        }
        return found;
      }
    };
  }

  SorterAccess access() {
    return new SorterAccess() {
      @Override
      public boolean owns(BlockRef block) {
        return !foreign.contains(block);
      }

      @Override
      public PlayerRef owner() {
        return owner;
      }
    };
  }

  boolean permits(Loc at) {
    return !denied.contains(block(at.blockX(), at.blockY(), at.blockZ()));
  }

  Optional<InventoryRef> container(BlockRef block) {
    return denied.contains(block) || foreign.contains(block) ? Optional.empty() : Optional.ofNullable(inventories.get(block));
  }

  void permitThrough(GolemSorterCore core) {
    lenient().when(core.canAccessChest(any(PlayerRef.class), any(Loc.class))).thenAnswer(invocation -> permits(invocation.getArgument(1)));
  }

  private InventoryRef place(BlockRef block, InventoryRef inventory) {
    lenient().when(block.container()).thenReturn(inventory);
    lenient().when(block.type()).thenReturn(CHEST);
    inventories.put(block, inventory);
    containers.add(block);
    return inventory;
  }
}
