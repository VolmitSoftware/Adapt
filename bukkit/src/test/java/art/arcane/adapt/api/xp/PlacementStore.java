package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.data.WorldDataPort;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Installs a map-backed {@link WorldDataPort} so placement provenance can be read and written without a mantle. */
public final class PlacementStore implements AutoCloseable {
  private final Map<String, WorldDataPort.Placement> stamps = new HashMap<>();
  private final boolean hadPort;
  private final WorldDataPort previous;

  public PlacementStore() {
    hadPort = AdaptPorts.isInstalled(WorldDataPort.class);
    previous = AdaptPorts.get(WorldDataPort.class, WorldDataPort.NONE);
    AdaptPorts.install(WorldDataPort.class, new WorldDataPort() {
      @Override
      public double reportEarnings(BlockRef block) {
        return 1D;
      }

      @Override
      public double getEarningsMultiplier(BlockRef block) {
        return 1D;
      }

      @Override
      public Placement placement(BlockRef block) {
        return stamps.get(key(block.x(), block.y(), block.z()));
      }

      @Override
      public void setPlacement(BlockRef block, Placement placement) {
        stamps.put(key(block.x(), block.y(), block.z()), placement);
      }

      @Override
      public void clearPlacement(BlockRef block) {
        stamps.remove(key(block.x(), block.y(), block.z()));
      }

      @Override
      public UUID blockOwner(BlockRef block) {
        return null;
      }

      @Override
      public void setBlockOwner(BlockRef block, UUID owner) {
      }
    });
  }

  public PlacementStamp get(Block block) {
    WorldDataPort.Placement stamp = stamps.get(key(block.getX(), block.getY(), block.getZ()));
    return stamp == null ? null : new PlacementStamp(stamp.placedAt(), stamp.brokenAt(), stamp.bonemealedAt());
  }

  public void put(Block block, PlacementStamp stamp) {
    stamps.put(key(block.getX(), block.getY(), block.getZ()),
        new WorldDataPort.Placement(stamp.getPlacedAt(), stamp.getBrokenAt(), stamp.getBonemealedAt()));
  }

  @Override
  public void close() {
    if (hadPort) {
      AdaptPorts.install(WorldDataPort.class, previous);
    } else {
      AdaptPorts.uninstall(WorldDataPort.class);
    }
  }

  private static String key(int x, int y, int z) {
    return x + "," + y + "," + z;
  }
}
