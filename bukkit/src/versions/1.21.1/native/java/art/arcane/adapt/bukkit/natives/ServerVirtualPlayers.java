package art.arcane.adapt.bukkit.natives;

import art.arcane.volmlib.nativelib.entity.ClientPacket;
import art.arcane.volmlib.nativelib.entity.VirtualPlayer;
import art.arcane.volmlib.nativelib.entity.VirtualPlayerAccess;
import art.arcane.volmlib.nativelib.entity.VirtualPlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Client-only player bodies (the Shadow Decoy): a tab entry carrying the owner's skin, a player entity under a fresh
 * entity id and the owner's equipment at creation, all sent as packets; the server holds no entity. Packets go out on
 * each viewer's own thread through the viewer executor, as the 26.x native virtual players do.
 */
final class ServerVirtualPlayers implements VirtualPlayerAccess {
  @Override
  public VirtualPlayers create(Plugin plugin, BiConsumer<org.bukkit.entity.Player, Runnable> viewerExecutor) {
    return new Players(plugin, viewerExecutor);
  }

  static ClientboundSetEquipmentPacket equipmentPacket(int entityId, Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> items) {
    List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>(items.size());
    for (Map.Entry<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> entry : items.entrySet()) {
      org.bukkit.inventory.ItemStack stack = entry.getValue() == null ? new org.bukkit.inventory.ItemStack(Material.AIR) : entry.getValue();
      slots.add(Pair.of(ServerHandles.slot(entry.getKey()), ServerHandles.item(stack)));
    }
    return slots.isEmpty() ? null : new ClientboundSetEquipmentPacket(entityId, slots);
  }

  private static byte angle(float degrees) {
    return (byte) (degrees * 256.0F / 360.0F);
  }

  private static final class Players implements VirtualPlayers {
    private final Plugin plugin;
    private final BiConsumer<org.bukkit.entity.Player, Runnable> viewerExecutor;
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    private Players(Plugin plugin, BiConsumer<org.bukkit.entity.Player, Runnable> viewerExecutor) {
      this.plugin = plugin;
      this.viewerExecutor = viewerExecutor;
    }

    @Override
    public VirtualPlayer create(org.bukkit.entity.Player owner, Location location, int skinLayerMask) {
      ServerPlayer source = ServerHandles.player(owner);
      if (source == null || location.getWorld() == null) {
        return null;
      }
      String name = owner.getName();
      if (name.length() > 16) {
        name = name.substring(0, 16);
      }
      UUID profileId = UUID.randomUUID();
      GameProfile profile = new GameProfile(profileId, name);
      profile.getProperties().putAll(source.getGameProfile().getProperties());
      int entityId = Bukkit.getUnsafe().nextEntityId();

      List<Packet<?>> spawnPackets = new ArrayList<>(4);
      spawnPackets.add(playerInfo(profileId, profile));
      spawnPackets.add(new ClientboundAddEntityPacket(entityId, profileId, location.getX(), location.getY(), location.getZ(),
          location.getPitch(), location.getYaw(), EntityType.PLAYER, 0, Vec3.ZERO, location.getYaw()));
      spawnPackets.add(new ClientboundSetEntityDataPacket(entityId,
          List.of(SynchedEntityData.DataValue.create(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) (skinLayerMask & 0xFF)))));
      ClientboundSetEquipmentPacket equipment = equipmentPacket(entityId, equipment(owner.getInventory(), false));
      if (equipment != null) {
        spawnPackets.add(equipment);
      }
      return new Body(this, profileId, entityId, List.copyOf(spawnPackets));
    }

    @Override
    public ClientPacket equipment(org.bukkit.entity.Player owner, boolean hidden) {
      if (owner == null || !owner.isOnline()) {
        return null;
      }
      ClientboundSetEquipmentPacket packet = equipmentPacket(owner.getEntityId(), equipment(owner.getInventory(), hidden));
      return packet == null ? null : viewer -> send(viewer, packet);
    }

