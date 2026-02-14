package art.arcane.adapt.util.mantle;

import art.arcane.spatial.matter.Matter;
import art.arcane.spatial.matter.MatterSlice;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.parallel.HyperLockSupport;
import art.arcane.adapt.Adapt;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.adapt.util.common.parallel.MultiBurst;
import art.arcane.adapt.util.mantle.io.IOWorker;

import java.io.File;
import java.io.IOException;

public class Mantle extends art.arcane.volmlib.util.mantle.Mantle<TectonicPlate, MantleChunk> {
    public Mantle(File dataFolder, int worldHeight) {
        super(dataFolder, worldHeight, DEFAULT_LOCK_SIZE, new HyperLockSupport(), MultiBurst.burst, new IOWorkerRegionIO(dataFolder, worldHeight));
    }

    @Override
    protected TectonicPlate createRegion(int x, int z) {
        return new TectonicPlate(getWorldHeight(), x, z);
    }

    @Override
    protected <T> void setChunkValue(MantleChunk chunk, int x, int y, int z, T value) {
        Matter matter = chunk.getOrCreate(y >> 4);
        matter.slice(matter.getClass(value)).set(x & 15, y & 15, z & 15, value);
    }

    @Override
    protected <T> void removeChunkValue(MantleChunk chunk, int x, int y, int z, Class<T> type) {
        Matter matter = chunk.getOrCreate(y >> 4);
        matter.slice(type).set(x & 15, y & 15, z & 15, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T getChunkValue(MantleChunk chunk, int x, int y, int z, Class<T> type) {
        return (T) chunk.getOrCreate(y >> 4)
                .slice(type)
                .get(x & 15, y & 15, z & 15);
    }

    @Override
    protected <T> void iterateChunkValues(MantleChunk chunk, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        chunk.iterate(type, iterator);
    }

    @Override
    protected void deleteChunkSlice(MantleChunk chunk, Class<?> type) {
        chunk.deleteSlices(type);
    }

    @Override
    protected String formatDuration(double millis) {
        return Form.duration(millis, 0);
    }

    @Override
    protected void onDebug(String message) {
        Adapt.debug(message);
    }

    @Override
    protected void onWarn(String message) {
        Adapt.warn(message);
    }

    @Override
    protected void onError(Throwable throwable) {
        Adapt.error(throwable.getMessage() == null ? "Mantle error" : throwable.getMessage());
        throwable.printStackTrace();
    }

    @Override
    protected void deleteTemporaryFiles() {
        IO.delete(new File(getDataFolder(), ".tmp"));
    }

    public <T> void set(int x, int y, int z, MatterSlice<T> slice) {
        if (slice.isEmpty()) {
            return;
        }

        slice.iterateSync((xx, yy, zz, value) -> set(x + xx, y + yy, z + zz, value));
    }

    private static final class IOWorkerRegionIO implements RegionIO<TectonicPlate> {
        private final IOWorker worker;

        private IOWorkerRegionIO(File root, int worldHeight) {
            this.worker = new IOWorker(root, worldHeight);
        }

        @Override
        public TectonicPlate read(String name) throws IOException {
            return worker.read(name);
        }

        @Override
        public void write(String name, TectonicPlate region) throws IOException {
            worker.write(name, region);
        }

        @Override
        public void close() throws IOException {
            worker.close();
        }
    }
}
