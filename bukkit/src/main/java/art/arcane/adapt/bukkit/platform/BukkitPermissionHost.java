package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;
import java.util.function.Supplier;

/** Bukkit permissions. Registration skips nodes another registrar already added. */
public final class BukkitPermissionHost implements PermissionHost {
  private final Supplier<PluginManager> plugins;

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
  public void register(String node, String description, PermissionDefault fallback) {
    if (node == null || node.isEmpty() || fallback == null) {
      return;
    }
    PluginManager manager = plugins.get();
    if (manager == null || manager.getPermission(node) != null) {
      return;
    }
    manager.addPermission(new Permission(node, description, fallback(fallback)));
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
