package art.arcane.adapt.mod.platform;

import net.minecraft.server.level.ServerPlayer;

/**
 * A loader permission system (NeoForge or Forge PermissionAPI, fabric-permissions-api) that may answer for an Adapt
 * node before vanilla permission atoms and the registered defaults are consulted.
 */
public interface PermissionBridge {
    /** The permission system's answer, or null when it has none and the defaults decide. */
    Boolean check(ServerPlayer player, String node);
}
