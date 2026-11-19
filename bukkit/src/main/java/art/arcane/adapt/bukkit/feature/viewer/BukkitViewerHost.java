package art.arcane.adapt.bukkit.feature.viewer;

import art.arcane.adapt.bukkit.packet.EquipmentPackets;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.viewer.VirtualPlayer;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.volmlib.nativelib.entity.EntityGlow;
import art.arcane.volmlib.nativelib.entity.VirtualPlayers;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Per-viewer state through Bukkit's entity hiding, VolmLib's native glow and virtual players: the same natives
 * {@code ViewerGlowCoordinator} and the stealth decoy use. Glow layering stays in the coordinator; this host only
 * sends what it is told. Glow packets are serialised on the plugin's shared glow lock.
 */
public final class BukkitViewerHost implements ViewerHost {
  private final Plugin plugin;
  private final Supplier<EntityGlow> glow;
  private final Object glowLock;
  private final Supplier<VirtualPlayers> virtualPlayers;
  private final PlatformLog log;
  private final AtomicBoolean glowFailureLogged = new AtomicBoolean();

  public BukkitViewerHost(Plugin plugin, Supplier<EntityGlow> glow, Object glowLock, Supplier<VirtualPlayers> virtualPlayers, PlatformLog log) {
    this.plugin = plugin;
    this.glow = glow;
    this.glowLock = glowLock;
    this.virtualPlayers = virtualPlayers;
    this.log = log;
  }

  @Override
  public void showEntity(PlayerRef viewer, EntityRef entity) {
    Player player = BukkitRefs.player(viewer);
    Entity target = BukkitRefs.entity(entity);
    if (player != null && target != null) {
      player.showEntity(plugin, target);
    }
  }

  @Override
  public void hideEntity(PlayerRef viewer, EntityRef entity) {
    Player player = BukkitRefs.player(viewer);
    Entity target = BukkitRefs.entity(entity);
    if (player != null && target != null && player != target) {
      player.hideEntity(plugin, target);
    }
  }

  @Override
  public boolean canSee(PlayerRef viewer, EntityRef entity) {
    Player player = BukkitRefs.player(viewer);
    Entity target = BukkitRefs.entity(entity);
    return player != null && target != null && player.canSee(target);
  }

  @Override
  public void setGlow(PlayerRef viewer, EntityRef entity, GlowColor color) {
    Player player = BukkitRefs.player(viewer);
    Entity target = BukkitRefs.entity(entity);
    EntityGlow nativeGlow = glow.get();
    if (player == null || target == null || color == null || nativeGlow == null) {
      return;
    }
    try {
      synchronized (glowLock) {
        nativeGlow.setGlowing(target, player, chatColor(color));
      }
    } catch (ReflectiveOperationException | IllegalStateException failure) {
      reportGlowFailure(failure);
    }
  }

  @Override
  public void clearGlow(PlayerRef viewer, int entityId) {
    Player player = BukkitRefs.player(viewer);
    EntityGlow nativeGlow = glow.get();
    if (player == null || nativeGlow == null) {
      return;
    }
    try {
      synchronized (glowLock) {
        nativeGlow.unsetGlowing(entityId, player);
      }
    } catch (ReflectiveOperationException | IllegalStateException failure) {
      reportGlowFailure(failure);
    }
  }

  @Override
  public void sendEquipment(PlayerRef viewer, EntityRef entity, Map<EquipmentSlot, ItemRef> items) {
    Player player = BukkitRefs.player(viewer);
    if (player == null || entity == null || items.isEmpty()) {
      return;
    }
    Map<org.bukkit.inventory.EquipmentSlot, ItemStack> stacks = stacks(items);
    if (BukkitRefs.entity(entity) instanceof LivingEntity living) {
      player.sendEquipmentChange(living, stacks);
      return;
    }
    EquipmentPackets.send(player, entity.entityId(), stacks);
  }

  @Override
  public VirtualPlayer createVirtualPlayer(PlayerRef skinSource, Loc location, int skinLayerMask) {
    Player source = skinSource == null ? null : BukkitRefs.player(skinSource);
    Location at = location == null ? null : BukkitRefs.location(location);
    VirtualPlayers players = virtualPlayers.get();
    if (source == null || at == null || players == null) {
      return null;
    }
    art.arcane.volmlib.nativelib.entity.VirtualPlayer created = players.create(source, at, skinLayerMask);
    return created == null ? null : new BukkitVirtualPlayer(created);
  }

  @Override
  public void setVisibleByDefault(EntityRef entity, boolean visible) {
    Entity target = entity == null ? null : BukkitRefs.entity(entity);
    if (target != null) {
      target.setVisibleByDefault(visible);
    }
  }

  @Override
  public Collection<PlayerRef> trackingPlayers(EntityRef entity) {
    Entity target = entity == null ? null : BukkitRefs.entity(entity);
    if (target == null) {
      return List.of();
    }
    Set<Player> tracking = PaperCompat.trackedPlayers(target);
    List<PlayerRef> players = new ArrayList<>(tracking.size());
    for (Player player : tracking) {
      players.add(BukkitRefs.player(player));
    }
    return players;
  }

  @Override
  public Collection<PlayerRef> chunkViewers(WorldRef world, int chunkX, int chunkZ) {
    World handle = world == null ? null : BukkitRefs.world(world);
    if (handle == null) {
      return List.of();
    }
    Collection<Player> seeing = BukkitChunkViewers.seeing(handle, chunkX, chunkZ);
    List<PlayerRef> players = new ArrayList<>(seeing.size());
    for (Player player : seeing) {
      players.add(BukkitRefs.player(player));
    }
    return players;
  }

  static Map<org.bukkit.inventory.EquipmentSlot, ItemStack> stacks(Map<EquipmentSlot, ItemRef> items) {
    Map<org.bukkit.inventory.EquipmentSlot, ItemStack> stacks = new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);
    for (Map.Entry<EquipmentSlot, ItemRef> entry : items.entrySet()) {
      ItemStack stack = entry.getValue() == null ? null : BukkitRefs.item(entry.getValue());
      stacks.put(BukkitConversions.slot(entry.getKey()), stack == null ? new ItemStack(Material.AIR) : stack.clone());
    }
    return stacks;
  }

  static ChatColor chatColor(GlowColor color) {
    return ChatColor.valueOf(color.name());
  }

  private void reportGlowFailure(Exception failure) {
    if (glowFailureLogged.compareAndSet(false, true)) {
      log.error("Failed to update a private viewer glow.", failure);
    }
  }
}
