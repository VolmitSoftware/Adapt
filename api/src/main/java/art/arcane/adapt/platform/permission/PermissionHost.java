package art.arcane.adapt.platform.permission;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** Bukkit permissions, or a permission API on modded (NeoForge PermissionAPI, Fabric permissions, else op levels). */
@ApiStatus.AvailableSince("1.0.0")
public interface PermissionHost {
  /**
   * True when the player holds the node. A null or empty node is always held; a node nobody registered resolves
   * as {@link PermissionDefault#OP}.
   */
  boolean has(PlayerRef player, String node);

  /**
   * True when the node is set for the player explicitly (granted or negated), as opposed to resolving from its
   * default. Bukkit: {@code isPermissionSet}. Modded: a loader permission system answered, or a permission atom is
   * granted.
   */
  boolean isSet(PlayerRef player, String node);

  /**
   * Registers a node with the default that applies when nothing sets it explicitly. A node that is already
   * registered, by Adapt, another addon or another plugin, is left as it is, and the returned registration then
   * removes nothing. Removing the returned registration is the only way to remove the node.
   */
  PermissionRegistration register(String node, String description, PermissionDefault fallback);
}
