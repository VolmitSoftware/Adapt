package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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

  final SorterItems items = new SorterItems();
  final World world = mock(World.class, withSettings().strictness(Strictness.LENIENT));
  final List<NodeIndex.Entry> nodes = new ArrayList<>();
  final Set<Block> denied = new HashSet<>();
  final Set<Block> foreign = new HashSet<>();
  final Player owner = mock(Player.class, withSettings().strictness(Strictness.LENIENT));
  private final Map<String, Block> blocks = new HashMap<>();
  private final Map<Block, Inventory> inventories = new HashMap<>();
  private final List<Block> containers = new ArrayList<>();

  SorterYard() {
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(owner.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000026d2"));
    lenient().when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
  }

  Block block(int x, int y, int z) {
    return blocks.computeIfAbsent(x + "," + y + "," + z, ignored -> {
      Block block = mock(Block.class, withSettings().strictness(Strictness.LENIENT));
      lenient().when(block.getWorld()).thenReturn(world);
      lenient().when(block.getX()).thenReturn(x);
      lenient().when(block.getY()).thenReturn(y);
      lenient().when(block.getZ()).thenReturn(z);
      lenient().when(block.getLocation()).thenAnswer(invocation -> new Location(world, x, y, z));
      lenient().when(block.getType()).thenReturn(Material.STONE);
      lenient().when(block.toString()).thenReturn("block " + x + "," + y + "," + z);
      return block;
    });
  }

  Inventory chest(int x, int y, int z, ItemStack... contents) {
    return place(block(x, y, z), items.inventory(27, new Location(world, x, y, z), contents));
  }

  Inventory doubleChest(int leftX, int rightX, int y, int z, ItemStack... contents) {
    Inventory inventory = items.doubleChest(54, new Location(world, leftX, y, z), contents);
    place(block(leftX, y, z), inventory);
    return place(block(rightX, y, z), inventory);
  }

  Inventory inventory(int x, int y, int z) {
    return inventories.get(block(x, y, z));
  }

  void node(int x, int y, int z, NodeRecord record) {
    nodes.add(new NodeIndex.Entry(new NodeKey(WORLD, x, y, z, record.face()), record));
  }

  SorterSpace space() {
    return new SorterSpace() {
      @Override
      public List<Block> containers(World at, int x, int y, int z, int radius) {
        List<Block> found = new ArrayList<>();
        for (Block block : containers) {
          double dx = block.getX() - x;
          double dy = block.getY() - y;
          double dz = block.getZ() - z;
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
      public boolean owns(Block block) {
        return !foreign.contains(block);
      }

      @Override
      public Player owner() {
        return owner;
      }
    };
  }

  boolean permits(Location at) {
    return !denied.contains(block(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
  }

  Optional<Inventory> container(Block block) {
    return denied.contains(block) || foreign.contains(block) ? Optional.empty() : Optional.ofNullable(inventories.get(block));
  }

  void permitThrough(GolemSorterCore core) {
    lenient().when(core.canAccessChest(any(Player.class), any(Location.class))).thenAnswer(invocation -> permits(invocation.getArgument(1)));
  }

  private Inventory place(Block block, Inventory inventory) {
    Chest state = mock(Chest.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(state.getInventory()).thenReturn(inventory);
    lenient().when(block.getState()).thenReturn(state);
    lenient().when(block.getState(false)).thenReturn(state);
    lenient().when(block.getType()).thenReturn(Material.CHEST);
    inventories.put(block, inventory);
    containers.add(block);
    return inventory;
  }
}
