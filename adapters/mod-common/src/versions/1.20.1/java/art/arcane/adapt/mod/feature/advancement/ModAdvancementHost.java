package art.arcane.adapt.mod.feature.advancement;

import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapt advancement tabs sent as per-player update packets, the way UltimateAdvancementAPI renders them on Bukkit.
 * Nothing is registered with the server advancement manager; vanilla progress and the client's vanilla tabs are left
 * alone. Player state lives on the server thread; progress reads are safe from any thread.
 */
public final class ModAdvancementHost implements AdvancementHost {
    private static final List<String> SINGLE = AdvancementCatalog.criteria(1);
    private static volatile ModAdvancementHost active;

    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private final AtomicLong toastCounter = new AtomicLong();
    private volatile AdvancementCatalog catalog = AdvancementCatalog.EMPTY;

    public ModAdvancementHost() {
        active = this;
    }

    /** Called after vanilla flushed a reset packet to {@code player}, which cleared every client-side tab. */
    public static void afterVanillaReset(ServerPlayer player) {
        ModAdvancementHost host = active;
        if (host == null) {
            return;
        }
        PlayerState state = host.players.get(player.getUUID());
        if (state == null) {
            return;
        }
        state.sent.clear();
        host.sync(player, state, true, null);
    }

    @Override
    public void setCatalog(List<AdvancementNode> roots) {
        catalog = AdvancementCatalog.build(List.copyOf(roots));
        run(() -> {
            MinecraftServer server = ModPlatform.server();
            for (Map.Entry<UUID, PlayerState> entry : players.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    sync(player, entry.getValue(), true, null);
                }
            }
        });
    }

    @Override
    public boolean isRegistered(Id key) {
        return key != null && catalog.contains(key);
    }

    @Override
    public boolean grant(PlayerRef player, Id key, boolean toast) {
        AdvancementCatalog current = catalog;
        AdvancementCatalog.Entry known = current.get(key);
        if (known == null) {
            return false;
        }
        UUID id = player.uuid();
        run(() -> {
            AdvancementCatalog.Entry entry = catalog.get(key);
            if (entry == null) {
                return;
            }
            PlayerState state = state(id);
            state.progress.put(key, entry.max());
            ServerPlayer target = online(id);
            if (target == null) {
                return;
            }
            sync(target, state, false, Set.of(key));
            if (toast) {
                toastDuringUpdate(target, state, entry);
            }
        });
        return true;
    }

    @Override
    public void revoke(PlayerRef player, Id key) {
        UUID id = player.uuid();
        run(() -> {
            PlayerState state = players.get(id);
            if (state == null || state.progress.remove(key) == null) {
                return;
            }
            ServerPlayer target = online(id);
            if (target != null) {
                sync(target, state, false, Set.of(key));
            }
        });
    }

    @Override
    public void setProgress(PlayerRef player, Id key, int progress) {
        UUID id = player.uuid();
        run(() -> {
            AdvancementCatalog.Entry entry = catalog.get(key);
            if (entry == null) {
                return;
            }
            int clamped = Math.max(0, Math.min(entry.max(), progress));
            PlayerState state = state(id);
            if (clamped == 0) {
                state.progress.remove(key);
            } else {
                state.progress.put(key, clamped);
            }
            ServerPlayer target = online(id);
            if (target != null) {
                sync(target, state, false, Set.of(key));
            }
        });
    }

    @Override
    public int progress(PlayerRef player, Id key) {
        PlayerState state = players.get(player.uuid());
        if (state == null) {
            return 0;
        }
        int done = state.done(key);
        AdvancementCatalog.Entry entry = catalog.get(key);
        return entry == null ? done : Math.min(done, entry.max());
    }

    @Override
    public void restore(PlayerRef player, Collection<Id> granted) {
        UUID id = player.uuid();
        List<Id> keys = List.copyOf(granted);
        run(() -> {
            PlayerState state = state(id);
            for (Id key : keys) {
                AdvancementCatalog.Entry entry = catalog.get(key);
                state.progress.put(key, entry == null ? Integer.MAX_VALUE : entry.max());
            }
            ServerPlayer target = online(id);
            if (target != null) {
                sync(target, state, true, null);
            }
        });
    }

    @Override
    public void toast(PlayerRef player, ItemRef icon, String titleMarkup, AdvancementFrame frame) {
        UUID id = player.uuid();
        run(() -> {
            ServerPlayer target = online(id);
            if (target == null) {
                return;
            }
            ResourceLocation key = ModRefs.identifier(Id.adapt("toast/notification_" + toastCounter.incrementAndGet()));
            DisplayInfo display = new DisplayInfo(AdvancementCatalog.icon(icon), ModText.component(titleMarkup), Component.empty(),
                    AdvancementCatalog.texture(null), AdvancementCatalog.type(frame), true, false, false);
            sendToast(target, key, null, display);
        });
    }

    @Override
    public void forget(PlayerRef player) {
        players.remove(player.uuid());
    }

    private void toastDuringUpdate(ServerPlayer target, PlayerState state, AdvancementCatalog.Entry entry) {
        if (!state.sent.contains(entry.id())) {
            return;
        }
        AdvancementNode node = entry.node();
        ResourceLocation key = ModRefs.identifier(Id.adapt("toast/update_" + toastCounter.incrementAndGet()));
        DisplayInfo display = new DisplayInfo(AdvancementCatalog.icon(node.icon()), ModText.component(node.title()), Component.empty(),
                null, AdvancementCatalog.type(node.frame()), true, false, false);
        sendToast(target, key, entry.id(), display);
    }

    /**
     * A {@code parent} is linked through a stand-in with the same id: this version links children into the parent
     * object, and the update packet only sends the parent's id.
     */
    private static void sendToast(ServerPlayer target, ResourceLocation key, ResourceLocation parent, DisplayInfo display) {
        Advancement.Builder builder = Advancement.Builder.advancement();
        if (parent != null) {
            builder.parent(Advancement.Builder.advancement().build(parent));
        }
        builder.display(display);
        AdvancementCriteria.impossible(builder, SINGLE.get(0));
        builder.requirements(AdvancementCatalog.allOf(SINGLE));
        Advancement advancement = builder.build(key);
        Map<ResourceLocation, AdvancementProgress> progress = Map.of(key, AdvancementCatalog.progress(advancement, SINGLE, 1));
        target.connection.send(AdvancementPackets.update(false, List.of(new PlacedAdvancement(advancement, 0.0F, 0.0F)), Set.of(), progress));
        target.connection.send(AdvancementPackets.update(false, List.of(), Set.of(key), Map.of()));
    }

    /**
     * Brings the client in line with the player's progress. A full sync removes and re-adds every visible node; an
     * incremental sync adds and removes only what visibility changed and sends progress for {@code changed} keys.
     */
    private void sync(ServerPlayer target, PlayerState state, boolean full, Set<Id> changed) {
        AdvancementCatalog current = catalog;
        Set<ResourceLocation> visible = visible(current, state);
        Set<ResourceLocation> removed = new LinkedHashSet<>();
        List<PlacedAdvancement> added = new ArrayList<>();
        Map<ResourceLocation, AdvancementProgress> progress = new HashMap<>();
        if (full) {
            removed.addAll(state.sent);
            state.sent.clear();
        } else {
            for (ResourceLocation id : state.sent) {
                if (!visible.contains(id)) {
                    removed.add(id);
                }
            }
            state.sent.removeAll(removed);
        }
        for (AdvancementCatalog.Entry entry : current.entries()) {
            ResourceLocation id = entry.id();
            if (!visible.contains(id)) {
                continue;
            }
            boolean fresh = !state.sent.contains(id);
            if (fresh) {
                added.add(entry.placed());
                state.sent.add(id);
            }
            if (fresh || (changed != null && changed.contains(entry.key()))) {
                progress.put(id, entry.progress(state.done(entry.key())));
            }
        }
        if (removed.isEmpty() && added.isEmpty() && progress.isEmpty()) {
            return;
        }
        ClientboundUpdateAdvancementsPacket packet = AdvancementPackets.update(false, added, removed, progress);
        target.connection.send(packet);
    }

    private static Set<ResourceLocation> visible(AdvancementCatalog current, PlayerState state) {
        Set<ResourceLocation> visible = new HashSet<>();
        for (AdvancementCatalog.Entry entry : current.entries()) {
            if (shown(entry, state)) {
                AdvancementCatalog.Entry cursor = entry;
                while (cursor != null && visible.add(cursor.id())) {
                    cursor = cursor.parent();
                }
            }
        }
        return visible;
    }

    private static boolean shown(AdvancementCatalog.Entry entry, PlayerState state) {
        AdvancementCatalog.Entry parent = entry.parent();
        if (parent == null || state.done(entry.key()) > 0) {
            return true;
        }
        return switch (entry.visibility()) {
            case ALWAYS -> true;
            case HIDDEN -> false;
            case PARENT_GRANTED -> state.granted(parent);
            case VANILLA -> state.granted(parent) || (parent.parent() != null && state.granted(parent.parent()));
        };
    }

    private PlayerState state(UUID id) {
        return players.computeIfAbsent(id, (UUID ignored) -> new PlayerState());
    }

    private static ServerPlayer online(UUID id) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player == null || player.hasDisconnected() ? null : player;
    }

    private static void run(Runnable task) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return;
        }
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }

    private static final class PlayerState {
        private final Map<Id, Integer> progress = new ConcurrentHashMap<>();
        private final Set<ResourceLocation> sent = new HashSet<>();

        int done(Id key) {
            Integer value = progress.get(key);
            return value == null ? 0 : value;
        }

        boolean granted(AdvancementCatalog.Entry entry) {
            return done(entry.key()) >= entry.max();
        }
    }
}
