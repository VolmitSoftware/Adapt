package art.arcane.adapt.mod.feature.viewer;

import art.arcane.adapt.mod.feature.mixin.ViewerAvatarDataAccess;
import art.arcane.adapt.mod.feature.mixin.ViewerEntityCounterAccess;
import art.arcane.adapt.mod.feature.mixin.ViewerPlayerInfoAccess;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.VirtualPlayer;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import art.arcane.adapt.mod.feature.FeatureEntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ModVirtualPlayer implements VirtualPlayer {
    private static final EquipmentSlot[] BODY_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAIN_HAND, EquipmentSlot.OFF_HAND
    };

    private final UUID profileId;
    private final int entityId;
    private final ClientboundPlayerInfoUpdatePacket playerInfo;
    private final ClientboundPlayerInfoRemovePacket removeInfo;
    private final ClientboundRemoveEntitiesPacket removeEntity;
    private final ClientboundSetEntityDataPacket skinData;
    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    ModVirtualPlayer(PlayerRef skinSource, Loc location, int skinLayerMask) {
        ServerPlayer source = ModRefs.player(skinSource);
        GameProfile sourceProfile = source.getGameProfile();
        String name = sourceProfile.name();
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        this.profileId = UUID.randomUUID();
        this.entityId = ViewerEntityCounterAccess.adapt$entityCounter().incrementAndGet();
        GameProfile profile = new GameProfile(profileId, name, sourceProfile.properties());
        this.playerInfo = playerInfo(profileId, profile);
        this.removeInfo = new ClientboundPlayerInfoRemovePacket(List.of(profileId));
        this.removeEntity = new ClientboundRemoveEntitiesPacket(entityId);
        this.skinData = new ClientboundSetEntityDataPacket(entityId,
                List.of(SynchedEntityData.DataValue.create(ViewerAvatarDataAccess.adapt$skinLayers(), (byte) (skinLayerMask & 0xFF))));
        this.x = location.x();
        this.y = location.y();
        this.z = location.z();
        this.yaw = location.yaw();
        this.pitch = location.pitch();
        for (EquipmentSlot slot : BODY_SLOTS) {
            equipment.put(slot, source.getItemBySlot(ModViewerHost.slot(slot)).copy());
        }
    }

    @Override
    public int entityId() {
        return entityId;
    }

    @Override
    public void spawn(PlayerRef viewer) {
        ServerPlayer player = ModRefs.player(viewer);
        if (player == null) {
            return;
        }
        List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> slots;
        float spawnYaw;
        float spawnPitch;
        double spawnX;
        double spawnY;
        double spawnZ;
        synchronized (this) {
            slots = slots(equipment);
            spawnYaw = yaw;
            spawnPitch = pitch;
            spawnX = x;
            spawnY = y;
            spawnZ = z;
        }
        player.connection.send(playerInfo);
        player.connection.send(new ClientboundAddEntityPacket(entityId, profileId, spawnX, spawnY, spawnZ, spawnPitch, spawnYaw,
                FeatureEntityTypes.player(), 0, Vec3.ZERO, spawnYaw));
        player.connection.send(skinData);
        if (!slots.isEmpty()) {
            player.connection.send(new ClientboundSetEquipmentPacket(entityId, slots));
        }
        player.connection.send(rotateHead(entityId, spawnYaw));
    }

    @Override
    public void destroy(PlayerRef viewer) {
        ServerPlayer player = ModRefs.player(viewer);
        if (player == null) {
            return;
        }
        player.connection.send(removeEntity);
        player.connection.send(removeInfo);
    }

    @Override
    public void removeFromTab(PlayerRef viewer) {
        ServerPlayer player = ModRefs.player(viewer);
        if (player != null) {
            player.connection.send(removeInfo);
        }
    }

    @Override
    public boolean move(Loc location, boolean onGround, Collection<PlayerRef> viewers) {
        synchronized (this) {
            x = location.x();
            y = location.y();
            z = location.z();
            yaw = location.yaw();
            pitch = location.pitch();
        }
        Packet<ClientGamePacketListener> packet = ViewerPackets.positionSync(entityId, location.x(), location.y(), location.z(),
                location.yaw(), location.pitch(), onGround);
        send(viewers, packet);
        return true;
    }

    @Override
    public void look(float newYaw, float newPitch, PlayerRef viewer) {
        ServerPlayer player = ModRefs.player(viewer);
        if (player == null) {
            return;
        }
        player.connection.send(new ClientboundMoveEntityPacket.Rot(entityId, angle(newYaw), angle(newPitch), true));
        player.connection.send(rotateHead(entityId, newYaw));
    }

    @Override
    public void hurt(float hurtYaw, Collection<PlayerRef> viewers) {
        send(viewers, new ClientboundHurtAnimationPacket(entityId, hurtYaw));
    }

    @Override
    public void equipment(Map<EquipmentSlot, ItemRef> items, Collection<PlayerRef> viewers) {
        Map<EquipmentSlot, ItemStack> changed = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, ItemRef> entry : items.entrySet()) {
            changed.put(entry.getKey(), ModViewerHost.stack(entry.getValue()));
        }
        synchronized (this) {
            equipment.putAll(changed);
        }
        List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> slots = slots(changed);
        if (!slots.isEmpty()) {
            send(viewers, new ClientboundSetEquipmentPacket(entityId, slots));
        }
    }

    private static List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> slots(Map<EquipmentSlot, ItemStack> items) {
        List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> slots = new ArrayList<>(items.size());
        for (Map.Entry<EquipmentSlot, ItemStack> entry : items.entrySet()) {
            slots.add(Pair.of(ModViewerHost.slot(entry.getKey()), entry.getValue().copy()));
        }
        return slots;
    }

    private static void send(Collection<PlayerRef> viewers, Packet<ClientGamePacketListener> packet) {
        for (PlayerRef viewer : viewers) {
            ServerPlayer player = ModRefs.player(viewer);
            if (player != null && !player.hasDisconnected()) {
                player.connection.send(packet);
            }
        }
    }

    private static ClientboundPlayerInfoUpdatePacket playerInfo(UUID id, GameProfile profile) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(id, profile, true, 0, GameType.DEFAULT_MODE, null, true, 0, null);
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        ((ViewerPlayerInfoAccess) packet).adapt$entries(List.of(entry));
        return packet;
    }

    private static Packet<ClientGamePacketListener> rotateHead(int id, float headYaw) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(8));
        try {
            buffer.writeVarInt(id);
            buffer.writeByte(angle(headYaw));
            return ClientboundRotateHeadPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static byte angle(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }
}
