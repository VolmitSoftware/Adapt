package art.arcane.adapt.mod.platform.data;

import art.arcane.adapt.api.data.WorldDataPort;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * XP earnings, placement stamps and block owners per block, the modded counterpart of the Bukkit mantle world data. Cells live in
 * one file per dimension and 32x32 chunk region under {@code <world>/data/adapt/xp/<namespace>/<path>/}, are loaded on
 * first use, written on world save and dropped from memory after a minute without access. A break keeps the history,
 * as the mantle does.
 */
public final class ModWorldDataPort implements WorldDataPort {
    private static final int MAX_EARNINGS = 127;
    private static final int MAGIC = 0x41585031;
    private static final int MAGIC_OWNERS = 0x41585032;
    private static final long IDLE_MILLIS = 60_000L;
    private static final long TRIM_INTERVAL_MILLIS = 30_000L;

    private final Path root;
    private final Map<Id, Long2ObjectMap<Region>> dimensions = new HashMap<>();
    private long lastTrim = System.currentTimeMillis();

    public ModWorldDataPort(Path root) {
        this.root = root;
    }

    @Override
    public synchronized double reportEarnings(BlockRef block) {
        Region region = region(block, true);
        long key = key(block);
        Cell cell = region.cells.get(key);
        if (cell == null) {
            cell = new Cell();
            region.cells.put(key, cell);
        }
        int previous = cell.earnings;
        if (previous < MAX_EARNINGS) {
            cell.earnings = previous + 1;
            region.dirty = true;
        }
        return 1D / Math.max(1, previous);
    }

    @Override
    public synchronized double getEarningsMultiplier(BlockRef block) {
        Cell cell = cell(block);
        return cell == null || cell.earnings <= 0 ? 1D : 1D / cell.earnings;
    }

    @Override
    public synchronized Placement placement(BlockRef block) {
        Cell cell = cell(block);
        return cell == null || !cell.placed ? null : new Placement(cell.placedAt, cell.brokenAt, cell.bonemealedAt);
    }

    @Override
    public synchronized void setPlacement(BlockRef block, Placement placement) {
        if (placement == null) {
            clearPlacement(block);
            return;
        }
        Region region = region(block, true);
        long key = key(block);
        Cell cell = region.cells.get(key);
        if (cell == null) {
            cell = new Cell();
            region.cells.put(key, cell);
        }
        cell.placed = true;
        cell.placedAt = placement.placedAt();
        cell.brokenAt = placement.brokenAt();
        cell.bonemealedAt = placement.bonemealedAt();
        region.dirty = true;
    }

    @Override
    public synchronized void clearPlacement(BlockRef block) {
        Region region = region(block, false);
        if (region == null) {
            return;
        }
        long key = key(block);
        Cell cell = region.cells.get(key);
        if (cell == null || !cell.placed) {
            return;
        }
        cell.placed = false;
        if (cell.isEmpty()) {
            region.cells.remove(key);
        }
        region.dirty = true;
    }

    @Override
    public synchronized UUID blockOwner(BlockRef block) {
        Cell cell = cell(block);
        return cell == null ? null : cell.owner;
    }

    @Override
    public synchronized void setBlockOwner(BlockRef block, UUID owner) {
        Region region = region(block, owner != null);
        if (region == null) {
            return;
        }
        long key = key(block);
        Cell cell = region.cells.get(key);
        if (cell == null) {
            if (owner == null) {
                return;
            }
            cell = new Cell();
            region.cells.put(key, cell);
        }
        cell.owner = owner;
        if (cell.isEmpty()) {
            region.cells.remove(key);
        }
        region.dirty = true;
    }

    /** Writes every changed region of the dimension. */
    public synchronized void save(Id dimension) {
        Long2ObjectMap<Region> regions = dimensions.get(dimension);
        if (regions != null) {
            for (Region region : regions.values()) {
                write(region);
            }
        }
    }

    /** Writes every changed region and forgets all of them. */
    public synchronized void close() {
        for (Long2ObjectMap<Region> regions : dimensions.values()) {
            for (Region region : regions.values()) {
                write(region);
            }
        }
        dimensions.clear();
    }

    private Cell cell(BlockRef block) {
        Region region = region(block, false);
        return region == null ? null : region.cells.get(key(block));
    }

