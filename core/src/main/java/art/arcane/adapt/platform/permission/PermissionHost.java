package art.arcane.adapt.platform.permission;

import art.arcane.adapt.platform.entity.PlayerRef;

/** Bukkit permissions, or a permission API on modded (NeoForge PermissionAPI, Fabric permissions, else op levels). */
public interface PermissionHost {
  boolean has(PlayerRef player, String node);

  /**
   * True when the node is set for the player explicitly (granted or negated), as opposed to resolving from its
   * default. Bukkit: {@code isPermissionSet}. Modded: a loader permission system answered, or a permission atom is
   * granted.
   */
  boolean isSet(PlayerRef player, String node);

  void register(String node, String description, PermissionDefault fallback);
}
