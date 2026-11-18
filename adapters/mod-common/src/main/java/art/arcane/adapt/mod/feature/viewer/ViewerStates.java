package art.arcane.adapt.mod.feature.viewer;

import art.arcane.adapt.mod.feature.mixin.ViewerEntityDataAccess;
import art.arcane.adapt.platform.viewer.GlowColor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-viewer client state shared between {@link ModViewerHost} and the viewer mixins: entities hidden from a
 * viewer and the glow colour each viewer sees on an entity. State resets when the viewer reconnects.
 */
public final class ViewerStates {
    static final byte GLOWING_BIT = 0x40;
    private static final Map<UUID, ViewerState> STATES = new ConcurrentHashMap<>();
    private static final AtomicInteger GLOWS = new AtomicInteger();
    private static final AtomicInteger HIDDEN = new AtomicInteger();
    private static final Set<Integer> DEFAULT_HIDDEN = ConcurrentHashMap.newKeySet();

    private ViewerStates() {
    }

    public static boolean isHidden(ServerPlayer viewer, Entity entity) {
        if (HIDDEN.get() == 0 && DEFAULT_HIDDEN.isEmpty()) {
            return false;
        }
        int id = entity.getId();
        boolean hiddenByDefault = DEFAULT_HIDDEN.contains(id);
        ViewerState state = existing(viewer);
        if (state == null) {
            return hiddenByDefault;
        }
        synchronized (state) {
            return state.hidden.contains(id) || (hiddenByDefault && !state.shown.contains(id));
        }
    }

    /** Entities spawned with visible-by-default off: only viewers that were explicitly shown them see them. */
    public static boolean isHiddenByDefault(int entityId) {
        return DEFAULT_HIDDEN.contains(entityId);
    }

    public static void setHiddenByDefault(int entityId, boolean hidden) {
        if (hidden) {
            DEFAULT_HIDDEN.add(entityId);
        } else {
            DEFAULT_HIDDEN.remove(entityId);
        }
    }

    public static boolean hasGlows() {
        return GLOWS.get() > 0;
    }

    public static Packet<?> rewrite(ServerPlayer viewer, Packet<?> packet) {
        ViewerState state = existing(viewer);
        if (state == null) {
            return packet;
        }
        synchronized (state) {
            if (state.glows.isEmpty()) {
                return packet;
            }
            if (packet instanceof ClientboundSetEntityDataPacket data) {
                return glowing(state, data);
            }
            if (packet instanceof ClientboundBundlePacket bundle) {
                List<Packet<? super ClientGamePacketListener>> parts = new ArrayList<>();
                boolean changed = false;
                for (Packet<? super ClientGamePacketListener> part : bundle.subPackets()) {
                    if (part instanceof ClientboundSetEntityDataPacket data) {
                        ClientboundSetEntityDataPacket rewritten = glowing(state, data);
                        changed |= rewritten != data;
                        parts.add(rewritten);
                    } else {
                        parts.add(part);
                    }
                }
                return changed ? new ClientboundBundlePacket(parts) : packet;
            }
            return packet;
        }
    }

    private static ClientboundSetEntityDataPacket glowing(ViewerState state, ClientboundSetEntityDataPacket data) {
        if (!state.glows.containsKey(data.id())) {
            return data;
        }
        EntityDataAccessor<Byte> flags = ViewerEntityDataAccess.adapt$sharedFlags();
        List<SynchedEntityData.DataValue<?>> values = data.packedItems();
        for (int index = 0; index < values.size(); index++) {
            SynchedEntityData.DataValue<?> value = values.get(index);
            if (value.id() == flags.id() && value.value() instanceof Byte current && (current & GLOWING_BIT) == 0) {
                List<SynchedEntityData.DataValue<?>> copy = new ArrayList<>(values);
                copy.set(index, SynchedEntityData.DataValue.create(flags, (byte) (current | GLOWING_BIT)));
                return new ClientboundSetEntityDataPacket(data.id(), copy);
            }
        }
        return data;
    }

    static ViewerState existing(ServerPlayer viewer) {
        ViewerState state = STATES.get(viewer.getUUID());
        return state != null && state.player == viewer ? state : null;
    }

    static ViewerState state(ServerPlayer viewer) {
        ViewerState state = STATES.get(viewer.getUUID());
        if (state != null && state.player == viewer) {
            return state;
        }
        ViewerState fresh = new ViewerState(viewer);
        ViewerState previous = STATES.put(viewer.getUUID(), fresh);
        if (previous != null) {
            discard(previous);
        }
        return fresh;
    }

    static void hide(ViewerState state, int entityId) {
        synchronized (state) {
            state.shown.remove(entityId);
            if (state.hidden.add(entityId)) {
                HIDDEN.incrementAndGet();
            }
        }
    }

    static boolean show(ViewerState state, int entityId) {
        synchronized (state) {
            boolean changed = DEFAULT_HIDDEN.contains(entityId) && state.shown.add(entityId);
            if (state.hidden.remove(entityId)) {
                HIDDEN.decrementAndGet();
                changed = true;
            }
            return changed;
        }
    }

    static GlowEntry putGlow(ViewerState state, int entityId, GlowEntry entry) {
        synchronized (state) {
            GlowEntry previous = state.glows.put(entityId, entry);
            if (previous == null) {
                GLOWS.incrementAndGet();
            }
            return previous;
        }
    }

    static GlowEntry removeGlow(ViewerState state, int entityId) {
        synchronized (state) {
            GlowEntry previous = state.glows.remove(entityId);
            if (previous != null) {
                GLOWS.decrementAndGet();
            }
            return previous;
        }
    }

    static GlowEntry glow(ViewerState state, int entityId) {
        synchronized (state) {
            return state.glows.get(entityId);
        }
    }

    static boolean markTeam(ViewerState state, GlowColor color) {
        synchronized (state) {
            return state.teams.add(color);
        }
    }

    static void prune(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ViewerState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ViewerState> entry = iterator.next();
            ViewerState state = entry.getValue();
            if (server.getPlayerList().getPlayer(entry.getKey()) != state.player || state.player.hasDisconnected()) {
                iterator.remove();
                discard(state);
                continue;
            }
            synchronized (state) {
                IntIterator ids = state.hidden.iterator();
                while (ids.hasNext()) {
                    if (!exists(server, ids.nextInt())) {
                        ids.remove();
                        HIDDEN.decrementAndGet();
                    }
                }
                state.shown.removeIf((int id) -> !exists(server, id));
            }
        }
        DEFAULT_HIDDEN.removeIf((Integer id) -> !exists(server, id));
    }

    private static boolean exists(MinecraftServer server, int entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(entityId) != null) {
                return true;
            }
        }
        return false;
    }

    private static void discard(ViewerState state) {
        synchronized (state) {
            GLOWS.addAndGet(-state.glows.size());
            HIDDEN.addAndGet(-state.hidden.size());
            state.glows.clear();
            state.hidden.clear();
            state.shown.clear();
            state.teams.clear();
        }
    }

    record GlowEntry(GlowColor color, String member) {
    }

    static final class ViewerState {
        final ServerPlayer player;
        final IntSet hidden = new IntOpenHashSet();
        final IntSet shown = new IntOpenHashSet();
        final Int2ObjectMap<GlowEntry> glows = new Int2ObjectOpenHashMap<>();
        final Set<GlowColor> teams = EnumSet.noneOf(GlowColor.class);

        ViewerState(ServerPlayer player) {
            this.player = player;
        }
    }
}
