package art.arcane.adapt.bukkit.feature.viewer;

import art.arcane.adapt.bukkit.packet.EquipmentPackets;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.VirtualPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** VolmLib's native virtual player (the stealth decoy body) behind the core interface. */
final class BukkitVirtualPlayer implements VirtualPlayer {
  private final art.arcane.volmlib.nativelib.entity.VirtualPlayer nativePlayer;
  private final int entityId;

  BukkitVirtualPlayer(art.arcane.volmlib.nativelib.entity.VirtualPlayer nativePlayer) {
    this.nativePlayer = nativePlayer;
    this.entityId = entityId(nativePlayer);
  }

  @Override
  public int entityId() {
    return entityId;
  }

  @Override
  public void spawn(PlayerRef viewer) {
    Player player = BukkitRefs.player(viewer);
    if (player != null) {
      nativePlayer.spawn(player);
    }
  }

  @Override
  public void destroy(PlayerRef viewer) {
    Player player = BukkitRefs.player(viewer);
    if (player != null) {
      nativePlayer.destroy(player);
    }
  }

  @Override
  public void removeFromTab(PlayerRef viewer) {
    Player player = BukkitRefs.player(viewer);
    if (player != null) {
      nativePlayer.removeFromTab(player);
    }
  }

  @Override
  public boolean move(Loc location, boolean onGround, Collection<PlayerRef> viewers) {
    Location at = BukkitRefs.location(location);
    return at != null && nativePlayer.move(at, onGround, players(viewers));
  }

  @Override
  public void look(float yaw, float pitch, PlayerRef viewer) {
    Player player = BukkitRefs.player(viewer);
    if (player != null) {
      nativePlayer.look(yaw, pitch, player);
    }
  }

  @Override
  public void hurt(float yaw, Collection<PlayerRef> viewers) {
    nativePlayer.hurt(yaw, players(viewers));
  }

  @Override
  public void equipment(Map<EquipmentSlot, ItemRef> items, Collection<PlayerRef> viewers) {
    if (items.isEmpty() || entityId < 0) {
      return;
    }
    Map<org.bukkit.inventory.EquipmentSlot, ItemStack> stacks = BukkitViewerHost.stacks(items);
    for (Player player : players(viewers)) {
      EquipmentPackets.send(player, entityId, stacks);
    }
  }

  private static List<Player> players(Collection<PlayerRef> viewers) {
    List<Player> players = new ArrayList<>(viewers.size());
    for (PlayerRef viewer : viewers) {
      Player player = BukkitRefs.player(viewer);
      if (player != null && player.isOnline()) {
        players.add(player);
      }
    }
    return players;
  }

  /** VolmLib keeps the id in its private {@code entityId} field; -1 when that cannot be read. */
  static int entityId(Object nativePlayer) {
    for (Class<?> type = nativePlayer.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField("entityId");
        field.setAccessible(true);
        return field.getInt(nativePlayer);
      } catch (NoSuchFieldException next) {
        continue;
      } catch (ReflectiveOperationException | RuntimeException failure) {
        return -1;
      }
    }
    return -1;
  }
}