    private Region region(BlockRef block, boolean create) {
        long now = System.currentTimeMillis();
        if (now - lastTrim >= TRIM_INTERVAL_MILLIS) {
            lastTrim = now;
            trim(now);
        }
        Id dimension = block.world().key();
        int regionX = block.x() >> 9;
        int regionZ = block.z() >> 9;
        long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
        Long2ObjectMap<Region> regions = dimensions.computeIfAbsent(dimension, (Id ignored) -> new Long2ObjectOpenHashMap<>());
        Region region = regions.get(regionKey);
        if (region == null) {
            Path file = root.resolve(dimension.namespace()).resolve(dimension.path()).resolve("r." + regionX + "." + regionZ + ".bin");
            if (!create && !Files.isRegularFile(file)) {
                return null;
            }
            region = read(file);
            regions.put(regionKey, region);
        }
        region.lastAccess = now;
        return region;
    }

    private void trim(long now) {
        List<Id> emptied = new ArrayList<>();
        for (Map.Entry<Id, Long2ObjectMap<Region>> dimension : dimensions.entrySet()) {
            Iterator<Region> regions = dimension.getValue().values().iterator();
            while (regions.hasNext()) {
                Region region = regions.next();
                if (now - region.lastAccess >= IDLE_MILLIS) {
                    write(region);
                    regions.remove();
                }
            }
            if (dimension.getValue().isEmpty()) {
                emptied.add(dimension.getKey());
            }
        }
        for (Id dimension : emptied) {
            dimensions.remove(dimension);
        }
    }

    private static long key(BlockRef block) {
        return BlockPos.asLong(block.x(), block.y(), block.z());
    }

    private static Region read(Path file) {
        Region region = new Region(file);
        if (!Files.isRegularFile(file)) {
            return region;
        }
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            int magic = in.readInt();
            if (magic != MAGIC && magic != MAGIC_OWNERS) {
                throw new IOException("not an Adapt XP region");
            }
            int count = in.readInt();
            for (int index = 0; index < count; index++) {
                long key = in.readLong();
                Cell cell = new Cell();
                cell.earnings = in.readUnsignedByte();
                cell.placed = in.readBoolean();
                if (cell.placed) {
                    cell.placedAt = in.readInt();
                    cell.brokenAt = in.readInt();
                    cell.bonemealedAt = in.readInt();
                }
                if (magic == MAGIC_OWNERS && in.readBoolean()) {
                    cell.owner = new UUID(in.readLong(), in.readLong());
                }
                region.cells.put(key, cell);
            }
        } catch (IOException failure) {
            ModPlatform.log().warn("Could not read XP data " + file + ": " + failure.getMessage());
        }
        return region;
    }

    private static void write(Region region) {
        if (!region.dirty) {
            return;
        }
        Path file = region.file;
        try {
            if (region.cells.isEmpty()) {
                Files.deleteIfExists(file);
                region.dirty = false;
                return;
            }
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream raw = Files.newOutputStream(temporary);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw)))) {
                out.writeInt(MAGIC_OWNERS);
                out.writeInt(region.cells.size());
                for (Long2ObjectMap.Entry<Cell> entry : region.cells.long2ObjectEntrySet()) {
                    Cell cell = entry.getValue();
                    out.writeLong(entry.getLongKey());
                    out.writeByte(cell.earnings);
                    out.writeBoolean(cell.placed);
                    if (cell.placed) {
                        out.writeInt(cell.placedAt);
                        out.writeInt(cell.brokenAt);
                        out.writeInt(cell.bonemealedAt);
                    }
                    out.writeBoolean(cell.owner != null);
                    if (cell.owner != null) {
                        out.writeLong(cell.owner.getMostSignificantBits());
                        out.writeLong(cell.owner.getLeastSignificantBits());
                    }
                }
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            region.dirty = false;
        } catch (IOException failure) {
            ModPlatform.log().warn("Could not write XP data " + file + ": " + failure.getMessage());
        }
    }

    private static final class Region {
        private final Path file;
        private final Long2ObjectMap<Cell> cells = new Long2ObjectOpenHashMap<>();
        private boolean dirty;
        private long lastAccess;

        private Region(Path file) {
            this.file = file;
        }
    }

    private static final class Cell {
        private int earnings;
        private boolean placed;
        private int placedAt;
        private int brokenAt;
        private int bonemealedAt;
        private UUID owner;

        private boolean isEmpty() {
            return earnings <= 0 && !placed && owner == null;
        }
    }
}
