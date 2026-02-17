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
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.util.common.parallel.MultiBurst;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.spatial.mantle.MantleRegion;
import art.arcane.spatial.matter.ClassReader;
import art.arcane.spatial.matter.Matter;
import art.arcane.spatial.matter.MatterSlice;
import art.arcane.spatial.matter.SpatialMatter;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.io.IOWorkerCodecSupport;
import art.arcane.volmlib.util.mantle.runtime.IOWorker;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.parallel.HyperLockSupport;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class WorldData extends TickedObject {
    private static final KMap<World, WorldData> mantles = new KMap<>();

    static {
        SpatialMatter.registerSliceType(new Earnings.EarningsMatter());
        ClassReader.add(WorldData.class.getClassLoader());
    }

    private final World world;
    private final int minHeight;
    private final Mantle<Matter> mantle;

    public WorldData(World world) {
        super("world-data", world.getUID().toString(), 30_000);
        this.world = world;
        this.minHeight = world.getMinHeight();
        mantle = createMantle(Adapt.instance.getDataFolder("data", "mantle", world.getName()), world.getMaxHeight());
    }

    private static Mantle<Matter> createMantle(File dataFolder, int worldHeight) {
        MantleDataAdapter<Matter> adapter = createAdapter();
        MantleHooks hooks = createHooks();
        art.arcane.volmlib.util.mantle.Mantle.RegionIO<TectonicPlate<Matter>> regionIO =
                createRegionIO(dataFolder, worldHeight, adapter, hooks);
        return new Mantle<>(
                dataFolder,
                worldHeight,
                new HyperLockSupport(),
                MultiBurst.burst,
                regionIO,
                adapter,
                hooks
        );
    }

    private static MantleDataAdapter<Matter> createAdapter() {
        return new MantleDataAdapter<>() {
            @Override
            public Matter createSection() {
                return new SpatialMatter(16, 16, 16);
            }

            @Override
            public Matter readSection(CountingDataInputStream din) throws IOException {
                try {
                    return Matter.readDin(din);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Failed to deserialize mantle section", e);
                }
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

    private static MantleHooks createHooks() {
        return new MantleHooks() {
            @Override
            public void onReadSectionFailure(int index,
                                             long start,
                                             long end,
                                             CountingDataInputStream din,
                                             IOException error) {
                Adapt.warn("Failed to read mantle chunk section, skipping it.");
                Adapt.error(error.getMessage() == null ? "Unknown mantle section read error" : error.getMessage());
                error.printStackTrace();
                TectonicPlate.addError();
            }

            @Override
            public void onReadChunkFailure(int index,
                                           long start,
                                           long end,
                                           CountingDataInputStream din,
                                           Throwable error) {
                Adapt.warn("Failed to read mantle chunk, creating a new chunk instead.");
                Adapt.error(error.getMessage() == null ? "Unknown mantle chunk read error" : error.getMessage());
                error.printStackTrace();
            }

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
                throwable.printStackTrace();
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
                try {
                    return worker.read(name, (regionName, in) -> TectonicPlate.read(worldHeight, in, true, adapter, hooks));
                } catch (IOException e) {
                    TectonicPlate<Matter> migrated = readLegacy(dataFolder, worldHeight, name, adapter, hooks, e);
                    if (migrated != null) {
                        Adapt.warn("Migrated legacy mantle region " + name + " to shared mantle format.");
                        return migrated;
                    }

                    throw e;
                }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TectonicPlate<Matter> readLegacy(File dataFolder,
                                                    int worldHeight,
                                                    String name,
                                                    MantleDataAdapter<Matter> adapter,
                                                    MantleHooks hooks,
                                                    IOException original) {
        File file = new File(dataFolder, name);
        if (!file.exists()) {
            return null;
        }

        try {
            MantleRegion region = MantleRegion.read(worldHeight, file);
            TectonicPlate<Matter> plate = new TectonicPlate<>(worldHeight, region.getX(), region.getZ(), adapter, hooks);
            int sectionCount = worldHeight >> 4;

            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    var legacyChunk = region.get(x, z);
                    if (legacyChunk == null) {
                        continue;
                    }

                    art.arcane.volmlib.util.mantle.runtime.MantleChunk<Matter> chunk = plate.getOrCreate(x, z);
                    for (int section = 0; section < sectionCount; section++) {
                        Matter legacySection = legacyChunk.get(section);
                        if (legacySection == null || legacySection.getSliceMap().isEmpty()) {
                            continue;
                        }

                        Matter target = chunk.getOrCreate(section);
                        target.clearSlices();

                        Matter copy = legacySection.copy();
                        for (var entry : copy.getSliceMap().entrySet()) {
                            target.putSlice(entry.getKey(), (MatterSlice) entry.getValue());
                        }
                    }
                }
            }

            return plate;
        } catch (Throwable t) {
            original.addSuppressed(t);
            return null;
        }
    }

    public static void stop() {
        mantles.v().forEach(WorldData::unregister);
    }

    public static WorldData of(World world) {
        return mantles.computeIfAbsent(world, WorldData::new);
    }

    public double getEarningsMultiplier(Block block) {
        Earnings e = get(block, Earnings.class);

        if (e == null) {
            return 1;
        }

        return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
    }

    public <T> T get(Block block, Class<T> type) {
        return mantle.get(block.getX(), block.getY() - minHeight, block.getZ(), type);
    }

    public <T> void set(Block block, T value) {
        mantle.set(block.getX(), block.getY() - minHeight, block.getZ(), value);
    }

    public <T> void remove(Block block, Class<T> type) {
        mantle.remove(block.getX(), block.getY() - minHeight, block.getZ(), type);
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
}
