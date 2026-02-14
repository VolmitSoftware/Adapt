package art.arcane.adapt.util.mantle.io;

import art.arcane.spatial.mantle.MantleRegion;
import art.arcane.spatial.matter.Matter;
import art.arcane.spatial.matter.MatterSlice;
import art.arcane.volmlib.util.mantle.io.IOWorkerCodecSupport;
import art.arcane.volmlib.util.mantle.io.IOWorkerRuntimeSupport;
import art.arcane.volmlib.util.mantle.io.IOWorkerSupport;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.mantle.MantleChunk;
import art.arcane.adapt.util.mantle.TectonicPlate;

import java.io.File;
import java.io.IOException;

public class IOWorker {
    private final File root;
    private final int worldHeight;
    private final IOWorkerSupport support;
    private final IOWorkerRuntimeSupport runtime;

    public IOWorker(File root, int worldHeight) {
        this.root = root;
        this.worldHeight = worldHeight;
        this.support = new IOWorkerSupport(root, 128, (name, millis) ->
                Adapt.debug("Acquired mantle channel for " + name + " in " + millis + "ms")
        );
        this.runtime = new IOWorkerRuntimeSupport(support, IOWorkerCodecSupport.identity());
    }

    public TectonicPlate read(String name) throws IOException {
        try {
            return runtime.read(name, (regionName, in) -> new TectonicPlate(worldHeight, in, true));
        } catch (IOException e) {
            TectonicPlate migrated = readLegacy(name, e);
            if (migrated != null) {
                Adapt.warn("Migrated legacy mantle region " + name + " to shared mantle format.");
                return migrated;
            }

            throw e;
        }
    }

    public void write(String name, TectonicPlate plate) throws IOException {
        runtime.write(name, plate, TectonicPlate::write);
    }

    public void close() throws IOException {
        support.close();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private TectonicPlate readLegacy(String name, IOException original) {
        File file = new File(root, name);
        if (!file.exists()) {
            return null;
        }

        try {
            MantleRegion region = MantleRegion.read(worldHeight, file);
            TectonicPlate plate = new TectonicPlate(worldHeight, region.getX(), region.getZ());
            int sectionCount = worldHeight >> 4;

            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    var legacyChunk = region.get(x, z);
                    if (legacyChunk == null) {
                        continue;
                    }

                    MantleChunk chunk = plate.getOrCreate(x, z);
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
}