    private static Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> equipment(PlayerInventory inventory, boolean hidden) {
      Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> items = new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);
      items.put(org.bukkit.inventory.EquipmentSlot.HEAD, hidden ? null : inventory.getHelmet());
      items.put(org.bukkit.inventory.EquipmentSlot.CHEST, hidden ? null : inventory.getChestplate());
      items.put(org.bukkit.inventory.EquipmentSlot.LEGS, hidden ? null : inventory.getLeggings());
      items.put(org.bukkit.inventory.EquipmentSlot.FEET, hidden ? null : inventory.getBoots());
      items.put(org.bukkit.inventory.EquipmentSlot.HAND, hidden ? null : inventory.getItemInMainHand());
      items.put(org.bukkit.inventory.EquipmentSlot.OFF_HAND, hidden ? null : inventory.getItemInOffHand());
      return items;
    }

    private static ClientboundPlayerInfoUpdatePacket playerInfo(UUID profileId, GameProfile profile) {
      EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
          ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
          ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
          ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
      return new ClientboundPlayerInfoUpdatePacket(actions, List.of(new ClientboundPlayerInfoUpdatePacket.Entry(profileId, profile,
          true, 0, GameType.DEFAULT_MODE, null, null)));
    }

    private void send(org.bukkit.entity.Player viewer, Packet<?> packet) {
      if (viewer == null || !viewer.isOnline()) {
        return;
      }
      viewerExecutor.accept(viewer, () -> {
        ServerPlayer player = ServerHandles.player(viewer);
        if (!viewer.isOnline() || player == null || player.connection == null) {
          return;
        }
        try {
          player.connection.send(packet);
        } catch (RuntimeException failure) {
          report("send virtual player packet", failure);
        }
      });
    }

    private void send(List<org.bukkit.entity.Player> viewers, Packet<?> packet) {
      for (org.bukkit.entity.Player viewer : viewers) {
        send(viewer, packet);
      }
    }

    private void report(String operation, Throwable failure) {
      if (reportedFailures.add(operation)) {
        plugin.getLogger().log(Level.WARNING, "Unable to " + operation, failure);
      }
    }
  }

  private static final class Body implements VirtualPlayer {
    private final Players players;
    private final List<Packet<?>> spawnPackets;
    private final ClientboundPlayerInfoRemovePacket removeInfo;
    private final ClientboundRemoveEntitiesPacket removeEntity;
    /** Read by the Bukkit viewer bridge, which addresses equipment packets to this id. */
    private final int entityId;

    private Body(Players players, UUID profileId, int entityId, List<Packet<?>> spawnPackets) {
      this.players = players;
      this.entityId = entityId;
      this.spawnPackets = spawnPackets;
      this.removeInfo = new ClientboundPlayerInfoRemovePacket(List.of(profileId));
      this.removeEntity = new ClientboundRemoveEntitiesPacket(entityId);
    }

    @Override
    public void spawn(org.bukkit.entity.Player viewer) {
      for (Packet<?> packet : spawnPackets) {
        players.send(viewer, packet);
      }
    }

    @Override
    public void removeFromTab(org.bukkit.entity.Player viewer) {
      players.send(viewer, removeInfo);
    }

    @Override
    public void destroy(org.bukkit.entity.Player viewer) {
      players.send(viewer, removeEntity);
      removeFromTab(viewer);
    }

    @Override
    public boolean look(float yaw, float pitch, org.bukkit.entity.Player viewer) {
      players.send(viewer, new ClientboundMoveEntityPacket.Rot(entityId, angle(yaw), angle(pitch), true));
      players.send(viewer, ServerPackets.rotateHead(entityId, angle(yaw)));
      return true;
    }

    @Override
    public boolean move(Location location, boolean onGround, List<org.bukkit.entity.Player> viewers) {
      players.send(viewers, ServerPackets.positionSync(entityId, location.getX(), location.getY(), location.getZ(),
          angle(location.getYaw()), angle(location.getPitch()), onGround));
      return true;
    }

    @Override
    public void hurt(float yaw, List<org.bukkit.entity.Player> viewers) {
      players.send(viewers, new ClientboundHurtAnimationPacket(entityId, yaw));
    }
  }
}
