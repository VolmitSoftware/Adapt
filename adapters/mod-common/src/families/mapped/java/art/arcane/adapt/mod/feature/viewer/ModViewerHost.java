package art.arcane.adapt.mod.feature.viewer;

import art.arcane.adapt.mod.feature.mixin.ViewerChunkMapAccess;
import art.arcane.adapt.mod.feature.mixin.ViewerEntityDataAccess;
import art.arcane.adapt.mod.platform.ModHosts;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.viewer.VirtualPlayer;
import art.arcane.adapt.platform.world.WorldRef;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vanilla viewer host. Hidden entities are refused by the entity tracker for that viewer (see the tracked-entity
 * mixin), so they stay hidden when the tracker would re-pair them. Per-viewer glow is a client-only team plus the
 * glowing flag, which the packet mixin re-applies to every later data update of that entity for that viewer.
 */
public final class ModViewerHost implements ViewerHost {
    private static final int PRUNE_INTERVAL_TICKS = 600;
    private static final Map<GlowColor, PlayerTeam> TEAMS = teams();
    private int ticks;

    public ModViewerHost() {
        ModHosts.onServerTick((MinecraftServer server) -> {
            if (++ticks >= PRUNE_INTERVAL_TICKS) {
                ticks = 0;
                ViewerStates.prune(server);
            }
        });
    }

    @Override
    public void showEntity(PlayerRef viewer, EntityRef entity) {
        ServerPlayer player = ModRefs.player(viewer);
        Entity target = ModRefs.entity(entity);
        if (player == null || target == null) {
            return;
        }
        if (ViewerStates.show(ViewerStates.state(player), target.getId())) {
            refresh(player, target);
        }
    }

    @Override
    public void hideEntity(PlayerRef viewer, EntityRef entity) {
        ServerPlayer player = ModRefs.player(viewer);
        Entity target = ModRefs.entity(entity);
        if (player == null || target == null || player == target) {
            return;
        }
        ViewerStates.hide(ViewerStates.state(player), target.getId());
        refresh(player, target);
    }

    @Override
    public boolean canSee(PlayerRef viewer, EntityRef entity) {
        ServerPlayer player = ModRefs.player(viewer);
        Entity target = ModRefs.entity(entity);
        return player != null && target != null && !ViewerStates.isHidden(player, target);
    }

