/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.data;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.util.common.parallel.MultiBurst;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.spatial.matter.ClassReader;
import art.arcane.spatial.matter.Matter;
import art.arcane.spatial.matter.MatterSlice;
import art.arcane.spatial.matter.SpatialMatter;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.io.IOWorkerCodecSupport;
import art.arcane.volmlib.util.mantle.runtime.IOWorker;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.parallel.HyperLockSupport;
import art.arcane.volmlib.util.parallel.MultiBurstSupport;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class WorldData extends TickedObject {
  private static final KMap<World, WorldData> mantles = new KMap<>();

  static {
    SpatialMatter.registerSliceType(new Earnings.EarningsMatter());
    SpatialMatter.registerSliceType(new PlacementStamp.PlacementStampMatter());
    ClassReader.add(WorldData.class.getClassLoader());
  }

  private final World world;
  private final int minHeight;
  private final AdaptMantle mantle;

  public WorldData(World world) {
    super("world-data", world.getUID().toString(), 30_000);
    this.world = world;
    this.minHeight = world.getMinHeight();
    NamespacedKey worldKey = WorldIdentity.key(world);
    mantle = createMantle(
        Adapt.instance.getDataFolder("data", "mantle", worldKey.getNamespace(), worldKey.getKey()),
        world.getMaxHeight() - minHeight
    );
  }

  private static AdaptMantle createMantle(File dataFolder, int worldHeight) {
    MantleDataAdapter<Matter> adapter = createAdapter();
    MantleHooks hooks = createHooks();
    art.arcane.volmlib.util.mantle.Mantle.RegionIO<TectonicPlate<Matter>> regionIO =
        createRegionIO(dataFolder, worldHeight, adapter, hooks);
    return new AdaptMantle(dataFolder, worldHeight, MultiBurst.burst, regionIO, adapter, hooks);
  }

  private static MantleDataAdapter<Matter> createAdapter() {
    return new MantleDataAdapter<>() {
      @Override
      public Matter createSection() {
        return new SpatialMatter(16, 16, 16);
      }

      @Override
      public Matter readSection(CountingDataInputStream din) throws IOException {
        return WorldData.readSection(din);
      }

      @Override
      public void writeSection(Matter section, DataOutputStream dos) throws IOException {
        section.writeDos(dos);
      }

      @Override
      public void trimSection(Matter section) {
        section.trimSlices();
      }

      @Override
      public boolean isSectionEmpty(Matter section) {
        return section.getSliceMap().isEmpty();
      }

      @Override
      public Class<?> classifyValue(Object value) {
        return value == null ? Object.class : value.getClass();
      }

      @Override
      @SuppressWarnings("unchecked")
      public <T> void set(Matter section, int x, int y, int z, Class<?> type, T value) {
        MatterSlice<T> slice = (MatterSlice<T>) section.slice(section.getClass(value));
        slice.set(x, y, z, value);
      }

      @Override
      public <T> void remove(Matter section, int x, int y, int z, Class<T> type) {
        MatterSlice<T> slice = section.slice(type);
        slice.set(x, y, z, null);
      }

      @Override
      public <T> T get(Matter section, int x, int y, int z, Class<T> type) {
        MatterSlice<T> slice = section.slice(type);
        return slice.get(x, y, z);
      }

      @Override
      public <T> void iterate(Matter section, Class<T> type, art.arcane.volmlib.util.function.Consumer4<Integer, Integer, Integer, T> iterator) {
        MatterSlice<T> slice = section.getSlice(type);
        if (slice != null) {
          slice.iterateSync((x, y, z, value) -> iterator.accept(x, y, z, value));
        }
      }

      @Override
      public boolean hasSlice(Matter section, Class<?> type) {
        return section.hasSlice(type);
      }

      @Override
      public void deleteSlice(Matter section, Class<?> type) {
        section.deleteSlice(type);
      }
    };
  }

  static Matter readSection(CountingDataInputStream in) throws IOException {
    int width = in.readInt();
    int height = in.readInt();
    int depth = in.readInt();
    if (width != 16 || height != 16 || depth != 16) {
      throw new IOException("Mantle section dimensions must be 16 by 16 by 16");
    }
    Matter section = new SpatialMatter(width, height, depth);
    int count = in.readUnsignedByte();
    section.getHeader().read(in);
    for (int index = 0; index < count; index++) {
      String name = in.readUTF();
      try {
        Class<?> type = section.getClass(ClassReader.find(name));
        if (section.hasSlice(type)) {
          throw new IOException("Duplicate mantle slice " + name);
        }
        MatterSlice<?> slice = section.createSlice(type, section);
        if (slice == null) {
          throw new IOException("Unavailable mantle slice codec " + name);
        }
        slice.read(in);
        section.putSlice(type, slice);
      } catch (ClassNotFoundException | RuntimeException failure) {
        throw new IOException("Could not read mantle slice " + name, failure);
      }
    }
    return section;
  }

  private static MantleHooks createHooks() {
    return new MantleHooks() {
      @Override
      public String formatDuration(double millis) {
        return Form.duration(millis, 0);
      }

      @Override
      public void onDebug(String message) {
        Adapt.debug(message);
      }

      @Override
      public void onWarn(String message) {
        Adapt.warn(message);
      }

      @Override
      public void onError(Throwable throwable) {
        Adapt.error(throwable.getMessage() == null ? "Mantle error" : throwable.getMessage());
        Adapt.error(throwable);
      }
    };
  }

  private static art.arcane.volmlib.util.mantle.Mantle.RegionIO<TectonicPlate<Matter>> createRegionIO(File dataFolder,
                                                                                                      int worldHeight,
                                                                                                      MantleDataAdapter<Matter> adapter,
                                                                                                      MantleHooks hooks) {
    IOWorker<TectonicPlate<Matter>> worker = new IOWorker<>(
        dataFolder,
        IOWorkerCodecSupport.identity(),
        128,
        (name, millis) -> Adapt.debug("Acquired mantle channel for " + name + " in " + millis + "ms")
    );

    return new art.arcane.volmlib.util.mantle.Mantle.RegionIO<>() {
      @Override
      public TectonicPlate<Matter> read(String name) throws IOException {
        return worker.read(name, (regionName, in) -> TectonicPlate.read(worldHeight, in, true, adapter, hooks));
      }

      @Override
      public void write(String name, TectonicPlate<Matter> region) throws IOException {
        worker.write(name, "adapt", ".bin", region, TectonicPlate::write);
      }

      @Override
      public void close() throws IOException {
        worker.close();
      }
    };
  }

  public static void stop() {
    mantles.v().forEach(WorldData::unregister);
    mantles.clear();
  }

  public static Optional<WorldData> loaded(World world) {
    return Bukkit.getWorld(world.getUID()) == world ? Optional.of(of(world)) : Optional.empty();
  }

  public static WorldData of(World world) {
    return mantles.computeIfAbsent(world, key -> {
      WorldData data = new WorldData(key);
      data.activateRuntime();
      return data;
    });
  }

  public double getEarningsMultiplier(Block block) {
    Earnings e = get(block, Earnings.class);

    if (e == null) {
      return 1;
    }

    return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
  }

  public <T> T get(Block block, Class<T> type) {
    return get(block.getX(), block.getY(), block.getZ(), type);
  }

  public <T> T get(int x, int y, int z, Class<T> type) {
    return mantle.get(x, y - minHeight, z, type);
  }

  public <T> void set(Block block, T value) {
    set(block.getX(), block.getY(), block.getZ(), value);
  }

  public <T> void set(int x, int y, int z, T value) {
    mantle.set(x, y - minHeight, z, value);
  }

  public <T> void remove(Block block, Class<T> type) {
    remove(block.getX(), block.getY(), block.getZ(), type);
  }

  public <T> void remove(int x, int y, int z, Class<T> type) {
    mantle.remove(x, y - minHeight, z, type);
  }

  public <T> void iterateChunk(int chunkX, int chunkZ, Class<T> type, Consumer4<Integer, Integer, Integer, T> visitor) {
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    mantle.iterateExisting(chunkX, chunkZ, type, (x, y, z, value) -> visitor.accept(baseX + x, y + minHeight, baseZ + z, value));
  }

  public boolean savePlate(int x, int z) {
    return mantle.saveIdleTectonicPlates(List.of(Mantle.key(x >> 9, z >> 9))).isEmpty();
  }

  public double reportEarnings(Block block) {
    Earnings e = get(block, Earnings.class);
    e = e == null ? new Earnings(0) : e;

    if (e.getEarnings() >= 127) {
      return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
    }

    set(block, e.increment());
    return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
  }

  public void unregister() {
    super.unregister();
    mantle.close();
    mantles.remove(world);
  }

  @EventHandler
  public void on(WorldSaveEvent e) {
    if (e.getWorld() != world) return;
    J.a(mantle::saveAll);
  }

  @EventHandler
  public void on(WorldUnloadEvent e) {
    if (e.getWorld() != world) return;
    unregister();
  }

  @Override
  public void onTick() {
    mantle.trim(60_000);
  }

  private static final class AdaptMantle extends Mantle<Matter> {
    private AdaptMantle(File dataFolder,
                        int worldHeight,
                        MultiBurstSupport ioBurst,
                        Mantle.RegionIO<TectonicPlate<Matter>> regionIO,
                        MantleDataAdapter<Matter> adapter,
                        MantleHooks hooks) {
      super(dataFolder, worldHeight, new HyperLockSupport(), ioBurst, regionIO, adapter, hooks);
    }

    private <T> void iterateExisting(int chunkX, int chunkZ, Class<T> type, Consumer4<Integer, Integer, Integer, T> visitor) {
      int regionX = chunkX >> 5;
      int regionZ = chunkZ >> 5;
      if (isClosed() || !hasTectonicPlate(regionX, regionZ)) {
        return;
      }
      TectonicPlate<Matter> plate = accessRegion(regionX, regionZ);
      while (!plate.pin()) {
        plate = accessRegion(regionX, regionZ);
      }
      try {
        MantleChunk<Matter> chunk = plate.get(chunkX & 31, chunkZ & 31);
        if (chunk != null && !chunk.isClosed()) {
          chunk.iterate(type, visitor);
        }
      } finally {
        plate.unpin();
      }
    }
  }
}
