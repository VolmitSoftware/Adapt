package art.arcane.adapt.util.mantle;

import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.adapt.Adapt;

import java.io.DataOutputStream;
import java.io.IOException;

public class TectonicPlate extends art.arcane.volmlib.util.mantle.TectonicPlate<MantleChunk> {
    public static final int MISSING = art.arcane.volmlib.util.mantle.TectonicPlate.MISSING;
    public static final int CURRENT = art.arcane.volmlib.util.mantle.TectonicPlate.CURRENT;

    public TectonicPlate(int worldHeight, int x, int z) {
        super(worldHeight, x, z);
    }

    public TectonicPlate(int worldHeight, CountingDataInputStream din, boolean versioned) throws IOException {
        super(worldHeight, din, versioned);
    }

    @Override
    protected void onReadChunkFailure(int index, long start, long end, CountingDataInputStream din, Throwable error) {
        Adapt.warn("Failed to read mantle chunk, creating a new chunk instead.");
        Adapt.error(error.getMessage() == null ? "Unknown mantle chunk read error" : error.getMessage());
        error.printStackTrace();
    }

    @Override
    protected MantleChunk readChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        return new MantleChunk(version, sectionHeight, din);
    }

    @Override
    protected MantleChunk createChunk(int sectionHeight, int x, int z) {
        return new MantleChunk(sectionHeight, x, z);
    }

    @Override
    protected boolean isChunkInUse(MantleChunk chunk) {
        return chunk.inUse();
    }

    @Override
    protected void closeChunk(MantleChunk chunk) {
        chunk.close();
    }

    @Override
    protected void writeChunk(MantleChunk chunk, DataOutputStream dos) throws IOException {
        chunk.write(dos);
    }

    public static void addError() {
        art.arcane.volmlib.util.mantle.TectonicPlate.addError();
    }

    public static boolean hasError() {
        return art.arcane.volmlib.util.mantle.TectonicPlate.hasError();
    }
}