    @Override
    public void setGlow(PlayerRef viewer, EntityRef entity, GlowColor color) {
        ServerPlayer player = ModRefs.player(viewer);
        Entity target = ModRefs.entity(entity);
        if (player == null || target == null || color == null) {
            return;
        }
        ViewerStates.ViewerState state = ViewerStates.state(player);
        String member = target.getScoreboardName();
        ViewerStates.GlowEntry previous = ViewerStates.putGlow(state, target.getId(), new ViewerStates.GlowEntry(color, member));
        if (previous != null && previous.color() == color && previous.member().equals(member)) {
            return;
        }
        PlayerTeam team = TEAMS.get(color);
        if (ViewerStates.markTeam(state, color)) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        }
        player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, member, ClientboundSetPlayerTeamPacket.Action.ADD));
        EntityDataAccessor<Byte> flags = ViewerEntityDataAccess.adapt$sharedFlags();
        byte current = target.getEntityData().get(flags);
        player.connection.send(new ClientboundSetEntityDataPacket(target.getId(),
                List.of(SynchedEntityData.DataValue.create(flags, (byte) (current | ViewerStates.GLOWING_BIT)))));
    }

    @Override
    public void clearGlow(PlayerRef viewer, int entityId) {
        ServerPlayer player = ModRefs.player(viewer);
        if (player == null) {
            return;
        }
        ViewerStates.ViewerState state = ViewerStates.existing(player);
        if (state == null) {
            return;
        }
        ViewerStates.GlowEntry removed = ViewerStates.removeGlow(state, entityId);
        if (removed == null) {
            return;
        }
        player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(TEAMS.get(removed.color()), removed.member(),
                ClientboundSetPlayerTeamPacket.Action.REMOVE));
        Entity target = player.level().getEntity(entityId);
        if (target == null) {
            return;
        }
        if (target.getTeam() instanceof PlayerTeam realTeam) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(realTeam, removed.member(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
        EntityDataAccessor<Byte> flags = ViewerEntityDataAccess.adapt$sharedFlags();
        player.connection.send(new ClientboundSetEntityDataPacket(entityId,
                List.of(SynchedEntityData.DataValue.create(flags, target.getEntityData().get(flags)))));
    }

    @Override
    public void sendEquipment(PlayerRef viewer, EntityRef entity, Map<EquipmentSlot, ItemRef> items) {
        ServerPlayer player = ModRefs.player(viewer);
        if (player == null || entity == null || items.isEmpty()) {
            return;
        }
        List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> slots = new ArrayList<>(items.size());
        for (Map.Entry<EquipmentSlot, ItemRef> entry : items.entrySet()) {
            net.minecraft.world.entity.EquipmentSlot slot = ViewerSlots.vanilla(entry.getKey());
            if (slot != null) {
                slots.add(Pair.of(slot, stack(entry.getValue())));
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        player.connection.send(new ClientboundSetEquipmentPacket(entity.entityId(), slots));
    }

    @Override
    public VirtualPlayer createVirtualPlayer(PlayerRef skinSource, Loc location, int skinLayerMask) {
        if (skinSource == null || location == null || ModRefs.player(skinSource) == null) {
            return null;
        }
        return new ModVirtualPlayer(skinSource, location, skinLayerMask);
    }

    @Override
    public void setVisibleByDefault(EntityRef entity, boolean visible) {
        Entity target = entity == null ? null : ModRefs.entity(entity);
        if (target == null || ViewerStates.isHiddenByDefault(target.getId()) == !visible) {
            return;
        }
        ViewerStates.setHiddenByDefault(target.getId(), !visible);
        refreshAll(target);
    }

    @Override
    public Collection<PlayerRef> trackingPlayers(EntityRef entity) {
        Entity target = entity == null ? null : ModRefs.entity(entity);
        if (target == null || !(target.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Object tracked = ((ViewerChunkMapAccess) level.getChunkSource().chunkMap).adapt$entityMap().get(target.getId());
        if (!(tracked instanceof TrackedEntityBridge bridge)) {
            return List.of();
        }
        List<PlayerRef> players = new ArrayList<>();
        for (ServerPlayer player : bridge.adapt$viewers()) {
            players.add(ModRefs.player(player));
        }
        return players;
    }

    @Override
    public Collection<PlayerRef> chunkViewers(WorldRef world, int chunkX, int chunkZ) {
        ServerLevel level = world == null ? null : ModRefs.world(world);
        if (level == null) {
            return List.of();
        }
        List<PlayerRef> players = new ArrayList<>();
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(chunkX, chunkZ), false)) {
            players.add(ModRefs.player(player));
        }
        return players;
    }

    /** Re-evaluates every player of the entity's level, after its default visibility changed. */
    public static void refreshAll(Entity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            refresh(player, target);
        }
    }

    private static void refresh(ServerPlayer viewer, Entity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Object tracked = ((ViewerChunkMapAccess) level.getChunkSource().chunkMap).adapt$entityMap().get(target.getId());
        if (tracked instanceof TrackedEntityBridge bridge) {
            bridge.adapt$refreshViewer(viewer);
        }
    }

    static ItemStack stack(ItemRef item) {
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = ModRefs.item(item);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static Map<GlowColor, PlayerTeam> teams() {
        Scoreboard board = new Scoreboard();
        Map<GlowColor, PlayerTeam> teams = new EnumMap<>(GlowColor.class);
        for (GlowColor color : GlowColor.values()) {
            PlayerTeam team = new PlayerTeam(board, "adapt_glow_" + color.name().toLowerCase(Locale.ROOT));
            GlowTeamColors.apply(team, color);
            teams.put(color, team);
        }
        return teams;
    }
}
