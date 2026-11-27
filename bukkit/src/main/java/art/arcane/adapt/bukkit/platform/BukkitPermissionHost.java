package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.api.internal.HostPermissions;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Bukkit permissions. Registration skips nodes another registrar already added, and removal touches only the nodes
 * this host added.
 */
public final class BukkitPermissionHost implements HostPermissions {
  private final Supplier<PluginManager> plugins;
  private final Set<String> added = ConcurrentHashMap.newKeySet();

  public BukkitPermissionHost(Supplier<PluginManager> plugins) {
    this.plugins = Objects.requireNonNull(plugins, "plugins");
  }

  @Override
  public boolean has(PlayerRef player, String node) {
    if (node == null || node.isEmpty()) {
      return true;
    }
    Player handle = BukkitRefs.player(player);
    return handle != null && handle.hasPermission(node);
  }

  @Override
  public boolean isSet(PlayerRef player, String node) {
    if (node == null || node.isEmpty()) {
      return false;
    }
    Player handle = BukkitRefs.player(player);
    return handle != null && handle.isPermissionSet(node);
  }

  @Override
  public boolean add(String node, String description, PermissionDefault fallback) {
    if (node == null || node.isEmpty() || fallback == null) {
      return false;
    }
    PluginManager manager = plugins.get();
    if (manager == null || manager.getPermission(node) != null) {
      return false;
    }
    manager.addPermission(new Permission(node, description, fallback(fallback)));
    added.add(node);
    return true;
  }

  @Override
  public void unregister(String node) {
    PluginManager manager = plugins.get();
    if (manager != null && node != null && added.remove(node) && manager.getPermission(node) != null) {
      manager.removePermission(node);
    }
  }

  static org.bukkit.permissions.PermissionDefault fallback(PermissionDefault fallback) {
    return switch (fallback) {
      case TRUE -> org.bukkit.permissions.PermissionDefault.TRUE;
      case FALSE -> org.bukkit.permissions.PermissionDefault.FALSE;
      case OP -> org.bukkit.permissions.PermissionDefault.OP;
      case NOT_OP -> org.bukkit.permissions.PermissionDefault.NOT_OP;
    };
  }
}
