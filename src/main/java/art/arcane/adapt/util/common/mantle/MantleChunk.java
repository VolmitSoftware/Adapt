package art.arcane.adapt.util.mantle;

import art.arcane.spatial.matter.Matter;
import art.arcane.spatial.matter.MatterSlice;
import art.arcane.spatial.matter.SpatialMatter;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.adapt.Adapt;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;

public class MantleChunk extends art.arcane.volmlib.util.mantle.MantleChunk<Matter> {
    public MantleChunk(int sectionHeight, int x, int z) {
        super(sectionHeight, x, z);
    }

    public MantleChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        super(version, sectionHeight, din);
    }

    @Override
    protected void onReadSectionFailure(int index, long start, long end, CountingDataInputStream din, IOException error) {
        Adapt.warn("Failed to read mantle chunk section, skipping it.");
        Adapt.error(error.getMessage() == null ? "Unknown mantle section read error" : error.getMessage());
        error.printStackTrace();
        TectonicPlate.addError();
    }

    @Override
    protected Matter createSection() {
        return new SpatialMatter(16, 16, 16);
    }

    @Override
    protected Matter readSection(CountingDataInputStream din) throws IOException {
        try {
            return Matter.readDin(din);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize mantle section", e);
        }
    }

    @Override
    protected void writeSection(Matter section, DataOutputStream dos) throws IOException {
        section.writeDos(dos);
    }

    @Override
    protected void trimSection(Matter section) {
        section.trimSlices();
    }

    @Override
    protected boolean isSectionEmpty(Matter section) {
        return section.getSliceMap().isEmpty();
    }

    @Override
    public MantleChunk use() {
        super.use();
        return this;
    }

    public void copyFrom(MantleChunk chunk) {
        super.copyFrom(chunk);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(int x, int y, int z, Class<T> type) {
        return (T) getOrCreate(y >> 4)
                .slice(type)
                .get(x & 15, y & 15, z & 15);
    }

    public <T> void iterate(Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        for (int i = 0; i < sectionCount(); i++) {
            int baseY = i << 4;
            Matter section = get(i);
            if (section == null) {
                continue;
            }

            MatterSlice<T> slice = section.getSlice(type);
            if (slice != null) {
                slice.iterateSync((x, y, z, value) -> iterator.accept(x, y + baseY, z, value));
            }
        }
    }

    public void deleteSlices(Class<?> type) {
        for (int i = 0; i < sectionCount(); i++) {
            Matter section = get(i);
            if (section != null && section.hasSlice(type)) {
                section.deleteSlice(type);
            }
        }
    }

    public void trimSlices() {
        trimSections();
    }
}
